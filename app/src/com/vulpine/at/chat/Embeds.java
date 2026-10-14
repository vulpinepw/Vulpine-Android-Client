package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import org.json.JSONObject;

public final class Embeds {
    static View view(ChatScreen s, String kind, String token, String url) {
        Context c = s.a;
        LinearLayout card = W.col(c);
        card.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        card.setBackground(W.round(T.dark ? 0x0fffffff : 0x0f000000, 12));
        LinearLayout.LayoutParams lp = W.lpm(T.dp(300), W.WC, 0, 4, 0, 4);
        card.setLayoutParams(lp);
        card.setVisibility(View.GONE);
        App.bg(() -> {
            Http.Resp r = Http.get("join".equals(kind) ? "/groups/lookup/" + token : "/call-rooms/link/" + token);
            App.ui(() -> {
                if ("join".equals(kind)) {
                    if (!r.ok()) return;
                    JSONObject g = r.obj().optJSONObject("group") != null ? r.obj().optJSONObject("group") : r.obj();
                    fillJoin(s, card, g, token);
                } else fillCall(s, card, r, token);
                card.setVisibility(View.VISIBLE);
            });
        });
        return card;
    }

    static LinearLayout head(Context c, JSONObject g, String sub) {
        LinearLayout r = W.row(c);
        AvatarView av = new AvatarView(c);
        av.textSize(13);
        av.set(g.optString("name"), g.has("avatarColor") ? g.optString("avatarColor") : null, g.optString("avatarUrl", null));
        r.addView(av, W.lp(T.dp(36), T.dp(36)));
        LinearLayout tx = W.col(c);
        TextView n = W.text(c, g.optString("name"), 14, 600, T.text);
        W.ellipsize(n);
        tx.addView(n);
        tx.addView(W.text(c, sub, 13, 400, T.textMuted));
        r.addView(tx, W.lpm(0, W.WC, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
        return r;
    }

    static void fillJoin(ChatScreen s, LinearLayout card, JSONObject g, String token) {
        Context c = s.a;
        card.removeAllViews();
        card.addView(head(c, g, g.optInt("memberCount", g.optInt("memberTotal")) + " members"));
        String d = g.optString("description", "");
        if (!d.isEmpty()) {
            TextView t = W.text(c, d, 13, 400, T.textSecondary);
            t.setLineSpacing(0, 1.4f);
            card.addView(t, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        }
        boolean member = g.optBoolean("isMember") || Chats.I.conv(g.optString("id")) != null;
        TextView b = W.btn(c, member ? "Member" : "Join group", !member, v -> com.vulpine.at.groups.Join.openToken(s.a, token));
        b.setTextSize(13);
        if (member) W.enable(b, false);
        card.addView(b, W.lpm(W.MP, T.dp(32), 0, 8, 0, 0));
    }

    static void fillCall(ChatScreen s, LinearLayout card, Http.Resp r, String token) {
        Context c = s.a;
        card.removeAllViews();
        if (!r.ok()) {
            card.addView(W.text(c, "Call room", 14, 600, T.text));
            card.addView(W.text(c, "This call room has ended", 13, 400, T.textMuted));
            return;
        }
        JSONObject g = r.obj().optJSONObject("group"), room = r.obj().optJSONObject("room");
        if (g == null) g = new JSONObject();
        card.addView(head(c, g, "Call room invite"));
        int n = room == null || room.optJSONArray("members") == null ? 0 : room.optJSONArray("members").length();
        LinearLayout rr = W.row(c);
        rr.setPadding(T.dp(10), T.dp(8), T.dp(10), T.dp(8));
        rr.setBackground(W.round(T.dark ? 0x0fffffff : 0x0f000000, 8));
        View dot = new View(c);
        dot.setBackground(W.oval(n > 0 ? T.success : T.mixA(T.text, .25f)));
        rr.addView(dot, W.lp(T.dp(8), T.dp(8)));
        LinearLayout tx = W.col(c);
        tx.addView(W.text(c, room == null ? "Room" : room.optString("name", "Room"), 14, 600, T.text));
        tx.addView(W.text(c, n > 0 ? n + " in call" : "Empty", 13, 400, T.textMuted));
        rr.addView(tx, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        card.addView(rr, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        String gid = g.optString("id");
        TextView b = W.btn(c, "Join call", true, v -> com.vulpine.at.call.Calls.openRooms(s.a, gid));
        b.setTextSize(13);
        card.addView(b, W.lpm(W.MP, T.dp(32), 0, 8, 0, 0));
    }
}
