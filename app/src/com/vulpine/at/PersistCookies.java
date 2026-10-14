package com.vulpine.at;

import com.vulpine.core.Kv;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

final class PersistCookies implements CookieStore {
    final CookieStore mem = new CookieManager().getCookieStore();
    final Kv kv;

    PersistCookies(Kv kv) {
        this.kv = kv;
        try {
            String s = kv.get("cookies");
            if (s != null) {
                JSONArray a = new JSONArray(s);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    long exp = o.optLong("exp");
                    if (exp > 0 && exp < System.currentTimeMillis()) continue;
                    HttpCookie c = new HttpCookie(o.getString("n"), o.getString("v"));
                    c.setDomain(o.optString("d", null));
                    c.setPath(o.optString("p", "/"));
                    c.setSecure(o.optBoolean("s"));
                    c.setHttpOnly(true);
                    c.setVersion(0);
                    c.setMaxAge(exp > 0 ? (exp - System.currentTimeMillis()) / 1000 : -1);
                    mem.add(URI.create(o.optString("u")), c);
                }
            }
        } catch (Exception e) {
        }
    }

    synchronized void persist() {
        try {
            JSONArray a = new JSONArray();
            for (URI u : mem.getURIs()) for (HttpCookie c : mem.get(u)) {
                if (c.hasExpired()) continue;
                a.put(new JSONObject().put("u", u.toString()).put("n", c.getName()).put("v", c.getValue()).put("d", c.getDomain()).put("p", c.getPath()).put("s", c.getSecure()).put("exp", c.getMaxAge() > 0 ? System.currentTimeMillis() + c.getMaxAge() * 1000 : 0));
            }
            kv.put("cookies", a.toString());
        } catch (Exception e) {
        }
    }

    public void add(URI uri, HttpCookie c) {
        c.setVersion(0);
        mem.add(uri, c);
        persist();
    }

    public List<HttpCookie> get(URI uri) {
        return mem.get(uri);
    }

    public List<HttpCookie> getCookies() {
        return mem.getCookies();
    }

    public List<URI> getURIs() {
        return mem.getURIs();
    }

    public boolean remove(URI uri, HttpCookie c) {
        boolean r = mem.remove(uri, c);
        persist();
        return r;
    }

    public boolean removeAll() {
        java.util.List<HttpCookie> keep = new java.util.ArrayList<>();
        java.util.List<URI> keepU = new java.util.ArrayList<>();
        for (URI u : mem.getURIs()) for (HttpCookie c : mem.get(u)) if ("vulpine_ac".equals(c.getName())) {
            keep.add(c);
            keepU.add(u);
        }
        boolean r = mem.removeAll();
        for (int i = 0; i < keep.size(); i++) mem.add(keepU.get(i), keep.get(i));
        persist();
        return r;
    }
}
