package com.vulpine.at.call;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.core.Chats;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Calls {
    public static Sig sig;
    public static CallMedia media;
    public static CallUi ui;
    public static boolean minimized;
    public static MediaFactory factory = RtcMedia::new;
    static boolean inited;
    static String acceptPending;
    static Sig.Incoming bgInc;
    static Intent screenData;
    static final String RING_CH = "vulpine_ring";

    public interface MediaFactory {
        CallMedia make();
    }

    static final Rt.Listener WS = m -> {
        String t = m.optString("type");
        if (t.startsWith("call.") || "auth.ok".equals(t) || "system.message".equals(t)) App.H.post(() -> frame(m));
    };

    public static synchronized void init() {
        if (inited) return;
        inited = true;
        sig = new Sig(new Host());
        sig.me = Session.userId();
        Rt.I.on(WS);
        try {
            ((Application) App.ctx.getApplicationContext()).registerActivityLifecycleCallbacks(new Life());
        } catch (Exception e) {
        }
        MainActivity a = MainActivity.cur;
        if (a != null) attach(a);
    }

    public static void reset() {
        if (sig != null && sig.cur != null) sig.end();
        if (sig != null) {
            sig.inc = null;
            sig.activeConvs.clear();
            sig.members.clear();
        }
        CallAudio.ringStop();
        cancelRing(null);
        syncConvs();
    }

    static void attach(MainActivity a) {
        if (ui != null && ui.a == a) {
            ui.refresh();
            return;
        }
        if (ui != null) try {
            ui.detach();
        } catch (Exception e) {
        }
        ui = new CallUi(a);
        ui.refresh();
    }

    static void frame(JSONObject m) {
        String t = m.optString("type");
        sig.me = Session.userId();
        if ("auth.ok".equals(t)) {
            sig.activeConvs.clear();
            syncConvs();
        }
        if ("call.rooms".equals(t)) {
            Rooms.onWs(m);
            return;
        }
        if ("system.message".equals(t)) {
            systemMessage(m);
            return;
        }
        sig.onFrame(m);
        if ("call.incoming".equals(t) && sig.inc != null && sig.cur == null) bgInc = null;
        if (("call.handled".equals(t) || ("call.state".equals(t) && !m.optBoolean("active"))) && sig.inc == null) {
            Sig.Incoming b = bgInc;
            if (b != null && (m.optString("callId").equals(b.callId) || m.optString("conversationId").equals(b.cid))) {
                bgInc = null;
                stopRinging();
            }
            cancelRing(m.optString("conversationId"));
        }
    }

    static void systemMessage(JSONObject m) {
        String text = m.optString("text");
        if (text.isEmpty()) return;
        String cid = m.optString("conversationId");
        if (Sync.contains(Session.settings().optJSONArray("mutedChatIds"), cid)) return;
        if (Session.s != null && "dnd".equals(Session.s.optString("status"))) return;
        String name = m.isNull("fromDisplayName") ? null : m.optString("fromDisplayName", null);
        Ov.toastCard("info", (name == null ? "Someone" : name) + ": " + text);
    }

    static void syncConvs() {
        java.util.Set<String> s = com.vulpine.at.home.ChatsTab.callConvs;
        synchronized (s) {
            s.clear();
            if (sig != null) s.addAll(sig.activeConvs);
        }
        com.vulpine.at.home.Home.refreshLists();
    }

    static final class Host implements Sig.Host {
        public void send(JSONObject m) {
            Rt.I.send(m);
        }

        public void post(Runnable r, long ms) {
            App.H.postDelayed(r, ms);
        }

        public Sig.Peer newPeer(String uid) {
            return media == null ? new Dead() : media.newPeer(uid);
        }

        public void acquire(boolean video, Sig.Cb<Boolean> ok) {
            MainActivity a = MainActivity.cur;
            Runnable go = () -> {
                if (media != null) media.release();
                media = factory.make();
                media.sig = sig;
                media.acquire(video && granted(Manifest.permission.CAMERA), r -> {
                    if (!r) {
                        media = null;
                        ok.on(false);
                        return;
                    }
                    ok.on(true);
                });
            };
            if (granted(Manifest.permission.RECORD_AUDIO) && (!video || granted(Manifest.permission.CAMERA))) {
                go.run();
                return;
            }
            if (a == null) {
                ok.on(false);
                return;
            }
            java.util.List<String> p = new java.util.ArrayList<>();
            p.add(Manifest.permission.RECORD_AUDIO);
            if (video) p.add(Manifest.permission.CAMERA);
            if (Build.VERSION.SDK_INT >= 31) p.add(Manifest.permission.BLUETOOTH_CONNECT);
            App.requestPerms(a, p.toArray(new String[0]), g -> {
                if (granted(Manifest.permission.RECORD_AUDIO)) go.run();
                else ok.on(false);
            });
        }

        public void release() {
            if (media != null) media.release();
            media = null;
        }

        public boolean localVideo() {
            return media != null && media.localVideo();
        }

        public void event(String what, Object arg) {
            switch (what) {
                case "changed":
                    if (ui != null) ui.refresh();
                    break;
                case "convs":
                    syncConvs();
                    break;
                case "maximize":
                    maximize();
                    break;
                case "toast-error":
                    Ov.toast(String.valueOf(arg), true);
                    break;
                case "toast":
                    Ov.toast(String.valueOf(arg));
                    break;
                case "started":
                    minimized = false;
                    Rt.I.callHint = true;
                    CallAudio.start();
                    CallService.update(App.ctx, false);
                    cancelRing(null);
                    break;
                case "ring":
                    ring((Sig.Incoming) arg);
                    break;
                case "ringStop":
                    stopRinging();
                    break;
                case "ended":
                    Rt.I.callHint = false;
                    minimized = false;
                    CallService.stop(App.ctx);
                    CallAudio.stop();
                    screenData = null;
                    break;
                case "rate":
                    if (ui != null && Rate.due()) ui.showRate(String.valueOf(arg));
                    break;
                case "beep":
                    CallAudio.beep("join".equals(arg));
                    break;
                case "mic":
                    if (media != null) media.setMic((Boolean) arg);
                    break;
                case "deaf":
                    if (media != null) media.setDeaf((Boolean) arg);
                    break;
            }
        }
    }

    static boolean granted(String p) {
        return App.ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    static final class Dead implements Sig.Peer {
        public String sigState() {
            return "closed";
        }

        public String connState() {
            return "closed";
        }

        public boolean hasRemote() {
            return false;
        }

        public String localSdp() {
            return null;
        }

        public String remoteSdp() {
            return null;
        }

        public void createOffer(boolean r, Sig.Cb<String> cb) {
            cb.on(null);
        }

        public void createAnswer(Sig.Cb<String> cb) {
            cb.on(null);
        }

        public void setLocal(String t, String s, Sig.Cb<Boolean> ok) {
            ok.on(false);
        }

        public void setRemote(String t, String s, Sig.Cb<Boolean> ok) {
            ok.on(false);
        }

        public void addIce(JSONObject c) {
        }

        public void restartIce() {
        }

        public void close() {
        }
    }

    static String ringingId, notifiedId;

    static boolean visible() {
        MainActivity a = MainActivity.cur;
        return App.fg && a != null && a.hasWindowFocus();
    }

    static void ring(Sig.Incoming inc) {
        if (inc == null) return;
        if (!inc.callId.equals(ringingId)) {
            ringingId = inc.callId;
            CallAudio.ringStart();
        }
        if (!visible()) notifyIncoming(App.ctx, inc);
    }

    static void stopRinging() {
        ringingId = null;
        CallAudio.ringStop();
    }

    public static void start(MainActivity a, String cid, boolean video) {
        start(a, cid, video, null);
    }

    public static void start(MainActivity a, String cid, boolean video, JSONArray members) {
        init();
        if (a != null) attach(a);
        if (sig.cur != null && !cid.equals(sig.cur.cid)) {
            final JSONArray mm = members;
            hangUp();
            App.H.postDelayed(() -> start(a, cid, video, mm), 400);
            return;
        }
        JSONArray fb = members;
        if (fb == null && !cid.startsWith("r:")) {
            Chats.Conv c = Chats.I.conv(cid);
            if (c != null && !c.group()) {
                try {
                    fb = new JSONArray().put(new JSONObject().put("userId", c.o.optString("peerId")).put("displayName", c.o.optString("peerDisplayName", c.o.optString("peerHandle"))).put("avatarUrl", c.avatarUrl() == null ? JSONObject.NULL : c.avatarUrl()).put("avatarColor", c.avatarColor() == null ? JSONObject.NULL : c.avatarColor()));
                } catch (Exception e) {
                }
            }
        }
        sig.start(cid, fb, video);
    }

    public static void joinRoom(MainActivity a, String roomKey, JSONArray members) {
        init();
        if (a != null) attach(a);
        Rooms.join(a, roomKey.startsWith("r:") ? roomKey : "r:" + roomKey, members == null ? new JSONArray() : members);
    }

    public static void openRooms(MainActivity a, String gid) {
        init();
        if (a != null) attach(a);
        Rooms.open(a, gid);
    }

    public static boolean inCall() {
        return sig != null && sig.cur != null;
    }

    public static boolean activeIn(String cid) {
        return sig != null && sig.cur != null && cid != null && cid.equals(sig.cur.cid);
    }

    public static boolean callLive(String cid) {
        return sig != null && sig.activeConvs.contains(cid);
    }

    public static JSONArray members(String cid) {
        return sig == null ? null : sig.members.get(cid);
    }

    public static JSONArray rooms(String gid) {
        return Rooms.list(gid);
    }

    public static boolean roomsLive(String gid) {
        return Rooms.anyLive(gid);
    }

    public static void accept() {
        if (sig == null) return;
        Sig.Incoming i = sig.inc;
        if (i == null) return;
        if (!Rt.I.authed()) {
            acceptFrom(i.callId);
            return;
        }
        stopRinging();
        cancelRing(null);
        sig.accept();
    }

    public static void decline() {
        if (sig == null) return;
        Sig.Incoming i = sig.inc;
        if (i == null) return;
        declineFrom(i.callId, i.cid);
    }

    public static void hangUp() {
        if (sig == null || sig.cur == null) return;
        sig.end();
    }

    public static void minimize() {
        if (sig == null || sig.cur == null) return;
        minimized = true;
        if (ui != null) ui.refresh();
    }

    public static void maximize() {
        minimized = false;
        if (ui != null) ui.refresh();
    }

    public static void toggleMic() {
        if (sig == null || sig.cur == null) return;
        sig.setMic(!sig.mic);
    }

    public static void toggleDeafen() {
        if (sig == null || sig.cur == null) return;
        sig.toggleDeafen();
    }

    public static void toggleCam() {
        if (sig == null || sig.cur == null || media == null) return;
        boolean on = !media.camOn();
        Runnable go = () -> {
            boolean hadScreen = media.screenOn();
            media.setCam(on, ok -> {
                if (hadScreen && on) CallService.wantScreen = false;
                sig.cam = media.camOn();
                sig.screen = media.screenOn();
                sig.renegotiateAll();
                sig.sendState(null);
                CallService.update(App.ctx, media.screenOn());
                CallAudio.apply();
                if (ui != null) ui.refresh();
            });
        };
        if (on && !granted(Manifest.permission.CAMERA)) {
            MainActivity a = MainActivity.cur;
            if (a == null) return;
            App.requestPerms(a, new String[]{Manifest.permission.CAMERA}, g -> {
                if (g && media != null) go.run();
            });
            return;
        }
        go.run();
    }

    public static void shareSound() {
        Ov.toast("Sound sharing isn't available in the Android app yet", true);
    }

    public static void flipCam() {
        if (media != null) media.switchCam();
    }

    public static void speakerTap() {
        int h = CallAudio.headset();
        MainActivity a = MainActivity.cur;
        if (h >= 0 && ui != null && a != null) {
            Ov.Menu mm = new Ov.Menu(a);
            mm.item(h == CallAudio.BLUETOOTH ? "Bluetooth" : "Headset", null, false, () -> route(h));
            mm.item("Speaker", null, false, () -> route(CallAudio.SPEAKER));
            if (CallAudio.hasEarpiece()) mm.item("Phone", null, false, () -> route(CallAudio.EARPIECE));
            mm.showAt(a.host.getWidth() / 2, a.host.getHeight() - T.dp(300), false);
            return;
        }
        route(CallAudio.speakerOn() && CallAudio.hasEarpiece() ? CallAudio.EARPIECE : CallAudio.SPEAKER);
    }

    static void route(int r) {
        CallAudio.set(r);
        if (ui != null) ui.refresh();
    }

    public static void toggleScreen() {
        if (sig == null || sig.cur == null || media == null) return;
        if (media.screenOn()) {
            stopScreen();
            return;
        }
        MainActivity a = MainActivity.cur;
        if (a == null) return;
        MediaProjectionManager mpm = (MediaProjectionManager) App.ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        App.startForResult(a, mpm.createScreenCaptureIntent(), (res, data) -> {
            if (res != Activity.RESULT_OK || data == null || media == null || sig.cur == null) return;
            screenData = data;
            CallService.update(App.ctx, true);
            App.H.postDelayed(() -> {
                if (screenData != null) serviceReady();
            }, 1500);
        });
    }

    static void serviceReady() {
        Intent d = screenData;
        if (d == null || !CallService.wantScreen || media == null || sig == null || sig.cur == null) return;
        screenData = null;
        if (!CallService.projOk) {
            CallService.update(App.ctx, false);
            Ov.toast("Couldn't start screen sharing", true);
            return;
        }
        media.startScreen(d, ok -> {
            if (!ok) CallService.update(App.ctx, false);
            sig.cam = media.camOn();
            sig.screen = media.screenOn();
            sig.renegotiateAll();
            sig.sendState(null);
            if (ui != null) ui.refresh();
        });
    }

    public static void stopScreen() {
        if (media == null) return;
        media.stopScreen();
        if (sig != null) {
            sig.screen = false;
            sig.renegotiateAll();
            sig.sendState(null);
        }
        CallService.update(App.ctx, false);
        if (ui != null) ui.refresh();
    }

    static void notifyIncoming(Context c, Sig.Incoming inc) {
        if (inc == null || inc.cid == null || inc.cid.startsWith("r:")) return;
        if (inc.callId.equals(notifiedId)) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(RING_CH, "Incoming calls", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            String title = inc.name == null || inc.anon ? "Vulpine" : inc.name;
            Chats.Conv cv = inc.anon ? null : Chats.I.conv(inc.cid);
            if (cv != null && cv.group()) title = title + " • " + cv.title();
            Intent full = new Intent(c, IncomingActivity.class).putExtra("callId", inc.callId).putExtra("cid", inc.cid).putExtra("name", inc.anon ? null : inc.name).putExtra("from", inc.from).putExtra("avatarUrl", inc.avatarUrl).putExtra("avatarColor", inc.avatarColor).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            PendingIntent fpi = PendingIntent.getActivity(c, 11, full, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent api = PendingIntent.getActivity(c, 12, new Intent(full).setAction(IncomingActivity.ACCEPT), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent dpi = PendingIntent.getBroadcast(c, 13, new Intent(c, CallActions.class).setAction(CallActions.DECLINE).putExtra("callId", inc.callId).putExtra("cid", inc.cid), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            boolean fsi = Build.VERSION.SDK_INT < 34;
            if (!fsi) try {
                fsi = nm.canUseFullScreenIntent();
            } catch (Throwable e) {
                fsi = true;
            }
            if (!fsi) App.prefs.edit().putBoolean("fsi.missed", true).apply();
            String tag = "vulpine-call-" + inc.cid;
            try {
                nm.notify(tag, 4, ringNote(c, inc, title, fpi, api, dpi, fsi, fsi));
            } catch (Exception e) {
                nm.notify(tag, 4, ringNote(c, inc, title, fpi, api, dpi, fsi, false));
            }
            notifiedId = inc.callId;
        } catch (Exception e) {
        }
    }

    static Notification ringNote(Context c, Sig.Incoming inc, String title, PendingIntent fpi, PendingIntent api, PendingIntent dpi, boolean fsi, boolean style) {
        Notification.Builder b = new Notification.Builder(c, RING_CH)
                .setSmallIcon(c.getResources().getIdentifier("ic_stat_vulpine", "drawable", c.getPackageName()))
                .setContentTitle(title)
                .setContentText("Incoming call")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(60000)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(fpi);
        if (fsi) b.setFullScreenIntent(fpi, true);
        boolean styled = false;
        if (style && Build.VERSION.SDK_INT >= 31) try {
            Person p = new Person.Builder().setName(inc.name == null ? "Vulpine" : inc.name).setImportant(true).build();
            b.setStyle(Notification.CallStyle.forIncomingCall(p, dpi, api));
            styled = true;
        } catch (Exception e) {
        }
        if (!styled) {
            b.addAction(new Notification.Action.Builder(null, "Decline", dpi).build());
            b.addAction(new Notification.Action.Builder(null, "Accept", api).build());
        }
        return b.build();
    }

    static void cancelRing(String cid) {
        notifiedId = null;
        try {
            NotificationManager nm = (NotificationManager) App.ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (cid != null) nm.cancel("vulpine-call-" + cid, 4);
            else for (android.service.notification.StatusBarNotification s : nm.getActiveNotifications())
                if (s.getTag() != null && s.getTag().startsWith("vulpine-call-")) nm.cancel(s.getTag(), s.getId());
        } catch (Exception e) {
        }
        IncomingActivity.dismiss();
    }

    public static void bgFrame(Context c, JSONObject m) {
        App.init(c.getApplicationContext());
        App.H.post(() -> {
            String t = m.optString("type");
            if (com.vulpine.at.Pin.fake) return;
            if ("call.incoming".equals(t)) {
                String id = m.optString("callId");
                if (sig != null && sig.cur != null) return;
                Sig.Incoming cur = pendingIncoming();
                if (cur != null && id.equals(cur.callId)) {
                    ring(cur);
                    return;
                }
                Sig.Incoming i = new Sig.Incoming();
                i.callId = id;
                i.cid = m.optString("conversationId");
                i.from = m.optString("fromUserId");
                i.name = m.isNull("fromDisplayName") ? null : m.optString("fromDisplayName", null);
                i.avatarUrl = m.isNull("fromAvatarUrl") ? null : m.optString("fromAvatarUrl", null);
                i.avatarColor = m.isNull("fromAvatarColor") ? null : m.optString("fromAvatarColor", null);
                i.anon = m.optBoolean("anon");
                bgInc = i;
                if (sig != null && sig.inc == null) {
                    sig.inc = i;
                    if (ui != null) ui.refresh();
                }
                ring(i);
            } else if ("call.handled".equals(t) || ("call.state".equals(t) && !m.optBoolean("active"))) {
                String id = m.optString("callId"), cid = m.optString("conversationId");
                Sig.Incoming i = pendingIncoming();
                if (i == null || !(id.equals(i.callId) || cid.equals(i.cid))) return;
                bgInc = null;
                if (sig != null && sig.inc == i) {
                    sig.inc = null;
                    if (ui != null) ui.refresh();
                }
                if (acceptPending != null && acceptPending.equals(i.callId)) acceptPending = null;
                stopRinging();
                cancelRing(i.cid);
            }
        });
    }

    static Sig.Incoming pendingIncoming() {
        if (sig != null && sig.inc != null) return sig.inc;
        return bgInc;
    }

    static void ensureRt() {
        if (!com.vulpine.core.Session.loggedIn() || com.vulpine.at.Pin.fake) return;
        Rt.I.start();
        Rt.I.wakeUp(false);
    }

    static void acceptFrom(String callId) {
        acceptPending = callId;
        acceptAt = System.currentTimeMillis();
        stopRinging();
        cancelRing(null);
        tryPending();
    }

    static void declineFrom(String callId, String cid) {
        stopRinging();
        cancelRing(cid);
        if (sig != null && sig.inc != null && sig.inc.callId.equals(callId)) {
            sig.inc = null;
            if (ui != null) ui.refresh();
        }
        if (bgInc != null && bgInc.callId.equals(callId)) bgInc = null;
        if (acceptPending != null && acceptPending.equals(callId)) acceptPending = null;
        JSONObject rj = Sig.jo("type", "call.reject", "callId", callId, "conversationId", cid);
        if (Rt.I.authed()) {
            Rt.I.send(rj);
            return;
        }
        ensureRt();
        final long until = System.currentTimeMillis() + 20000;
        App.H.postDelayed(new Runnable() {
            public void run() {
                if (Rt.I.authed()) {
                    Rt.I.send(rj);
                    App.H.postDelayed(() -> {
                        if (!App.fg && !inCall() && acceptPending == null) Rt.I.stop();
                    }, 5000);
                } else if (System.currentTimeMillis() < until) App.H.postDelayed(this, 300);
                else if (!App.fg && !inCall()) Rt.I.stop();
            }
        }, 300);
    }

    static long acceptAt;

    static void tryPending() {
        String id = acceptPending;
        if (id == null) return;
        if (System.currentTimeMillis() - acceptAt > 45000) {
            acceptPending = null;
            return;
        }
        if (sig == null || MainActivity.cur == null || !App.fg) return;
        if (!Rt.I.authed()) {
            ensureRt();
            App.H.removeCallbacks(Calls::tryPending);
            App.H.postDelayed(Calls::tryPending, 300);
            return;
        }
        acceptPending = null;
        if (sig.inc == null && bgInc != null && bgInc.callId.equals(id) && sig.cur == null) sig.inc = bgInc;
        bgInc = null;
        if (sig.inc != null && sig.inc.callId.equals(id)) {
            cancelRing(null);
            sig.accept();
        }
    }

    static void onFront(MainActivity a) {
        if (sig == null) return;
        if (sig.cur == null && sig.inc == null && bgInc != null && acceptPending == null) sig.inc = bgInc;
        if (sig.inc != null && sig.cur == null && acceptPending == null) {
            if (ui != null) ui.refresh();
            cancelRing(null);
            if (!sig.inc.callId.equals(ringingId)) {
                ringingId = sig.inc.callId;
                CallAudio.ringStart();
            }
        }
        askFsi(a);
    }

    static void askFsi(MainActivity a) {
        if (Build.VERSION.SDK_INT < 34 || !App.prefs.getBoolean("fsi.missed", false) || App.prefs.getBoolean("fsi.asked", false)) return;
        try {
            NotificationManager nm = (NotificationManager) App.ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.canUseFullScreenIntent()) return;
        } catch (Exception e) {
            return;
        }
        App.prefs.edit().putBoolean("fsi.asked", true).apply();
        App.H.postDelayed(() -> Ov.confirm("Show incoming calls", "Allow Vulpine to show incoming calls on the full screen, also when your phone is locked.", "Allow", false, () -> {
            try {
                a.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) {
            }
        }), 800);
    }

    static final class Life implements Application.ActivityLifecycleCallbacks {
        public void onActivityCreated(Activity a, Bundle b) {
        }

        public void onActivityStarted(Activity a) {
        }

        public void onActivityResumed(Activity a) {
            if (a instanceof MainActivity) {
                attach((MainActivity) a);
                App.H.postDelayed(Calls::tryPending, 200);
                App.H.postDelayed(() -> onFront((MainActivity) a), 250);
            }
        }

        public void onActivityPaused(Activity a) {
        }

        public void onActivityStopped(Activity a) {
        }

        public void onActivitySaveInstanceState(Activity a, Bundle b) {
        }

        public void onActivityDestroyed(Activity a) {
            if (ui != null && ui.a == a) {
                try {
                    ui.detach();
                } catch (Exception e) {
                }
                ui = null;
            }
        }
    }
}
