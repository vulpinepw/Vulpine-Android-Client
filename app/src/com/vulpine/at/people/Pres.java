package com.vulpine.at.people;

import com.vulpine.at.App;
import com.vulpine.at.home.ChatsTab;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONObject;

public final class Pres {
    public interface L {
        void on(String uid);
    }

    static final Map<String, JSONObject> pres = new ConcurrentHashMap<>();
    static final Map<String, JSONObject> act = new ConcurrentHashMap<>();
    static final Map<String, Long> actAt = new ConcurrentHashMap<>();
    static final List<L> ls = new CopyOnWriteArrayList<>();
    static volatile boolean started;
    static volatile long presAt;

    public static synchronized void start() {
        if (started) return;
        started = true;
        Rt.I.on(Pres::onWs);
        tick();
    }

    static void tick() {
        if (!started) return;
        if (App.fg && System.currentTimeMillis() - presAt > 44000) App.bg(Pres::loadPresence);
        App.H.postDelayed(Pres::tick, 45000);
    }

    public static void loadPresence() {
        Http.Resp r = Http.get("/presence");
        if (!r.ok()) return;
        presAt = System.currentTimeMillis();
        JSONObject p = r.obj().optJSONObject("presence");
        pres.clear();
        if (p != null) {
            java.util.Iterator<String> it = p.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject o = p.optJSONObject(k);
                if (o != null) pres.put(k, o);
            }
        }
        fire(null);
    }

    static void onWs(JSONObject m) {
        String t = m.optString("type");
        if ("presence".equals(t)) {
            String uid = m.optString("userId");
            if (uid.isEmpty()) return;
            if (m.optBoolean("online")) {
                try {
                    pres.put(uid, new JSONObject().put("online", true).put("status", m.optString("status", "online")));
                } catch (Exception e) {
                }
            } else pres.remove(uid);
            fire(uid);
        } else if ("activity".equals(t)) {
            String uid = m.optString("userId");
            if (uid.isEmpty()) return;
            setAct(uid, m.optJSONObject("activity"));
            fire(uid);
        }
    }

    static void setAct(String uid, JSONObject a) {
        actAt.put(uid, System.currentTimeMillis());
        if (a == null) {
            act.remove(uid);
            ChatsTab.listening.remove(uid);
        } else {
            act.put(uid, a);
            ChatsTab.listening.put(uid, a.optString("title") + " · " + a.optString("artist"));
        }
    }

    static void fire(String uid) {
        App.ui(() -> {
            for (L l : ls) l.on(uid);
        });
    }

    public static void on(L l) {
        ls.add(l);
    }

    public static void off(L l) {
        ls.remove(l);
    }

    public static String status(String uid) {
        JSONObject o = uid == null ? null : pres.get(uid);
        if (o == null || !o.optBoolean("online")) return null;
        return o.optString("status", "online");
    }

    public static boolean online(String uid) {
        return status(uid) != null;
    }

    public static int dotColor(String status) {
        if (status == null) return 0;
        if ("dnd".equals(status)) return com.vulpine.at.ui.T.danger;
        if ("inactive".equals(status)) return 0xfff59e0b;
        return com.vulpine.at.ui.T.success;
    }

    public static JSONObject activity(String uid) {
        return uid == null ? null : act.get(uid);
    }

    public static void loadActivity(List<String> ids) {
        List<String> need = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String id : ids) {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) continue;
            Long at = actAt.get(id);
            if (at == null || now - at > 30000) need.add(id);
        }
        for (int i = 0; i < need.size(); i += 100) {
            List<String> chunk = need.subList(i, Math.min(need.size(), i + 100));
            Http.Resp r = Http.get("/activity?ids=" + String.join(",", chunk));
            if (!r.ok()) continue;
            JSONObject a = r.obj().optJSONObject("activities");
            for (String id : chunk) setAct(id, a == null ? null : a.optJSONObject(id));
        }
        if (!need.isEmpty()) fire(null);
    }
}
