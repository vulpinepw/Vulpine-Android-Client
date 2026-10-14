package com.vulpine.at.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public final class Selection {
    static TextView count;
    static ImageView del;

    static ImageView act(Context c, android.graphics.drawable.Drawable d, String label, View.OnClickListener on) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(d);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription(label);
        b.setBackground(W.ripple(W.oval(0), 99));
        b.setOnClickListener(on);
        return b;
    }

    static void enter(ChatScreen s) {
        Context c = s.a;
        s.header.setVisibility(View.GONE);
        s.composer.view().setVisibility(View.GONE);
        s.pinBar.setAlpha(0);
        LinearLayout top = W.row(c);
        top.setPadding(T.dp(20), Math.max(s.a.insetTop, T.dp(24)) + T.dp(8), T.dp(20), T.dp(10));
        ImageView x = act(c, Svg.stroke(18, T.text, 2.2f, "M6 6l12 12", "M18 6L6 18"), "Close selection", v -> s.exitSelect());
        x.setBackground(W.ripple(W.oval(T.mixA(T.text, .09f)), 99));
        top.addView(x, W.lp(T.dp(42), T.dp(42)));
        count = W.text(c, "1", 15, 700, T.text);
        count.setPadding(T.dp(20), T.dp(10), T.dp(20), T.dp(10));
        count.setBackground(W.round(T.mixA(T.text, .09f), 999));
        top.addView(count, W.lpm(W.WC, W.WC, 10, 0, 0, 0));
        top.addView(W.flex(c));
        LinearLayout grp = W.row(c);
        grp.setPadding(T.dp(2), T.dp(2), T.dp(2), T.dp(2));
        grp.setBackground(W.round(T.mixA(T.text, .09f), 999));
        grp.addView(act(c, Svg.stroke(22, T.text, 2, "M9 9h11v11H9z", "M5 15V4h11"), "Copy", v -> copy(s)), W.lp(T.dp(40), T.dp(40)));
        grp.addView(act(c, Svg.stroke(22, T.text, 2, "M22 2 11 13", "M22 2 15 22l-4-9-9-4 20-7z"), "Forward", v -> {
            List<Chats.Msg> sel = s.selectedMsgs();
            if (!sel.isEmpty()) Forward.open(s, sel);
        }), W.lp(T.dp(40), T.dp(40)));
        del = act(c, Svg.stroke(22, T.text, 2, "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13"), "Delete", v -> delete(s));
        grp.addView(del, W.lp(T.dp(40), T.dp(40)));
        top.addView(grp);
        s.selTop = top;
        s.root.addView(top, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.TOP));
        LinearLayout bot = W.row(c);
        bot.setGravity(Gravity.CENTER);
        bot.setPadding(T.dp(20), T.dp(16), T.dp(20), T.dp(20) + s.a.insetBottom);
        bot.addView(lab(c, Svg.stroke(19, T.text, 2, "M17.94 17.94A10 10 0 0 1 12 20c-7 0-11-8-11-8a18 18 0 0 1 5.06-5.94", "M9.9 4.24A9 9 0 0 1 12 4c7 0 11 8 11 8a18 18 0 0 1-2.16 3.19", "M1 1l22 22"), "Hide", v -> hide(s)));
        View sp = new View(c);
        bot.addView(sp, W.lp(T.dp(30), 1));
        bot.addView(lab(c, Svg.stroke(19, T.text, 2, "M12 3v12", "M7 10l5 5 5-5", "M5 21h14"), "Save", v -> save(s)));
        s.selBottom = bot;
        s.root.addView(bot, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.BOTTOM));
        update(s);
    }

    static View lab(Context c, android.graphics.drawable.Drawable d, String label, View.OnClickListener on) {
        LinearLayout b = W.row(c);
        b.setPadding(T.dp(18), T.dp(10), T.dp(18), T.dp(10));
        b.setBackground(W.ripple(W.round(T.mixA(T.surface, .94f), 999, T.glassStroke, .5f), 999));
        b.setElevation(T.dpf(4));
        b.addView(W.img(c, d), W.lp(T.dp(19), T.dp(19)));
        b.addView(W.text(c, label, 13, 600, T.text), W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        b.setOnClickListener(on);
        return b;
    }

    static void update(ChatScreen s) {
        if (count != null) count.setText(String.valueOf(s.selected.size()));
        if (del != null) {
            boolean can = false;
            boolean admin = s.conv != null && s.conv.group() && isAdmin(s);
            for (Chats.Msg m : s.selectedMsgs()) if (m.mine() || admin) can = true;
            del.setEnabled(can);
            del.setAlpha(can ? 1f : .35f);
        }
    }

    static boolean isAdmin(ChatScreen s) {
        JSONObject me = s.conv.member(Session.userId());
        if (me == null) return false;
        String r = me.optString("role");
        return me.optBoolean("isCreator") || "creator".equals(r) || "admin".equals(r);
    }

    static void exit(ChatScreen s) {
        if (s.selTop != null) s.root.removeView(s.selTop);
        if (s.selBottom != null) s.root.removeView(s.selBottom);
        s.selTop = s.selBottom = null;
        s.header.setVisibility(View.VISIBLE);
        s.composer.view().setVisibility(View.VISIBLE);
        s.pinBar.setAlpha(1);
    }

    static void copy(ChatScreen s) {
        List<Chats.Msg> sel = s.selectedMsgs();
        sel.sort((x, y) -> Long.compare(x.createdAt, y.createdAt));
        StringBuilder b = new StringBuilder();
        for (Chats.Msg m : sel) {
            if (m.text == null) continue;
            if (b.length() > 0) b.append("\n\n");
            b.append(s.senderName(m).equals("You") ? Session.s.optString("displayName") : s.senderName(m)).append(" • ").append(Fmt.fullDate(m.createdAt)).append(" ").append(Fmt.time(m.createdAt)).append("\n").append(m.text);
        }
        try {
            ((ClipboardManager) s.a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("messages", b.toString()));
            Ov.toast(sel.size() + " message" + (sel.size() == 1 ? "" : "s") + " copied");
        } catch (Exception e) {
            Ov.toast("Copy failed", true);
        }
    }

    static void delete(ChatScreen s) {
        List<Chats.Msg> sel = s.selectedMsgs();
        if (!del.isEnabled()) {
            Ov.toast("No permission to delete", true);
            return;
        }
        Ov.confirm("Delete " + sel.size() + " message" + (sel.size() == 1 ? "" : "s") + "?", null, "Delete", true, () -> App.bg(() -> {
            for (Chats.Msg m : sel) if (m.id != null) Chats.I.delete(s.cid, m.id);
            App.ui(() -> {
                Chats.I.list(s.cid).removeAll(sel);
                Ov.toast("Deleted");
                s.exitSelect();
                s.rebuild(false);
            });
        }));
    }

    static void hide(ChatScreen s) {
        List<String> ids = new ArrayList<>();
        for (Chats.Msg m : s.selectedMsgs()) if (m.id != null) ids.add(m.id);
        MsgMenu.hide(ids);
        Ov.toast(ids.size() + " message" + (ids.size() == 1 ? "" : "s") + " hidden");
        s.exitSelect();
        s.rebuild(false);
    }

    static void save(ChatScreen s) {
        List<Fmt.Att> files = new ArrayList<>();
        for (Chats.Msg m : s.selectedMsgs()) if (m.text != null) files.addAll(Fmt.parse(m.text).atts);
        if (files.isEmpty()) {
            Ov.toast("No media in selection");
            return;
        }
        Ov.toast("Saving " + files.size() + " file" + (files.size() == 1 ? "" : "s"));
        for (Fmt.Att a : files) Download.save(s.a, a.url, Viewer.safeName(a), a.mime);
    }
}
