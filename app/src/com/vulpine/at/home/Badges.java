package com.vulpine.at.home;

import com.vulpine.at.App;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Badges {
    public static final String BOT_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"4\" y=\"8\" width=\"16\" height=\"12\" rx=\"3\"/><circle cx=\"9\" cy=\"14\" r=\"1\" fill=\"currentColor\"/><circle cx=\"15\" cy=\"14\" r=\"1\" fill=\"currentColor\"/><path d=\"M12 8V5\"/><circle cx=\"12\" cy=\"4\" r=\"1.4\"/><path d=\"M8 4l-2-1M16 4l2-1\"/></svg>";
    static JSONObject d;

    static synchronized JSONObject d() {
        if (d == null) {
            try (InputStream in = App.ctx.getAssets().open("badges.json")) {
                byte[] b = new byte[in.available()];
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
                d = new JSONObject(new String(b, "UTF-8"));
            } catch (Exception e) {
                d = new JSONObject();
            }
        }
        return d;
    }

    static String pick(String idMap, String hMap, String id, String h) {
        JSONObject a = d().optJSONObject(idMap), b = d().optJSONObject(hMap);
        if (id != null && a != null && a.has(id)) return a.optString(id);
        if (h != null && b != null && b.has(h)) return b.optString(h);
        return null;
    }

    public static boolean isBot(String handle) {
        String h = handle == null ? "" : handle.toLowerCase();
        return h.equals("vulpine.alerts") || h.endsWith("_bot");
    }

    public static List<String> forUser(String id, String handle) {
        List<String> out = new ArrayList<>();
        String h = handle == null ? "" : handle.toLowerCase();
        if (isBot(h)) return out;
        for (String[] k : new String[][]{{"BY_ID", "BY_HANDLE"}, {"EXTRA_BY_ID", "EXTRA_BY_HANDLE"}, {"PROMOTER_BY_ID", "PROMOTER_BY_HANDLE"}}) {
            String u = pick(k[0], k[1], id, h);
            if (u != null) out.add(u);
        }
        return out;
    }

    public static String forGroup(String cid) {
        JSONArray g = d().optJSONArray("GROUP");
        if (g != null) for (int i = 0; i < g.length(); i++) if (g.optString(i).equals(cid)) return "/landing/badge-certified.gif";
        return null;
    }
}
