package com.vulpine.at.home;

import android.content.Intent;
import android.net.Uri;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.groups.Join;
import com.vulpine.at.people.Profile;
import com.vulpine.at.ui.Ov;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public final class Links2 {
    static final Pattern WEB = Pattern.compile("(?i)^(?:https?://)?(?:www\\.)?vulpine\\.(?:pw|at)(/[^?#]*)?(?:\\?([^#]*))?");
    static final Pattern APP = Pattern.compile("(?i)^vulpine://([^?#]*)(?:\\?([^#]*))?");

    public static void open(String url) {
        if (url == null) return;
        String u = url.trim();
        MainActivity a = MainActivity.cur;
        if (a == null || u.isEmpty()) return;
        String path = null, query = null;
        Matcher m = WEB.matcher(u);
        if (m.find()) {
            path = m.group(1) == null ? "/" : m.group(1);
            query = m.group(2);
        } else {
            Matcher v = APP.matcher(u);
            if (v.find()) {
                path = "/" + v.group(1);
                query = v.group(2);
            }
        }
        if (path == null) {
            external(a, u);
            return;
        }
        String[] seg = path.replaceAll("^/+", "").replaceAll("/+$", "").split("/");
        String head = seg.length > 0 ? seg[0].toLowerCase() : "";
        String arg = seg.length > 1 ? Uri.decode(seg[1]) : null;
        if (head.equals("app") && seg.length > 1) {
            head = seg[1].toLowerCase();
            arg = seg.length > 2 ? Uri.decode(seg[2]) : null;
        }
        String cParam = param(query, "c");
        if (cParam != null && !cParam.isEmpty()) {
            Home.openChat(cParam);
            return;
        }
        switch (head) {
            case "join":
                if (arg != null && !arg.isEmpty()) Join.openToken(a, arg);
                return;
            case "call":
                if (arg != null && !arg.isEmpty()) call(a, arg);
                return;
            case "u":
            case "user":
            case "profile":
                if (arg != null && !arg.isEmpty()) Profile.open(a, arg);
                return;
            case "c":
            case "chat":
                if (arg != null && !arg.isEmpty()) Home.openChat(arg);
                return;
            case "":
            case "app":
                return;
        }
        if (u.matches("(?i)^vulpine://.*")) return;
        external(a, u.matches("(?i)^https?://.*") ? u : "https://" + u);
    }

    static String param(String q, String k) {
        if (q == null) return null;
        for (String p : q.split("&")) {
            int i = p.indexOf('=');
            if (i > 0 && p.substring(0, i).equals(k)) return Uri.decode(p.substring(i + 1));
        }
        return null;
    }

    static void call(MainActivity a, String token) {
        App.bg(() -> {
            Http.Resp r = Http.get("/call-rooms/link/" + Uri.encode(token));
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast("This call room has ended", true);
                    return;
                }
                JSONObject g = r.obj().optJSONObject("group");
                if (g == null) {
                    Ov.toast("This call room has ended", true);
                    return;
                }
                String gid = g.optString("id");
                if (g.optBoolean("isMember")) {
                    App.bg(() -> {
                        if (Chats.I.conv(gid) == null) Chats.I.refresh(gid);
                        App.ui(() -> {
                            Home.openChat(gid);
                            App.H.postDelayed(() -> com.vulpine.at.call.Calls.openRooms(a, gid), 450);
                        });
                    });
                    return;
                }
                String jt = g.isNull("joinToken") ? null : g.optString("joinToken", null);
                if (jt != null && !jt.isEmpty()) Join.openToken(a, jt);
                else Ov.toast("Group is invite-only - the link only works after opening joining.", true);
            });
        });
    }

    static void external(MainActivity a, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            Ov.toast("No app can open this link", true);
        }
    }
}
