package com.vulpine.at;

import android.content.Intent;
import android.net.Uri;

public final class Links {
    public static void handle(MainActivity a, Intent i) {
        if (i == null) return;
        String cid = i.getStringExtra("cid");
        if (cid != null) App.H.postDelayed(() -> com.vulpine.at.home.Home.openChat(cid), 50);
        Uri u = i.getData();
        if (u != null) App.H.postDelayed(() -> com.vulpine.at.home.Home.openLink(u.toString()), 50);
    }
}
