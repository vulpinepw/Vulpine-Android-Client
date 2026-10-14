package com.vulpine.at.notes;

import com.vulpine.at.App;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Ov;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Notes {
    public static void saveMessage(String text) {
        if (text == null || text.isEmpty()) return;
        App.ui(() -> {
            JSONObject nb = find("default");
            if (nb == null) nb = list().get(0);
            add(nb, text);
            Ov.toast("Saved to notes");
        });
    }

    public static List<JSONObject> list() {
        JSONObject s = Session.settings();
        JSONArray a = s.optJSONArray("notebooks");
        if (a == null || a.length() == 0) {
            a = new JSONArray();
            JSONObject d = App.jo("id", "default", "name", "Saved Messages", "messages", new JSONArray(), "createdAt", System.currentTimeMillis());
            JSONArray legacy = s.optJSONArray("notes");
            if (legacy != null) for (int i = 0; i < legacy.length(); i++) d.optJSONArray("messages").put(legacy.opt(i));
            a.put(d);
            try {
                s.put("notebooks", a);
            } catch (Exception e) {
            }
        }
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) out.add(a.optJSONObject(i));
        return out;
    }

    public static JSONObject find(String id) {
        for (JSONObject nb : list()) if (nb.optString("id").equals(id)) return nb;
        return null;
    }

    public static JSONArray msgs(JSONObject nb) {
        JSONArray m = nb.optJSONArray("messages");
        if (m == null) {
            m = new JSONArray();
            put(nb, "messages", m);
        }
        return m;
    }

    static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (Exception e) {
        }
    }

    public static JSONArray pinnedIds() {
        return Sync.arr("pinnedNotebookIds");
    }

    public static boolean pinned(String id) {
        return Sync.contains(pinnedIds(), id);
    }

    public static List<JSONObject> sorted() {
        List<JSONObject> all = list(), out = new ArrayList<>(), rest = new ArrayList<>();
        JSONArray p = pinnedIds();
        for (int i = 0; i < p.length(); i++) for (JSONObject nb : all) if (nb.optString("id").equals(p.optString(i))) out.add(nb);
        for (JSONObject nb : all) if (!out.contains(nb)) rest.add(nb);
        rest.sort((x, y) -> Long.compare(x.optLong("createdAt"), y.optLong("createdAt")));
        out.addAll(rest);
        return out;
    }

    public static JSONObject create(String name) {
        JSONObject nb = App.jo("id", UUID.randomUUID().toString(), "name", name, "messages", new JSONArray(), "createdAt", System.currentTimeMillis());
        list();
        Session.settings().optJSONArray("notebooks").put(nb);
        Sync.save();
        return nb;
    }

    public static void rename(JSONObject nb, String name) {
        put(nb, "name", name);
        Sync.save();
    }

    public static void delete(String id) {
        JSONArray a = Session.settings().optJSONArray("notebooks"), n = new JSONArray();
        if (a != null) for (int i = 0; i < a.length(); i++) if (!id.equals(a.optJSONObject(i).optString("id"))) n.put(a.opt(i));
        put(Session.settings(), "notebooks", n);
        JSONArray p = pinnedIds(), np = new JSONArray();
        for (int i = 0; i < p.length(); i++) if (!id.equals(p.optString(i))) np.put(p.optString(i));
        put(Session.settings(), "pinnedNotebookIds", np);
        Sync.save();
    }

    public static void togglePin(String id) {
        Sync.toggle("pinnedNotebookIds", id, !pinned(id));
    }

    public static void movePinned(List<String> order) {
        put(Session.settings(), "pinnedNotebookIds", new JSONArray(order));
        Sync.save();
    }

    public static JSONObject add(JSONObject nb, String text) {
        JSONObject m = App.jo("id", UUID.randomUUID().toString(), "text", text, "createdAt", System.currentTimeMillis());
        msgs(nb).put(m);
        Sync.save();
        return m;
    }

    public static void edit(JSONObject note, String text) {
        put(note, "text", text);
        Sync.save();
    }

    public static void remove(JSONObject nb, String noteId) {
        JSONArray m = msgs(nb), n = new JSONArray();
        for (int i = 0; i < m.length(); i++) if (!noteId.equals(m.optJSONObject(i).optString("id"))) n.put(m.opt(i));
        put(nb, "messages", n);
        Sync.save();
    }

    public static void pinNote(JSONObject note, boolean on) {
        if (on) put(note, "pinned", true);
        else note.remove("pinned");
        Sync.save();
    }

    public static String count(JSONObject nb) {
        int n = msgs(nb).length();
        return n + (n == 1 ? " note" : " notes");
    }

    public static String preview(String text) {
        if (text == null) return "";
        if (text.startsWith("📎|")) {
            String[] p = text.split("\n", 2)[0].substring(3).split("\\|");
            return "[media] " + (p.length > 0 ? p[0] : "");
        }
        return text.replaceAll("(?m)^»\\|", "").replaceAll("(?m)^>\\s?", "").replaceAll("\\s+", " ").trim();
    }

    public static String export(JSONObject nb) {
        StringBuilder b = new StringBuilder();
        b.append("# ").append(nb.optString("name")).append("\n\n");
        JSONArray m = msgs(nb);
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM);
        for (int i = 0; i < m.length(); i++) {
            JSONObject n = m.optJSONObject(i);
            if (n == null) continue;
            long at = n.optLong("createdAt");
            boolean pin = n.optBoolean("pinned");
            if (at > 0) b.append('_').append(df.format(new java.util.Date(at))).append('_').append(pin ? " 📌" : "").append('\n');
            else if (pin) b.append("📌\n");
            for (String line : n.optString("text").split("\n", -1)) {
                if (line.startsWith("📎|")) {
                    String[] p = line.substring(3).split("\\|", 3);
                    String url = p.length > 2 ? p[2] : "";
                    if (url.startsWith("/")) url = com.vulpine.core.Http.origin + url;
                    String nm = p.length > 0 && !p[0].isEmpty() ? p[0] : "file";
                    b.append('[').append(nm).append("](").append(url).append(")\n");
                } else b.append(line).append('\n');
            }
            b.append("\n---\n\n");
        }
        return b.toString();
    }

    public static String fileName(JSONObject nb) {
        String n = nb.optString("name", "notebook").replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_");
        if (n.length() > 80) n = n.substring(0, 80);
        if (n.trim().isEmpty()) n = "notebook";
        return n + ".md";
    }
}
