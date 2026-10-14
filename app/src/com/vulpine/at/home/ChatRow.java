package com.vulpine.at.home;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Session;
import org.json.JSONArray;
import org.json.JSONObject;

public class ChatRow extends LinearLayout {
    public final AvatarView av;
    final FrameLayout avWrap;
    final TextView title, prev, time, badge, timer, pin, check;
    final LinearLayout titleRow, badges;
    final ImageView callIc, grip, pro;
    public String cid;

    public ChatRow(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(T.dp(12), T.dp(14), T.dp(12), T.dp(12));
        setClipChildren(false);
        setClipToPadding(false);
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(T.mixA(T.text, .09f)));
        sl.addState(new int[]{}, new ColorDrawable(0));
        setBackground(sl);
        avWrap = new FrameLayout(c);
        avWrap.setClipChildren(false);
        av = new AvatarView(c);
        av.textSize(18);
        avWrap.addView(av, new FrameLayout.LayoutParams(T.dp(48), T.dp(48), Gravity.TOP));
        pin = new TextView(c);
        ImageView pinIc = new ImageView(c);
        pinIc.setImageDrawable(Svg.fill(12, T.accent, "M16 9V4l1 0c.55 0 1-.45 1-1s-.45-1-1-1H7c-.55 0-1 .45-1 1s.45 1 1 1l1 0v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3z"));
        FrameLayout.LayoutParams pp = W.flp(T.dp(12), T.dp(12), Gravity.TOP | Gravity.END);
        pp.topMargin = -T.dp(4);
        pp.rightMargin = -T.dp(4);
        pinIc.setTag("pin");
        avWrap.addView(pinIc, pp);
        timer = W.text(c, "", 9, 700, T.accentFg);
        timer.setBackground(W.round(T.accent, 999));
        timer.setPadding(T.dp(3), 0, T.dp(4), 0);
        timer.setGravity(Gravity.CENTER);
        timer.setElevation(T.dpf(2));
        timer.setCompoundDrawablePadding(T.dp(2));
        timer.setCompoundDrawablesWithIntrinsicBounds(Svg.stroke(9, T.accentFg, 2.6f, "M10 2h4", "M12 14l3-3", "M12 22a8 8 0 1 0 0-16 8 8 0 0 0 0 16z"), null, null, null);
        FrameLayout.LayoutParams tp = W.flp(W.WC, T.dp(15), Gravity.BOTTOM | Gravity.END);
        tp.rightMargin = -T.dp(6);
        tp.bottomMargin = -T.dp(4);
        avWrap.addView(timer, tp);
        check = W.text(c, "✓", 11, 700, T.accentFg);
        check.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable cb = W.oval(T.accent);
        cb.setStroke(T.dp(2), T.bg);
        check.setBackground(cb);
        FrameLayout.LayoutParams ckp = W.flp(T.dp(22), T.dp(22), Gravity.TOP | Gravity.END);
        ckp.topMargin = -T.dp(7);
        ckp.rightMargin = -T.dp(7);
        avWrap.addView(check, ckp);
        addView(avWrap, W.lp(T.dp(48), T.dp(48)));
        LinearLayout main = W.col(c);
        titleRow = W.row(c);
        title = W.text(c, "", 14, 500, T.text);
        title.setSingleLine(true);
        title.setEllipsize(null);
        T.lh(title, 21);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, W.WC, 1f) {{
            weight = 0;
            width = W.WC;
        }});
        pro = new ImageView(c);
        pro.setScaleType(ImageView.ScaleType.FIT_CENTER);
        titleRow.addView(pro, W.lpm(T.dp(14), T.dp(14), 12, 0, 0, 0));
        badges = W.row(c);
        titleRow.addView(badges, W.lpm(W.WC, T.dp(14), 12, 0, 0, 0));
        main.addView(titleRow);
        LinearLayout sub = W.row(c);
        callIc = new ImageView(c);
        callIc.setImageDrawable(Svg.fill(12, T.success, "M20.01 15.38c-1.23 0-2.42-.2-3.53-.56-.35-.12-.74-.03-1.01.24l-1.57 1.97c-2.83-1.35-5.48-3.9-6.89-6.83l1.95-1.66c.27-.28.35-.67.24-1.02-.37-1.11-.56-2.3-.56-3.53 0-.54-.45-.99-.99-.99H4.19C3.65 3 3 3.24 3 3.99 3 13.28 10.73 21 20.01 21c.71 0 .99-.63.99-1.18v-3.45c0-.54-.45-.99-.99-.99z"));
        sub.addView(callIc, W.lpm(T.dp(12), T.dp(12), 0, 0, 4, 0));
        prev = W.text(c, "", 13, 400, T.textMuted);
        W.ellipsize(prev);
        T.lh(prev, 19.5f);
        sub.addView(prev, W.lp(0, W.WC, 1));
        main.addView(sub, W.lpm(W.MP, W.WC, 0, 0, 0, 0));
        LinearLayout.LayoutParams mp = W.lp(0, W.WC, 1);
        mp.leftMargin = T.dp(12);
        addView(main, mp);
        LinearLayout meta = W.col(c);
        meta.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        time = W.text(c, "", 11, 400, T.textMuted);
        time.setFontFeatureSettings("tnum");
        int tm = Math.round((time.getPaint().getFontMetricsInt().descent - time.getPaint().getFontMetricsInt().ascent - T.dpf(11)) / 2f);
        meta.addView(time, W.lpm(W.WC, W.WC, 0, 0, 0, 0));
        ((LinearLayout.LayoutParams) time.getLayoutParams()).topMargin = -tm;
        ((LinearLayout.LayoutParams) time.getLayoutParams()).bottomMargin = -tm;
        badge = W.text(c, "", 10, 700, T.accentFg);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(T.dp(18));
        badge.setPadding(T.dp(5), 0, T.dp(5), 0);
        badge.setBackground(W.round(T.accent, 999));
        meta.addView(badge, W.lpm(W.WC, T.dp(18), 0, 4, 0, 0));
        grip = new ImageView(c);
        grip.setImageDrawable(Svg.fill(14, T.textSecondary, "M9 5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zM15 5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zM9 10.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zM15 10.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zM9 16a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zM15 16a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z"));
        grip.setAlpha(.4f);
        meta.addView(grip, W.lpm(T.dp(14), T.dp(14), 0, 4, 0, 0));
        LinearLayout.LayoutParams mtp = W.lp(W.WC, W.MP);
        mtp.leftMargin = T.dp(12);
        addView(meta, mtp);
        pinIc.setVisibility(GONE);
        this.pinIcon = pinIc;
    }

    ImageView pinIcon;
    android.view.View secLock;
    ImageView secTitle;

    public void bindSecret(com.vulpine.at.secret.Sec.Chat s) {
        cid = "secret:" + s.id;
        JSONObject o = s.o;
        String name = s.peerName();
        String ac = o.optString("peerAvatarColor", ""), au = o.optString("peerAvatarUrl", "");
        av.set(name.isEmpty() ? "?" : name, ac.isEmpty() ? null : ac, au.isEmpty() ? null : au);
        avH(!au.isEmpty() && !"null".equals(au));
        if (secLock == null) {
            secLock = com.vulpine.at.secret.Secret.lockBadge(getContext());
            avWrap.addView(secLock, com.vulpine.at.secret.Secret.lockLp());
            secTitle = new ImageView(getContext());
            secTitle.setImageDrawable(new Svg.Icon(0, 0, 24, 24, T.dp(13), 0xff22c55e, 2.4f).add("M4.5 10.5h15v10.5h-15z", false).add("M7.5 10.5V7.5a4.5 4.5 0 0 1 9 0v3", false));
            titleRow.addView(secTitle, 0, W.lpm(T.dp(13), T.dp(13), 0, 0, 4, 0));
        }
        secLock.setVisibility(VISIBLE);
        secTitle.setVisibility(VISIBLE);
        title.setText(name);
        title.setTypeface(s.unread > 0 ? T.b : T.m);
        pro.setVisibility(GONE);
        badges.removeAllViews();
        callIc.setVisibility(GONE);
        String sub = com.vulpine.at.secret.Secret.sub(s);
        if (sub == null) {
            prev.setText("typing...");
            prev.setTextColor(0xff22c55e);
        } else {
            prev.setText(sub);
            prev.setTextColor(T.textMuted);
        }
        time.setText(s.lastAt > 0 ? Fmt.listTime(s.lastAt) : "");
        time.setVisibility(s.lastAt > 0 ? VISIBLE : GONE);
        badge.setVisibility(s.unread > 0 ? VISIBLE : GONE);
        badge.setText(s.unread > 9 ? "9+" : String.valueOf(s.unread));
        badge.setBackground(W.round(0xff22c55e, 999));
        badge.setTextColor(0xffffffff);
        grip.setVisibility(GONE);
        pinIcon.setVisibility(GONE);
        timer.setVisibility(GONE);
        check.setVisibility(GONE);
        setAlpha(1f);
        setEnabled(true);
    }

    void avH(boolean img) {
        int h = T.dp(img ? 53 : 48);
        android.view.ViewGroup.LayoutParams p = avWrap.getLayoutParams();
        if (p.height != h) {
            p.height = h;
            avWrap.setLayoutParams(p);
        }
    }

    public static String shortTimer(long ms) {
        if (ms >= 30L * 86400000L) return "1mt";
        if (ms >= 7L * 86400000L) return "1w";
        if (ms >= 86400000L) return "1d";
        if (ms >= 3600000L) return "1h";
        if (ms >= 600000L) return "10m";
        return "";
    }

    public void bind(Chats.Conv c, boolean pinned, boolean selected, boolean selecting, String previewText, String senderPrefix, boolean inCall, String listen) {
        cid = c.id;
        if (secLock != null) {
            secLock.setVisibility(GONE);
            secTitle.setVisibility(GONE);
            badge.setBackground(W.round(T.accent, 999));
            badge.setTextColor(T.accentFg);
        }
        JSONObject o = c.o;
        String name = c.title();
        if (name == null || name.isEmpty()) name = c.group() ? "Group" : o.optString("peerHandle");
        av.set(name, c.avatarColor(), c.avatarUrl());
        String au = c.avatarUrl();
        avH(au != null && !au.isEmpty() && !"null".equals(au));
        title.setText(name);
        int unread = Sync.unread().optInt(c.id);
        title.setTypeface(unread > 0 ? T.b : T.m);
        pro.setVisibility(!c.group() && o.optBoolean("peerPro") ? VISIBLE : GONE);
        if (pro.getVisibility() == VISIBLE && pro.getDrawable() == null) Img.anim(pro, "asset:pro-badge.gif", T.dp(28), true);
        badges.removeAllViews();
        badges.setVisibility(GONE);
        if (!c.group()) {
            String h = o.optString("peerHandle");
            if (Badges.isBot(h) || o.optBoolean("peerIsBot")) {
                ImageView b = new ImageView(getContext());
                b.setImageDrawable(Ic.svg(Badges.BOT_SVG, 14, T.text));
                badges.addView(b, W.lp(T.dp(14), T.dp(14)));
            }
            for (String url : Badges.forUser(o.optString("peerId"), h)) {
                ImageView b = new ImageView(getContext());
                b.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Img.anim(b, url, T.dp(28), true);
                badges.addView(b, W.lpm(T.dp(14), T.dp(14), badges.getChildCount() > 0 ? 2 : 0, 0, 0, 0));
            }
        }
        badges.setVisibility(badges.getChildCount() > 0 ? VISIBLE : GONE);
        callIc.setVisibility(inCall ? VISIBLE : GONE);
        prev.setTextColor(T.textMuted);
        if (listen != null && unread == 0) {
            prev.setText(listen);
        } else if (previewText != null) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            if (senderPrefix != null) {
                sb.append(senderPrefix);
                sb.setSpan(new ForegroundColorSpan(T.text), 0, senderPrefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            sb.append(previewText);
            prev.setText(sb);
        } else if (o.optBoolean("pendingRequest")) prev.setText("Awaiting approval");
        else if (c.group()) {
            int n = o.optInt("memberTotal", o.optJSONArray("members") == null ? 0 : o.optJSONArray("members").length());
            prev.setText("Group · " + n + " member" + (n == 1 ? "" : "s"));
        } else prev.setText("@" + o.optString("peerHandle"));
        long at = o.optLong("lastMessageAt", 0);
        time.setText(Fmt.listTime(at));
        time.setVisibility(at > 0 ? VISIBLE : GONE);
        badge.setVisibility(unread > 0 ? VISIBLE : GONE);
        badge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        grip.setVisibility(pinned && unread == 0 && !selecting ? VISIBLE : GONE);
        pinIcon.setVisibility(pinned ? VISIBLE : GONE);
        long ms = o.optLong("autoDeleteMs", 0);
        String st = shortTimer(ms);
        timer.setVisibility(ms > 0 && !st.isEmpty() ? VISIBLE : GONE);
        timer.setText(st);
        check.setVisibility(selected ? VISIBLE : GONE);
        boolean pend = o.optBoolean("pendingRequest");
        setAlpha(pend ? .45f : 1f);
        setEnabled(!pend);
    }
}
