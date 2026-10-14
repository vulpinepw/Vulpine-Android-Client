package com.vulpine.at.people;

import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Conns {
    public static final class P {
        public String cid, id, name, handle, avatarColor, avatarUrl, status, requesterId, targetId;
        public boolean pro;
        public JSONObject raw;
    }

    public static volatile JSONObject cache;

    public static P peer(JSONObject r, String me) {
        P p = new P();
        p.raw = r;
        p.cid = r.optString("id");
        p.status = r.optString("status");
        p.requesterId = r.optString("requesterId");
        p.targetId = r.optString("targetId");
        boolean mine = r.optString("requesterId").equals(me);
        String k = mine ? "target" : "requester";
        p.id = mine ? r.optString("targetId") : r.optString("requesterId");
        p.handle = str(r, k + "Handle");
        String n = str(r, k + "Name");
        p.name = n.isEmpty() ? (p.handle.isEmpty() ? "Unknown" : p.handle) : n;
        p.avatarColor = str(r, k + "AvatarColor");
        if (p.avatarColor.isEmpty()) p.avatarColor = null;
        String u = str(r, k + "AvatarUrl");
        p.avatarUrl = u.isEmpty() ? null : u;
        p.pro = r.optBoolean(k + "Pro");
        return p;
    }

    static String str(JSONObject o, String k) {
        if (!o.has(k) || o.isNull(k)) return "";
        return o.optString(k, "");
    }

    public static List<P> list(JSONObject lists, String key) {
        List<P> out = new ArrayList<>();
        JSONArray a = lists == null ? null : lists.optJSONArray(key);
        String me = Session.userId();
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(peer(o, me));
        }
        return out;
    }

    public static JSONObject loadAll() {
        Http.Resp r = Http.get("/connections");
        if (!r.ok()) return null;
        cache = r.obj();
        return cache;
    }

    public static boolean match(P p, String q) {
        if (q == null || q.isEmpty()) return true;
        String l = q.toLowerCase();
        return p.name.toLowerCase().contains(l) || p.handle.toLowerCase().contains(l);
    }
}
