package com.vulpine.at;

import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Sync {
    public interface L {
        void on(String what);
    }

    static final CopyOnWriteArrayList<L> ls = new CopyOnWriteArrayList<>();
    static volatile boolean running, dirty;
    static volatile long lastMe, lastUnread;
    static Thread th;
    static final Runnable saver = Sync::flush;
    public static volatile boolean newAccount;

    public static void on(L l) {
        ls.add(l);
    }

    public static void off(L l) {
        ls.remove(l);
    }

    static void emit(String w) {
        App.ui(() -> {
            for (L l : ls) try {
                l.on(w);
            } catch (Exception e) {
            }
        });
    }

    public static JSONObject defaults() {
        try {
            return new JSONObject().put("toastEnabled", true).put("toastSound", true).put("messageDeleteConfirm", true).put("autoDeleteDefaultMs", 0).put("chatFolders", new JSONArray()).put("pinnedChatIds", new JSONArray()).put("unreadByChat", new JSONObject())
                    .put("notebooks", new JSONArray().put(new JSONObject().put("id", "default").put("name", "Saved Messages").put("messages", new JSONArray()).put("createdAt", System.currentTimeMillis())))
                    .put("pinnedNotebookIds", new JSONArray()).put("showForwardAuthor", true).put("pinnedMessages", new JSONObject()).put("hiddenChatIds", new JSONArray());
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static synchronized void start() {
        if (running) return;
        running = true;
        th = new Thread(() -> {
            boolean first = true;
            while (running) {
                long now = System.currentTimeMillis();
                if (first || (App.fg && now - lastMe > 60000)) {
                    refreshMe(first);
                    first = false;
                }
                if (App.fg && now - lastUnread > 60000) pullUnread();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "vsync");
        th.setDaemon(true);
        th.start();
    }

    public static void stop() {
        running = false;
        if (th != null) th.interrupt();
    }

    public static void onForeground() {
        if (!running) return;
        App.bg(() -> {
            if (System.currentTimeMillis() - lastMe > 20000) refreshMe(false);
            if (System.currentTimeMillis() - lastUnread > 500) pullUnread();
        });
    }

    public static void refreshMe(boolean boot) {
        Http.Resp r = Http.get("/me");
        if (!r.ok()) return;
        lastMe = System.currentTimeMillis();
        JSONObject me = r.obj();
        JSONObject srv = me.optJSONObject("settings");
        App.ui(() -> {
            Session.merge(me);
            JSONObject loc = Session.settings();
            try {
                if (srv == null || srv.length() == 0) {
                    if (boot) {
                        newAccount = true;
                        JSONObject ns = defaults();
                        merge(ns, loc);
                        ns.put("hiddenChatIds", ns.optJSONArray("hiddenChatIds") == null ? new JSONArray() : ns.optJSONArray("hiddenChatIds"));
                        ns.put("tourPending", true);
                        Session.s.put("settings", ns);
                        Session.save();
                        App.bg(() -> Http.patch("/me", App.jo("settings", ns)));
                    }
                } else if (!dirty) {
                    JSONObject ns = defaults();
                    merge(ns, srv);
                    JSONObject lu = loc.optJSONObject("unreadByChat"), su = ns.optJSONObject("unreadByChat");
                    if (lu != null && su != null) {
                        Iterator<String> it = lu.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            if (lu.optInt(k) > su.optInt(k)) su.put(k, lu.optInt(k));
                        }
                    }
                    if (ns.optBoolean("tourPending")) newAccount = true;
                    Session.s.put("settings", ns);
                    Session.save();
                }
            } catch (Exception e) {
            }
            Prefs.applyTheme();
            emit("me");
        });
    }

    static void merge(JSONObject into, JSONObject from) {
        if (from == null) return;
        Iterator<String> it = from.keys();
        while (it.hasNext()) {
            String k = it.next();
            try {
                into.put(k, from.get(k));
            } catch (Exception e) {
            }
        }
    }

    public static void pullUnread() {
        Http.Resp r = Http.get("/unread");
        if (!r.ok()) return;
        lastUnread = System.currentTimeMillis();
        JSONObject c = r.obj().optJSONObject("counts");
        if (c == null) return;
        App.ui(() -> {
            JSONObject u = unread();
            JSONArray muted = Session.settings().optJSONArray("mutedChatIds");
            boolean ch = false;
            Iterator<String> it = c.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.equals(Chats.I.activeCid) || contains(muted, k)) continue;
                int v = c.optInt(k);
                if (v > u.optInt(k)) {
                    try {
                        u.put(k, v);
                    } catch (Exception e) {
                    }
                    ch = true;
                }
            }
            if (ch) {
                Session.save();
                emit("unread");
            }
        });
    }

    public static JSONObject unread() {
        JSONObject s = Session.settings();
        JSONObject u = s.optJSONObject("unreadByChat");
        if (u == null) {
            u = new JSONObject();
            try {
                s.put("unreadByChat", u);
            } catch (Exception e) {
            }
        }
        return u;
    }

    public static boolean contains(JSONArray a, String v) {
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) if (v.equals(a.optString(i))) return true;
        return false;
    }

    public static JSONArray arr(String key) {
        JSONObject s = Session.settings();
        JSONArray a = s.optJSONArray(key);
        if (a == null) {
            a = new JSONArray();
            try {
                s.put(key, a);
            } catch (Exception e) {
            }
        }
        return a;
    }

    public static void toggle(String key, String id, boolean on) {
        JSONArray a = arr(key);
        JSONArray n = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!id.equals(a.optString(i))) n.put(a.optString(i));
        if (on) n.put(id);
        try {
            Session.settings().put(key, n);
        } catch (Exception e) {
        }
        save();
    }

    public static void save() {
        dirty = true;
        Session.save();
        App.H.removeCallbacks(saver);
        App.H.postDelayed(saver, 800);
        emit("settings");
    }

    static void flush() {
        JSONObject loc;
        try {
            loc = new JSONObject(Session.settings().toString());
        } catch (Exception e) {
            return;
        }
        App.bg(() -> {
            Http.Resp me = Http.get("/me");
            JSONObject srv = me.ok() ? me.obj().optJSONObject("settings") : null;
            JSONObject out = new JSONObject();
            merge(out, srv);
            merge(out, loc);
            try {
                if (!out.has("hiddenChatIds")) out.put("hiddenChatIds", new JSONArray());
                Http.Resp r = Http.patch("/me", new JSONObject().put("settings", out));
                if (r.ok()) dirty = false;
            } catch (Exception e) {
            }
        });
    }

    public static void patchMe(JSONObject fields, Http.Resp[] out, Runnable done) {
        App.bg(() -> {
            Http.Resp r = Http.patch("/me", fields);
            if (r.ok()) App.ui(() -> {
                Session.merge(r.obj());
                Prefs.applyTheme();
                emit("me");
            });
            if (out != null) out[0] = r;
            if (done != null) App.ui(done);
        });
    }
}
