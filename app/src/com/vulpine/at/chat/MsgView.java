package com.vulpine.at.chat;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.home.Badges;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Session;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MsgView {
    public static final class Item {
        public int type;
        public Chats.Msg m;
        public long at;
        public String sys;
        public boolean first, last, prevSame, nextSame;
        public String key;
    }

    static class Spacer extends ReplacementSpan {
        int w;

        Spacer(int w) {
            this.w = w;
        }

        public int getSize(Paint p, CharSequence t, int s, int e, Paint.FontMetricsInt fm) {
            return w;
        }

        public void draw(android.graphics.Canvas c, CharSequence t, int s, int e, float x, int top, int y, int bottom, Paint p) {
        }
    }

    public static View date(Context c, long at) {
        LinearLayout r = W.row(c);
        r.setPadding(0, T.dp(12), 0, T.dp(4));
        int lc = ChatScreen.wpLine();
        View l1 = new View(c), l2 = new View(c);
        l1.setBackgroundColor(lc);
        l2.setBackgroundColor(lc);
        r.addView(l1, W.lp(0, Math.max(1, T.dp(1)), 1));
        TextView t = W.text(c, Fmt.fullDate(at), 13, 400, ChatScreen.wpMuted());
        r.addView(t, W.lpm(W.WC, W.WC, 8, 0, 8, 0));
        r.addView(l2, W.lp(0, Math.max(1, T.dp(1)), 1));
        return r;
    }

    public static View system(Context c, String text) {
        FrameLayout f = new FrameLayout(c);
        TextView t = W.text(c, text, 13, 400, ChatScreen.wpMuted());
        t.setGravity(Gravity.CENTER);
        t.setPadding(T.dp(12), T.dp(3), T.dp(12), T.dp(3));
        t.setBackground(W.round(T.dark ? 0x0fffffff : 0x0f000000, 99));
        f.addView(t, W.flp(W.WC, W.WC, Gravity.CENTER_HORIZONTAL));
        t.setMaxWidth(Math.round(c.getResources().getDisplayMetrics().widthPixels * .8f));
        return f;
    }

    public static int bubbleColor(ChatScreen s, Chats.Msg m) {
        if (!m.mine()) return T.bubbleIn;
        if (s.conv != null && s.conv.group()) return T.bubbleOut;
        return dmBubble()[0];
    }

    public static int[] dmBubble() {
        JSONObject d = Session.settings().optJSONObject("dmBubble");
        float mix = T.dark ? .24f : .16f;
        if (d != null && "color".equals(d.optString("mode"))) {
            Integer c = Css.color(d.optString("color"));
            if (c != null) return new int[]{T.mix(T.bubbleIn, c | 0xff000000, mix)};
        }
        if (d != null && "gradient".equals(d.optString("mode")) && Session.s != null && Session.s.optBoolean("pro")) {
            Integer a = Css.color(d.optString("from")), b = Css.color(d.optString("to"));
            if (a != null && b != null) return new int[]{T.mix(T.bubbleIn, a, mix), T.mix(T.bubbleIn, a, mix), T.mix(T.bubbleIn, b, mix), Math.round((float) d.optDouble("angle", 135))};
        }
        return new int[]{T.mix(T.bubbleIn, T.accent | 0xff000000, mix)};
    }

    public static View message(ChatScreen s, Item it) {
        Context c = s.a;
        Chats.Msg m = it.m;
        boolean mine = m.mine();
        boolean group = s.conv != null && s.conv.group();
        Fmt.Parsed p = m.text == null ? null : Fmt.parse(m.text);
        int jumbo = p != null && p.atts.isEmpty() && p.pollId == null && p.replyAuthor == null && !p.forwarded ? Md.jumboCount(p.body) : 0;
        String sticker = null;
        if (p != null && p.atts.isEmpty() && p.replyAuthor == null && !p.forwarded) {
            java.util.regex.Matcher sm = java.util.regex.Pattern.compile("^:([a-z0-9_]{2,32}):$").matcher(p.body.trim());
            if (sm.matches()) sticker = Emoji.custom(sm.group(1));
        }
        boolean media = p != null && !p.atts.isEmpty() && (p.atts.size() == 1 ? p.atts.get(0).media() : hasMedia(p.atts));
        boolean pack = p != null && p.atts.size() == 1 && Media.isSticker(p.atts.get(0)) && p.body.trim().isEmpty() && p.replyAuthor == null && !p.forwarded;
        boolean bare = jumbo > 0 || sticker != null || pack;
        Bubble b = new Bubble(c);
        b.setTag("bubble");
        int sw = c.getResources().getDisplayMetrics().widthPixels - T.dp(32);
        b.maxW = Math.round(sw * .95f);
        if (bare) b.fill(0);
        else {
            JSONObject snd = m.sender;
            JSONObject mem = group && !mine ? s.conv.member(m.senderId) : null;
            String pbg = mem != null && mem.optBoolean("profileBgGroups") ? mem.optString("profileBackground", "") : "";
            if (!pbg.isEmpty() && (p == null || p.pollId == null)) {
                Integer one = Css.color(pbg);
                int[] g = Css.gradientColors(pbg);
                if (one != null) b.fill(one);
                else if (g != null && g.length >= 2) b.gradient(g, Css.gradientAngle(pbg));
                else b.fill(T.bubbleIn);
            } else if (mine && !group) {
                int[] d = dmBubble();
                if (d.length == 4) b.gradient(new int[]{d[1], d[2]}, d[3]);
                else b.fill(d[0]);
            } else b.fill(bubbleColor(s, m));
        }
        float tl = 16, tr = 16, br = 16, bl = 16;
        boolean sibling = !(group && !mine);
        if (mine) {
            if (it.nextSame) br = 6;
            if (it.prevSame && sibling) tr = 6;
            if (!it.nextSame) br = 5;
        } else {
            if (it.nextSame && sibling) bl = 6;
            if (it.prevSame && sibling) tl = 6;
            if (!it.nextSame || !sibling) bl = 5;
        }
        if (bare) tl = tr = br = bl = 0;
        b.corners(tl, tr, br, bl);
        LinearLayout col = new WrapCol(c);
        b.addView(col, new FrameLayout.LayoutParams(W.WC, W.WC));
        int padH = media || bare ? 0 : T.dp(12), padV = media || bare ? 0 : T.dp(8);
        col.setPadding(padH, padV, padH, padV);
        TextView time = timeView(c, m, mine);
        boolean showFwd = p != null && p.forwarded;
        if (showFwd) col.addView(fwdHeader(s, p, media || bare));
        if (p != null && p.replyAuthor != null) col.addView(replyQuote(s, p, media || bare));
        int timeW = measureTime(time);
        if (m.text == null) {
            TextView t = body(c, s, m.decryptFailed ? "🔒 (encrypted)" : "", timeW, null);
            col.addView(t);
        } else if (p.pollId != null) {
            col.addView(com.vulpine.at.polls.Polls.view(c, p.pollId, s.cid), new LinearLayout.LayoutParams(W.WC, W.WC));
            col.addView(new View(c), W.lp(1, T.dp(10)));
        } else if (sticker != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Img.anim(iv, sticker, T.dp(192), true);
            col.addView(iv, W.lp(T.dp(96), T.dp(96)));
            col.addView(new View(c), W.lp(1, T.dp(16)));
        } else if (jumbo > 0) {
            col.addView(Jumbo.view(c, p.body.trim(), jumbo, s));
        } else if (!p.atts.isEmpty()) {
            Media.attachments(s, col, m, p, timeW);
        } else {
            String bodyText = p.body;
            if (s.isBotSender(m)) bodyText = Bots.strip(bodyText);
            TextView t = newBody(c);
            Md.Out out = Md.render(bodyText, t, s.links);
            for (String g : out.gifs) Media.inlineGif(s, col, g);
            if (!out.codes.isEmpty()) blocks(s, col, t, out, timeW);
            else if (out.sb.length() > 0 || out.gifs.isEmpty()) {
                finishBody(t, out.sb, timeW);
                col.addView(t);
            }
            if (s.isBotSender(m)) Bots.attachButtons(s, col, m, p.body);
            for (String[] em : out.embeds) col.addView(Embeds.view(s, em[0], em[1], em[2]));
            String lp = LinkPreview.pick(out.urls, bodyText);
            if (lp != null && out.embeds.isEmpty()) col.addView(WrapCol.stretch(LinkPreview.card(s, lp)));
        }
        List<Object[]> rx = m.id == null ? null : s.reactions.get(m.id);
        if (rx != null && !rx.isEmpty()) col.addView(Reactions.row(s, m, rx, media || bare));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.BOTTOM | Gravity.END);
        tp.rightMargin = T.dp(6);
        tp.bottomMargin = T.dp(3);
        b.addView(time, tp);
        if (m.pinned) {
            ImageView pin = new ImageView(c);
            pin.setImageDrawable(Svg.fill(10, T.accentFg, "M16 9V4l1 0c.55 0 1-.45 1-1s-.45-1-1-1H7c-.55 0-1 .45-1 1s.45 1 1 1l1 0v5c0 1.66-1.34 3-3 3v2h5.97v7l1 1 1-1v-7H19v-2c-1.66 0-3-1.34-3-3z"));
            pin.setScaleType(ImageView.ScaleType.CENTER);
            pin.setBackground(W.oval(T.accent));
            pin.setElevation(T.dpf(2));
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(T.dp(18), T.dp(18), Gravity.TOP | (mine ? Gravity.END : Gravity.START));
            pp.topMargin = -T.dp(6);
            if (mine) pp.rightMargin = -T.dp(6);
            else pp.leftMargin = -T.dp(6);
            pin.setContentDescription("Pinned");
            b.addView(pin, pp);
            b.setClipChildren(false);
        }
        b.select(s.selected.contains(m.id != null ? m.id : m.tempId));
        if (b.selected) {
            TextView ck = W.text(c, "✓", 11, 700, T.accentFg);
            ck.setGravity(Gravity.CENTER);
            ck.setBackground(W.oval(T.accent));
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(T.dp(18), T.dp(18), Gravity.TOP | Gravity.END);
            cp.topMargin = T.dp(4);
            cp.rightMargin = T.dp(4);
            b.addView(ck, cp);
            tintSelected(col);
        }
        LinearLayout row = W.row(c);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setTag(m.id != null ? m.id : m.tempId);
        if (group && !mine) {
            LinearLayout outer = W.row(c);
            outer.setGravity(it.nextSame ? Gravity.TOP : Gravity.BOTTOM);
            JSONObject mem = s.conv.member(m.senderId);
            JSONObject sd = m.sender != null ? m.sender : mem;
            String name = sd == null ? "Member" : sd.optString("displayName", sd.optString("handle", "Member"));
            String handle = sd == null ? "" : sd.optString("handle");
            if (!it.nextSame) {
                FrameLayout aw = new FrameLayout(c);
                aw.setClipChildren(false);
                AvatarView av = new AvatarView(c);
                av.textSize(13);
                aw.addView(av, new FrameLayout.LayoutParams(T.dp(32), T.dp(32)));
                av.set(name, sd == null || sd.isNull("avatarColor") || sd.optString("avatarColor").isEmpty() ? null : sd.optString("avatarColor"), sd == null ? null : sd.optString("avatarUrl", null));
                com.vulpine.at.deco.Deco.attach(aw, m.senderId, T.dp(32), false);
                aw.setOnClickListener(v -> s.openProfile(handle));
                outer.addView(aw, W.lp(T.dp(32), T.dp(32)));
            } else outer.addView(new View(c), W.lp(T.dp(32), 1));
            LinearLayout cc = W.col(c);
            if (!it.prevSame) cc.addView(nameLine(s, mem, sd, name, handle, m.senderId), W.lpm(W.WC, W.WC, 0, 0, 0, 2));
            cc.addView(b, W.lp(W.WC, W.WC));
            outer.addView(cc, W.lpm(0, W.WC, 8, 0, 0, 0));
            ((LinearLayout.LayoutParams) cc.getLayoutParams()).weight = 1;
            b.maxW = Math.round((sw * .8f - T.dp(40)) * .95f);
            row.addView(outer, new LinearLayout.LayoutParams(Math.round(sw * .8f), W.WC));
        } else row.addView(b, W.lp(W.WC, W.WC));
        return row;
    }

    static CharSequence trimNl(CharSequence cs) {
        int a = 0, e = cs.length();
        while (a < e && cs.charAt(a) == '\n') a++;
        while (e > a && cs.charAt(e - 1) == '\n') e--;
        return cs.subSequence(a, e);
    }

    static void blocks(ChatScreen s, LinearLayout col, TextView first, Md.Out out, int timeW) {
        Context c = s.a;
        int pos = 0;
        TextView cur = first;
        for (Object[] cb : out.codes) {
            int st = (Integer) cb[0], en = (Integer) cb[1];
            CharSequence before = trimNl(out.sb.subSequence(pos, st));
            if (before.length() > 0) {
                TextView t = cur != null ? cur : newBody(c);
                cur = null;
                t.setText(before);
                col.addView(t);
            }
            col.addView(WrapCol.stretch(codeBlock(c, (String) cb[2], (String) cb[3])), W.lpm(W.WC, W.WC, 0, 4, 0, 4));
            pos = en;
        }
        CharSequence after = trimNl(out.sb.subSequence(pos, out.sb.length()));
        TextView t = cur != null ? cur : newBody(c);
        finishBody(t, new SpannableStringBuilder(after), timeW);
        col.addView(t);
    }

    static View codeBlock(Context c, String code, String lang) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(W.round(T.dark ? 0x0fffffff : 0x0d000000, 8));
        boolean head = !lang.isEmpty() || code.length() > 50;
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        TextView t = new TextView(c);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextColor(T.text);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14 * msgScale() * .86f);
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setLineHeight(Math.round(T.dpf(14 * msgScale() * .86f * 1.5f)));
        t.setHorizontallyScrolling(true);
        t.setText(code);
        t.setPadding(T.dp(12), head ? T.dp(30) : T.dp(8), T.dp(12), T.dp(8));
        hs.addView(t);
        f.addView(hs, new FrameLayout.LayoutParams(W.MP, W.WC));
        if (!lang.isEmpty()) {
            TextView l = W.text(c, lang.toLowerCase(java.util.Locale.ROOT), 11, 600, T.textMuted);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.START);
            lp.leftMargin = T.dp(12);
            lp.topMargin = T.dp(7);
            f.addView(l, lp);
        }
        if (code.length() > 50) {
            TextView cp = W.text(c, "Copy", 11, 600, T.text);
            cp.setGravity(Gravity.CENTER);
            cp.setPadding(T.dp(8), 0, T.dp(8), 0);
            cp.setBackground(W.round(T.dark ? 0x1fffffff : 0x14000000, 6));
            cp.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("code", code));
                cp.setText("Copied");
                cp.postDelayed(() -> cp.setText("Copy"), 1200);
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W.WC, T.dp(24), Gravity.TOP | Gravity.END);
            lp.topMargin = T.dp(4);
            lp.rightMargin = T.dp(4);
            f.addView(cp, lp);
        }
        return f;
    }

    static View fallback(ChatScreen s, Item it) {
        Context c = s.a;
        Chats.Msg m = it.m;
        Bubble b = new Bubble(c);
        b.setTag("bubble");
        b.fill(bubbleColor(s, m));
        b.corners(16, 16, 16, 16);
        TextView t = newBody(c);
        String txt = m.text == null ? "" : m.text;
        try {
            txt = Fmt.parse(txt).body;
        } catch (Throwable e) {
        }
        t.setText(txt);
        t.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
        b.addView(t, new FrameLayout.LayoutParams(W.WC, W.WC));
        LinearLayout row = W.row(c);
        row.setGravity(m.mine() ? Gravity.END : Gravity.START);
        row.setTag(m.id != null ? m.id : m.tempId);
        row.addView(b, W.lp(W.WC, W.WC));
        return row;
    }

    static void tintSelected(View v) {
        if (v instanceof TextView) ((TextView) v).setTextColor(T.accentFg);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) tintSelected(((ViewGroup) v).getChildAt(i));
    }

    static boolean hasMedia(List<Fmt.Att> a) {
        for (Fmt.Att x : a) if (x.media()) return true;
        return false;
    }

    static View nameLine(ChatScreen s, JSONObject mem, JSONObject sd, String name, String handle, String uid) {
        Context c = s.a;
        LinearLayout r = W.row(c);
        String role = mem == null ? "member" : mem.optString("role", "member");
        boolean creator = mem != null && mem.optBoolean("isCreator") || "creator".equals(role);
        int color = T.accent;
        String roleName = "Member";
        JSONArray roles = s.conv.o.optJSONArray("roles");
        if (creator) {
            color = 0xfff59e0b;
            roleName = "Creator";
        } else if (roles != null) {
            for (int i = 0; i < roles.length(); i++) {
                JSONObject ro = roles.optJSONObject(i);
                if (ro != null && role.equals(ro.optString("id"))) {
                    Integer cc = Css.color(ro.optString("color"));
                    if (cc != null) color = cc;
                    roleName = ro.optString("name", role);
                }
            }
            if ("admin".equals(role) && roleName.equals("Member")) roleName = "Admin";
        }
        TextView n = W.text(c, name, 13, 600, color);
        W.ellipsize(n);
        n.setOnClickListener(v -> s.openProfile(handle));
        r.addView(n);
        for (String u : Badges.forUser(uid, handle)) {
            ImageView bi = new ImageView(c);
            Img.anim(bi, u, T.dp(28), true);
            r.addView(bi, W.lpm(T.dp(14), T.dp(14), 4, 0, 0, 0));
        }
        if (sd != null && sd.optBoolean("pro")) {
            ImageView pb = new ImageView(c);
            Img.anim(pb, "asset:pro-badge.gif", T.dp(24), true);
            r.addView(pb, W.lpm(T.dp(12), T.dp(12), 4, 0, 0, 0));
        }
        int boosts = s.boostCount(uid);
        if (boosts > 0) {
            TextView chip = W.text(c, boosts > 1 ? String.valueOf(boosts) : "", 10, 600, 0xffffffff);
            chip.setCompoundDrawablesWithIntrinsicBounds(Svg.stroke(11, 0xffffffff, 2, "M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z", "M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z"), null, null, null);
            chip.setCompoundDrawablePadding(T.dp(2));
            chip.setPadding(T.dp(4), T.dp(1), T.dp(6), T.dp(1));
            T.lh(chip, 16);
            chip.setContentDescription(boosts + " boost" + (boosts == 1 ? "" : "s"));
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xffa78bfa, 0xff7c3aed});
            g.setCornerRadius(T.dpf(99));
            chip.setBackground(g);
            r.addView(chip, W.lpm(W.WC, W.WC, 4, 0, 0, 0));
        }
        TextView tag = W.text(c, roleName, 10, 400, T.textSecondary);
        tag.setPadding(T.dp(6), T.dp(1), T.dp(6), T.dp(1));
        tag.setBackground(W.round(T.surface2, 99));
        r.addView(tag, W.lpm(W.WC, W.WC, 4, 0, 0, 0));
        return r;
    }

    static TextView timeView(Context c, Chats.Msg m, boolean mine) {
        TextView t = W.text(c, Fmt.time(m.createdAt > 0 ? m.createdAt : System.currentTimeMillis()) + (m.editedAt > 0 ? " · edited" : ""), 9, 400, T.text);
        t.setAlpha(.6f);
        t.setShadowLayer(T.dpf(3), 0, T.dpf(1), 0x73000000);
        t.setSingleLine(true);
        if (mine) {
            android.graphics.drawable.Drawable st;
            if (m.state == Chats.Msg.FAILED) st = null;
            else if (m.state == Chats.Msg.SENDING || m.id == null) st = Svg.stroke(11, T.text, 1.8f, "M12 5.8a6.2 6.2 0 1 0 0 12.4 6.2 6.2 0 0 0 0-12.4z", "M12 9v3l2 1.5");
            else if (m.readAt != null) st = new Svg.Icon(0, 0, 20, 13, T.dp(13), T.text, 2.5f).add("M1 6.5L5.5 11L13 2", false).add("M6 6.5L10.5 11L18 2", false);
            else st = new Svg.Icon(0, 0, 14, 13, T.dp(11), T.text, 2.5f).add("M1 6.5L5.5 11L13 2", false);
            if (st != null) {
                st.setBounds(0, 0, st.getIntrinsicWidth(), Math.round(st.getIntrinsicHeight() * .82f));
                t.setCompoundDrawables(null, null, st, null);
                t.setCompoundDrawablePadding(T.dp(3));
            }
        }
        return t;
    }

    static int measureTime(TextView t) {
        t.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return t.getMeasuredWidth() + T.dp(10);
    }

    static TextView body(Context c, ChatScreen s, String plain, int timeW, Md.Out out) {
        TextView t = newBody(c);
        finishBody(t, new SpannableStringBuilder(plain == null ? "" : plain), timeW);
        return t;
    }

    public static TextView newBody(Context c) {
        TextView t = new TextView(c);
        T.text(t, 14, 400, T.text);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14 * msgScale());
        lineHeight(t, 21 * msgScale());
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setLinksClickable(true);
        t.setHighlightColor(0);
        t.setFocusable(false);
        t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        return t;
    }

    public static void finishBody(TextView t, SpannableStringBuilder sb, int timeW) {
        int st = sb.length();
        sb.append("\u2060");
        sb.setSpan(new Spacer(timeW), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setText(sb);
    }

    public static void lineHeight(TextView t, float dp) {
        int target = T.dp(dp);
        android.graphics.Paint.FontMetricsInt fm = t.getPaint().getFontMetricsInt();
        int natural = fm.descent - fm.ascent;
        int extra = Math.max(0, target - natural);
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setLineHeight(target);
        else t.setLineSpacing(extra, 1f);
        t.setPadding(t.getPaddingLeft(), extra / 2, t.getPaddingRight(), extra - extra / 2);
    }

    public static float msgScale() {
        double v = Session.settings().optDouble("textScale", 1);
        if (Double.isNaN(v) || v < .8 || v > 1.5) v = 1;
        return (float) v;
    }

    static View fwdHeader(ChatScreen s, Fmt.Parsed p, boolean pad) {
        Context c = s.a;
        LinearLayout r = W.row(c);
        if (pad) r.setPadding(T.dp(12), T.dp(8), T.dp(12), 0);
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Svg.stroke(13, T.accent, 2.4f, "M15 14l5-5-5-5", "M4 20v-7a4 4 0 0 1 4-4h12"));
        r.addView(ic, W.lp(T.dp(13), T.dp(13)));
        TextView a = W.text(c, p.fwdFrom == null ? "Forwarded" : "Forwarded from", 13, 500, T.accent);
        a.setAlpha(.85f);
        r.addView(a, W.lpm(W.WC, W.WC, 5, 0, 0, 0));
        if (p.fwdFrom != null) {
            String nm = p.fwdFrom;
            String handle = null;
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("^(.*) \\(@([a-z0-9_]{3,20})\\)$").matcher(nm);
            if (mm.matches()) {
                nm = mm.group(1);
                handle = mm.group(2);
            }
            TextView n = W.text(c, nm, 13, 700, T.accent);
            W.ellipsize(n);
            if (handle != null) {
                final String h = handle;
                n.setOnClickListener(v -> s.openProfile(h));
            }
            r.addView(n, W.lpm(W.WC, W.WC, 5, 0, 0, 0));
        }
        LinearLayout.LayoutParams lp = W.lp(W.WC, W.WC);
        lp.bottomMargin = T.dp(3);
        r.setLayoutParams(lp);
        return r;
    }

    static View replyQuote(ChatScreen s, Fmt.Parsed p, boolean pad) {
        Context c = s.a;
        LinearLayout q = W.col(c);
        q.setPadding(T.dp(8), 0, 0, 0);
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(0);
        q.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bar}) {
            {
                setLayerInset(0, 0, 0, 0, 0);
            }
        });
        View line = new View(c);
        FrameLayout wrap = new FrameLayout(c);
        line.setBackgroundColor(T.text);
        TextView a = W.text(c, p.replyAuthor, 13, 600, T.text);
        W.ellipsize(a);
        TextView sn = W.text(c, p.replySnippet == null ? "" : p.replySnippet, 13, 400, T.text);
        W.ellipsize(sn);
        q.addView(a);
        q.addView(sn);
        wrap.addView(q, new FrameLayout.LayoutParams(W.WC, W.WC));
        wrap.addView(line, new FrameLayout.LayoutParams(T.dp(3), W.MP));
        wrap.setAlpha(.75f);
        LinearLayout.LayoutParams lp = W.lp(W.WC, W.WC);
        lp.bottomMargin = T.dp(4);
        if (pad) {
            lp.leftMargin = T.dp(12);
            lp.topMargin = T.dp(8);
            lp.rightMargin = T.dp(12);
        }
        wrap.setLayoutParams(lp);
        if (p.replyId != null) wrap.setOnClickListener(v -> s.jumpTo(p.replyId));
        return wrap;
    }
}
