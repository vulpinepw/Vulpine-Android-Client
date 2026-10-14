package com.vulpine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Chats {
    public static final class Conv {
        public JSONObject o;
        public String id, type;
        public volatile byte[] key;
        public volatile String keyError;

        Conv(JSONObject j) {
            o = j;
            id = j.optString("id");
            type = j.optString("type");
        }

        public boolean group() {
            return "group".equals(type);
        }

        public String title() {
            return group() ? o.optString("name") : o.optString("peerDisplayName");
        }

        public String avatarUrl() {
            String u = group() ? o.optString("avatarUrl", null) : o.optString("peerAvatarUrl", null);
            return u == null || u.isEmpty() || "null".equals(u) ? null : u;
        }

        public String avatarColor() {
            String k = group() ? "avatarColor" : "peerAvatarColor";
            if (!o.has(k) || o.isNull(k)) return null;
            return o.optString(k);
        }

        public long lastAt() {
            return o.optLong("lastMessageAt", o.optLong("createdAt"));
        }

        public JSONObject member(String uid) {
            JSONArray m = o.optJSONArray("members");
            if (m != null) for (int i = 0; i < m.length(); i++) {
                JSONObject x = m.optJSONObject(i);
                if (x != null && uid.equals(x.optString("id"))) return x;
            }
            return null;
        }
    }

    public static final class Msg {
        public String id, tempId, cid, senderId;
        public long seq, createdAt, editedAt;
        public Long readAt;
        public boolean pinned;
        public String text;
        public boolean decryptFailed;
        public JSONObject sender, raw;
        public int state;
        public Map<String, List<String>> reactions = new HashMap<>();

        public static final int SENT = 0, SENDING = 1, FAILED = 2;

        public boolean mine() {
            return senderId != null && senderId.equals(Session.userId());
        }
    }

    public interface Listener {
        void on(String event, String cid, Object data);
    }

    public static final Chats I = new Chats();
    public final Map<String, Conv> convs = new ConcurrentHashMap<>();
    public final List<String> order = new CopyOnWriteArrayList<>();
    public final Map<String, List<Msg>> msgs = new ConcurrentHashMap<>();
    public final Map<String, Boolean> hasOlder = new ConcurrentHashMap<>();
    private final List<Listener> ls = new CopyOnWriteArrayList<>();
    public volatile int total = -1;
    public volatile String activeCid;
    private boolean wired;

    public void on(Listener l) {
        ls.add(l);
    }

    public void off(Listener l) {
        ls.remove(l);
    }

    void emit(String ev, String cid, Object d) {
        for (Listener l : ls) try {
            l.on(ev, cid, d);
        } catch (Exception e) {
        }
    }

    public synchronized void wire() {
        if (wired) return;
        wired = true;
        Rt.I.on(this::onWs);
    }

    public void restoreCache() {
        try {
            String s = Session.kv.get("vulpine.convlist." + Session.userId());
            if (s == null) return;
            JSONArray a = new JSONArray(s);
            apply(a, true);
        } catch (Exception e) {
        }
    }

    private void apply(JSONArray a, boolean replace) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id");
            Conv c = convs.get(id);
            if (c == null) convs.put(id, new Conv(o));
            else {
                c.o = o;
                c.type = o.optString("type");
            }
            ids.add(id);
        }
        if (replace) {
            order.clear();
            order.addAll(ids);
        } else for (String id : ids) if (!order.contains(id)) order.add(id);
    }

    public boolean loadList(int offset) {
        Http.Resp r = Http.get("/conversations?limit=30&offset=" + offset);
        if (!r.ok()) return false;
        JSONObject o = r.obj();
        JSONArray a = o.optJSONArray("conversations");
        if (a == null) return false;
        total = o.optInt("total", a.length());
        apply(a, offset == 0);
        if (offset == 0) persistList();
        emit("list", null, null);
        return true;
    }

    public void loadAll() {
        Http.Resp r = Http.get("/conversations");
        if (!r.ok()) return;
        JSONArray a = r.obj().optJSONArray("conversations");
        if (a == null) return;
        total = a.length();
        apply(a, true);
        persistList();
        emit("list", null, null);
    }

    void persistList() {
        JSONArray a = new JSONArray();
        int n = 0;
        for (String id : order) {
            Conv c = convs.get(id);
            if (c != null && n++ < 60) a.put(c.o);
        }
        Session.kv.put("vulpine.convlist." + Session.userId(), a.toString());
    }

    public Conv conv(String id) {
        return convs.get(id);
    }

    public Conv refresh(String id) {
        Http.Resp r = Http.get("/conversations/" + id + "?memberLimit=200&memberOffset=0");
        if (!r.ok()) return convs.get(id);
        JSONObject o = r.obj();
        Conv c = convs.get(id);
        if (c == null) {
            c = new Conv(o);
            convs.put(id, c);
        } else {
            JSONObject old = c.o;
            if (old.has("lastMessage") && !o.has("lastMessage")) try {
                o.put("lastMessage", old.get("lastMessage"));
                o.put("lastMessageAt", old.opt("lastMessageAt"));
            } catch (Exception e) {
            }
            c.o = o;
        }
        emit("conv", id, c);
        return c;
    }

    public byte[] key(Conv c) {
        if (c.key != null) return c.key;
        VCrypto.Identity me = Session.id;
        if (me == null) return null;
        try {
            if (!c.group()) {
                String pk = c.o.optString("peerEncPubkey");
                if (pk.isEmpty()) return null;
                c.key = VCrypto.dmKey(me.encPrivkey, pk);
                return c.key;
            }
            String cached = Session.kv.get("vulpine.gk." + c.id);
            if (cached != null) {
                c.key = VCrypto.unb64(cached);
                return c.key;
            }
            Http.Resp r = Http.get("/conversations/group-key/" + c.id);
            if (!r.ok()) {
                c.keyError = r.error();
                return null;
            }
            JSONObject k = r.obj();
            byte[] key = null;
            if (!k.optString("key").isEmpty()) key = VCrypto.unb64(k.getString("key"));
            else if ("__plain__".equals(k.optString("nonce"))) key = VCrypto.unb64(k.getString("ciphertext"));
            else {
                JSONObject cr = c.member(c.o.optString("creatorId"));
                if (cr == null) cr = refresh(c.id).member(c.o.optString("creatorId"));
                if (cr != null) {
                    String t = VCrypto.decrypt(k.optString("ciphertext"), k.optString("nonce"), VCrypto.dmKey(me.encPrivkey, cr.optString("encPubkey")));
                    if (t != null) key = VCrypto.unb64(t);
                }
            }
            if (key != null && key.length == 32) {
                c.key = key;
                Session.kv.put("vulpine.gk." + c.id, VCrypto.b64(key));
            } else c.keyError = "no_key";
            return c.key;
        } catch (Exception e) {
            c.keyError = String.valueOf(e.getMessage());
            return null;
        }
    }

    public String decrypt(Conv c, String ct, String nonce) {
        byte[] k = key(c);
        return k == null ? null : VCrypto.decrypt(ct, nonce, k);
    }

    public String preview(Conv c) {
        JSONObject lm = c.o.optJSONObject("lastMessage");
        if (lm == null) return null;
        if (lm.optBoolean("truncated")) return "📎 Attachment";
        String t = decrypt(c, lm.optString("ciphertext", null), lm.optString("nonce", null));
        return t;
    }

    Msg parse(JSONObject j, Conv c) {
        Msg m = new Msg();
        m.raw = j;
        m.id = j.optString("id");
        m.cid = j.optString("conversationId", c == null ? null : c.id);
        m.senderId = j.optString("senderId");
        m.seq = j.optLong("seq");
        m.createdAt = j.optLong("createdAt");
        m.editedAt = j.optLong("editedAt");
        m.readAt = j.isNull("readAt") || !j.has("readAt") ? null : j.optLong("readAt");
        m.pinned = j.optBoolean("pinned");
        m.sender = j.optJSONObject("sender");
        if (c != null) {
            m.text = decrypt(c, j.optString("ciphertext", null), j.optString("nonce", null));
            m.decryptFailed = m.text == null;
        }
        return m;
    }

    public List<Msg> list(String cid) {
        List<Msg> l = msgs.get(cid);
        if (l == null) {
            l = Collections.synchronizedList(new ArrayList<>());
            msgs.put(cid, l);
        }
        return l;
    }

    public void restoreMsgs(String cid) {
        Conv c = convs.get(cid);
        if (c == null || !list(cid).isEmpty()) return;
        try {
            String s = Session.kv.get("vulpine.msgs." + cid);
            if (s == null) return;
            JSONArray a = new JSONArray(s);
            List<Msg> l = list(cid);
            for (int i = 0; i < a.length(); i++) l.add(parse(a.getJSONObject(i), c));
        } catch (Exception e) {
        }
    }

    void persistMsgs(String cid) {
        List<Msg> l = list(cid);
        JSONArray a = new JSONArray();
        synchronized (l) {
            int from = Math.max(0, l.size() - 120);
            for (int i = from; i < l.size(); i++) if (l.get(i).id != null && l.get(i).raw != null) a.put(l.get(i).raw);
        }
        Session.kv.put("vulpine.msgs." + cid, a.toString());
    }

    public boolean loadMessages(String cid) {
        Conv c = convs.get(cid);
        if (c == null) c = refresh(cid);
        if (c == null) return false;
        key(c);
        List<Msg> l = list(cid);
        long after = 0;
        synchronized (l) {
            for (Msg m : l) if (m.id != null) after = Math.max(after, Math.max(m.createdAt, m.editedAt));
        }
        Http.Resp r = Http.get("/messages/" + cid + (after > 0 ? "?after=" + after : "?limit=30"));
        if (!r.ok()) return false;
        JSONObject o = r.obj();
        JSONArray a = o.optJSONArray("messages");
        if (a == null) return false;
        if (after == 0) hasOlder.put(cid, o.optBoolean("hasMore"));
        for (int i = 0; i < a.length(); i++) { JSONObject x = a.optJSONObject(i); if (x != null) upsert(parse(x, c), false); }
        persistMsgs(cid);
        emit("messages", cid, null);
        return true;
    }

    public int loadOlder(String cid) {
        Conv c = convs.get(cid);
        if (c == null) return 0;
        List<Msg> l = list(cid);
        long min = Long.MAX_VALUE;
        synchronized (l) {
            for (Msg m : l) if (m.seq > 0) min = Math.min(min, m.seq);
        }
        if (min == Long.MAX_VALUE) {
            hasOlder.put(cid, false);
            return 0;
        }
        Http.Resp r = Http.get("/messages/" + cid + "?before=" + min + "&limit=30");
        if (!r.ok()) return 0;
        JSONObject o = r.obj();
        JSONArray a = o.optJSONArray("messages");
        hasOlder.put(cid, o.optBoolean("hasMore"));
        if (a == null) return 0;
        List<Msg> add = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) { JSONObject x = a.optJSONObject(i); if (x != null) add.add(parse(x, c)); }
        synchronized (l) {
            l.addAll(0, add);
        }
        emit("older", cid, add.size());
        return add.size();
    }

    void upsert(Msg m, boolean notify) {
        List<Msg> l = list(m.cid);
        synchronized (l) {
            for (int i = 0; i < l.size(); i++) {
                Msg x = l.get(i);
                if ((m.id != null && m.id.equals(x.id)) || (m.tempId != null && m.tempId.equals(x.tempId))) {
                    m.reactions = x.reactions;
                    l.set(i, m);
                    if (notify) emit("update", m.cid, m);
                    return;
                }
            }
            int pos = l.size();
            while (pos > 0 && l.get(pos - 1).createdAt > m.createdAt && l.get(pos - 1).state == Msg.SENT) pos--;
            l.add(pos, m);
        }
        if (notify) emit("new", m.cid, m);
    }

    public Msg send(String cid, String text, List<String> mentions) {
        Conv c = convs.get(cid);
        if (c == null) return null;
        byte[] k = key(c);
        if (k == null) return null;
        JSONObject env = VCrypto.encrypt(text, k);
        Msg m = new Msg();
        m.tempId = UUID.randomUUID().toString();
        m.cid = cid;
        m.senderId = Session.userId();
        m.createdAt = System.currentTimeMillis();
        m.text = text;
        m.state = Msg.SENDING;
        try {
            JSONObject f = new JSONObject().put("type", "message.send").put("tempId", m.tempId).put("conversationId", cid).put("ciphertext", env.getString("ciphertext")).put("nonce", env.getString("nonce"));
            if (mentions != null && !mentions.isEmpty()) f.put("mentions", new JSONArray(mentions));
            upsert(m, true);
            Rt.I.send(f);
        } catch (Exception e) {
            m.state = Msg.FAILED;
        }
        return m;
    }

    public boolean edit(String cid, String mid, String text) {
        Conv c = convs.get(cid);
        if (c == null || key(c) == null) return false;
        JSONObject env = VCrypto.encrypt(text, key(c));
        try {
            return Rt.I.send(new JSONObject().put("type", "message.edit").put("conversationId", cid).put("messageId", mid).put("ciphertext", env.getString("ciphertext")).put("nonce", env.getString("nonce")));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean delete(String cid, String mid) {
        return Http.delete("/messages/" + cid + "/" + mid, null).ok();
    }

    public void react(String cid, String mid, String emoji) {
        try {
            Rt.I.send(new JSONObject().put("type", "reaction.toggle").put("conversationId", cid).put("messageId", mid).put("emoji", emoji));
        } catch (Exception e) {
        }
    }

    public void pin(String cid, String mid, boolean on) {
        try {
            Rt.I.send(new JSONObject().put("type", "message.pin").put("conversationId", cid).put("messageId", mid).put("pinned", on));
        } catch (Exception e) {
        }
    }

    public void read(String cid, List<String> ids) {
        if (ids.isEmpty()) return;
        try {
            Rt.I.send(new JSONObject().put("type", "message.read").put("conversationId", cid).put("messageIds", new JSONArray(ids)));
        } catch (Exception e) {
        }
    }

    public void typing(String cid, boolean on) {
        try {
            Rt.I.send(new JSONObject().put("type", "typing").put("conversationId", cid).put("isTyping", on));
        } catch (Exception e) {
        }
    }

    void bumpConv(String cid, JSONObject raw, long at) {
        Conv c = convs.get(cid);
        if (c == null) return;
        try {
            JSONObject lm = new JSONObject();
            for (String k : new String[]{"id", "seq", "senderId", "ciphertext", "nonce", "createdAt", "readAt", "editedAt"}) if (raw.has(k)) lm.put(k, raw.get(k));
            c.o.put("lastMessage", lm);
            c.o.put("lastMessageAt", at);
        } catch (Exception e) {
        }
        order.remove(cid);
        int pins = 0;
        JSONArray pinned = Session.settings().optJSONArray("pinnedChatIds");
        if (pinned != null) for (int i = 0; i < pinned.length(); i++) if (order.contains(pinned.optString(i))) pins++;
        boolean isPinned = false;
        if (pinned != null) for (int i = 0; i < pinned.length(); i++) if (cid.equals(pinned.optString(i))) isPinned = true;
        order.add(isPinned ? 0 : Math.min(pins, order.size()), cid);
        emit("list", cid, null);
    }

    void onWs(JSONObject m) {
        String t = m.optString("type");
        switch (t) {
            case "message.ack": {
                JSONObject j = m.optJSONObject("message");
                if (j == null) return;
                Conv c = convs.get(j.optString("conversationId"));
                Msg x = parse(j, c);
                x.tempId = m.optString("tempId");
                upsert(x, true);
                bumpConv(x.cid, j, x.createdAt);
                persistMsgs(x.cid);
                break;
            }
            case "message.received": {
                JSONObject j = m.optJSONObject("message");
                if (j == null) return;
                String cid = j.optString("conversationId");
                Conv c = convs.get(cid);
                if (c == null) {
                    c = refresh(cid);
                    if (c == null) return;
                    if (!order.contains(cid)) order.add(0, cid);
                }
                Msg x = parse(j, c);
                upsert(x, true);
                bumpConv(cid, j, x.createdAt);
                persistMsgs(cid);
                break;
            }
            case "message.updated": {
                JSONObject j = m.optJSONObject("message");
                if (j == null) return;
                Conv c = convs.get(j.optString("conversationId"));
                if (c == null) return;
                upsert(parse(j, c), true);
                persistMsgs(c.id);
                break;
            }
            case "message.deleted": {
                String cid = m.optString("conversationId"), mid = m.optString("messageId");
                removeIds(cid, Collections.singletonList(mid));
                break;
            }
            case "messages.cleared": {
                String cid = m.optString("conversationId");
                if (m.optBoolean("clearedAll")) {
                    list(cid).clear();
                    persistMsgs(cid);
                    emit("messages", cid, null);
                } else {
                    JSONArray a = m.optJSONArray("messageIds");
                    List<String> ids = new ArrayList<>();
                    if (a != null) for (int i = 0; i < a.length(); i++) ids.add(a.optString(i));
                    removeIds(cid, ids);
                }
                break;
            }
            case "local.outbox": {
                String cid = m.optString("conversationId"), tmp = m.optString("tempId");
                List<Msg> l = list(cid);
                synchronized (l) {
                    for (Msg x : l) if (tmp.equals(x.tempId) && x.id == null) {
                        x.state = Msg.FAILED;
                        emit("update", cid, x);
                    }
                }
                break;
            }
            default:
                emit("ws:" + t, m.optString("conversationId", null), m);
        }
    }

    void removeIds(String cid, List<String> ids) {
        List<Msg> l = list(cid);
        boolean ch = false;
        synchronized (l) {
            for (int i = l.size() - 1; i >= 0; i--) if (ids.contains(l.get(i).id)) {
                l.remove(i);
                ch = true;
            }
        }
        if (ch) {
            persistMsgs(cid);
            emit("removed", cid, ids);
        }
    }

    public void reset() {
        convs.clear();
        order.clear();
        msgs.clear();
        hasOlder.clear();
        total = -1;
    }
}
