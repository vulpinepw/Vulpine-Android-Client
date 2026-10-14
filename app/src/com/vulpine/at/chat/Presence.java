package com.vulpine.at.chat;

import com.vulpine.at.App;
import com.vulpine.at.ui.T;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONObject;

public final class Presence {
    public static final Map<String, String> state = new ConcurrentHashMap<>();
    static final List<Runnable> ls = new CopyOnWriteArrayList<>();
    static boolean wired;
    static long lastPoll;

    public static synchronized void wire() {
        if (wired) {
            poll();
            return;
        }
        wired = true;
        Rt.I.on(m -> {
            if (!"presence".equals(m.optString("type"))) return;
            String uid = m.optString("userId");
            if (m.optBoolean("online")) state.put(uid, m.optString("status", "online"));
            else state.remove(uid);
            fire();
        });
        poll();
        App.H.postDelayed(new Runnable() {
            public void run() {
                if (App.fg) poll();
                App.H.postDelayed(this, 45000);
            }
        }, 45000);
    }

    static void poll() {
        if (System.currentTimeMillis() - lastPoll < 10000) return;
        lastPoll = System.currentTimeMillis();
        App.bg(() -> {
            Http.Resp r = Http.get("/presence");
            if (!r.ok()) return;
            JSONObject p = r.obj().optJSONObject("presence");
            if (p == null) return;
            state.clear();
            java.util.Iterator<String> it = p.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject o = p.optJSONObject(k);
                if (o != null && o.optBoolean("online")) state.put(k, o.optString("status", "online"));
            }
            fire();
        });
    }

    static void fire() {
        App.ui(() -> {
            for (Runnable r : ls) r.run();
        });
    }

    public static void on(Runnable r) {
        ls.add(r);
    }

    public static void off(Runnable r) {
        ls.remove(r);
    }

    public static int color(String uid) {
        String s = state.get(uid);
        if (s == null) return 0;
        if ("dnd".equals(s)) return T.danger;
        if ("inactive".equals(s)) return 0xfff59e0b;
        return T.success;
    }
}
