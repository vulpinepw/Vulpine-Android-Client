package com.vulpine.at.call;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import org.json.JSONObject;

final class Rate {
    final CallUi ui;
    final String callId;
    final FrameLayout root;
    final LinearLayout inner;
    final ImageView[] stars = new ImageView[5];
    TextView send;
    int pick;
    boolean busy, sent, closed;
    final Runnable auto = this::close;

    Rate(CallUi ui, String callId) {
        this.ui = ui;
        this.callId = callId;
        Context c = ui.c;
        root = new FrameLayout(c);
        root.setClickable(true);
        CallBg bg = new CallBg(c, false);
        root.addView(bg, new FrameLayout.LayoutParams(W.MP, W.MP));
        inner = W.col(c);
        inner.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = T.dp(24);
        FrameLayout wrap = new FrameLayout(c);
        wrap.setPadding(pad, Math.max(pad, ui.a.insetTop), pad, Math.max(pad, ui.a.insetBottom));
        wrap.addView(inner, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.CENTER));
        root.addView(wrap, new FrameLayout.LayoutParams(W.MP, W.MP));
        body();
        ui.layer.addView(root, new FrameLayout.LayoutParams(W.MP, W.MP));
        Ov.push(root, this::close);
        root.setAlpha(0);
        root.animate().alpha(1).setDuration(340).setInterpolator(new android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f)).start();
        inner.setAlpha(0);
        inner.setTranslationY(T.dpf(18));
        inner.setScaleX(.97f);
        inner.setScaleY(.97f);
        inner.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(420).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .3f, 1.1f)).start();
        App.H.postDelayed(auto, 25000);
    }

    void body() {
        Context c = ui.c;
        inner.removeAllViews();
        if (sent) {
            FrameLayout ck = new FrameLayout(c);
            ck.setBackground(W.oval(T.success));
            ImageView ci = new ImageView(c);
            ci.setImageDrawable(Ic.get("call.check", 22, 0xffffffff));
            ck.addView(ci, new FrameLayout.LayoutParams(T.dp(22), T.dp(22), Gravity.CENTER));
            inner.addView(ck, W.lpm(T.dp(64), T.dp(64), 0, 6, 0, 8));
            CallUi.pop(ck, 0);
            TextView t = W.text(c, "Thanks for the feedback", 26, 700, T.text);
            t.setGravity(Gravity.CENTER);
            T.lh(t, 31.2f);
            inner.addView(t, W.lpm(W.WC, W.WC, 0, 0, 0, 4));
            return;
        }
        TextView t = W.text(c, "How was the call?", 26, 700, T.text);
        t.setGravity(Gravity.CENTER);
        T.lh(t, 31.2f);
        inner.addView(t);
        TextView s = W.text(c, "Rate your call", 14, 400, T.textSecondary);
        T.lh(s, 21);
        inner.addView(s, W.lpm(W.WC, W.WC, 0, 6, 0, 0));
        LinearLayout row = W.row(c);
        for (int i = 0; i < 5; i++) {
            final int k = i + 1;
            ImageView st = new ImageView(c);
            st.setPadding(T.dp(3), T.dp(3), T.dp(3), T.dp(3));
            st.setContentDescription(k + " star" + (k > 1 ? "s" : ""));
            st.setOnClickListener(v -> {
                pick = k;
                paint();
                v.animate().scaleX(1.3f).scaleY(1.3f).rotation(-8).setDuration(160).withEndAction(() -> v.animate().scaleX(1).scaleY(1).rotation(0).setDuration(200).start()).start();
            });
            stars[i] = st;
            row.addView(st, W.lpm(T.dp(50), T.dp(50), i == 0 ? 0 : 8, 0, 0, 0));
        }
        inner.addView(row, W.lpm(W.WC, W.WC, 0, 28, 0, 32));
        LinearLayout acts = W.row(c);
        acts.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView skip = W.text(c, "Not now", 14, 600, T.textSecondary);
        T.lh(skip, 21);
        skip.setPadding(T.dp(26), T.dp(12), T.dp(26), T.dp(12));
        skip.setOnClickListener(v -> close());
        acts.addView(skip);
        send = W.text(c, "Submit", 14, 600, T.accentFg);
        T.lh(send, 21);
        send.setPadding(T.dp(26), T.dp(12), T.dp(26), T.dp(12));
        send.setBackground(W.round(T.accent, 999));
        send.setOnClickListener(v -> submit());
        acts.addView(send, W.lpm(W.WC, W.WC, 12, 0, 0, 0));
        inner.addView(acts, W.lp(W.WC, W.WC));
        paint();
    }

    void paint() {
        for (int i = 0; i < 5; i++) stars[i].setImageDrawable(Ic.get("call.star", 44, i < pick ? 0xfffbbf24 : T.mixA(T.text, .22f)));
        if (send != null) {
            send.setAlpha(pick == 0 || busy ? .45f : 1f);
            send.setEnabled(pick > 0 && !busy);
        }
    }

    void submit() {
        if (pick == 0 || busy) return;
        busy = true;
        paint();
        final int st = pick;
        App.bg(() -> {
            Http.Resp r = Http.post("/calls/rating", App.jo("callId", callId, "stars", st));
            App.H.post(() -> {
                busy = false;
                if (closed) return;
                if (r.status > 0) {
                    sent = true;
                    body();
                    App.H.removeCallbacks(auto);
                    App.H.postDelayed(auto, 1600);
                } else paint();
            });
        });
    }

    void close() {
        if (closed) return;
        closed = true;
        App.H.removeCallbacks(auto);
        Ov.remove(root);
        root.animate().alpha(0).setDuration(260).setInterpolator(T.EASE_IN).withEndAction(() -> ui.layer.removeView(root)).start();
        if (ui.rate == this) ui.rate = null;
    }

    static boolean due() {
        int n = App.prefs.getInt("vulpine.rateN", 0) + 1;
        App.prefs.edit().putInt("vulpine.rateN", n).apply();
        return (n & 3) == 0;
    }
}
