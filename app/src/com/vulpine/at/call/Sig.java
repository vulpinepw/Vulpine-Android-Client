package com.vulpine.at.call;

import com.vulpine.core.VCrypto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Sig {
    public interface Cb<T> {
        void on(T v);
    }

    public interface Peer {
        String sigState();

        String connState();

        boolean hasRemote();

        String localSdp();

        String remoteSdp();

        void createOffer(boolean iceRestart, Cb<String> sdp);

        void createAnswer(Cb<String> sdp);

        void setLocal(String type, String sdp, Cb<Boolean> ok);

        void setRemote(String type, String sdp, Cb<Boolean> ok);

        void addIce(JSONObject cand);

        void restartIce();

        void close();
    }

    public interface Host {
        void send(JSONObject m);

        void post(Runnable r, long delayMs);

        Peer newPeer(String uid);

        void acquire(boolean video, Cb<Boolean> ok);

        void release();

        boolean localVideo();

        void event(String what, Object arg);
    }

    public static final class Part {
        public final String uid;
        public String name, avatarUrl, avatarColor;
        public boolean stream, connected;
        public Boolean rMuted, rDeaf, rVideo;
        public int[] keys;
        public double volume = 1;
        public boolean mutedLocally, videoHidden, streamDisabled;

        public Part(String uid) {
            this.uid = uid;
        }
    }

    public static final class Call {
        public String callId, cid;
        public final LinkedHashMap<String, Part> parts = new LinkedHashMap<>();
        public long startedAt = System.currentTimeMillis();

        public boolean room() {
            return cid != null && cid.startsWith("r:");
        }

        public boolean connected() {
            if (room()) return true;
            for (Part p : parts.values()) if (p.stream) return true;
            return false;
        }
    }

    public static final class Incoming {
        public String callId, cid, from, name, avatarUrl, avatarColor;
        public boolean anon;
    }

    static final class P {
        final Peer pc;
        final String uid;
        boolean pend, pendR, mk, fp;
        int oseq;

        P(String uid, Peer pc) {
            this.uid = uid;
            this.pc = pc;
        }
    }

    public final Host h;
    public String me = "";
    public Call cur;
    public Incoming inc;
    public final Set<String> activeConvs = new HashSet<>();
    public final Map<String, JSONArray> members = new HashMap<>();
    final Map<String, P> pcs = new LinkedHashMap<>();
    final Map<String, List<JSONObject>> iceQ = new HashMap<>();
    final Map<String, String[]> info = new HashMap<>();
    public boolean mic = true, deaf, cam, screen;
    boolean deafMuted, wasRemote, acquiring;
    int wdSeq;

    public Sig(Host h) {
        this.h = h;
    }

    void changed() {
        h.event("changed", null);
    }

    void convs() {
        h.event("convs", null);
    }

    static String str(JSONObject o, String k) {
        if (o == null || !o.has(k) || o.isNull(k)) return null;
        String s = o.optString(k, null);
        return s == null || s.isEmpty() ? null : s;
    }

    void remember(String uid, String name, String url, String color) {
        if (uid == null) return;
        String[] o = info.get(uid);
        if (o == null || o[0] == null || o[0].isEmpty()) info.put(uid, new String[]{name, url, color});
    }

    Part addPart(String uid, String name, String url, String color) {
        Part p = cur.parts.get(uid);
        if (p != null) return p;
        p = new Part(uid);
        p.name = name == null ? uid : name;
        p.avatarUrl = url;
        p.avatarColor = color;
        cur.parts.put(uid, p);
        return p;
    }

    public Part part(String uid) {
        return cur == null ? null : cur.parts.get(uid);
    }

    public void start(String cid, JSONArray fallback, boolean video) {
        if (cur != null && cid.equals(cur.cid)) {
            h.event("maximize", null);
            return;
        }
        if (acquiring) return;
        acquiring = true;
        cam = video;
        h.acquire(video, ok -> {
            acquiring = false;
            if (!ok) {
                cam = false;
                h.event("toast-error", "Could not access your microphone — check app permissions");
                return;
            }
            Call c = new Call();
            c.callId = UUID.randomUUID().toString();
            c.cid = cid;
            cur = c;
            mic = true;
            JSONArray ms = members.get(cid);
            if (ms == null) ms = fallback;
            if (ms != null) for (int i = 0; i < ms.length(); i++) {
                JSONObject m = ms.optJSONObject(i);
                if (m == null) continue;
                String uid = m.optString("userId", m.optString("id"));
                if (uid.isEmpty() || uid.equals(me)) continue;
                remember(uid, str(m, "displayName"), str(m, "avatarUrl"), str(m, "avatarColor"));
                addPart(uid, str(m, "displayName"), str(m, "avatarUrl"), str(m, "avatarColor"));
            }
            h.send(jo("type", "call.invite", "callId", c.callId, "conversationId", cid));
            h.event("started", null);
            changed();
        });
    }

    public void accept() {
        Incoming v = inc;
        if (v == null || acquiring) return;
        inc = null;
        h.event("ringStop", null);
        changed();
        acquiring = true;
        cam = false;
        h.acquire(false, ok -> {
            acquiring = false;
            if (!ok) {
                h.event("toast-error", "Could not access your microphone — check app permissions");
                h.send(jo("type", "call.reject", "callId", v.callId, "conversationId", v.cid));
                return;
            }
            Call c = new Call();
            c.callId = v.callId;
            c.cid = v.cid;
            cur = c;
            mic = true;
            addPart(v.from, v.name == null ? v.from : v.name, v.avatarUrl, v.avatarColor);
            h.send(jo("type", "call.accept", "callId", v.callId, "conversationId", v.cid));
            armWd(v.callId);
            h.event("started", null);
            changed();
        });
    }

    public void reject() {
        Incoming v = inc;
        if (v == null) return;
        h.send(jo("type", "call.reject", "callId", v.callId, "conversationId", v.cid));
        h.event("ringStop", null);
        inc = null;
        changed();
    }

    public void end() {
        Call c = cur;
        if (c == null) return;
        h.send(jo("type", "call.end", "callId", c.callId, "conversationId", c.cid));
        teardown(true);
    }

    public void teardown(boolean byMe) {
        Call c = cur;
        if (c != null && wasRemote) h.event("rate", c.callId);
        wasRemote = false;
        h.event("ringStop", null);
        wdSeq++;
        for (P p : pcs.values()) try {
            p.pc.close();
        } catch (Exception e) {
        }
        pcs.clear();
        iceQ.clear();
        if (c != null) h.release();
        cur = null;
        screen = false;
        cam = false;
        mic = true;
        deaf = false;
        deafMuted = false;
        if (c != null) h.event("ended", byMe ? "me" : "remote");
        changed();
    }

    void armWd(String cid) {
        final int s = ++wdSeq;
        h.post(() -> {
            if (s != wdSeq) return;
            Call d = cur;
            if (d == null || !d.callId.equals(cid)) return;
            for (Part p : d.parts.values()) if (p.stream) return;
            h.send(jo("type", "call.end", "callId", cid, "conversationId", d.cid));
            teardown(false);
            h.event("toast-error", "Call failed to connect");
        }, 45000);
    }

    P mkPeer(String uid) {
        Peer pc = h.newPeer(uid);
        P p = new P(uid, pc);
        pcs.put(uid, p);
        for (long ms : new long[]{6000, 14000, 26000})
            h.post(() -> {
                if (pcs.get(uid) != p) return;
                String cs = pc.connState();
                if ("connected".equals(cs) || "closed".equals(cs) || p.mk) return;
                String ss = pc.sigState();
                if ("have-local-offer".equals(ss)) {
                    pc.setLocal("rollback", "", ok -> offer(uid, pc.hasRemote()));
                    return;
                }
                if ("stable".equals(ss)) offer(uid, pc.hasRemote());
            }, ms);
        return p;
    }

    public void offer(String uid, boolean restart) {
        Call c = cur;
        if (c == null) return;
        P p = pcs.get(uid);
        if (p == null) p = mkPeer(uid);
        final P q = p;
        if (!"stable".equals(q.pc.sigState()) || q.mk) {
            q.pend = true;
            q.pendR = q.pendR || restart;
            return;
        }
        q.mk = true;
        q.pc.createOffer(restart, sdp -> {
            if (sdp == null) {
                q.mk = false;
                return;
            }
            if (!"stable".equals(q.pc.sigState())) {
                q.pend = true;
                q.mk = false;
                return;
            }
            q.pc.setLocal("offer", sdp, ok -> {
                q.mk = false;
                if (!ok || pcs.get(uid) != q) return;
                Call d = cur;
                if (d == null) return;
                h.send(signal(d, uid, jo("kind", "offer", "sdp", sdpJson("offer", sdp))));
                final int k = ++q.oseq;
                h.post(() -> {
                    if (pcs.get(uid) != q || q.oseq != k || q.mk || !"have-local-offer".equals(q.pc.sigState()) || !"connected".equals(q.pc.connState())) return;
                    q.pc.setLocal("rollback", "", ok2 -> {
                        if (ok2) offer(uid, false);
                    });
                }, 6000);
            });
        });
    }

    public void renegotiateAll() {
        for (String uid : new ArrayList<>(pcs.keySet())) offer(uid, false);
    }

    public void sendState(String only) {
        Call d = cur;
        if (d == null) return;
        JSONObject data = jo("kind", "state", "muted", !mic, "deafened", deaf, "video", h.localVideo());
        for (String uid : pcs.keySet()) {
            if (only != null && !only.equals(uid)) continue;
            h.send(signal(d, uid, data));
        }
    }

    static JSONObject signal(Call d, String uid, JSONObject data) {
        return jo("type", "call.signal", "callId", d.callId, "conversationId", d.cid, "targetUserId", uid, "data", data);
    }

    static String sdpJson(String type, String sdp) {
        return jo("type", type, "sdp", sdp).toString();
    }

    public void onIce(String uid, Peer pc, JSONObject cand) {
        Call d = cur;
        P p = pcs.get(uid);
        if (d == null || p == null || p.pc != pc) return;
        h.send(signal(d, uid, jo("kind", "ice", "candidate", cand.toString())));
    }

    public void onSigState(String uid, Peer pc, String st) {
        P p = pcs.get(uid);
        if (p == null || p.pc != pc) return;
        if (!"stable".equals(st) || !p.pend) return;
        boolean rs = p.pendR;
        p.pend = false;
        p.pendR = false;
        h.post(() -> {
            if (pcs.get(uid) == p) offer(uid, rs);
        }, 0);
    }

    public void onConn(String uid, Peer pc, String st) {
        P p = pcs.get(uid);
        if (p == null || p.pc != pc) return;
        Part pt = part(uid);
        if ("connected".equals(st)) {
            if (pt != null) pt.connected = true;
            if (!p.fp) {
                String a = pc.localSdp(), b = pc.remoteSdp();
                int[] k = a == null || b == null ? null : VCrypto.callEmojiIdx(a, b);
                if (k != null) {
                    p.fp = true;
                    if (pt != null) pt.keys = k;
                }
            }
            sendState(uid);
            changed();
        } else if ("failed".equals(st)) {
            h.post(() -> {
                if (pcs.get(uid) != p || !"failed".equals(pc.connState())) return;
                try {
                    pc.restartIce();
                } catch (Exception e) {
                }
                offer(uid, true);
            }, me.compareTo(uid) > 0 ? 400 : 2500);
        } else if ("disconnected".equals(st) || "closed".equals(st)) {
            if (pt != null) pt.connected = false;
            changed();
        }
    }

    public void onTrack(String uid, Peer pc) {
        P p = pcs.get(uid);
        if (p == null || p.pc != pc || cur == null) return;
        wasRemote = true;
        Part pt = cur.parts.get(uid);
        if (pt == null) {
            String[] i = info.get(uid);
            pt = addPart(uid, i == null ? uid : i[0], i == null ? null : i[1], i == null ? null : i[2]);
        }
        boolean was = cur.connected();
        pt.stream = true;
        if (!was) h.event("connectedFirst", null);
        changed();
    }

    public void onFrame(JSONObject v) {
        String t = v.optString("type");
        switch (t) {
            case "auth.ok": {
                Call c = cur;
                if (c != null && c.callId != null) h.send(jo("type", "call.resume", "callId", c.callId, "conversationId", c.cid));
                break;
            }
            case "call.members": {
                String cid = v.optString("conversationId");
                JSONArray ms = v.optJSONArray("members");
                if (v.optBoolean("active") && ms != null && ms.length() > 0) members.put(cid, ms);
                else members.remove(cid);
                if (ms != null) for (int i = 0; i < ms.length(); i++) {
                    JSONObject m = ms.optJSONObject(i);
                    if (m == null) continue;
                    String uid = m.optString("userId");
                    remember(uid, str(m, "displayName"), str(m, "avatarUrl"), str(m, "avatarColor"));
                    Part p = part(uid);
                    if (p != null && cid.equals(cur.cid) && (p.name == null || p.name.equals(p.uid))) {
                        String n = str(m, "displayName");
                        if (n != null) p.name = n;
                        if (p.avatarUrl == null) p.avatarUrl = str(m, "avatarUrl");
                        if (p.avatarColor == null) p.avatarColor = str(m, "avatarColor");
                    }
                }
                convs();
                changed();
                break;
            }
            case "call.state": {
                String cid = v.optString("conversationId");
                if (!v.optBoolean("active")) {
                    members.remove(cid);
                    if (cur != null && cid.equals(cur.cid)) teardown(false);
                    if (inc != null && cid.equals(inc.cid)) {
                        inc = null;
                        h.event("ringStop", null);
                    }
                    activeConvs.remove(cid);
                } else {
                    String id = str(v, "callId");
                    if (id != null && cur != null && cid.equals(cur.cid) && !id.equals(cur.callId)) cur.callId = id;
                    activeConvs.add(cid);
                }
                convs();
                changed();
                break;
            }
            case "call.incoming": {
                String cid = v.optString("conversationId"), from = v.optString("fromUserId");
                if (cur != null) {
                    if (cid.equals(cur.cid)) {
                        P st = pcs.remove(from);
                        if (st != null) try {
                            st.pc.close();
                        } catch (Exception e) {
                        }
                        info.put(from, new String[]{str(v, "fromDisplayName"), str(v, "fromAvatarUrl"), str(v, "fromAvatarColor")});
                        addPart(from, str(v, "fromDisplayName"), str(v, "fromAvatarUrl"), str(v, "fromAvatarColor"));
                        h.event("beep", "join");
                        offer(from, false);
                        changed();
                    } else {
                        h.event("toast-error", "Incoming call declined - you are already in a call");
                        h.send(jo("type", "call.reject", "callId", v.optString("callId"), "conversationId", cid));
                    }
                    return;
                }
                info.put(from, new String[]{str(v, "fromDisplayName"), str(v, "fromAvatarUrl"), str(v, "fromAvatarColor")});
                Incoming n = new Incoming();
                n.callId = v.optString("callId");
                n.cid = cid;
                n.from = from;
                n.name = str(v, "fromDisplayName");
                n.avatarUrl = str(v, "fromAvatarUrl");
                n.avatarColor = str(v, "fromAvatarColor");
                inc = n;
                h.event("ring", n);
                changed();
                break;
            }
            case "call.handled": {
                String id = v.optString("callId");
                if (!(cur != null && id.equals(cur.callId)) && inc != null && id.equals(inc.callId)) {
                    inc = null;
                    h.event("ringStop", null);
                    changed();
                }
                break;
            }
            case "call.accepted": {
                Call c = cur;
                String id = v.optString("callId"), uid = v.optString("userId");
                if (c == null || !id.equals(c.callId)) break;
                if (!c.parts.containsKey(uid)) {
                    info.put(uid, new String[]{str(v, "displayName"), str(v, "avatarUrl"), str(v, "avatarColor")});
                    addPart(uid, str(v, "displayName"), str(v, "avatarUrl"), str(v, "avatarColor"));
                }
                h.event("beep", "join");
                P p = pcs.get(uid);
                if (!(p != null && p.pc.hasRemote())) offer(uid, false);
                armWd(id);
                changed();
                break;
            }
            case "call.rejected": {
                Call c = cur;
                if (c != null && v.optString("conversationId").equals(c.cid)) {
                    h.event("beep", "leave");
                    h.event("toast", "Call declined");
                }
                break;
            }
            case "call.ended": {
                Call c = cur;
                String cid = v.optString("conversationId"), uid = v.optString("userId");
                if (c == null || !cid.equals(c.cid)) break;
                h.event("beep", "leave");
                P p = pcs.remove(uid);
                if (p != null) try {
                    p.pc.close();
                } catch (Exception e) {
                }
                int before = c.parts.size();
                c.parts.remove(uid);
                if (before <= 1 && !cid.startsWith("r:")) {
                    h.send(jo("type", "call.end", "callId", c.callId, "conversationId", cid));
                    teardown(false);
                }
                changed();
                break;
            }
            case "call.signal":
                signal(v, 0);
                break;
        }
    }

    void signal(JSONObject v, int tries) {
        Call ee = cur;
        String cid = v.optString("conversationId");
        if (ee == null || !cid.equals(ee.cid)) {
            if (tries < 40) h.post(() -> signal(v, tries + 1), 250);
            return;
        }
        String from = v.optString("fromUserId");
        JSONObject data = v.optJSONObject("data");
        if (data == null) return;
        String kind = data.optString("kind");
        P p = pcs.get(from);
        if ("state".equals(kind)) {
            Part pt = ee.parts.get(from);
            if (pt != null) {
                pt.rMuted = data.optBoolean("muted");
                pt.rDeaf = data.optBoolean("deafened");
                pt.rVideo = data.has("video") && !data.isNull("video") ? data.optBoolean("video") : null;
            }
            changed();
            return;
        }
        if ("offer".equals(kind)) {
            String[] d = parseSdp(data.optString("sdp"));
            if (d == null) return;
            if (p == null) p = mkPeer(from);
            final P q = p;
            boolean pol = me.compareTo(from) < 0;
            boolean col = q.mk || !"stable".equals(q.pc.sigState());
            if (col && !pol) {
                q.pend = true;
                return;
            }
            if (col) q.pend = true;
            Runnable apply = () -> q.pc.setRemote("offer", d[1], ok -> {
                if (!ok) return;
                flushIce(from, q);
                q.pc.createAnswer(ans -> {
                    if (ans == null) return;
                    q.pc.setLocal("answer", ans, ok2 -> {
                        Call c2 = cur;
                        if (!ok2 || c2 == null) return;
                        h.send(signal(c2, from, jo("kind", "answer", "sdp", sdpJson("answer", ans))));
                    });
                });
            });
            if ("have-local-offer".equals(q.pc.sigState())) q.pc.setLocal("rollback", "", ok -> apply.run());
            else apply.run();
        } else if ("answer".equals(kind)) {
            if (p == null || !"have-local-offer".equals(p.pc.sigState())) return;
            String[] d = parseSdp(data.optString("sdp"));
            if (d == null) return;
            final P q = p;
            q.pc.setRemote("answer", d[1], ok -> {
                if (ok) flushIce(from, q);
            });
        } else if ("ice".equals(kind)) {
            JSONObject c;
            try {
                c = new JSONObject(data.optString("candidate"));
            } catch (Exception e) {
                return;
            }
            if (p != null && p.pc.hasRemote()) p.pc.addIce(c);
            else {
                List<JSONObject> l = iceQ.get(from);
                if (l == null) iceQ.put(from, l = new ArrayList<>());
                l.add(c);
            }
        }
    }

    void flushIce(String uid, P p) {
        List<JSONObject> l = iceQ.remove(uid);
        if (l != null) for (JSONObject c : l) p.pc.addIce(c);
    }

    static String[] parseSdp(String s) {
        try {
            JSONObject o = new JSONObject(s);
            return new String[]{o.optString("type"), o.optString("sdp")};
        } catch (Exception e) {
            return null;
        }
    }

    public void setMic(boolean on) {
        if (on && deaf) {
            deaf = false;
            deafMuted = false;
        }
        mic = on;
        h.event("mic", on);
        sendState(null);
        changed();
    }

    public void toggleDeafen() {
        boolean nd = !deaf;
        deaf = nd;
        if (nd) {
            if (mic) {
                deafMuted = true;
                mic = false;
                h.event("mic", false);
            }
        } else {
            if (deafMuted && !mic) {
                mic = true;
                h.event("mic", true);
            }
            deafMuted = false;
        }
        h.event("deaf", nd);
        sendState(null);
        changed();
    }

    public int peerCount() {
        return pcs.size();
    }

    public List<String> peers() {
        return new ArrayList<>(pcs.keySet());
    }

    public Peer peer(String uid) {
        P p = pcs.get(uid);
        return p == null ? null : p.pc;
    }

    static JSONObject jo(Object... kv) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
        } catch (Exception e) {
        }
        return o;
    }
}
