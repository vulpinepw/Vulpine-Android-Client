package com.vulpine.at.svc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.vulpine.at.App;
import com.vulpine.core.Session;

public class Boot extends BroadcastReceiver {
    public void onReceive(Context c, Intent i) {
        String a = i == null ? "" : String.valueOf(i.getAction());
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        App.init(c.getApplicationContext());
        if (Session.loggedIn()) BgService.start(c);
    }
}
