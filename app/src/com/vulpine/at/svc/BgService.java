package com.vulpine.at.svc;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import com.vulpine.at.App;
import com.vulpine.core.Session;

public class BgService extends Service {
    static final String CHANNEL_KEEP = "vulpine_keepalive";

    public static void start(Context c) {
        try {
            if (!Session.loggedIn()) return;
            c.getApplicationContext().startService(new Intent(c.getApplicationContext(), BgService.class));
        } catch (Exception e) {
        }
    }

    public static void stop(Context c) {
        try {
            Push.get(c).stop();
            c.getApplicationContext().stopService(new Intent(c.getApplicationContext(), BgService.class));
        } catch (Exception e) {
        }
    }

    public IBinder onBind(Intent i) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        App.init(getApplicationContext());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notifier.ensureChannel(this);
        if (nm != null) {
            nm.cancel(2);
            if (nm.getNotificationChannel(CHANNEL_KEEP) != null) nm.deleteNotificationChannel(CHANNEL_KEEP);
        }
        Push.get(this).start();
    }

    public int onStartCommand(Intent i, int flags, int id) {
        if (!Session.loggedIn()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Push.get(this).start();
        return START_STICKY;
    }

    public void onTaskRemoved(Intent i) {
        super.onTaskRemoved(i);
        Push.get(this).start();
    }
}
