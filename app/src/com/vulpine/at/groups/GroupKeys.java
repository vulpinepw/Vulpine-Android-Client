package com.vulpine.at.groups;

import com.vulpine.core.Http;
import com.vulpine.core.Session;
import com.vulpine.core.VCrypto;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class GroupKeys {
    public static final class Result {
        public Http.Resp resp;
        public String key, cid;
    }

    public static JSONArray envelopes(String myPriv, String keyB64, List<String> handles) throws Exception {
        JSONArray env = new JSONArray();
        for (String h : handles) {
            Http.Resp u = Http.get("/users/" + h);
            if (!u.ok()) continue;
            JSONObject o = u.obj();
            String pub = o.optString("encPubkey", "");
            if (pub.isEmpty() || "_deleted".equals(o.optString("id"))) continue;
            JSONObject e = VCrypto.encrypt(keyB64, VCrypto.dmKey(myPriv, pub));
            env.put(new JSONObject().put("recipientId", o.optString("id")).put("ciphertext", e.getString("ciphertext")).put("nonce", e.getString("nonce")));
        }
        return env;
    }

    public static Result create(String name, List<String> handles) throws Exception {
        VCrypto.Identity me = Session.id;
        if (me == null || me.encPrivkey == null) throw new Exception("identity_unavailable");
        String P = VCrypto.b64(VCrypto.random(32));
        JSONArray env = envelopes(me.encPrivkey, P, handles);
        env.put(new JSONObject().put("recipientId", Session.userId()).put("ciphertext", P).put("nonce", "__plain__"));
        JSONObject body = new JSONObject().put("name", name).put("memberHandles", new JSONArray(handles)).put("keyEnvelopes", env).put("creatorKey", P);
        Result r = new Result();
        r.resp = Http.post("/conversations/group", body);
        r.key = P;
        if (r.resp.ok()) {
            r.cid = r.resp.obj().optString("id");
            Session.kv.put("vulpine.gk." + r.cid, P);
        }
        return r;
    }
}
