package com.vulpine.at.stories;

import com.vulpine.at.App;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Store {
    public static JSONObject self;
    public static List<JSONObject> feed = new ArrayList<>();
    public static boolean pro;
    public static int perDay = 3;
    public static int[] hours = {6, 12, 24};
    public static long stealthUntil, loadedAt;
    static final CopyOnWriteArrayList<Runnable> subs = new CopyOnWriteArrayList<>();
    static volatile boolean loading, again, wired;
    static final Runnable soonR = Store::load;
    static final Runnable periodic = new Runnable() {
        public void run() {
            if (App.fg) load();
            App.H.postDelayed(this, 120000);
        }
    };

    static synchronized void wire() {
        if (wired) return;
        wired = true;
        Rt.I.on(m -> {
            String t = m.optString("type");
            if ("story.new".equals(t) || "story.deleted".equals(t)) App.ui(Store::soon);
        });
        App.H.postDelayed(periodic, 120000);
    }

    static void soon() {
        App.H.removeCallbacks(soonR);
        App.H.postDelayed(soonR, 700);
    }

    static void maybeRefresh() {
        if (System.currentTimeMillis() - loadedAt > 60000) load();
    }

    public static void on(Runnable r) {
        subs.add(r);
    }

    public static void off(Runnable r) {
        subs.remove(r);
    }

    static void emit() {
        App.ui(() -> {
            for (Runnable r : subs) try {
                r.run();
            } catch (Exception e) {
            }
        });
    }

    public static void load() {
        if (Session.token() == null) return;
        synchronized (Store.class) {
            if (loading) {
                again = true;
                return;
            }
            loading = true;
        }
        App.bg(() -> {
            Http.Resp r = Http.get("/stories/feed");
            if (r.ok()) {
                JSONObject j = r.obj();
                List<JSONObject> f = new ArrayList<>();
                JSONArray a = j.optJSONArray("feed");
                if (a != null) for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) f.add(a.optJSONObject(i));
                JSONObject lim = j.optJSONObject("limits");
                App.ui(() -> {
                    self = j.optJSONObject("self");
                    feed = f;
                    pro = j.optBoolean("pro");
                    if (lim != null) {
                        perDay = lim.optInt("perDay", perDay);
                        JSONArray h = lim.optJSONArray("hours");
                        if (h != null) {
                            int[] x = new int[h.length()];
                            for (int i = 0; i < x.length; i++) x[i] = h.optInt(i);
                            hours = x;
                        }
                    }
                    stealthUntil = j.optLong("stealthUntil");
                    loadedAt = System.currentTimeMillis();
                    emit();
                });
            }
            boolean go;
            synchronized (Store.class) {
                loading = false;
                go = again;
                again = false;
            }
            if (go) load();
        });
    }

    public static int count(JSONObject e) {
        JSONArray s = e == null ? null : e.optJSONArray("stories");
        return s == null ? 0 : s.length();
    }

    public static List<JSONObject> entries() {
        List<JSONObject> out = new ArrayList<>();
        if (count(self) > 0) out.add(self);
        out.addAll(feed);
        return out;
    }

    public static List<JSONObject> miniList() {
        List<JSONObject> l = new ArrayList<>();
        for (JSONObject e : feed) if (e.optBoolean("unseen") && !e.optBoolean("hidden")) l.add(e);
        if (l.isEmpty()) for (JSONObject e : feed) if (!e.optBoolean("hidden")) l.add(e);
        if (count(self) > 0) l.add(0, self);
        return l.size() > 3 ? new ArrayList<>(l.subList(0, 3)) : l;
    }

    public static int unseenCount() {
        int n = 0;
        for (JSONObject e : feed) if (e.optBoolean("unseen") && !e.optBoolean("hidden")) n++;
        return n;
    }

    public static boolean anyUnseen() {
        return unseenCount() > 0;
    }

    public static JSONObject entry(String key) {
        for (JSONObject e : entries()) if (key.equals(e.optString("key")) && count(e) > 0) return e;
        return null;
    }

    public static String name(JSONObject e) {
        String d = e.optString("displayName", "");
        if (d.isEmpty() || "null".equals(d)) d = e.optString("handle", "");
        return d;
    }

    public static String avatarColor(JSONObject e) {
        String c = e.optString("avatarColor", "");
        return c.matches("^#[0-9a-fA-F]{3,8}$") ? c : "";
    }

    public static String avatarUrl(JSONObject e) {
        String u = e.optString("avatarUrl", "");
        return u.matches("^/uploads/[A-Za-z0-9._/-]+$") ? u : null;
    }

    public static String safeMedia(String u) {
        return u != null && u.matches("^/uploads/story-[A-Za-z0-9-]+\\.(png|jpg|gif|webp|mp4|webm)$") ? u : null;
    }

    public static String ago(long ts) {
        long d = Math.max(0, System.currentTimeMillis() - ts), m = d / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }
}
