package com.vulpine.at.groups;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.home.Home;
import com.vulpine.at.people.U;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import org.json.JSONObject;

public final class Join {
    public interface Done {
        void on(boolean ok, JSONObject res, String err);
    }

    public static void openToken(MainActivity a, String tokenOrSlug) {
        if (a == null || tokenOrSlug == null || tokenOrSlug.isEmpty()) return;
        String tk = tokenOrSlug.replaceAll("[?#].*$", "").replaceAll("/+$", "");
        App.bg(() -> {
            Http.Resp r = Http.get("/groups/lookup/" + Uri(tk));
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast("Could not join", true);
                    return;
                }
                dialog(a, r.obj(), tk);
            });
        });
    }

    static String Uri(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    public static View groupAvatar(Context c, String name, String color, String url, int sizeDp, float textDp, float radiusDp, int textColor) {
        FrameLayout f = new FrameLayout(c);
        Integer col = U.hex(color);
        f.setBackground(W.round(col != null ? col : T.accent, radiusDp));
        f.setClipToOutline(true);
        String n = name == null || name.isEmpty() ? "?" : name;
        TextView t = U.txt(c, n.substring(0, Math.min(2, n.length())).toUpperCase(), textDp, 700, textColor);
        t.setGravity(Gravity.CENTER);
        f.addView(t, new FrameLayout.LayoutParams(W.MP, W.MP));
        if (url != null && !url.isEmpty() && !"null".equals(url)) {
            ImageView iv = new U.RoundImg(c, radiusDp);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP));
            Img.get(url, T.dp(sizeDp * 2), false, (d, b) -> {
                if (b != null) {
                    iv.setImageBitmap(b);
                    t.setVisibility(View.INVISIBLE);
                }
            });
        }
        return f;
    }

    static void dialog(MainActivity a, JSONObject g, String token) {
        Context c = a;
        Ov.Sheet s = new Ov.Sheet(c, true);
        String name = g.optString("name");
        s.title(name);
        LinearLayout row = W.row(c);
        row.addView(groupAvatar(c, name, g.isNull("avatarColor") ? null : g.optString("avatarColor"), g.isNull("avatarUrl") ? null : g.optString("avatarUrl", null), 48, 18, 999, 0xffffffff), W.lp(T.dp(48), T.dp(48)));
        LinearLayout m = W.col(c);
        TextView n = U.txt(c, name, 14, 600, T.text);
        m.addView(n);
        m.addView(U.muted(c, g.optInt("memberCount") + " members", 13));
        row.addView(m, W.lpm(0, W.WC, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) m.getLayoutParams()).weight = 1;
        s.body.addView(row, W.lp(W.MP, W.WC));
        String desc = g.isNull("description") ? "" : g.optString("description", "");
        if (!desc.isEmpty()) s.body.addView(U.txt(c, desc, 14, 400, T.text), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        LinearLayout act = W.row(c);
        act.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        act.addView(U.btn(c, "Cancel", U.GHOST, v -> s.close()));
        boolean member = g.optBoolean("isMember");
        String label = member ? "Open chat" : "request".equals(g.optString("joinMode")) ? "Request to join" : "Join group";
        TextView go = U.btn(c, label, U.PRIMARY, null);
        go.setOnClickListener(v -> {
            if (member) {
                s.close();
                Home.openChat(g.optString("id"));
                return;
            }
            joinToken(token, (ok, res, err) -> {
                if (ok) {
                    s.close();
                    afterJoin(res, "Joined!");
                } else if (err != null) Ov.toast(err, true);
            });
        });
        LinearLayout.LayoutParams gp = W.lp(W.WC, T.dp(36));
        gp.leftMargin = T.dp(8);
        act.addView(go, gp);
        s.body.addView(act, W.lpm(W.MP, W.WC, 0, 28, 0, 0));
        s.show();
    }

    public static void afterJoin(JSONObject res, String msg) {
        boolean requested = res != null && res.optBoolean("requested") && !res.optBoolean("alreadyMember");
        Ov.toast(msg);
        if (requested) App.H.postDelayed(() -> Ov.toast("Join request sent - pending owner acceptance"), 400);
        String cid = res == null ? null : res.optString("conversationId", res.optString("id", null));
        App.bg(() -> {
            Chats.I.loadList(0);
            if (cid != null && !requested) {
                Chats.I.refresh(cid);
                if (!Chats.I.order.contains(cid)) Chats.I.order.add(0, cid);
                App.ui(() -> Home.openChat(cid));
            }
        });
    }

    public static void joinToken(String token, Done d) {
        join("/groups/join/" + Uri(token), d);
    }

    public static void joinId(String id, Done d) {
        join("/groups/" + Uri(id) + "/join", d);
    }

    static void join(String path, Done d) {
        Captcha.ask(tok -> {
            if (tok == null) {
                d.on(false, null, null);
                return;
            }
            App.bg(() -> {
                Http.Resp r = Http.post(path, App.jo("captchaToken", tok));
                App.ui(() -> {
                    if (r.ok()) d.on(true, r.obj(), null);
                    else {
                        String m = U.err(r, "Could not join");
                        if ("captcha_failed".equals(m)) m = "Verification expired or invalid - please try again.";
                        d.on(false, null, m);
                    }
                });
            });
        });
    }
}
