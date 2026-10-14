package com.vulpine.at.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import org.json.JSONArray;

public final class MsgMenu {
    static FrameLayout scrim;
    static LinearLayout card;

    static android.graphics.drawable.Drawable ic(String... d) {
        return Svg.stroke(15, T.text, 1.8f, d);
    }

    public static boolean dismiss() {
        if (scrim == null) return false;
        FrameLayout s = scrim;
        LinearLayout c = card;
        scrim = null;
        card = null;
        Ov.remove(s);
        if (c != null) c.animate().alpha(0).scaleX(.92f).scaleY(.92f).setDuration(140).setInterpolator(T.EASE_IN).start();
        s.animate().alpha(0).setDuration(160).withEndAction(() -> {
            if (s.getParent() != null) ((FrameLayout) s.getParent()).removeView(s);
        }).start();
        return true;
    }

    static void show(ChatScreen s, Chats.Msg m, int x, int y, String gifUrl) {
        dismiss();
        Context c = s.a;
        FrameLayout host = s.a.host;
        scrim = new FrameLayout(c);
        scrim.setBackgroundColor(T.dark ? 0x33000000 : 0x14000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> dismiss());
        scrim.setElevation(T.dpf(40));
        host.addView(scrim, new FrameLayout.LayoutParams(W.MP, W.MP));
        scrim.setAlpha(0);
        scrim.animate().alpha(1).setDuration(200).start();
        Ov.push(scrim, MsgMenu::dismiss);
        LinearLayout wrap = W.col(c);
        LinearLayout qr = W.row(c);
        qr.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        qr.setBackground(W.round(T.surface, 999));
        qr.setElevation(T.dpf(8));
        for (String e : Emoji.REACTIONS) {
            FrameLayout b = new FrameLayout(c);
            b.setPadding(T.dp(5), T.dp(4), T.dp(5), T.dp(4));
            View ev = Anim.emoji(c, e, 22, s);
            b.addView(ev, new FrameLayout.LayoutParams(T.dp(22), T.dp(22)));
            b.setContentDescription("React " + e);
            b.setOnClickListener(v -> {
                dismiss();
                Reactions.toggle(s.cid, m.id, e);
            });
            W.press(b, .9f);
            qr.addView(b);
        }
        LinearLayout.LayoutParams qp = W.lp(W.WC, W.WC);
        qp.bottomMargin = T.dp(6);
        qp.leftMargin = -T.dp(4);
        if (m.id != null) wrap.addView(qr, qp);
        card = W.col(c);
        card.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        card.setBackground(W.round(T.glassBgStrong == 0 ? T.surface : T.glassBgStrong, 14, T.glassStroke, .5f));
        card.setElevation(T.dpf(12));
        card.setMinimumWidth(T.dp(180));
        Fmt.Parsed p = m.text == null ? null : Fmt.parse(m.text);
        boolean hasText = m.text != null;
        if (gifUrl != null) {
            boolean fav = GifFav.has(gifUrl);
            item(card, fav ? "Remove from favourites" : "Add to favourites", null, false, true, () -> {
                if (fav) GifFav.remove(gifUrl);
                else GifFav.add(gifUrl, null);
                Ov.toast(fav ? "Removed from favourites" : "Added to favourites");
            });
        }
        item(card, "Reply", ic("M9 14L4 9l5-5", "M4 9h9a7 7 0 0 1 7 7v3"), false, true, () -> s.composer.reply(m));
        boolean editable = m.mine() && hasText && p != null && !p.forwarded && p.atts.isEmpty() && p.pollId == null && m.id != null;
        if (editable) item(card, "Edit", ic("M4 20h4L19 9l-4-4L4 16v4z"), false, true, () -> s.composer.edit(m));
        item(card, "Copy", ic("M9 9h11v11H9z", "M5 15V4h11"), false, hasText, () -> {
            ((ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("message", m.text));
            Ov.toast("Copied");
        });
        if (m.id != null) item(card, m.pinned ? "Unpin" : "Pin", m.pinned ? ic("M12 17v5", "M9 4h6l-1 7 3 3H7l3-3z", "M4 4l16 16") : ic("M12 17v5", "M9 4h6l-1 7 3 3H7l3-3z"), false, true, () -> {
            if (!Rt.I.authed()) {
                Ov.toast("Not connected - try again", true);
                return;
            }
            boolean on = !m.pinned;
            m.pinned = on;
            Chats.I.pin(s.cid, m.id, on);
            Ov.toast(on ? "Message pinned" : "Message unpinned");
            s.rebuild(false);
        });
        item(card, "Save to notes", ic("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6", "M8 13h8", "M8 17h5"), false, hasText, () -> {
            com.vulpine.at.notes.Notes.saveMessage(m.text);
            Ov.toast("Saved to notes");
        });
        item(card, "Forward", ic("M15 14l5-5-5-5", "M4 20v-7a4 4 0 0 1 4-4h12"), false, hasText && m.id != null, () -> Forward.open(s, java.util.Collections.singletonList(m)));
        if (p != null && !p.atts.isEmpty() && m.id != null) item(card, "Hide", ic("M17.94 17.94A10 10 0 0 1 12 20c-7 0-11-8-11-8a18 18 0 0 1 5.06-5.94", "M9.9 4.24A9 9 0 0 1 12 4c7 0 11 8 11 8a18 18 0 0 1-2.16 3.19", "M1 1l22 22"), true, true, () -> {
            hide(java.util.Collections.singletonList(m.id));
            Ov.toast("Hidden for you");
            s.rebuild(false);
        });
        if (s.conv != null && s.conv.group() && m.id != null) item(card, "Report", ic("M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z", "M4 22v-7"), true, true, () -> report(s, m));
        if (m.id != null) item(card, "Delete", ic("M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13"), true, true, () -> delete(s, m));
        item(card, "Select", Svg.stroke(16, T.text, 1.8f, "M9 11l3 3L22 4", "M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"), false, true, () -> s.enterSelect(m));
        wrap.addView(card);
        scrim.addView(wrap, new FrameLayout.LayoutParams(W.WC, W.WC));
        wrap.setVisibility(View.INVISIBLE);
        wrap.post(() -> {
            int W0 = host.getWidth(), H0 = host.getHeight();
            int ww = wrap.getWidth(), wh = wrap.getHeight();
            int left = Math.max(T.dp(8), Math.min(x, W0 - ww - T.dp(8)));
            int top = Math.max(s.a.insetTop + T.dp(8), Math.min(y - (m.id != null ? T.dp(46) : 0), H0 - wh - T.dp(16) - Math.max(s.a.insetBottom, s.a.insetIme)));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
            lp.leftMargin = left;
            lp.topMargin = top;
            wrap.setLayoutParams(lp);
            wrap.setPivotX(x - left);
            wrap.setPivotY(y - top);
            wrap.setScaleX(.86f);
            wrap.setScaleY(.86f);
            wrap.setAlpha(0);
            wrap.setVisibility(View.VISIBLE);
            wrap.animate().scaleX(1).scaleY(1).alpha(1).setDuration(200).setInterpolator(T.POP).start();
        });
    }

    static void item(LinearLayout card, String label, android.graphics.drawable.Drawable icon, boolean danger, boolean enabled, Runnable run) {
        Context c = card.getContext();
        LinearLayout r = W.row(c);
        r.setPadding(T.dp(12), 0, T.dp(16), 0);
        r.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 8));
        if (icon != null) {
            if (icon instanceof Svg.Icon) ((Svg.Icon) icon).color(danger ? T.danger : T.text);
            View iv = W.img(c, icon);
            iv.setAlpha(.85f);
            r.addView(iv, W.lp(T.dp(15), T.dp(15)));
        }
        TextView t = W.text(c, label, 14, 400, danger ? T.danger : T.text);
        r.addView(t, W.lpm(W.WC, W.WC, icon != null ? 8 : 0, 0, 0, 0));
        r.setEnabled(enabled);
        r.setAlpha(enabled ? 1 : .5f);
        r.setOnClickListener(v -> {
            dismiss();
            run.run();
        });
        card.addView(r, W.lp(W.MP, T.dp(36)));
    }

    static void hide(java.util.List<String> ids) {
        JSONArray a = Sync.arr("hiddenMsgIds");
        for (String id : ids) if (!Sync.contains(a, id)) a.put(id);
        JSONArray n = new JSONArray();
        for (int i = Math.max(0, a.length() - 500); i < a.length(); i++) n.put(a.optString(i));
        try {
            Session.settings().put("hiddenMsgIds", n);
        } catch (Exception e) {
        }
        Sync.save();
    }

    static void delete(ChatScreen s, Chats.Msg m) {
        Runnable go = () -> {
            java.util.List<Chats.Msg> l = Chats.I.list(s.cid);
            int idx = l.indexOf(m);
            l.remove(m);
            s.rebuild(false);
            App.bg(() -> {
                boolean ok = Chats.I.delete(s.cid, m.id);
                App.ui(() -> {
                    if (ok) Ov.toast("Message deleted");
                    else {
                        if (idx >= 0 && idx <= l.size()) l.add(idx, m);
                        s.rebuild(false);
                        Ov.toast("Could not delete", true);
                    }
                });
            });
        };
        if (Session.settings().optBoolean("messageDeleteConfirm", true)) Ov.confirm("Delete message?", "This message will be permanently deleted for everyone in this chat.", "Delete", true, go::run);
        else go.run();
    }

    static void report(ChatScreen s, Chats.Msg m) {
        Context c = s.a;
        Ov.Sheet sh = new Ov.Sheet(c, false);
        sh.title("Report message");
        sh.msg("Report this message to the group admins and creator. Add a reason (optional).");
        EditText in = new EditText(c);
        in.setHint("Reason (optional)...");
        T.text(in, 14, 400, T.text);
        in.setHintTextColor(T.textMuted);
        in.setMinLines(3);
        in.setGravity(Gravity.TOP);
        in.setBackground(W.round(T.surface2, 10));
        in.setPadding(T.dp(12), T.dp(10), T.dp(12), T.dp(10));
        sh.body.addView(in, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.END);
        r.addView(W.btn(c, "Cancel", false, v -> sh.close()));
        TextView[] go = new TextView[1];
        go[0] = W.btn(c, "Report", true, v -> {
            go[0].setText("Reporting…");
            W.enable(go[0], false);
            App.bg(() -> {
                Http.Resp res = Http.post("/messages/" + s.cid + "/" + m.id + "/report", App.jo("reason", in.getText().toString().trim()));
                App.ui(() -> {
                    sh.close();
                    if (res.ok()) Ov.toast("Message reported to the admins");
                    else if (res.status == 409) Ov.toast("You already reported this message");
                    else Ov.toast("Could not report", true);
                });
            });
        });
        go[0].setBackground(W.ripple(W.round(T.danger, 999), 999));
        go[0].setTextColor(0xffffffff);
        r.addView(go[0], W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        sh.body.addView(r);
        sh.show();
        W.showKb(in);
    }
}
