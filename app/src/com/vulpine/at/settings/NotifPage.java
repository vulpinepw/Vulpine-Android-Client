package com.vulpine.at.settings;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import org.json.JSONObject;

public class NotifPage extends SetPage {
    public static final String CHANNEL = com.vulpine.at.svc.Notifier.CHANNEL;
    boolean busy;

    public NotifPage() {
        super("notifications", "Notifications");
    }

    public static boolean enabled() {
        return !"0".equals(App.prefs.getString("notif", ""));
    }

    public static void setEnabled(boolean on) {
        App.prefs.edit().putString("notif", on ? "1" : "0").apply();
    }

    static boolean permitted(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm == null || nm.areNotificationsEnabled();
    }

    static boolean blocked(Context c) {
        if (permitted(c)) return false;
        if (Build.VERSION.SDK_INT >= 33) return App.prefs.getBoolean("notif.asked", false) && !(c instanceof android.app.Activity && ((android.app.Activity) c).shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS"));
        return true;
    }

    protected void content(LinearLayout body) {
        Context c = a;
        JSONObject st = Session.settings();
        add(S.switchCard(c, "Toast notifications", "Show in-app notifications for events.", st.optBoolean("toastEnabled", true), v -> {
            put("toastEnabled", v);
            if (v) Ov.toast("Notifications enabled");
        }));
        add(S.switchCard(c, "Notification sound", "Play a sound when a notification appears.", st.optBoolean("toastSound", true), v -> {
            put("toastSound", v);
            if (v) {
                Ov.toast("Sound on - this is a test");
                testSound();
            }
        }));
        add(deviceCard(c), 24);
        add(soundsCard(c), 24);
    }

    void put(String k, boolean v) {
        try {
            Session.settings().put(k, v);
        } catch (Exception e) {
        }
        Sync.save();
    }

    void testSound() {
        try {
            android.media.MediaPlayer mp = new android.media.MediaPlayer();
            String u = Session.s == null ? "" : Session.s.optString("notifSoundUrl", "");
            if (!u.isEmpty() && !"null".equals(u)) {
                java.io.File f = com.vulpine.at.ui.Img.cached(Http.abs(u));
                if (f != null) mp.setDataSource(f.getPath());
                else mp.setDataSource(Http.abs(u));
            } else {
                android.content.res.AssetFileDescriptor fd = a.getAssets().openFd("default-ringtone.mp3");
                mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), Math.min(fd.getLength(), 60000));
            }
            mp.setOnPreparedListener(m -> m.start());
            mp.setOnCompletionListener(android.media.MediaPlayer::release);
            mp.prepareAsync();
            App.H.postDelayed(() -> {
                try {
                    if (mp.isPlaying()) mp.stop();
                    mp.release();
                } catch (Exception e) {
                }
            }, 1600);
        } catch (Exception e) {
        }
    }

    View deviceCard(Context c) {
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Desktop notifications", 500));
        boolean on = enabled() && permitted(c);
        boolean bl = blocked(c);
        String d = "Native notifications with the sender and a message preview while Vulpine is in the background. When the app is locked, they only say \"New message\".";
        if (bl) d += " Blocked - allow notifications for Vulpine in your phone settings.";
        S.add(card, S.secondary(c, d, 13), 4);
        LinearLayout row = W.row(c);
        if (on) {
            row.addView(S.smallBtn(c, "Disable", false, v -> {
                setEnabled(false);
                rebuild();
            }), W.lp(0, W.WC, 1));
            LinearLayout.LayoutParams p = W.lp(0, W.WC, 1);
            p.leftMargin = T.dp(8);
            row.addView(S.smallBtn(c, "Send test", false, v -> sendTest(c)), p);
        } else {
            row.addView(S.smallBtn(c, "Enable", false, v -> enable(c, bl)), W.lp(0, W.WC, 1));
        }
        S.add(card, row, 12);
        return card;
    }

    void enable(Context c, boolean bl) {
        if (permitted(c)) {
            setEnabled(true);
            Ov.toast("Desktop notifications enabled");
            rebuild();
            return;
        }
        if (bl || Build.VERSION.SDK_INT < 33) {
            try {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
                a.startActivity(i);
            } catch (Exception e) {
            }
            setEnabled(true);
            return;
        }
        App.prefs.edit().putBoolean("notif.asked", true).apply();
        App.requestPerms(a, new String[]{"android.permission.POST_NOTIFICATIONS"}, ok -> {
            if (ok) {
                setEnabled(true);
                Ov.toast("Desktop notifications enabled");
            } else Ov.toast("Permission was not granted", true);
            if (alive) rebuild();
        });
    }

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        com.vulpine.at.svc.Notifier.ensureChannel(c);
    }

    void sendTest(Context c) {
        try {
            ensureChannel(c);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b = new Notification.Builder(c, CHANNEL).setContentTitle("Vulpine").setContentText("Notifications are working").setSmallIcon(c.getApplicationInfo().icon).setAutoCancel(true);
            nm.notify(4242, b.build());
        } catch (Exception e) {
            Ov.toast("Could not send", true);
        }
    }

    View soundsCard(Context c) {
        LinearLayout box = W.col(c);
        box.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        boolean pro = S.pro();
        box.addView(S.text(c, "Custom sounds" + (pro ? "" : " (Pro)"), 14, 600, T.text, 21));
        S.add(box, S.muted(c, "Upload your own .mp3 ringtone and notification sound."), 2);
        S.Flow f = new S.Flow(c, 8, 8);
        f.addView(soundBtn(c, "Ringtone", "ringtoneUrl", "ringtone", pro));
        f.addView(soundBtn(c, "Notif. sound", "notifSoundUrl", "notif", pro));
        S.add(box, f, 8);
        return box;
    }

    View soundBtn(Context c, String label, String field, String kind, boolean pro) {
        String u = Session.s == null ? "" : Session.s.optString(field, "");
        boolean set = !u.isEmpty() && !"null".equals(u);
        LinearLayout r = W.row(c);
        TextView b = W.text(c, label + (set ? " - change" : " - upload"), 13, 400, T.text);
        b.setPadding(T.dp(12), T.dp(5), T.dp(12), T.dp(5));
        T.lh(b, 19.5f);
        b.setPadding(T.dp(12), T.dp(5) + b.getPaddingTop(), T.dp(12), T.dp(5) + b.getPaddingBottom());
        b.setBackground(W.ripple(W.round(T.surface2, 999), 999));
        b.setOnClickListener(v -> {
            if (!pro) {
                Ov.toast("Custom sounds are a Pro Box feature");
                return;
            }
            Pick.file(a, new String[]{"audio/mpeg", "audio/mp3"}, (f, name, mime) -> upload(kind, f, name));
        });
        r.addView(b);
        if (set) {
            TextView rm = W.text(c, "remove", 13, 400, T.danger);
            rm.setPadding(T.dp(6), T.dp(5), T.dp(6), T.dp(5));
            rm.setOnClickListener(v -> remove(kind));
            LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
            p.leftMargin = T.dp(6);
            r.addView(rm, p);
        }
        return r;
    }

    void upload(String kind, java.io.File f, String name) {
        if (busy) return;
        busy = true;
        App.bg(() -> {
            String nm = name.toLowerCase().endsWith(".mp3") ? name : name + ".mp3";
            Http.Resp r = Http.upload("/me/sound?kind=" + kind, "file", f, nm, "audio/mpeg", null);
            f.delete();
            App.ui(() -> {
                busy = false;
                if (r.ok()) {
                    Session.merge(r.obj());
                    Ov.toast("Sound updated");
                } else Ov.toast(r.status == 413 ? "File too large (max 10 MB)" : S.err(r, "Upload failed"), true);
                if (alive) rebuild();
            });
        });
    }

    void remove(String kind) {
        App.bg(() -> {
            Http.Resp r = Http.delete("/me/sound?kind=" + kind, null);
            App.ui(() -> {
                if (r.ok()) {
                    Session.merge(r.obj());
                    Ov.toast("Sound removed");
                } else Ov.toast(S.err(r, "Failed"), true);
                if (alive) rebuild();
            });
        });
    }
}
