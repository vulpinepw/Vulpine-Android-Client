package com.vulpine.at;

import android.content.Context;

public final class Push {
    public static void ensure(Context c) {
        com.vulpine.at.svc.BgService.start(c);
    }

    public static void stop(Context c) {
        com.vulpine.at.svc.BgService.stop(c);
    }
}
