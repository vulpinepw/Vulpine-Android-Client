package com.vulpine.at.chat;

import com.vulpine.at.App;
import com.vulpine.at.ui.Ov;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public final class Commands {
    static boolean handle(Composer cp, String text) {
        ChatScreen s = cp.s;
        if (text.equals("/help") && !hasBotHelp(s)) {
            s.addSystem("💬 Commands - /clear: delete all your messages (Pro) · /mute @user [minutes]: mute a member in a group (needs Mute members perm) · /unmute @user: remove the mute");
            return true;
        }
        if (text.equals("/clear") && !hasBot(s, "clear")) {
            if (Session.s == null || !Session.s.optBoolean("pro")) return false;
            App.bg(() -> {
                Http.Resp r = Http.delete("/messages/" + s.cid + "/all-mine", null);
                App.ui(() -> {
                    if (r.ok()) {
                        Chats.I.list(s.cid).removeIf(Chats.Msg::mine);
                        s.rebuild(false);
                        Ov.toast("Cleared your messages");
                    }
                });
            });
            return true;
        }
        Matcher m = Pattern.compile("^/(mute|unmute)\\s+@([a-z0-9_]{3,20})(?:\\s+(\\d+))?\\s*$").matcher(text);
        if (m.matches() && s.conv != null && s.conv.group() && !hasBot(s, m.group(1))) {
            String h = m.group(2);
            JSONObject mem = null;
            org.json.JSONArray a = s.conv.o.optJSONArray("members");
            if (a != null) for (int i = 0; i < a.length(); i++) if (h.equalsIgnoreCase(a.optJSONObject(i).optString("handle"))) mem = a.optJSONObject(i);
            if (mem == null) {
                Ov.toast("No @" + h + " in this group", true);
                return true;
            }
            String uid = mem.optString("id");
            boolean mute = m.group(1).equals("mute");
            int min = m.group(3) == null ? 10 : Math.max(1, Math.min(1440, Integer.parseInt(m.group(3))));
            App.bg(() -> {
                Http.Resp r = mute ? Http.post("/conversations/" + s.cid + "/mute/" + uid, App.jo("minutes", min)) : Http.delete("/conversations/" + s.cid + "/mute/" + uid, null);
                App.ui(() -> {
                    if (r.ok()) Ov.toast(mute ? "Muted @" + h + " for " + min + "m" : "Unmuted @" + h);
                    else Ov.toast("Could not mute - check your permissions", true);
                });
            });
            return true;
        }
        return false;
    }

    static boolean hasBot(ChatScreen s, String cmd) {
        Object[] c = Mentions.cmdCache.get(s.cid);
        if (c == null) return false;
        org.json.JSONArray a = (org.json.JSONArray) c[1];
        for (int i = 0; i < a.length(); i++) if (cmd.equals(a.optJSONObject(i).optString("command"))) return true;
        return false;
    }

    static boolean hasBotHelp(ChatScreen s) {
        return hasBot(s, "help");
    }
}
