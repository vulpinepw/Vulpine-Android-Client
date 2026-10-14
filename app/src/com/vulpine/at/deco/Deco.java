package com.vulpine.at.deco;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Deco {
    public interface Cb<T> {
        void on(T v);
    }

    public interface L {
        void on();
    }

    public static final String[] KINDS = {"frame", "avatar", "effect"};
    static final String UKEY = "vulpine.deco.users.v1";

    static final LinkedHashMap<String, DecoItem> items = new LinkedHashMap<>(16, .75f, true);
    static final Map<String, List<Cb<DecoItem>>> waiting = new HashMap<>();
    static Set<String> assetIds;
    static long itemBytes;
    static final long MAX_BYTES = 64L << 20;

    static final Map<String, JSONObject> users = new HashMap<>();
    static final Map<String, Long> usersAt = new HashMap<>();
    static final Map<String, Long> usersFail = new HashMap<>();
    static final Map<String, List<L>> subs = new HashMap<>();
    static final Set<String> queue = new HashSet<>();
    static boolean queued, loadedUsers;

    static JSONObject cat;
    static long catAt;
    static final List<Cb<JSONObject>> catSubs = new CopyOnWriteArrayList<>();
    static final Set<String> shownOrders = new HashSet<>();
    static boolean wired;

    static synchronized void wire() {
        if (wired) return;
        wired = true;
        Rt.I.on(m -> {
            if ("pro.updated".equals(m.optString("type"))) App.bg(() -> com.vulpine.at.Sync.refreshMe(false));
        });
        Rt.I.on(m -> {
            String t = m.optString("type");
            if ("deco.granted".equals(t)) App.ui(() -> {
                catAt = 0;
                Grant.show(m.optJSONArray("items"), m.optString("orderId", null));
            });
        });
    }

    public static boolean validId(String id) {
        return id != null && id.matches("^[a-z]+-(frame|avatar|effect)$");
    }

    public static synchronized boolean has(String id) {
        if (assetIds == null) {
            assetIds = new HashSet<>();
            try {
                String[] l = App.ctx.getAssets().list("deco");
                if (l != null) for (String s : l) if (s.endsWith(".json")) assetIds.add(s.substring(0, s.length() - 5));
            } catch (Exception e) {
            }
        }
        return assetIds.contains(id);
    }

    public static DecoItem peekItem(String id) {
        return peekItem(id, false);
    }

    public static DecoItem peekItem(String id, boolean small) {
        synchronized (items) {
            return items.get(small ? id + "#s" : id);
        }
    }

    public static void item(String id, Cb<DecoItem> cb) {
        item(id, false, cb);
    }

    public static void item(String id, boolean small, Cb<DecoItem> cb) {
        if (!validId(id) || !has(id)) {
            App.ui(() -> cb.on(null));
            return;
        }
        String key = small ? id + "#s" : id;
        synchronized (items) {
            DecoItem it = items.get(key);
            if (it != null) {
                App.ui(() -> cb.on(it));
                return;
            }
            List<Cb<DecoItem>> w = waiting.get(key);
            if (w != null) {
                w.add(cb);
                return;
            }
            w = new ArrayList<>();
            w.add(cb);
            waiting.put(key, w);
        }
        App.bg(() -> {
            DecoItem it = DecoItem.load(id, small ? 6L << 20 : 40L << 20);
            List<Cb<DecoItem>> w;
            synchronized (items) {
                if (it != null) {
                    items.put(key, it);
                    itemBytes += it.bytes();
                    trim();
                }
                w = waiting.remove(key);
            }
            if (w != null) App.ui(() -> {
                for (Cb<DecoItem> c : w) c.on(it);
            });
        });
    }

    static void trim() {
        Iterator<Map.Entry<String, DecoItem>> i = items.entrySet().iterator();
        while (itemBytes > MAX_BYTES && items.size() > 2 && i.hasNext()) {
            Map.Entry<String, DecoItem> e = i.next();
            itemBytes -= e.getValue().bytes();
            i.remove();
        }
    }

    static void loadUsers() {
        if (loadedUsers) return;
        loadedUsers = true;
        try {
            String s = App.kv == null ? null : App.kv.get(UKEY);
            if (s == null) return;
            JSONObject o = new JSONObject(s);
            if (!o.optString("u").equals(Session.userId())) return;
            JSONObject d = o.optJSONObject("d");
            if (d == null) return;
            for (Iterator<String> it = d.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject v = d.optJSONObject(k);
                if (v != null) {
                    users.put(k, v);
                    usersAt.put(k, 0L);
                }
            }
        } catch (Exception e) {
        }
    }

    static boolean saveQueued;

    static void saveUsers() {
        if (saveQueued) return;
        saveQueued = true;
        App.H.postDelayed(() -> {
            saveQueued = false;
            try {
                JSONObject d = new JSONObject();
                List<Map.Entry<String, Long>> l = new ArrayList<>(usersAt.entrySet());
                l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                int n = 0;
                for (Map.Entry<String, Long> e : l) {
                    JSONObject v = users.get(e.getKey());
                    if (v == null || v.length() == 0) continue;
                    d.put(e.getKey(), v);
                    if (++n >= 800) break;
                }
                String s = new JSONObject().put("u", Session.userId()).put("d", d).toString();
                App.bg(() -> {
                    if (App.kv != null) App.kv.put(UKEY, s);
                });
            } catch (Exception e) {
            }
        }, 1500);
    }

    public static JSONObject peek(String uid) {
        if (uid == null) return null;
        loadUsers();
        request(uid);
        return users.get(uid);
    }

    public static String itemFor(String uid, String kind) {
        JSONObject d = peek(uid);
        if (d == null) return null;
        JSONObject k = d.optJSONObject(kind);
        String id = k == null ? null : k.optString("item", null);
        return validId(id) ? id : null;
    }

    public static void subscribe(String uid, L l) {
        if (uid == null) return;
        List<L> s = subs.get(uid);
        if (s == null) subs.put(uid, s = new ArrayList<>());
        if (!s.contains(l)) s.add(l);
    }

    public static void unsubscribe(String uid, L l) {
        List<L> s = uid == null ? null : subs.get(uid);
        if (s != null) s.remove(l);
    }

    public static void request(String uid) {
        if (uid == null || !uid.matches("^[A-Za-z0-9_-]{1,64}$")) return;
        long now = System.currentTimeMillis();
        Long at = usersAt.get(uid);
        if (at != null && now - at < 120000) return;
        Long f = usersFail.get(uid);
        if (f != null && now - f < 30000) return;
        queue.add(uid);
        if (queued) return;
        queued = true;
        App.H.postDelayed(Deco::flush, 40);
    }

    static void flush() {
        queued = false;
        if (queue.isEmpty()) return;
        List<String> all = new ArrayList<>(queue);
        queue.clear();
        long now = System.currentTimeMillis();
        for (String u : all) usersAt.put(u, now);
        for (int i = 0; i < all.size(); i += 100) {
            List<String> chunk = all.subList(i, Math.min(all.size(), i + 100));
            String ids = String.join(",", chunk);
            List<String> ch = new ArrayList<>(chunk);
            App.bg(() -> {
                Http.Resp r = Http.get("/shop/deco/users?ids=" + ids);
                App.ui(() -> {
                    if (!r.ok()) {
                        long t = System.currentTimeMillis();
                        for (String u : ch) {
                            usersAt.remove(u);
                            usersFail.put(u, t);
                        }
                        return;
                    }
                    JSONObject us = r.obj().optJSONObject("users");
                    for (String u : ch) set(u, us == null ? new JSONObject() : us.optJSONObject(u) == null ? new JSONObject() : us.optJSONObject(u));
                });
            });
        }
    }

    static void set(String uid, JSONObject d) {
        JSONObject old = users.get(uid);
        users.put(uid, d);
        usersAt.put(uid, System.currentTimeMillis());
        saveUsers();
        if (old != null && old.toString().equals(d.toString())) return;
        List<L> s = subs.get(uid);
        if (s != null) for (L l : new ArrayList<>(s)) l.on();
    }

    public static void catalog(boolean force, Cb<JSONObject> cb) {
        wire();
        if (!force && cat != null && System.currentTimeMillis() - catAt < 30000) {
            if (cb != null) App.ui(() -> cb.on(cat));
            return;
        }
        App.bg(() -> {
            Http.Resp r = Http.get("/shop/deco");
            App.ui(() -> {
                if (!r.ok()) {
                    if (cb != null) cb.on(cat);
                    return;
                }
                setCat(norm(r.obj()));
                if (cb != null) cb.on(cat);
            });
        });
    }

    static JSONObject norm(JSONObject o) {
        try {
            if (o.optJSONArray("items") == null) o.put("items", new JSONArray());
            if (o.optJSONArray("bundles") == null) o.put("bundles", new JSONArray());
            if (o.optJSONObject("owned") == null) o.put("owned", new JSONObject());
            if (o.optJSONObject("applied") == null) o.put("applied", new JSONObject());
            if (o.optJSONObject("minted") == null) o.put("minted", new JSONObject());
        } catch (Exception e) {
        }
        return o;
    }

    static void setCat(JSONObject c) {
        cat = c;
        catAt = System.currentTimeMillis();
        syncSelf();
        for (Cb<JSONObject> s : catSubs) s.on(cat);
    }

    static void syncSelf() {
        String me = Session.userId();
        if (me == null || cat == null) return;
        JSONObject ap = cat.optJSONObject("applied"), ow = cat.optJSONObject("owned");
        JSONObject d = new JSONObject();
        try {
            for (String k : KINDS) {
                String id = ap == null ? null : ap.optString(k, null);
                if (id == null || "null".equals(id) || ow == null || !ow.has(id)) continue;
                d.put(k, new JSONObject().put("item", id).put("serial", ow.optJSONObject(id).optInt("serial")));
            }
        } catch (Exception e) {
        }
        set(me, d);
    }

    public static JSONObject cat() {
        return cat;
    }

    public static void onCatalog(Cb<JSONObject> cb) {
        catSubs.add(cb);
    }

    public static void offCatalog(Cb<JSONObject> cb) {
        catSubs.remove(cb);
    }

    public static void apply(String kind, String item, Cb<Boolean> done) {
        App.bg(() -> {
            JSONObject b = new JSONObject();
            try {
                b.put("kind", kind).put("item", item == null ? JSONObject.NULL : item);
            } catch (Exception e) {
            }
            Http.Resp r = Http.post("/shop/deco/apply", b);
            App.ui(() -> {
                if (r.ok() && cat != null) {
                    try {
                        JSONObject ap = r.obj().optJSONObject("applied");
                        if (ap != null) cat.put("applied", ap);
                    } catch (Exception e) {
                    }
                    setCat(cat);
                }
                if (done != null) done.on(r.ok());
            });
        });
    }

    public static JSONObject itemInfo(String id) {
        JSONArray it = cat == null ? null : cat.optJSONArray("items");
        if (it != null) for (int i = 0; i < it.length(); i++) {
            JSONObject o = it.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        JSONObject o = new JSONObject();
        try {
            String[] p = id.split("-");
            o.put("id", id).put("kind", p.length > 1 ? p[1] : "").put("name", id).put("pack", p[0]).put("packName", p[0]);
        } catch (Exception e) {
        }
        return o;
    }

    public static String serialLabel(int n) {
        return n > 0 ? "#" + n : "Staff";
    }

    public static String kindLabel(String k, boolean plural) {
        return "avatar".equals(k) ? (plural ? "Avatars" : "Avatar") : "frame".equals(k) ? (plural ? "Frames" : "Frame") : (plural ? "Effects" : "Effect");
    }

    public static JSONObject selfUser() {
        JSONObject s = Session.s == null ? new JSONObject() : Session.s;
        JSONObject u = new JSONObject();
        try {
            u.put("displayName", s.optString("displayName", "")).put("handle", s.optString("handle", "")).put("handles", s.optJSONArray("handles") == null ? new JSONArray() : s.optJSONArray("handles")).put("avatarUrl", s.optString("avatarUrl", "")).put("avatarColor", s.optString("avatarColor", "")).put("profileBackground", s.optString("profileBackground", "")).put("profileFont", s.optString("profileFont", "")).put("bio", s.optString("bio", ""));
        } catch (Exception e) {
        }
        return u;
    }

    static void unclip(View v, int levels) {
        ViewParent p = v instanceof ViewGroup ? (ViewParent) v : v.getParent();
        for (int i = 0; i < levels && p instanceof ViewGroup; i++) {
            ((ViewGroup) p).setClipChildren(false);
            ((ViewGroup) p).setClipToPadding(false);
            p = p.getParent();
        }
    }

    static DecoView find(FrameLayout f, String kind) {
        for (int i = 0; i < f.getChildCount(); i++) {
            View c = f.getChildAt(i);
            if (c instanceof DecoView && kind.equals(((DecoView) c).kind)) return (DecoView) c;
        }
        return null;
    }

    public static void attach(FrameLayout avatarWrap, String userId, int avatarPx, boolean live) {
        if (avatarWrap == null) return;
        wire();
        DecoView v = find(avatarWrap, "avatar");
        if (v == null) {
            v = new DecoView(avatarWrap.getContext(), "avatar");
            int s = Math.round(avatarPx * 1.5f);
            avatarWrap.addView(v, new FrameLayout.LayoutParams(s, s, android.view.Gravity.CENTER));
            avatarWrap.setClipChildren(false);
            avatarWrap.setClipToPadding(false);
            DecoView fv = v;
            avatarWrap.post(() -> unclip(fv, 3));
        } else {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            int s = Math.round(avatarPx * 1.5f);
            if (lp.width != s) {
                lp.width = lp.height = s;
                v.setLayoutParams(lp);
            }
        }
        v.avatarPx = avatarPx;
        v.setUser(userId, live);
    }

    public static void attachGroup(FrameLayout avatarWrap, String groupId, int avatarPx, boolean live) {
        if (avatarWrap == null) return;
        DecoView v = find(avatarWrap, "avatar");
        if (v != null) v.setUser(null, live);
    }

    public static void attachCard(FrameLayout card, String userId, float radiusDp) {
        if (card == null) return;
        Context c = card.getContext();
        DecoView fx = find(card, "effect");
        if (fx == null) {
            fx = new DecoView(c, "effect");
            card.addView(fx, Math.max(0, card.getChildCount() - 1), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        DecoView fr = find(card, "frame");
        if (fr == null) {
            fr = new DecoView(c, "frame");
            card.addView(fr, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else fr.bringToFront();
        card.setClipChildren(false);
        card.setClipToPadding(false);
        DecoView f2 = fr;
        card.post(() -> unclip(f2, 3));
        fx.radius = radiusDp;
        fr.radius = radiusDp;
        fx.setUser(userId, true);
        fr.setUser(userId, true);
    }

    public static void attachCard(FrameLayout card, String userId) {
        attachCard(card, userId, 16);
    }

    public static final int ROOM_TAG = 0x7f0d0e02;

    public interface Room {
        void on(String frameId);
    }

    public static String frameOf(String userId) {
        return itemFor(userId, "frame");
    }

    public static int[] frameRoom(String frameId) {
        if (frameId == null) return new int[]{0, 0, 0, 0};
        String p = frameId.split("-")[0];
        switch (p) {
            case "coder": return new int[]{44, 76, 44, 48};
            case "hacker": return new int[]{56, 76, 56, 48};
            case "desktop": return new int[]{8, 76, 8, 56};
            case "dark": return new int[]{22, 76, 22, 22};
            case "edgy": return new int[]{26, 76, 26, 26};
            default: return new int[]{8, 76, 8, 32};
        }
    }

    public static View effectLayer(Context c, JSONObject user) {
        Fx v = new Fx(c);
        if (user != null) v.set(user.optString("profileBgEffect", ""), user.optJSONObject("profileBgEffectConfig"));
        return v;
    }

    public static View effectLayer(Context c, String fx, JSONObject cfg) {
        Fx v = new Fx(c);
        v.set(fx, cfg);
        return v;
    }

    public static void showGrant(JSONArray items, String orderId) {
        wire();
        Grant.show(items, orderId);
    }

    public static void openSection(MainActivity a) {
        wire();
        if (a != null) a.push(new Section());
    }

    public static void openShop() {
        com.vulpine.at.shop.ShopTab.openOfficial();
    }
}
