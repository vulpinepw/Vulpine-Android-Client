package com.vulpine.at.chat;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;

public final class Forward {
    public static void open(ChatScreen s, List<Chats.Msg> msgs) {
        Context c = s.a;
        FrameLayout host = s.a.host;
        FrameLayout bg = new FrameLayout(c);
        bg.setBackgroundColor(T.dark ? 0x57000000 : 0x24000000);
        bg.setElevation(T.dpf(70));
        bg.setClickable(true);
        Runnable[] close = new Runnable[1];
        close[0] = () -> {
            Ov.remove(bg);
            W.hideKb(bg);
            bg.animate().alpha(0).setDuration(200).withEndAction(() -> host.removeView(bg)).start();
        };
        bg.setOnClickListener(v -> close[0].run());
        host.addView(bg, new FrameLayout.LayoutParams(W.MP, W.MP));
        Ov.push(bg, close[0]);
        bg.setAlpha(0);
        bg.animate().alpha(1).setDuration(240).start();
        int top = Math.max(Math.round(host.getHeight() * .14f), s.a.insetTop + T.dp(28));
        int width = Math.min(T.dp(640), host.getWidth() - T.dp(32));
        LinearLayout bar = W.row(c);
        bar.setBackground(W.round(T.mixA(T.surface, .96f), 18, T.glassStroke, .5f));
        bar.setElevation(T.dpf(16));
        bar.setClickable(true);
        ImageView fi = new ImageView(c);
        fi.setImageDrawable(Svg.stroke(22, T.textSecondary, 2, "M15 14l5-5-5-5", "M4 20v-7a4 4 0 0 1 4-4h12"));
        bar.addView(fi, W.lpm(T.dp(22), T.dp(22), 18, 0, 14, 0));
        EditText in = new EditText(c);
        in.setBackground(null);
        T.text(in, 20, 400, T.text);
        in.setSingleLine(true);
        in.setHint(msgs.size() > 1 ? "Forward " + msgs.size() + " messages to..." : "Forward to a chat or @username");
        in.setHintTextColor(T.textMuted);
        in.setImeOptions(EditorInfo.IME_ACTION_GO);
        bar.addView(in, W.lp(0, W.MP, 1));
        ImageView x = new ImageView(c);
        x.setImageDrawable(Svg.stroke(18, T.textSecondary, 2, "M6 6l12 12", "M18 6L6 18"));
        x.setScaleType(ImageView.ScaleType.CENTER);
        x.setContentDescription("Close");
        x.setOnClickListener(v -> close[0].run());
        bar.addView(x, W.lpm(T.dp(36), T.dp(36), 0, 0, 12, 0));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(width, T.dp(60), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bp.topMargin = top;
        bg.addView(bar, bp);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setBackground(W.round(T.mixA(T.surface, .96f), 18, T.glassStroke, .5f));
        sv.setElevation(T.dpf(16));
        sv.setClickable(true);
        LinearLayout res = W.col(c);
        res.setPadding(T.dp(6), T.dp(6), T.dp(6), T.dp(6));
        sv.addView(res);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(width, W.WC, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rp.topMargin = top + T.dp(70);
        bg.addView(sv, rp);
        sv.setMinimumHeight(T.dp(60));
        int maxH = Math.min(Math.round(host.getHeight() * .56f), host.getHeight() - top - T.dp(90));
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            res.removeAllViews();
            String q = in.getText().toString().trim().toLowerCase().replaceFirst("^@", "");
            int n = 0;
            for (String id : Chats.I.order) {
                Chats.Conv cv = Chats.I.conv(id);
                if (cv == null || cv.o.optBoolean("pendingRequest")) continue;
                String name = cv.title();
                String sub = cv.group() ? cv.o.optInt("memberTotal", 0) + " member" + (cv.o.optInt("memberTotal", 0) == 1 ? "" : "s") : "@" + cv.o.optString("peerHandle");
                if (!q.isEmpty() && !(name.toLowerCase().contains(q) || sub.toLowerCase().contains(q))) continue;
                LinearLayout r = W.row(c);
                r.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
                r.setBackground(W.ripple(W.round(0, 12), 12));
                AvatarView av = new AvatarView(c);
                av.textSize(16);
                av.set(name, cv.avatarColor(), cv.avatarUrl());
                r.addView(av, W.lp(T.dp(40), T.dp(40)));
                LinearLayout tx = W.col(c);
                TextView nm = W.text(c, name, 14, 600, T.text);
                W.ellipsize(nm);
                tx.addView(nm);
                tx.addView(W.text(c, sub, 13, 400, T.textSecondary));
                r.addView(tx, W.lpm(0, W.WC, 12, 0, 0, 0));
                ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
                r.setOnClickListener(v -> {
                    close[0].run();
                    send(s, msgs, cv);
                });
                if (n < 10) {
                    r.setAlpha(0);
                    r.setTranslationY(T.dp(6));
                    r.animate().alpha(1).translationY(0).setStartDelay(n * 18L).setDuration(220).start();
                }
                res.addView(r);
                n++;
            }
            if (n == 0) {
                TextView e = W.text(c, "No chats found", 14, 400, T.textMuted);
                e.setGravity(Gravity.CENTER);
                e.setPadding(0, T.dp(14), 0, T.dp(14));
                res.addView(e, W.lp(W.MP, W.WC));
            }
            res.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sv.getLayoutParams();
            lp.height = Math.min(maxH, res.getMeasuredHeight());
            sv.setLayoutParams(lp);
        };
        in.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int cc, int d) {
            }

            public void onTextChanged(CharSequence a, int b, int cc, int d) {
            }

            public void afterTextChanged(Editable e) {
                render[0].run();
            }
        });
        render[0].run();
        App.bg(() -> {
            Http.Resp r = Http.get("/conversations?limit=100");
            if (r.ok()) App.ui(render[0]);
        });
        bar.setAlpha(0);
        bar.setTranslationY(-T.dp(10));
        bar.setScaleX(.95f);
        bar.setScaleY(.95f);
        bar.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(280).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
        bar.postDelayed(() -> W.showKb(in), 30);
    }

    static String body(Chats.Msg m, ChatScreen s) {
        String t = m.text;
        if (t.startsWith("» Forwarded")) return t;
        if (t.startsWith("»|")) {
            String[] ls = t.split("\n", 3);
            if (ls.length >= 2 && ls[1].startsWith("»|")) t = ls.length > 2 ? ls[2] : "";
        }
        String name, handle;
        if (m.mine()) {
            name = Session.s.optString("displayName");
            handle = Session.s.optString("handle");
        } else {
            org.json.JSONObject sd = m.sender != null ? m.sender : s.conv == null ? null : s.conv.group() ? s.conv.member(m.senderId) : null;
            if (sd == null && s.conv != null && !s.conv.group()) {
                name = s.conv.o.optString("peerDisplayName");
                handle = s.conv.o.optString("peerHandle");
            } else {
                name = sd == null ? "" : sd.optString("displayName");
                handle = sd == null ? "" : sd.optString("handle");
            }
        }
        name = name.replaceAll("[\\r\\n]", " ");
        if (name.length() > 60) name = name.substring(0, 60);
        if (name.isEmpty()) return "» Forwarded\n" + t;
        return "» Forwarded from " + name + (handle.isEmpty() ? "" : " (@" + handle + ")") + "\n" + t;
    }

    static void send(ChatScreen s, List<Chats.Msg> msgs, Chats.Conv target) {
        List<String> bodies = new ArrayList<>();
        List<Chats.Msg> sorted = new ArrayList<>(msgs);
        sorted.sort((x, y) -> Long.compare(x.createdAt, y.createdAt));
        for (Chats.Msg m : sorted) if (m.text != null) bodies.add(body(m, s));
        if (bodies.isEmpty()) {
            Ov.toast("Could not read messages", true);
            return;
        }
        App.bg(() -> {
            if (Chats.I.key(target) == null) {
                App.ui(() -> Ov.toast("Forward failed", true));
                return;
            }
            for (String b : bodies) {
                grant(target.id, b);
                Chats.I.send(target.id, b, null);
                try {
                    Thread.sleep(target.group() ? 550 : 250);
                } catch (InterruptedException e) {
                }
            }
            App.ui(() -> Ov.toast("Forwarded " + bodies.size() + " message" + (bodies.size() == 1 ? "" : "s") + " to " + target.title()));
        });
    }

    static final Pattern UP = Pattern.compile("/uploads/[^\\s|#?)\\]]+");

    public static void grant(String cid, String text) {
        Matcher m = UP.matcher(text);
        JSONArray urls = new JSONArray();
        while (m.find() && urls.length() < 20) urls.put(m.group());
        if (urls.length() == 0) return;
        Http.post("/uploads/grant", App.jo("conversationId", cid, "urls", urls));
    }
}
