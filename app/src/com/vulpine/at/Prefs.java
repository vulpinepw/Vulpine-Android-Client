package com.vulpine.at;

import android.content.res.Configuration;
import com.vulpine.at.ui.T;
import com.vulpine.core.Session;
import org.json.JSONObject;

public final class Prefs {
    public static String theme() {
        JSONObject ss = Session.s;
        String st = ss == null ? "" : ss.optString("theme", "");
        if ("dark".equals(st) || "light".equals(st) || "system".equals(st)) return st;
        return App.prefs.getString("theme", "dark");
    }

    public static void setTheme(String t) {
        App.prefs.edit().putString("theme", t).apply();
        applyTheme();
    }

    public static String power() {
        return App.prefs.getString("power", "");
    }

    public static void setPower(String p) {
        App.prefs.edit().putString("power", p).apply();
        applyTheme();
    }

    public static boolean systemDark() {
        int m = App.ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m != Configuration.UI_MODE_NIGHT_NO;
    }

    public static boolean isDark() {
        String t = theme();
        if ("light".equals(t)) return false;
        if ("system".equals(t)) return systemDark();
        return true;
    }

    public static void applyTheme() {
        String p = power();
        T.lowfx = !"off".equals(p);
        T.power = "on".equals(p);
        JSONObject s = Session.s;
        String ac = s == null ? null : s.optString("accentColor", null);
        T.apply(isDark(), ac);
        T.scale = 1f;
    }

    public static boolean powerSaving() {
        return "on".equals(power());
    }
}
