package com.vulpine.at.call;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.vulpine.at.App;

public class CallActions extends BroadcastReceiver {
    public static final String HANGUP = "com.vulpine.at.call.HANGUP", DECLINE = "com.vulpine.at.call.DECLINE";

    @Override
    public void onReceive(Context c, Intent i) {
        App.init(c.getApplicationContext());
        String a = i.getAction();
        if (HANGUP.equals(a)) App.H.post(Calls::hangUp);
        else if (DECLINE.equals(a)) {
            String id = i.getStringExtra("callId"), cid = i.getStringExtra("cid");
            App.H.post(() -> Calls.declineFrom(id, cid));
        }
    }
}
