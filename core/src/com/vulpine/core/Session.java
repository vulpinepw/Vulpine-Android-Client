package com.vulpine.core;

import org.json.JSONObject;

public final class Session {
    public static Kv kv;
    public static JSONObject s;
    public static VCrypto.Identity id;

    public static synchronized void load() {
        try {
            String a = kv.get("vulpine.session"), b = kv.get("vulpine.identity");
            s = a == null ? null : new JSONObject(a);
            id = b == null ? null : VCrypto.Identity.from(new JSONObject(b));
        } catch (Exception e) {
            s = null;
            id = null;
        }
        if (s != null && !valid(s)) s = null;
        Http.token = s == null ? null : s.optString("token");
    }

    static boolean valid(JSONObject o) {
        for (String k : new String[]{"token", "userId", "handle", "displayName", "signPubkey", "encPubkey"})
            if (o.optString(k).isEmpty()) return false;
        return true;
    }

    public static boolean loggedIn() {
        return s != null;
    }

    public static String userId() {
        return s == null ? "" : s.optString("userId");
    }

    public static String token() {
        return s == null ? null : s.optString("token");
    }

    public static JSONObject settings() {
        if (s == null) return new JSONObject();
        JSONObject o = s.optJSONObject("settings");
        if (o == null) {
            o = new JSONObject();
            try {
                s.put("settings", o);
            } catch (Exception e) {
            }
        }
        return o;
    }

    public static synchronized void save() {
        if (s != null) kv.put("vulpine.session", s.toString());
        if (id != null) kv.put("vulpine.identity", id.json().toString());
    }

    public static synchronized void login(JSONObject session, VCrypto.Identity ident) {
        s = session;
        if (ident != null) id = ident;
        Http.token = s.optString("token");
        save();
    }

    public static synchronized void merge(JSONObject me) {
        if (s == null || me == null) return;
        try {
            java.util.Iterator<String> it = me.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.equals("settings") || k.equals("token") || k.equals("id")) continue;
                s.put(k, me.get(k));
            }
            if (me.has("id")) s.put("userId", me.getString("id"));
        } catch (Exception e) {
        }
        save();
    }

    public static synchronized void logout() {
        s = null;
        id = null;
        Http.token = null;
        kv.remove("vulpine.session");
        kv.remove("vulpine.identity");
    }
}
