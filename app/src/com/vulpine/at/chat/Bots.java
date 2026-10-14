package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public final class Bots {
    static final Pattern BTN = Pattern.compile("\\[btn:([^|\\]]{1,64})\\|([^|\\]]{1,300})(?:\\|(#[0-9a-fA-F]{6}))?\\]");
    static final Pattern GOTO = Pattern.compile("\\[\\[goto:([^/\\]]+)/([^\\]]+)\\]\\]");
    static final Pattern JOINREQ = Pattern.compile("\\[\\[joinreq:([^:\\]]+):([^\\]]+)\\]\\]");
    static final Pattern BOTJOIN = Pattern.compile("\\[\\[botjoin:([^|\\]]*)\\|?([^\\]]*)\\]\\]");
    static final Map<String, TextView> pending = new ConcurrentHashMap<>();
    static boolean wired;

    static synchronized void wire() {
        if (wired) return;
        wired = true;
        Rt.I.on(m -> {
            if (!"button.answer".equals(m.optString("type"))) return;
            App.ui(() -> {
                TextView b = pending.remove(m.optString("callbackId"));
                if (b != null) b.setAlpha(1f);
                String text = m.optString("text", ""), url = m.optString("url", "");
                if (m.optBoolean("alert") && !text.isEmpty()) {
                    Ov.Sheet s = new Ov.Sheet(Ov.host().getContext(), false);
                    s.msg(text.length() > 200 ? text.substring(0, 200) : text);
                    s.body.addView(W.btn(s.body.getContext(), "OK", true, v -> s.close()), W.lp(W.MP, T.dp(40)));
                    s.show();
                } else if (!text.isEmpty()) Ov.toast(text);
                if (!url.isEmpty() && ChatScreen.I != null) ChatScreen.I.links.url(url, true);
            });
        });
    }

    static String strip(String t) {
        String s = BTN.matcher(t).replaceAll("");
        s = s.replaceAll("\\[(?:btnlayout|kbd|kbdopt|kbdph):[^\\]]*\\]", "").replace("[btnrow]", "").replace("[kbdremove]", "");
        s = GOTO.matcher(s).replaceAll("");
        s = JOINREQ.matcher(s).replaceAll("");
        s = BOTJOIN.matcher(s).replaceAll("");
        return s.replaceAll("\\n{3,}", "\n\n").trim();
    }

    static TextView button(Context c, String label, Integer color) {
        TextView b = W.text(c, label, 13, 600, color != null ? color : T.text);
        b.setGravity(Gravity.CENTER);
        W.ellipsize(b);
        b.setPadding(T.dp(10), 0, T.dp(10), 0);
        b.setBackground(W.ripple(W.round(T.mixA(T.surface3, .5f), 12, color != null ? color : T.mixA(T.borderStrong, .5f), 1), 12));
        return b;
    }

    static void attachButtons(ChatScreen s, LinearLayout col, Chats.Msg m, String raw) {
        wire();
        Context c = s.a;
        List<List<String[]>> rows = new ArrayList<>();
        List<String[]> cur = new ArrayList<>();
        String layout = "auto";
        Matcher lm = Pattern.compile("\\[btnlayout:(row|column|auto)\\]").matcher(raw);
        if (lm.find()) layout = lm.group(1);
        boolean explicitRows = raw.contains("[btnrow]");
        Matcher mm = Pattern.compile("\\[btn:([^|\\]]{1,64})\\|([^|\\]]{1,300})(?:\\|(#[0-9a-fA-F]{6}))?\\]|\\[btnrow\\]").matcher(raw);
        int n = 0;
        while (mm.find() && n < 48) {
            if (mm.group().equals("[btnrow]")) {
                if (!cur.isEmpty()) rows.add(cur);
                cur = new ArrayList<>();
                continue;
            }
            cur.add(new String[]{mm.group(1), mm.group(2), mm.group(3)});
            n++;
        }
        if (!cur.isEmpty()) rows.add(cur);
        Matcher g = GOTO.matcher(raw);
        if (g.find()) {
            List<String[]> r = new ArrayList<>();
            r.add(new String[]{"Go to message", "goto:" + g.group(1) + "/" + g.group(2), null});
            rows.add(r);
        }
        Matcher jr = JOINREQ.matcher(raw);
        if (jr.find()) {
            List<String[]> r = new ArrayList<>();
            r.add(new String[]{"Accept", "jr:accept:" + jr.group(1) + ":" + jr.group(2), "#22c55e"});
            r.add(new String[]{"Deny", "jr:deny:" + jr.group(1) + ":" + jr.group(2), "#ef4444"});
            rows.add(r);
        }
        Matcher bj = BOTJOIN.matcher(raw);
        if (bj.find()) {
            LinearLayout card = W.col(c);
            card.addView(W.text(c, "Added to this group", 14, 600, T.text));
            card.addView(W.text(c, "all".equals(bj.group(2)) ? "This bot can read all new messages in this group." : "This bot only sees commands, mentions and replies to it.", 13, 400, T.textMuted));
            col.addView(card);
        }
        if (rows.isEmpty()) return;
        LinearLayout box = W.col(c);
        box.setPadding(0, T.dp(8), 0, T.dp(2));
        boolean single = rows.size() == 1 && !explicitRows && !"row".equals(layout);
        if (single) {
            int cols = "column".equals(layout) ? 1 : 2;
            List<String[]> all = rows.get(0);
            for (int i = 0; i < all.size(); i += cols) {
                LinearLayout r = W.row(c);
                for (int k = 0; k < cols; k++) {
                    if (i + k < all.size()) {
                        View b = make(s, m, all.get(i + k));
                        LinearLayout.LayoutParams lp = W.lp(0, T.dp(34), 1);
                        if (k > 0) lp.leftMargin = T.dp(6);
                        r.addView(b, lp);
                    } else {
                        LinearLayout.LayoutParams lp = W.lp(0, T.dp(34), 1);
                        lp.leftMargin = T.dp(6);
                        r.addView(new View(c), lp);
                    }
                }
                box.addView(r, W.lpm(W.MP, W.WC, 0, i > 0 ? 6 : 0, 0, 0));
            }
        } else {
            for (int i = 0; i < rows.size(); i++) {
                LinearLayout r = W.row(c);
                List<String[]> rr = rows.get(i);
                for (int k = 0; k < rr.size(); k++) {
                    LinearLayout.LayoutParams lp = W.lp(0, T.dp(34), 1);
                    if (k > 0) lp.leftMargin = T.dp(6);
                    r.addView(make(s, m, rr.get(k)), lp);
                }
                box.addView(r, W.lpm(W.MP, W.WC, 0, i > 0 ? 6 : 0, 0, 0));
            }
        }
        int sw = c.getResources().getDisplayMetrics().widthPixels;
        col.addView(box, new LinearLayout.LayoutParams(Math.round(sw * .7f), W.WC));
    }

    static View make(ChatScreen s, Chats.Msg m, String[] b) {
        Context c = s.a;
        Integer col = b[2] == null ? null : Css.color(b[2]);
        TextView v = button(c, b[0], col);
        String data = b[1];
        if (data.startsWith("jr:")) {
            if (col != null) v.setBackground(W.ripple(W.round(T.mix(T.surface3, col, data.startsWith("jr:accept") ? .24f : .22f), 12, T.mix(T.surface3, col, .55f), 1), 12));
            v.setTextColor(T.text);
        }
        v.setOnClickListener(x -> {
            if (data.startsWith("url:")) s.links.url(data.substring(4), true);
            else if (data.startsWith("goto:")) {
                String[] q = data.substring(5).split("/", 2);
                ChatScreen.gotoMsg(q[0], q.length > 1 ? q[1] : null);
            } else if (data.startsWith("jr:")) {
                String[] q = data.split(":");
                v.setAlpha(.55f);
                App.bg(() -> {
                    Http.Resp r = Http.post("/conversations/" + q[2] + "/join-requests/" + q[3] + "/" + q[1], null);
                    App.ui(() -> {
                        v.setAlpha(1f);
                        v.setText(r.ok() ? ("accept".equals(q[1]) ? "Member accepted" : "Request denied") : (r.status == 404 ? "Awaiting approval" : "Action failed"));
                    });
                });
            } else {
                v.setAlpha(.55f);
                App.bg(() -> {
                    Http.Resp r = Http.post("/bots/callback", App.jo("conversationId", s.cid, "messageId", m.id, "data", data));
                    App.ui(() -> {
                        if (r.ok()) {
                            String cb = r.obj().optString("callbackId");
                            pending.put(cb, v);
                            App.H.postDelayed(() -> {
                                if (pending.remove(cb) != null) v.setAlpha(1f);
                            }, 10000);
                        } else {
                            v.setAlpha(1f);
                            String e = r.error();
                            Ov.toast("bot_busy".equals(e) ? "This bot is busy, try again in a moment" : "rate_limited".equals(e) ? "Slow down a little" : "Action failed", true);
                        }
                    });
                });
            }
        });
        return v;
    }
}
