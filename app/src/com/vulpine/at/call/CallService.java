package com.vulpine.at.call;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;

public class CallService extends Service {
    static final String CH = "vulpine_call";
    static final int ID = 3;
    static boolean wantScreen, running, pending, projOk;

    static int types(Context c) {
        int t = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        if (Build.VERSION.SDK_INT >= 30 && Calls.media != null && Calls.media.camOn() && c.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            t |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        if (wantScreen) t |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        return t;
    }

    public static void update(Context c, boolean screen) {
        wantScreen = screen;
        try {
            c.startForegroundService(new Intent(c, CallService.class));
            pending = true;
        } catch (Exception e) {
        }
    }

    public static void stop(Context c) {
        wantScreen = false;
        if (pending) return;
        try {
            c.stopService(new Intent(c, CallService.class));
        } catch (Exception e) {
        }
    }

    public IBinder onBind(Intent i) {
        return null;
    }

    Notification build() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "Ongoing calls", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        if (nm != null) nm.createNotificationChannel(ch);
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 7, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent hang = PendingIntent.getBroadcast(this, 8, new Intent(this, CallActions.class).setAction(CallActions.HANGUP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(getResources().getIdentifier("ic_stat_vulpine", "drawable", getPackageName()))
                .setContentTitle("Vulpine")
                .setContentText(wantScreen ? "Sharing your screen" : "Call in progress")
                .setContentIntent(pi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .addAction(new Notification.Action.Builder(null, "Hang up", hang).build());
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    public int onStartCommand(Intent i, int flags, int id) {
        Notification n = build();
        pending = false;
        if (!Calls.inCall()) {
            try {
                if (Build.VERSION.SDK_INT >= 30) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                else startForeground(ID, n);
            } catch (Exception e) {
            }
            try {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } catch (Exception e) {
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean ok = false;
        int[] tries = {types(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | (wantScreen ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION : 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE};
        for (int t : tries) {
            try {
                if (Build.VERSION.SDK_INT >= 30) startForeground(ID, n, t);
                else startForeground(ID, n);
                ok = true;
                projOk = Build.VERSION.SDK_INT < 30 || (t & ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) != 0;
                break;
            } catch (Exception e) {
            }
        }
        if (!ok) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        App.H.post(Calls::serviceReady);
        return START_NOT_STICKY;
    }

    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    public void onTaskRemoved(Intent i) {
        super.onTaskRemoved(i);
        App.H.post(() -> {
            if (Calls.inCall()) Calls.hangUp();
        });
        stopSelf();
    }
}
