package com.vulpine.at.svc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.service.notification.StatusBarNotification;
import com.vulpine.at.MainActivity;
import com.vulpine.at.R;

public final class Notifier {
    public static final String CHANNEL = "vulpine_msgs_high";

    public static String safe(String cid) {
        if (cid == null || cid.isEmpty() || cid.length() > 80 || cid.equals("test")) return "";
        for (int i = 0; i < cid.length(); i++) {
            char c = cid.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == ':';
            if (!ok) return "";
        }
        return cid;
    }

    static String cidFromTag(String tag) {
        if (tag == null) return "";
        if (tag.startsWith("vulpine-call-")) return safe(tag.substring(13));
        if (tag.startsWith("vulpine-")) return safe(tag.substring(8));
        return "";
    }

    static int idFor(String tag) {
        return tag == null || tag.isEmpty() ? 1 : Math.abs(tag.hashCode());
    }

    public static void ensureChannel(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Messages and calls", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("New messages and incoming calls");
            ch.enableVibration(true);
            ch.setShowBadge(true);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel("vulpine_messages") != null) nm.deleteNotificationChannel("vulpine_messages");
    }

    static Bitmap appIcon(Context ctx) {
        try {
            return BitmapFactory.decodeResource(ctx.getResources(), R.drawable.vulpine_large);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isActive(Context ctx, String tag) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;
        int id = idFor(tag);
        try {
            for (StatusBarNotification n : nm.getActiveNotifications()) if (n.getId() == id) return true;
        } catch (Exception e) {
        }
        return false;
    }

    public static void post(Context ctx, String title, String body, String tag, Bitmap large, boolean call) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(ctx);
        int id = idFor(tag);
        String cid = cidFromTag(tag);
        Intent open = new Intent(ctx, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!cid.isEmpty()) open.putExtra("cid", cid);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String t = title == null || title.isEmpty() ? "Vulpine" : title;
        String b = body == null ? "" : body;
        Bitmap li = large != null ? large : appIcon(ctx);
        Notification.Builder nb = new Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_vulpine).setColor(0xFFF97316).setContentTitle(t).setContentText(b).setStyle(new Notification.BigTextStyle().bigText(b)).setAutoCancel(true).setShowWhen(true).setWhen(System.currentTimeMillis()).setCategory(call ? Notification.CATEGORY_CALL : Notification.CATEGORY_MESSAGE).setContentIntent(pi);
        if (li != null) nb.setLargeIcon(li);
        if (call) nb.setOngoing(true).setTimeoutAfter(60000);
        nm.notify(id, nb.build());
    }

    public static void cancel(Context ctx, String tag) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(idFor(tag));
    }

    public static void cancelConv(Context ctx, String cid) {
        cancel(ctx, "vulpine-" + cid);
    }

    public static boolean granted(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }
}
