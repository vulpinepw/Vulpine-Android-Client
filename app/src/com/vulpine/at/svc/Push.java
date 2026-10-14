package com.vulpine.at.svc;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import com.vulpine.at.App;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import com.vulpine.core.WsConn;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Push implements Runnable {
    static Push inst;
    static final Map<String, Long> shown = new LinkedHashMap<String, Long>() {
        protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
            return size() > 300;
        }
    };
    final Context ctx;
    final Object lock = new Object();
    volatile boolean stopped;
    volatile WsConn cur;
    volatile long blockedUntil;
    Thread thread;
    JSONObject me;
    long meAt;
    final Map<String, String> names = new HashMap<>(), types = new HashMap<>();
    long convAt;
    final Map<String, Bitmap> avatars = new LinkedHashMap<String, Bitmap>() {
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
            return size() > 40;
        }
    };
    final Map<String, Integer> counts = new HashMap<>();
    final ExecutorService work = Executors.newSingleThreadExecutor();
    boolean netCb;

    Push(Context c) {
        ctx = c.getApplicationContext();
    }

    public static synchronized Push get(Context c) {
        if (inst == null) inst = new Push(c);
        return inst;
    }

    public static void markShown(String mid) {
        if (mid == null || mid.isEmpty()) return;
        synchronized (shown) {
            shown.put(mid, System.currentTimeMillis());
        }
    }

    static boolean wasShown(String mid) {
        synchronized (shown) {
            return shown.containsKey(mid);
        }
    }

    public static boolean enabled() {
        if (App.prefs == null) return true;
        try {
            return !"0".equals(App.prefs.getString("notif", ""));
        } catch (ClassCastException e) {
            return App.prefs.getBoolean("notif", true);
        }
    }

    synchronized void start() {
        stopped = false;
        if (thread != null && thread.isAlive()) return;
        thread = new Thread(this, "vpush");
        thread.setDaemon(true);
        thread.start();
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && !netCb) {
                cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                    public void onAvailable(Network n) {
                        blockedUntil = 0;
                        kick();
                        com.vulpine.core.Rt.I.wakeUp(true);
                    }
                });
                netCb = true;
            }
        } catch (Exception e) {
        }
    }

    void stop() {
        stopped = true;
        kick();
    }

    public void kick() {
        WsConn w = cur;
        if (w != null) w.close();
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    void sleep(long ms) {
        synchronized (lock) {
            try {
                lock.wait(ms);
            } catch (InterruptedException e) {
            }
        }
    }

    public void run() {
        long backoff = 2000;
        while (!stopped) {
            String tok = Session.token();
            long now = System.currentTimeMillis();
            if (tok == null || tok.isEmpty() || now < blockedUntil) {
                sleep(tok == null ? 600000 : Math.max(1000, blockedUntil - now));
                continue;
            }
            WsConn w = null;
            long started = System.currentTimeMillis();
            try {
                w = WsConn.connect(com.vulpine.core.Rt.I.url, Http.origin, Http.userAgent);
                cur = w;
                w.sendText(new JSONObject().put("type", "auth").put("token", tok).put("bg", true).toString());
                final WsConn ww = w;
                Thread pinger = new Thread(() -> {
                    while (cur == ww && !stopped) {
                        try {
                            Thread.sleep(25000);
                            if (cur == ww) ww.sendText("{\"type\":\"ping\"}");
                        } catch (Exception e) {
                            ww.close();
                            return;
                        }
                    }
                }, "vpush-ping");
                pinger.setDaemon(true);
                pinger.start();
                while (!stopped) {
                    String s = w.readText();
                    JSONObject d;
                    try {
                        d = new JSONObject(s);
                    } catch (Exception e) {
                        continue;
                    }
                    String type = d.optString("type");
                    if ("auth.ok".equals(type)) backoff = 2000;
                    else if ("auth.error".equals(type)) {
                        String r = d.optString("reason");
                        if ("invalid_token".equals(r) || "account_locked".equals(r)) blockedUntil = System.currentTimeMillis() + 1800000;
                        else if ("too_many_connections".equals(r)) blockedUntil = System.currentTimeMillis() + 120000;
                    } else work.execute(() -> handle(d));
                }
            } catch (Exception e) {
                if (w != null && (w.closeCode == 4003 || w.closeCode == 4001)) blockedUntil = Math.max(blockedUntil, System.currentTimeMillis() + 1800000);
            } finally {
                cur = null;
                if (w != null) w.close();
            }
            if (stopped) break;
            if (System.currentTimeMillis() - started > 60000) backoff = 2000;
            sleep(backoff);
            backoff = Math.min(backoff * 2, 60000);
        }
    }

    static boolean appVisible() {
        if (App.fg) return true;
        try {
            ActivityManager.RunningAppProcessInfo i = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(i);
            return i.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        } catch (Exception e) {
            return false;
        }
    }

    void handle(JSONObject d) {
        String type = d.optString("type");
        boolean call = "call.incoming".equals(type) || "call.handled".equals(type) || "call.state".equals(type);
        if (appVisible() && !call) {
            counts.clear();
            return;
        }
        if ("message.received".equals(type)) {
            JSONObject m = d.optJSONObject("message");
            if (m != null) onMessage(m);
        } else if ("call.incoming".equals(type) || "call.handled".equals(type) || "call.state".equals(type)) {
            if ("call.incoming".equals(type) && !allowCall(d)) return;
            com.vulpine.at.call.Calls.bgFrame(ctx, d);
        }
    }

    boolean allow() {
        return enabled() && Notifier.granted(ctx);
    }

    JSONObject me() {
        synchronized (lock) {
            if (me != null && System.currentTimeMillis() - meAt < 60000) return me;
        }
        Http.Resp r = Http.get("/me");
        synchronized (lock) {
            if (r.ok()) {
                me = r.obj();
                meAt = System.currentTimeMillis();
            }
            return me;
        }
    }

    static boolean inList(JSONObject m, String key, String cid) {
        if (m == null) return false;
        JSONObject st = m.optJSONObject("settings");
        JSONArray a = st == null ? null : st.optJSONArray(key);
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) if (cid.equals(a.optString(i))) return true;
        return false;
    }

    void loadConvs(String cid) {
        if (types.containsKey(cid) && System.currentTimeMillis() - convAt < 600000) return;
        if (System.currentTimeMillis() - convAt < 15000 && convAt != 0) return;
        Http.Resp r = Http.get("/conversations");
        convAt = System.currentTimeMillis();
        if (!r.ok()) return;
        JSONArray a = r.obj().optJSONArray("conversations");
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject c = a.optJSONObject(i);
            if (c == null) continue;
            String id = c.optString("id");
            types.put(id, c.optString("type"));
            String n = c.optString("name", "");
            if (n.isEmpty()) n = c.optString("peerDisplayName", "");
            names.put(id, n);
        }
    }

    void onMessage(JSONObject m) {
        String mid = m.optString("id"), cid = Notifier.safe(m.optString("conversationId"));
        String uid = Session.userId();
        if (cid.isEmpty() || mid.isEmpty()) return;
        if (!uid.isEmpty() && uid.equals(m.optString("senderId"))) return;
        if (wasShown(mid)) return;
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }
        if (wasShown(mid) || appVisible() || !allow()) return;
        JSONObject u = me();
        if (u != null && "dnd".equals(u.optString("status"))) return;
        if (inList(u, "mutedChatIds", cid)) return;
        boolean locked = u == null || u.optBoolean("hasPin") || inList(u, "hiddenChatIds", cid) || com.vulpine.at.Pin.fake;
        markShown(mid);
        if (!Notifier.isActive(ctx, "vulpine-" + cid)) counts.remove(cid);
        int n = counts.containsKey(cid) ? counts.get(cid) + 1 : 1;
        counts.put(cid, n);
        String body = n > 1 ? n + " new messages" : "New message";
        if (locked) {
            Notifier.post(ctx, "Vulpine", body, "vulpine-" + cid, null, false);
            return;
        }
        JSONObject s = m.optJSONObject("sender");
        String name = s == null ? "" : s.optString("displayName", "");
        if (name.isEmpty() && s != null) name = s.optString("handle", "");
        if (name.isEmpty()) name = "Someone";
        loadConvs(cid);
        String title = name;
        if ("group".equals(types.get(cid))) {
            String g = names.get(cid);
            title = name + " • " + (g == null || g.isEmpty() ? "Group" : g);
        }
        Bitmap icon = avatar(s == null ? null : s.optString("avatarUrl", null), name, s == null ? "" : s.optString("avatarColor", ""));
        Notifier.post(ctx, title, body, "vulpine-" + cid, icon, false);
    }

    boolean allowCall(JSONObject d) {
        String cid = Notifier.safe(d.optString("conversationId"));
        if (cid.isEmpty() || cid.startsWith("r:") || !enabled()) return false;
        JSONObject u = me();
        if (u != null && "dnd".equals(u.optString("status"))) return false;
        if (u == null || u.optBoolean("hasPin") || inList(u, "hiddenChatIds", cid)) {
            try {
                d.put("fromDisplayName", JSONObject.NULL).put("fromAvatarUrl", JSONObject.NULL).put("fromAvatarColor", JSONObject.NULL).put("anon", true);
            } catch (Exception e) {
            }
        }
        return true;
    }

    void onCall(JSONObject d) {
        String cid = Notifier.safe(d.optString("conversationId"));
        if (cid.isEmpty() || cid.startsWith("r:") || !allow()) return;
        JSONObject u = me();
        if (u != null && "dnd".equals(u.optString("status"))) return;
        boolean locked = u == null || u.optBoolean("hasPin") || inList(u, "hiddenChatIds", cid);
        if (locked) {
            Notifier.post(ctx, "Vulpine", "Incoming call", "vulpine-call-" + cid, null, true);
            return;
        }
        String who = d.optString("fromDisplayName", "Someone");
        loadConvs(cid);
        String title = who;
        if ("group".equals(types.get(cid))) {
            String g = names.get(cid);
            title = who + " • " + (g == null || g.isEmpty() ? "Group" : g);
        }
        Bitmap icon = avatar(d.optString("fromAvatarUrl", null), who, d.optString("fromAvatarColor", ""));
        Notifier.post(ctx, title, "Incoming call", "vulpine-call-" + cid, icon, true);
    }

    Bitmap avatar(String url, String name, String color) {
        if (url != null && url.startsWith("/uploads/") && url.length() < 300) {
            synchronized (avatars) {
                if (avatars.containsKey(url)) return avatars.get(url);
            }
            try {
                byte[] b = Http.bytes(url, 6 * 1024 * 1024);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(b, 0, b.length, o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 192 && o.outHeight / (sample * 2) >= 192) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                Bitmap src = BitmapFactory.decodeByteArray(b, 0, b.length, o2);
                if (src != null) {
                    Bitmap r = circle(src);
                    synchronized (avatars) {
                        avatars.put(url, r);
                    }
                    return r;
                }
            } catch (Exception e) {
            }
        }
        return letter(name, color);
    }

    static Bitmap circle(Bitmap src) {
        int s = 192;
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Path p = new Path();
        p.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW);
        cv.clipPath(p);
        float k = Math.max((float) s / src.getWidth(), (float) s / src.getHeight());
        float w = src.getWidth() * k, h = src.getHeight() * k;
        cv.drawBitmap(src, new Rect(0, 0, src.getWidth(), src.getHeight()), new RectF((s - w) / 2f, (s - h) / 2f, (s + w) / 2f, (s + h) / 2f), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
        return out;
    }

    static Bitmap letter(String name, String color) {
        int s = 192;
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        int col = 0xFFF97316;
        try {
            if (color != null && color.matches("#[0-9a-fA-F]{6}")) col = Color.parseColor(color);
        } catch (Exception e) {
        }
        bg.setColor(col);
        cv.drawCircle(s / 2f, s / 2f, s / 2f, bg);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(Color.WHITE);
        t.setTextSize(90);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextAlign(Paint.Align.CENTER);
        String ch = name == null || name.trim().isEmpty() ? "?" : name.trim().substring(0, name.trim().offsetByCodePoints(0, 1)).toUpperCase();
        Paint.FontMetrics fm = t.getFontMetrics();
        cv.drawText(ch, s / 2f, s / 2f - (fm.ascent + fm.descent) / 2f, t);
        return out;
    }
}
