package com.vulpine.at.secret;

import com.vulpine.at.App;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import com.vulpine.core.VCrypto;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Sec {
    public static final String CHECK = "vulpine-secret-ok-v1";
    public static final int CHUNK = 192 * 1024;

    public static final class Chat {
        public JSONObject o;
        public String id;
        public volatile byte[] key;
        public volatile boolean peerOnline;
        public volatile long typingAt;
        public final List<Msg> msgs = new CopyOnWriteArrayList<>();
        public volatile int unread;
        public volatile long lastAt;
        volatile boolean loadedMsgs;

        public String peerName() {
            String n = o.optString("peerDisplayName", "");
            return n.isEmpty() ? o.optString("peerHandle", "") : n;
        }

        public boolean pending() {
            return "pending".equals(o.optString("status"));
        }

        public boolean mineCreated() {
            return Session.userId().equals(o.optString("createdBy"));
        }

        public boolean unlocked() {
            return key != null;
        }

        public boolean typing() {
            return System.currentTimeMillis() - typingAt < 5000;
        }

        public Msg last() {
            Msg l = null;
            for (Msg m : msgs) if (l == null || m.at > l.at) l = m;
            return l;
        }

        public boolean live() {
            return key != null && peerOnline && "active".equals(o.optString("status"));
        }

        public Msg find(String id) {
            for (Msg m : msgs) if (m.id.equals(id)) return m;
            return null;
        }
    }

    public static final class Msg {
        public String id, from, text = "";
        public long at;
        public JSONObject reply, file;
        public String status = "sending";
        public final Map<String, List<String>> rx = new HashMap<>();
        public long edited;
        public volatile File local;
        public volatile float progress = -1;
        String sealed;

        public boolean mine() {
            return Session.userId().equals(from);
        }

        public boolean ready() {
            return file != null && local != null && progress < 0;
        }

        JSONObject json() {
            try {
                JSONObject o = new JSONObject().put("id", id).put("from", from).put("at", at).put("text", text == null ? "" : text).put("st", status);
                if (edited > 0) o.put("edited", edited);
                if (reply != null) o.put("reply", reply);
                if (file != null) o.put("file", file);
                if (!rx.isEmpty()) {
                    JSONObject r = new JSONObject();
                    for (Map.Entry<String, List<String>> e : rx.entrySet()) r.put(e.getKey(), new JSONArray(e.getValue()));
                    o.put("rx", r);
                }
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        static Msg from(JSONObject o) {
            Msg m = new Msg();
            m.id = o.optString("id");
            m.from = o.optString("from");
            m.at = o.optLong("at");
            m.text = o.optString("text");
            m.status = o.optString("st", "sent");
            m.edited = o.optLong("edited");
            m.reply = o.optJSONObject("reply");
            m.file = o.optJSONObject("file");
            JSONObject r = o.optJSONObject("rx");
            if (r != null) {
                Iterator<String> it = r.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    List<String> l = new ArrayList<>();
                    JSONArray a = r.optJSONArray(k);
                    if (a != null) for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
                    if (!l.isEmpty()) m.rx.put(k, l);
                }
            }
            return m;
        }
    }

    public interface L {
        void on(String ev, String chatId);
    }

    public static final Map<String, Chat> chats = new ConcurrentHashMap<>();
    static final List<L> ls = new CopyOnWriteArrayList<>();
    static boolean wired;
    static final Map<String, Object[]> incoming = new ConcurrentHashMap<>();
    static final Map<String, Set<String>> sentRead = new ConcurrentHashMap<>();
    static final Set<String> closedByMe = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static volatile String active;
    public static volatile boolean loaded;

    public static void on(L l) {
        ls.add(l);
    }

    public static void off(L l) {
        ls.remove(l);
    }

    static void fire(String ev, String id) {
        App.ui(() -> {
            for (L l : ls) l.on(ev, id);
        });
    }

    public static synchronized void wire() {
        if (wired) return;
        wired = true;
        Rt.I.on(Sec::frame);
        App.bg(Sec::fetch);
    }

    public static void reset() {
        for (Chat c : chats.values()) {
            c.key = null;
            c.msgs.clear();
        }
        chats.clear();
        incoming.clear();
        sentRead.clear();
        active = null;
        loaded = false;
        fire("list", null);
    }

    public static List<Chat> list() {
        List<Chat> l = new ArrayList<>(chats.values());
        for (Chat c : l) {
            Msg m = c.last();
            if (m != null && m.at > c.lastAt) c.lastAt = m.at;
        }
        Collections.sort(l, (x, y) -> Long.compare(y.lastAt, x.lastAt));
        return l;
    }

    public static Chat byPeer(String peerId) {
        for (Chat c : chats.values()) if (peerId.equals(c.o.optString("peerId"))) return c;
        return null;
    }

    static void fetch() {
        Http.Resp r = Http.get("/secret-chats");
        if (!r.ok()) return;
        JSONArray a = r.obj().optJSONArray("chats");
        if (a == null) return;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) {
                seen.add(o.optString("id"));
                upsert(o);
            }
        }
        for (String id : new ArrayList<>(chats.keySet())) if (!seen.contains(id)) wipe(id, false);
        loaded = true;
        fire("list", null);
    }

    static Chat upsert(JSONObject o) {
        String id = o.optString("id");
        Chat c = chats.get(id);
        if (c == null) {
            c = new Chat();
            c.id = id;
            chats.put(id, c);
        }
        if (c.o != null) {
            Iterator<String> it = o.keys();
            JSONObject m = c.o;
            try {
                while (it.hasNext()) {
                    String k = it.next();
                    m.put(k, o.get(k));
                }
            } catch (Exception e) {
            }
        } else c.o = o;
        long mt = meta(id);
        if (mt == 0) {
            mt = o.optLong("acceptedAt", 0) > 0 ? o.optLong("acceptedAt") : o.optLong("createdAt", System.currentTimeMillis());
            setMeta(id, mt);
        }
        c.lastAt = Math.max(c.lastAt, mt);
        return c;
    }

    static synchronized JSONObject metaObj() {
        try {
            String s = App.kv.get("vulpine.sec.meta");
            return s == null ? new JSONObject() : new JSONObject(s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static long meta(String id) {
        return metaObj().optLong(id);
    }

    static synchronized void setMeta(String id, long at) {
        try {
            JSONObject o = metaObj();
            if (at <= 0) o.remove(id);
            else o.put(id, Math.max(o.optLong(id), at));
            App.kv.put("vulpine.sec.meta", o.toString());
        } catch (Exception e) {
        }
    }

    static void touch(Chat c, long at) {
        c.lastAt = Math.max(c.lastAt, at);
        setMeta(c.id, at);
    }

    static void wipe(String id, boolean fireClosed) {
        Chat c = chats.remove(id);
        if (c != null) {
            c.key = null;
            c.msgs.clear();
        }
        sentRead.remove(id);
        setMeta(id, 0);
        App.kv.remove("vulpine.sec.msgs." + id);
        for (String k : new ArrayList<>(incoming.keySet())) if (k.startsWith(id + "/")) incoming.remove(k);
        File d = new File(App.ctx.getFilesDir(), "sec/" + id);
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        d.delete();
        fire("list", id);
        if (fireClosed) fire("closed", id);
    }

    public static String errorText(String e, String name) {
        if ("already_exists".equals(e)) return "You already have a secret chat with " + name;
        if ("blocked".equals(e)) return "You can't start a secret chat with this person";
        if ("no_chat".equals(e)) return "Start a normal chat with " + name + " first";
        return "Could not create the secret chat";
    }

    public static Object create(String peerId, String pass) {
        String salt = VCrypto.b64(VCrypto.random(16));
        byte[] key = VCrypto.secretKey(pass, salt);
        String check = VCrypto.b64(VCrypto.seal(key, VCrypto.utf8(CHECK)));
        Http.Resp r = Http.post("/secret-chats", App.jo("peerId", peerId, "salt", salt, "check", check));
        JSONObject o = r.ok() ? r.obj().optJSONObject("chat") : null;
        if (o == null) return r.obj().optString("error", "failed");
        Chat c = upsert(o);
        c.key = key;
        c.loadedMsgs = true;
        touch(c, System.currentTimeMillis());
        open(c);
        fire("list", c.id);
        return c;
    }

    public static int unlock(Chat c, String pass) {
        byte[] key = VCrypto.secretKey(pass, c.o.optString("salt"));
        byte[] ok = VCrypto.open(key, VCrypto.unb64(c.o.optString("check")));
        if (ok == null || !CHECK.equals(new String(ok, StandardCharsets.UTF_8))) return 1;
        if (c.pending() && !c.mineCreated()) {
            Http.Resp r = Http.post("/secret-chats/" + c.id + "/accept", new JSONObject());
            if (!r.ok()) return 2;
            JSONObject o = r.obj().optJSONObject("chat");
            if (o != null) upsert(o);
        }
        c.key = key;
        load(c);
        open(c);
        if (c.id.equals(active)) markRead(c);
        fire("list", c.id);
        fire("msgs", c.id);
        return 0;
    }

    static void open(Chat c) {
        try {
            Rt.I.send(new JSONObject().put("type", "secret.open").put("chatId", c.id));
        } catch (Exception e) {
        }
    }

    public static boolean close(Chat c) {
        Http.Resp r = Http.delete("/secret-chats/" + c.id, null);
        if (!r.ok()) return false;
        closedByMe.add(c.id);
        wipe(c.id, false);
        return true;
    }

    static synchronized void load(Chat c) {
        if (c.key == null || c.loadedMsgs) return;
        c.loadedMsgs = true;
        List<Msg> out = new ArrayList<>();
        try {
            String s = App.kv.get("vulpine.sec.msgs." + c.id);
            if (s != null) {
                JSONArray a = new JSONArray(s);
                for (int i = 0; i < a.length(); i++) {
                    byte[] p = VCrypto.open(c.key, VCrypto.unb64(a.optString(i)));
                    if (p == null) continue;
                    Msg m = Msg.from(new JSONObject(new String(p, StandardCharsets.UTF_8)));
                    if (m.id.isEmpty() || c.find(m.id) != null) continue;
                    m.sealed = a.optString(i);
                    if ("sending".equals(m.status)) {
                        m.status = "failed";
                        m.sealed = null;
                    }
                    if (m.file != null) {
                        File f = new File(App.ctx.getFilesDir(), "sec/" + c.id + "/" + safe(m.file.optString("f")));
                        if (f.exists()) m.local = f;
                    }
                    out.add(m);
                }
            }
        } catch (Exception e) {
        }
        out.addAll(c.msgs);
        Collections.sort(out, (x, y) -> x.at != y.at ? Long.compare(x.at, y.at) : x.id.compareTo(y.id));
        c.msgs.clear();
        c.msgs.addAll(out);
    }

    static String safe(String f) {
        return f.replaceAll("[^A-Za-z0-9-]", "");
    }

    static synchronized void save(Chat c) {
        byte[] k = c.key;
        if (k == null) return;
        JSONArray a = new JSONArray();
        for (Msg m : c.msgs) {
            if (m.sealed == null) m.sealed = VCrypto.b64(VCrypto.seal(k, VCrypto.utf8(m.json().toString())));
            a.put(m.sealed);
        }
        App.kv.put("vulpine.sec.msgs." + c.id, a.toString());
    }

    static void dirty(Chat c, Msg m) {
        m.sealed = null;
        save(c);
    }

    static synchronized void add(Chat c, Msg m) {
        int i = c.msgs.size();
        while (i > 0 && c.msgs.get(i - 1).at > m.at) i--;
        c.msgs.add(i, m);
    }

    static boolean relay(Chat c, JSONObject obj, String ref) {
        byte[] k = c.key;
        if (k == null) return false;
        try {
            String payload = VCrypto.b64(VCrypto.seal(k, VCrypto.utf8(obj.toString())));
            JSONObject f = new JSONObject().put("type", "secret.relay").put("chatId", c.id).put("payload", payload);
            if (ref != null) f.put("ref", ref);
            return Rt.I.send(f);
        } catch (Exception e) {
            return false;
        }
    }

    static final List<String> RANK = java.util.Arrays.asList("failed", "sending", "sent", "read");

    static void setSt(Chat c, Msg m, String st) {
        if (!"failed".equals(st) && RANK.indexOf(st) <= RANK.indexOf(m.status == null ? "sending" : m.status)) return;
        m.status = st;
        dirty(c, m);
        fire("msgs", c.id);
    }

    public static JSONObject replyOf(Msg r) {
        if (r == null) return null;
        return App.jo("id", r.id, "text", preview(r), "from", r.from);
    }

    public static String preview(Msg m) {
        String t = m.text == null ? "" : m.text;
        if (m.file != null) {
            String mime = m.file.optString("mime");
            String k = mime.startsWith("image/") ? "Photo" : mime.startsWith("video/") ? "Video" : mime.startsWith("audio/") ? "Voice message" : "File";
            t = k + (t.isEmpty() ? "" : ": " + t);
        }
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    public static void sendText(Chat c, String text, Msg reply) {
        Msg m = new Msg();
        m.id = UUID.randomUUID().toString();
        m.from = Session.userId();
        m.at = System.currentTimeMillis();
        m.text = text.length() > 8000 ? text.substring(0, 8000) : text;
        m.reply = replyOf(reply);
        m.status = "sending";
        add(c, m);
        touch(c, m.at);
        fire("msgs", c.id);
        fire("list", c.id);
        App.bg(() -> {
            save(c);
            sendMsg(c, m, null);
        });
    }

    public static String sendFile(Chat c, File src, String name, String mime, Msg reply) {
        long limit = (Session.s != null && Session.s.optBoolean("pro") ? 100L : 30L) * 1024 * 1024;
        if (src.length() > limit) return "This file is too big";
        App.bg(() -> {
            try {
                byte[] all = Files.readAllBytes(src.toPath());
                Msg m = new Msg();
                m.id = UUID.randomUUID().toString();
                m.from = Session.userId();
                m.at = System.currentTimeMillis();
                m.text = "";
                m.reply = replyOf(reply);
                String f = UUID.randomUUID().toString();
                m.file = new JSONObject().put("f", f).put("name", name.length() > 200 ? name.substring(0, 200) : name).put("mime", mime == null || mime.isEmpty() ? "application/octet-stream" : mime).put("size", all.length).put("ready", true);
                m.status = "sending";
                File dir = new File(App.ctx.getFilesDir(), "sec/" + c.id);
                dir.mkdirs();
                File loc = new File(dir, f);
                try (FileOutputStream o = new FileOutputStream(loc)) {
                    o.write(VCrypto.seal(c.key, all));
                }
                m.local = loc;
                add(c, m);
                save(c);
                touch(c, m.at);
                fire("msgs", c.id);
                fire("list", c.id);
                sendMsg(c, m, all);
            } catch (Throwable e) {
                App.ui(() -> com.vulpine.at.ui.Ov.toast("Could not send the file", true));
            }
        });
        return null;
    }

    static boolean sendMsg(Chat c, Msg m, byte[] bytes) {
        try {
            JSONObject w = new JSONObject().put("t", "msg").put("id", m.id).put("at", m.at).put("text", m.text == null ? "" : m.text);
            if (m.reply != null) w.put("reply", m.reply);
            int n = 0;
            if (m.file != null) {
                long size = m.file.optLong("size");
                n = (int) Math.max(1, (size + CHUNK - 1) / CHUNK);
                w.put("file", new JSONObject().put("f", m.file.optString("f")).put("name", m.file.optString("name")).put("mime", m.file.optString("mime")).put("size", size).put("n", n));
            }
            if (!relay(c, w, m.id)) {
                setSt(c, m, "failed");
                return false;
            }
            if (m.file == null) return true;
            byte[] all = bytes != null ? bytes : fileBytes(c, m);
            if (all == null) {
                setSt(c, m, "failed");
                return false;
            }
            String f = m.file.optString("f");
            m.progress = 0;
            for (int i = 0; i < n; i++) {
                if (c.key == null) return false;
                int st = i * CHUNK, len = Math.min(CHUNK, all.length - st);
                byte[] part = new byte[Math.max(0, len)];
                if (len > 0) System.arraycopy(all, st, part, 0, len);
                if (!relay(c, new JSONObject().put("t", "chunk").put("f", f).put("i", i).put("n", n).put("d", VCrypto.b64(part)), m.id)) {
                    m.progress = -1;
                    setSt(c, m, "failed");
                    return false;
                }
                m.progress = (i + 1) / (float) n;
                if (i % 6 == 5) {
                    fire("progress", c.id);
                    Thread.sleep(16);
                }
            }
            m.progress = -1;
            fire("msgs", c.id);
            return true;
        } catch (Exception e) {
            m.progress = -1;
            setSt(c, m, "failed");
            return false;
        }
    }

    public static byte[] fileBytes(Chat c, Msg m) {
        try {
            byte[] k = c.key;
            if (m.local == null || k == null) return null;
            return VCrypto.open(k, Files.readAllBytes(m.local.toPath()));
        } catch (Throwable e) {
            return null;
        }
    }

    public static void retry(Chat c, Msg m) {
        if (!m.mine()) return;
        m.status = "sending";
        fire("msgs", c.id);
        App.bg(() -> {
            dirty(c, m);
            sendMsg(c, m, null);
        });
    }

    public static boolean edit(Chat c, Msg m, String text) {
        if (!m.mine()) return false;
        String t = text.length() > 8000 ? text.substring(0, 8000) : text;
        try {
            if (!relay(c, new JSONObject().put("t", "edit").put("id", m.id).put("text", t), null) || !c.peerOnline) return false;
        } catch (Exception e) {
            return false;
        }
        m.text = t;
        m.edited = System.currentTimeMillis();
        dirty(c, m);
        fire("msgs", c.id);
        return true;
    }

    public static boolean delete(Chat c, Msg m) {
        try {
            if (!relay(c, new JSONObject().put("t", "del").put("id", m.id), null) || !c.peerOnline) return false;
        } catch (Exception e) {
            return false;
        }
        removeLocal(c, m);
        save(c);
        fire("msgs", c.id);
        fire("list", c.id);
        return true;
    }

    static void removeLocal(Chat c, Msg m) {
        c.msgs.remove(m);
        if (m.file != null) incoming.remove(c.id + "/" + m.file.optString("f"));
        if (m.local != null) m.local.delete();
    }

    static void toggleRx(Msg m, String e, String uid, boolean on) {
        List<String> u = m.rx.get(e);
        if (u == null) u = new ArrayList<>();
        if (on && !u.contains(uid)) u.add(uid);
        if (!on) u.remove(uid);
        if (u.isEmpty()) m.rx.remove(e);
        else m.rx.put(e, u);
    }

    public static void react(Chat c, Msg m, String e) {
        String me = Session.userId();
        List<String> u = m.rx.get(e);
        boolean on = u == null || !u.contains(me);
        App.bg(() -> {
            try {
                if (!relay(c, new JSONObject().put("t", "react").put("id", m.id).put("e", e).put("on", on), null) || !c.peerOnline) return;
            } catch (Exception x) {
                return;
            }
            toggleRx(m, e, me, on);
            dirty(c, m);
            fire("msgs", c.id);
        });
    }

    public static void typing(Chat c) {
        if (!c.live()) return;
        App.bg(() -> {
            try {
                relay(c, new JSONObject().put("t", "typing"), null);
            } catch (Exception e) {
            }
        });
    }

    public static void markRead(Chat c) {
        if (c.key == null) return;
        Set<String> done = sentRead.computeIfAbsent(c.id, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        List<String> ids = new ArrayList<>();
        for (Msg m : c.msgs) if (!m.mine() && !done.contains(m.id)) ids.add(m.id);
        boolean had = c.unread > 0;
        c.unread = 0;
        if (had) fire("list", c.id);
        if (ids.isEmpty()) return;
        List<String> send = new ArrayList<>(ids.size() > 500 ? ids.subList(ids.size() - 500, ids.size()) : ids);
        App.bg(() -> {
            try {
                if (relay(c, new JSONObject().put("t", "read").put("ids", new JSONArray(send)), null)) done.addAll(ids);
            } catch (Exception e) {
            }
        });
    }

    static void frame(JSONObject f) {
        String t = f.optString("type");
        if ("auth.ok".equals(t)) {
            App.bg(() -> {
                fetch();
                for (Chat c : chats.values()) if (c.key != null) open(c);
            });
            return;
        }
        if (!t.startsWith("secret.")) return;
        switch (t) {
            case "secret.created":
            case "secret.updated": {
                JSONObject o = f.optJSONObject("chat");
                if (o == null) return;
                Chat c = upsert(o);
                if (c.key != null) open(c);
                fire("list", c.id);
                fire("presence", c.id);
                break;
            }
            case "secret.closed": {
                String id = f.optString("chatId");
                Chat c = chats.get(id);
                String name = c == null ? "the other person" : c.peerName();
                boolean byMe = Session.userId().equals(f.optString("byUserId")) || closedByMe.remove(id);
                wipe(id, true);
                if (!byMe && c != null) App.ui(() -> com.vulpine.at.ui.Ov.toast("Secret chat closed by " + name));
                break;
            }
            case "secret.presence": {
                Chat c = chats.get(f.optString("chatId"));
                if (c == null || Session.userId().equals(f.optString("userId"))) return;
                c.peerOnline = f.optBoolean("online");
                if (!c.peerOnline) c.typingAt = 0;
                fire("presence", c.id);
                break;
            }
            case "secret.undelivered": {
                Chat c = chats.get(f.optString("chatId"));
                if (c == null) return;
                c.peerOnline = false;
                Msg m = c.find(f.optString("ref"));
                if (m != null) App.bg(() -> setSt(c, m, "failed"));
                fire("presence", c.id);
                break;
            }
            case "secret.relay":
                App.bg(() -> handle(f));
                break;
        }
    }

    static synchronized void handle(JSONObject f) {
        Chat c = chats.get(f.optString("chatId"));
        if (c == null || c.key == null) return;
        load(c);
        byte[] p = VCrypto.open(c.key, VCrypto.unb64(f.optString("payload")));
        if (p == null) return;
        JSONObject o;
        try {
            o = new JSONObject(new String(p, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return;
        }
        String from = f.optString("fromUserId");
        String t = o.optString("t");
        try {
            if ("msg".equals(t)) {
                String id = o.optString("id", "");
                Object tx = o.opt("text");
                if (id.isEmpty() || id.length() > 64 || (tx != null && tx != JSONObject.NULL && (!(tx instanceof String) || ((String) tx).length() > 8000))) return;
                if (c.find(id) != null) {
                    relay(c, new JSONObject().put("t", "ack").put("id", id), null);
                    return;
                }
                Msg m = new Msg();
                m.id = id;
                m.from = from;
                long at = o.optLong("at", 0);
                m.at = Math.min(at > 0 ? at : System.currentTimeMillis(), System.currentTimeMillis() + 60000);
                m.text = tx instanceof String ? (String) tx : "";
                m.status = "sent";
                JSONObject r = o.optJSONObject("reply");
                if (r != null && r.opt("id") instanceof String) m.reply = App.jo("id", cut(r.optString("id"), 64), "text", cut(r.optString("text"), 300), "from", cut(r.optString("from"), 64));
                JSONObject fo = o.optJSONObject("file");
                if (fo != null && fo.opt("f") instanceof String) {
                    int n = fo.optInt("n");
                    long size = fo.optLong("size");
                    if (n > 0 && n <= 700 && size > 0 && size <= 105L * 1024 * 1024) {
                        String fid = cut(fo.optString("f"), 64);
                        m.file = new JSONObject().put("f", fid).put("name", cut(fo.optString("name", "file"), 200)).put("mime", cut(fo.optString("mime", "application/octet-stream"), 100)).put("size", size).put("ready", false);
                        Object[] inc = incoming.get(c.id + "/" + fid);
                        if (inc == null) {
                            inc = new Object[]{null, new byte[n][], 0, n};
                            incoming.put(c.id + "/" + fid, inc);
                        }
                        inc[0] = m.id;
                        m.progress = (Integer) inc[2] / (float) n;
                    }
                }
                add(c, m);
                save(c);
                touch(c, m.at);
                relay(c, new JSONObject().put("t", "ack").put("id", m.id), null);
                if (m.file != null) {
                    Object[] inc = incoming.get(c.id + "/" + m.file.optString("f"));
                    if (inc != null && ((Integer) inc[2]).intValue() == ((Integer) inc[3]).intValue()) finishFile(c, m.file.optString("f"));
                }
                boolean seen = c.id.equals(active) && App.fg;
                if (seen) markRead(c);
                else c.unread++;
                c.typingAt = 0;
                fire("msgs", c.id);
                fire("list", c.id);
            } else if ("chunk".equals(t)) {
                String fid = o.optString("f");
                String key = c.id + "/" + fid;
                Object[] inc = incoming.get(key);
                int i = o.optInt("i", -1), n = o.optInt("n");
                if (inc == null) {
                    if (n <= 0 || n > 700) return;
                    inc = new Object[]{null, new byte[n][], 0, n};
                    incoming.put(key, inc);
                }
                byte[][] parts = (byte[][]) inc[1];
                if (i < 0 || i >= parts.length || parts[i] != null) return;
                parts[i] = VCrypto.unb64(o.optString("d"));
                inc[2] = (Integer) inc[2] + 1;
                float pr = (Integer) inc[2] / (float) (Integer) inc[3];
                Msg m = inc[0] == null ? null : c.find((String) inc[0]);
                if (m != null) m.progress = pr;
                if (((Integer) inc[2]).intValue() == ((Integer) inc[3]).intValue() && inc[0] != null) finishFile(c, fid);
                else if ((Integer) inc[2] % 4 == 0) fire("progress", c.id);
            } else if ("ack".equals(t)) {
                Msg m = c.find(o.optString("id"));
                if (m != null && m.mine()) setSt(c, m, "sent");
            } else if ("read".equals(t)) {
                JSONArray ids = o.optJSONArray("ids");
                if (ids != null) for (int i = 0; i < Math.min(500, ids.length()); i++) {
                    Msg m = c.find(ids.optString(i));
                    if (m != null && m.mine()) setSt(c, m, "read");
                }
            } else if ("edit".equals(t)) {
                Msg m = c.find(o.optString("id"));
                Object nt = o.opt("text");
                if (m == null || !(nt instanceof String) || ((String) nt).length() > 8000 || !m.from.equals(from)) return;
                m.text = (String) nt;
                m.edited = System.currentTimeMillis();
                dirty(c, m);
                fire("msgs", c.id);
            } else if ("del".equals(t)) {
                Msg m = c.find(o.optString("id"));
                if (m == null) return;
                removeLocal(c, m);
                save(c);
                fire("msgs", c.id);
                fire("list", c.id);
            } else if ("react".equals(t)) {
                Msg m = c.find(o.optString("id"));
                String e = o.optString("e", "");
                if (m == null || e.isEmpty() || e.length() > 16) return;
                toggleRx(m, e, from, o.optBoolean("on"));
                dirty(c, m);
                fire("msgs", c.id);
            } else if ("typing".equals(t)) {
                c.typingAt = System.currentTimeMillis();
                fire("typing", c.id);
                App.H.postDelayed(() -> fire("typing", c.id), 5100);
            }
        } catch (Exception e) {
        }
    }

    static String cut(String s, int n) {
        return s == null ? "" : s.length() > n ? s.substring(0, n) : s;
    }

    static void finishFile(Chat c, String fid) {
        String key = c.id + "/" + fid;
        Object[] inc = incoming.remove(key);
        if (inc == null) return;
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            for (byte[] part : (byte[][]) inc[1]) bo.write(part);
            File dir = new File(App.ctx.getFilesDir(), "sec/" + c.id);
            dir.mkdirs();
            File loc = new File(dir, safe(fid));
            try (FileOutputStream fo = new FileOutputStream(loc)) {
                fo.write(VCrypto.seal(c.key, bo.toByteArray()));
            }
            Msg m = c.find((String) inc[0]);
            if (m != null) {
                m.local = loc;
                m.progress = -1;
                m.file.put("ready", true);
                dirty(c, m);
            }
        } catch (Throwable e) {
        }
        fire("msgs", c.id);
    }
}
