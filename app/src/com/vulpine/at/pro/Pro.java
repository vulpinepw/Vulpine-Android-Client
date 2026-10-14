package com.vulpine.at.pro;

import com.vulpine.at.App;
import com.vulpine.core.Http;
import org.json.JSONObject;

public final class Pro {
    static JSONObject cache;
    static long at;

    public interface Cb {
        void on(JSONObject d);
    }

    public static void status(boolean force, Cb cb) {
        if (!force && cache != null && System.currentTimeMillis() - at < 4000) {
            JSONObject c = cache;
            App.ui(() -> cb.on(c));
            return;
        }
        App.bg(() -> {
            Http.Resp r = Http.get("/pro/status");
            App.ui(() -> {
                if (r.ok()) {
                    cache = r.obj();
                    at = System.currentTimeMillis();
                    cb.on(cache);
                } else cb.on(null);
            });
        });
    }

    public static String labelFor(int m) {
        if (m % 12 == 0) {
            int y = m / 12;
            return y + (y == 1 ? " year" : " years");
        }
        return m + (m == 1 ? " month" : " months");
    }
}
