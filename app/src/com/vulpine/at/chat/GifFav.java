package com.vulpine.at.chat;

import com.vulpine.at.Sync;
import com.vulpine.core.Session;
import org.json.JSONArray;
import org.json.JSONObject;

public final class GifFav {
    static String norm(String u) {
        return u.replaceAll("[?#].*$", "");
    }

    public static JSONArray list() {
        JSONArray a = Session.settings().optJSONArray("favouriteGifs");
        return a == null ? new JSONArray() : a;
    }

    public static boolean has(String u) {
        JSONArray a = list();
        String n = norm(u);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && n.equals(norm(o.optString("u")))) return true;
        }
        return false;
    }

    public static void add(String u, String thumb) {
        JSONArray a = list(), n = new JSONArray();
        try {
            JSONObject o = new JSONObject().put("u", norm(u));
            if (thumb != null) o.put("th", thumb);
            n.put(o);
        } catch (Exception e) {
        }
        for (int i = 0; i < a.length() && n.length() < 60; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !norm(u).equals(norm(o.optString("u")))) n.put(o);
        }
        try {
            Session.settings().put("favouriteGifs", n);
        } catch (Exception e) {
        }
        Sync.save();
    }

    public static void remove(String u) {
        JSONArray a = list(), n = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !norm(u).equals(norm(o.optString("u")))) n.put(o);
        }
        try {
            Session.settings().put("favouriteGifs", n);
        } catch (Exception e) {
        }
        Sync.save();
    }
}
