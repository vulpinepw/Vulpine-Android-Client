package com.vulpine.at.settings;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.LinkedHashMap;
import java.util.Map;

public class Modal {
    public interface Done {
        void on(String err);
    }

    public interface Submit {
        void on(Map<String, String> v, Done done);
    }

    public final FrameLayout ov;
    public final LinearLayout card;
    boolean closed;

    public Modal(Context c) {
        ov = new FrameLayout(c);
        ov.setBackgroundColor(0xb8000000);
        ov.setClickable(true);
        ov.setOnClickListener(v -> close());
        ScrollView sv = new ScrollView(c);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(false);
        card = W.col(c);
        card.setBackground(W.round(T.vdCard, 16));
        card.setElevation(T.dpf(16));
        card.setPadding(T.dp(24), T.dp(24), T.dp(24), T.dp(24));
        card.setClickable(true);
        sv.addView(card, new FrameLayout.LayoutParams(W.MP, W.WC));
        FrameLayout.LayoutParams p = W.flp(W.MP, W.WC, Gravity.CENTER);
        p.leftMargin = p.rightMargin = T.dp(24);
        FrameLayout box = new FrameLayout(c) {
            protected void onMeasure(int wm, int hm) {
                int w = MeasureSpec.getSize(wm);
                super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(w, T.dp(360)), MeasureSpec.EXACTLY), hm);
            }
        };
        box.addView(sv, new FrameLayout.LayoutParams(W.MP, W.WC));
        ov.addView(box, p);
    }

    public Modal show() {
        FrameLayout h = Ov.host();
        if (h == null) return this;
        MainActivity a = MainActivity.cur;
        ov.setPadding(0, a == null ? 0 : a.insetTop, 0, a == null ? 0 : Math.max(a.insetBottom, a.insetIme));
        h.addView(ov, new FrameLayout.LayoutParams(W.MP, W.MP));
        ov.setAlpha(0);
        ov.animate().alpha(1).setDuration(T.DUR_FAST).start();
        card.setScaleX(.96f);
        card.setScaleY(.96f);
        card.animate().scaleX(1).scaleY(1).setDuration(T.DUR).setInterpolator(T.EASE).start();
        Ov.push(ov, this::close);
        return this;
    }

    public void close() {
        if (closed) return;
        closed = true;
        Ov.remove(ov);
        W.hideKb(card);
        FrameLayout h = Ov.host();
        ov.animate().alpha(0).setDuration(T.DUR_FAST).withEndAction(() -> {
            if (h != null) h.removeView(ov);
        }).start();
    }

    static int fg() {
        return T.dark ? 0xffffffff : T.text;
    }

    static int muted() {
        return T.dark ? 0xffa0a0a8 : 0xff55545c;
    }

    public static TextView btn(Context c, String label, int bg, int fg, int weight, View.OnClickListener on) {
        TextView b = W.text(c, label, 14, weight, fg);
        b.setGravity(Gravity.CENTER);
        b.setPadding(T.dp(16), T.dp(8), T.dp(16), T.dp(8));
        T.lh(b, 21);
        b.setPadding(T.dp(16), T.dp(8) + b.getPaddingTop(), T.dp(16), T.dp(8) + b.getPaddingBottom());
        b.setBackground(W.ripple(bg == T.accent ? W.accentBg(8) : W.round(bg, 8), 8));
        b.setOnClickListener(on);
        return b;
    }

    public static Modal form(Context c, String title, String desc, String[][] fields, Submit sub) {
        Modal m = new Modal(c);
        TextView h = W.text(c, title, 17, 700, fg());
        m.card.addView(h);
        if (desc != null) {
            TextView d = S.text(c, desc, 13, 400, muted(), 19.5f);
            S.add(m.card, d, 6);
        }
        Map<String, EditText> ins = new LinkedHashMap<>();
        boolean first = desc == null;
        for (String[] f : fields) {
            TextView l = W.text(c, f[1], 13, 600, fg());
            S.add(m.card, l, first ? 16 : 10);
            first = false;
            EditText e = new EditText(c);
            T.text(e, 14, 400, fg());
            e.setBackground(W.round(T.dark ? 0xff0d0d0e : 0xfff1f0ee, 8));
            e.setPadding(T.dp(11), T.dp(9), T.dp(11), T.dp(9));
            e.setHint(f[2]);
            e.setHintTextColor(muted());
            e.setSingleLine(true);
            int max = f.length > 4 ? Integer.parseInt(f[4]) : 200;
            e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
            if ("tel".equals(f[3])) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            S.add(m.card, e, 5);
            ins.put(f[0], e);
        }
        LinearLayout act = W.row(c);
        act.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        act.addView(btn(c, "Cancel", T.vdBtn, fg(), 400, v -> m.close()));
        TextView save = btn(c, "Save", T.accent, T.accentFg, 600, null);
        save.setOnClickListener(v -> {
            if (!save.isEnabled()) return;
            save.setEnabled(false);
            save.setText("…");
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, EditText> en : ins.entrySet()) out.put(en.getKey(), en.getValue().getText().toString());
            sub.on(out, err -> {
                if (err != null) {
                    save.setEnabled(true);
                    save.setText("Save");
                    Ov.toast(err, true);
                } else m.close();
            });
        });
        LinearLayout.LayoutParams sp = W.lp(W.WC, W.WC);
        sp.leftMargin = T.dp(8);
        act.addView(save, sp);
        S.add(m.card, act, 18);
        m.show();
        if (!ins.isEmpty()) {
            EditText f0 = ins.values().iterator().next();
            f0.postDelayed(() -> W.showKb(f0), 120);
        }
        return m;
    }

    public static Modal confirm(Context c, String title, String msg, String ok, Runnable yes) {
        Modal m = new Modal(c);
        m.card.addView(W.text(c, title, 17, 700, fg()));
        S.add(m.card, S.text(c, msg, 14, 400, muted(), 21), 8);
        LinearLayout act = W.row(c);
        act.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        act.addView(btn(c, "Cancel", T.vdBtn, fg(), 400, v -> m.close()));
        LinearLayout.LayoutParams sp = W.lp(W.WC, W.WC);
        sp.leftMargin = T.dp(8);
        act.addView(btn(c, ok, 0xffef4444, 0xffffffff, 600, v -> {
            m.close();
            yes.run();
        }), sp);
        S.add(m.card, act, 18);
        return m.show();
    }
}
