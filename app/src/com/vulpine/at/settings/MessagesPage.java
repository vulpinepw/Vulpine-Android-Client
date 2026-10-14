package com.vulpine.at.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import org.json.JSONObject;

public class MessagesPage extends SetPage {
    static final String[] LABELS = {"Off", "10 minutes", "1 hour", "1 day", "1 week", "1 month"};
    static final long[] MS = {0, 600000L, 3600000L, 86400000L, 604800000L, 2592000000L};
    boolean applying;

    public MessagesPage() {
        super("messages", "Messages");
    }

    protected void content(LinearLayout body) {
        Context c = a;
        JSONObject st = Session.settings();
        add(S.switchCard(c, "Ask before deleting messages", "Show a confirmation before deleting a single message.", st.optBoolean("messageDeleteConfirm", true), v -> put("messageDeleteConfirm", v)));
        add(S.switchCard(c, "Show forwarded message author", "When someone forwards your message, show your name as the original sender.", st.optBoolean("showForwardAuthor", true), v -> put("showForwardAuthor", v)));
        add(timerCard(c));
    }

    void put(String k, Object v) {
        try {
            Session.settings().put(k, v);
        } catch (Exception e) {
        }
        Sync.save();
    }

    View timerCard(Context c) {
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Default auto-delete for new chats", 500));
        S.add(card, S.muted(c, "New private chats and groups you create will auto-delete messages after this time. People who start a chat with you get it too, and only you can turn it off."), 12);
        long cur = Session.settings().optLong("autoDeleteDefaultMs", 0);
        LinearLayout seg = new LinearLayout(c) {
            protected void onMeasure(int wm, int hm) {
                int n = getChildCount();
                int avail = MeasureSpec.getSize(wm) - getPaddingLeft() - getPaddingRight() - T.dp(4) * (n - 1);
                int[] mw = new int[n];
                for (int i = 0; i < n; i++) {
                    TextView t = (TextView) ((FrameLayout) getChildAt(i)).getChildAt(0);
                    for (String w : t.getText().toString().split(" ")) mw[i] = Math.max(mw[i], (int) Math.ceil(t.getPaint().measureText(w)));
                }
                boolean[] fixed = new boolean[n];
                int left = avail, free = n;
                boolean ch = true;
                while (ch && free > 0) {
                    ch = false;
                    int base = left / free;
                    for (int i = 0; i < n; i++) if (!fixed[i] && mw[i] > base) {
                        fixed[i] = true;
                        left -= mw[i];
                        free--;
                        ch = true;
                    }
                }
                int base = free > 0 ? left / free : 0;
                for (int i = 0; i < n; i++) {
                    LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) getChildAt(i).getLayoutParams();
                    p.weight = 0;
                    p.width = fixed[i] ? mw[i] : base;
                }
                super.onMeasure(wm, hm);
            }
        };
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setGravity(Gravity.CENTER_VERTICAL);
        seg.setBackground(W.round(T.surface2, 12));
        seg.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        seg.setClipChildren(false);
        seg.setClipToPadding(false);
        FrameLayout[] cells = new FrameLayout[LABELS.length];
        TextView apply = S.btn(c, "Apply to existing chats", S.SECONDARY, 34, 14, null);
        apply.setPadding(T.dp(16), 0, T.dp(16), 0);
        FrameLayout applyWrap = new FrameLayout(c);
        applyWrap.addView(apply, W.flp(W.WC, T.dp(34), Gravity.START | Gravity.TOP));
        applyWrap.setClipChildren(true);
        for (int i = 0; i < LABELS.length; i++) {
            final int k = i;
            FrameLayout cell = new FrameLayout(c);
            cell.setClipChildren(false);
            TextView t = W.text(c, LABELS[i], 14, 500, MS[i] == cur ? T.text : T.textSecondary);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(2);
            t.setLineSpacing(T.dp(21) - (t.getPaint().getFontMetricsInt().descent - t.getPaint().getFontMetricsInt().ascent), 1f);
            cell.addView(t, W.flp(W.MP, W.WC, Gravity.CENTER));
            if (MS[i] == cur) cell.setBackground(W.round(T.surface, 999));
            cell.setOnClickListener(v -> pick(MS[k], cells, apply, applyWrap));
            cells[i] = cell;
            LinearLayout.LayoutParams p = W.lp(0, T.dp(34), 1);
            if (i > 0) p.leftMargin = T.dp(4);
            seg.addView(cell, p);
        }
        S.add(card, seg, 12);
        LinearLayout.LayoutParams ap = S.gap(12);
        ap.height = cur != 0 ? T.dp(34) : 0;
        applyWrap.setAlpha(cur != 0 ? 1f : 0f);
        card.addView(applyWrap, ap);
        W.enable(apply, cur != 0);
        apply.setOnClickListener(v -> applyAll(apply));
        return card;
    }

    void pick(long ms, FrameLayout[] cells, TextView apply, FrameLayout wrap) {
        long was = Session.settings().optLong("autoDeleteDefaultMs", 0);
        put("autoDeleteDefaultMs", ms);
        App.bg(() -> Http.post("/me/auto-delete", App.jo("ms", ms)));
        for (int i = 0; i < cells.length; i++) {
            boolean on = MS[i] == ms;
            cells[i].setBackground(on ? W.round(T.surface, 999) : null);
            ((TextView) cells[i].getChildAt(0)).setTextColor(on ? T.text : T.textSecondary);
        }
        W.enable(apply, ms != 0 && !applying);
        if ((was != 0) != (ms != 0)) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) wrap.getLayoutParams();
            int from = p.height, to = ms != 0 ? T.dp(34) : 0;
            wrap.animate().alpha(ms != 0 ? 1f : 0f).setDuration(200).start();
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0, 1);
            va.setDuration(260);
            va.setInterpolator(new android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f));
            va.addUpdateListener(x -> {
                float f = (float) x.getAnimatedValue();
                p.height = Math.round(from + (to - from) * f);
                wrap.setLayoutParams(p);
            });
            va.start();
        }
    }

    void applyAll(TextView b) {
        long cur = Session.settings().optLong("autoDeleteDefaultMs", 0);
        if (cur == 0 || applying) return;
        applying = true;
        S.busy(b, true, "Apply to existing chats", "Applying...");
        App.bg(() -> {
            Http.post("/me/auto-delete", App.jo("ms", cur));
            Http.Resp r = Http.post("/me/auto-delete/apply", new JSONObject());
            App.ui(() -> {
                applying = false;
                S.busy(b, false, "Apply to existing chats", "Applying...");
                if (r.ok()) {
                    int n = r.obj().optInt("changed");
                    Ov.toast(n > 0 ? "Applied to " + n + " chat" + (n == 1 ? "" : "s") : "All your chats already use this timer");
                } else Ov.toast("Could not apply the timer", true);
            });
        });
    }
}
