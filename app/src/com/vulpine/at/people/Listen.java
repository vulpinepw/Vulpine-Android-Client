package com.vulpine.at.people;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import org.json.JSONObject;

public final class Listen {
    public static final int GREEN = 0xff1db954;

    public static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    public static LinearLayout line(Context c, JSONObject a, int color, float px) {
        LinearLayout r = W.row(c);
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Ic.get("ppl.spotify", 12, GREEN));
        r.addView(ic, W.lp(T.dp(12), T.dp(12)));
        TextView t = W.text(c, "Listening to " + a.optString("title"), px, 400, color);
        W.ellipsize(t);
        r.addView(t, W.lpm(W.WC, W.WC, 5, 0, 0, 0));
        return r;
    }

    public static class Card extends LinearLayout {
        final String uid;
        final TextView head, title, by, on, pos, dur;
        final ImageView cover;
        final FrameLayout bar;
        final View fill;
        final LinearLayout prog;
        String coverUrl;

        public Card(Context c, String uid, int fg) {
            super(c);
            this.uid = uid;
            setOrientation(VERTICAL);
            setBackground(W.round(T.surface2, 12));
            setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
            LinearLayout h = W.row(c);
            ImageView ic = new ImageView(c);
            ic.setImageDrawable(Ic.get("ppl.spotify", 14, GREEN));
            h.addView(ic, W.lp(T.dp(14), T.dp(14)));
            head = W.text(c, "", 13, 600, T.textSecondary);
            h.addView(head, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
            addView(h, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
            LinearLayout b = W.row(c);
            cover = new ImageView(c);
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setClipToOutline(true);
            cover.setBackground(W.round(T.mixA(T.text, .08f), 8));
            b.addView(cover, W.lp(T.dp(60), T.dp(60)));
            LinearLayout m = W.col(c);
            title = W.text(c, "", 14, 600, fg);
            W.ellipsize(title);
            by = W.text(c, "", 13, 400, T.textSecondary);
            W.ellipsize(by);
            on = W.text(c, "", 13, 400, T.textSecondary);
            W.ellipsize(on);
            m.addView(title);
            m.addView(by);
            m.addView(on);
            b.addView(m, W.lpm(0, W.WC, 12, 0, 0, 0));
            ((LinearLayout.LayoutParams) m.getLayoutParams()).weight = 1;
            addView(b, W.lp(W.MP, W.WC));
            prog = W.row(c);
            pos = W.text(c, "", 11, 400, T.textSecondary);
            pos.setFontFeatureSettings("tnum");
            dur = W.text(c, "", 11, 400, T.textSecondary);
            dur.setFontFeatureSettings("tnum");
            bar = new FrameLayout(c);
            bar.setBackground(W.round(T.mixA(T.text, .14f), 2));
            bar.setClipToOutline(true);
            fill = new View(c);
            fill.setBackground(W.round(T.text, 2));
            bar.addView(fill, new FrameLayout.LayoutParams(0, W.MP));
            prog.addView(pos);
            prog.addView(bar, W.lpm(0, T.dp(4), 8, 0, 8, 0));
            ((LinearLayout.LayoutParams) bar.getLayoutParams()).weight = 1;
            prog.addView(dur);
            addView(prog, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
            bind();
        }

        final Pres.L pl = id -> {
            if (id == null || id.equals(Card.this.uid)) bind();
        };

        final Runnable tick = new Runnable() {
            public void run() {
                bind();
            }
        };

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Pres.on(pl);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            Pres.off(pl);
            removeCallbacks(tick);
        }

        void bind() {
            removeCallbacks(tick);
            JSONObject a = Pres.activity(uid);
            if (a == null) {
                setVisibility(GONE);
                return;
            }
            setVisibility(VISIBLE);
            boolean playing = a.optBoolean("playing");
            head.setText(playing ? "Listening to Spotify" : "Paused on Spotify");
            title.setText(a.optString("title"));
            by.setText("by " + a.optString("artist"));
            String al = a.optString("album", "");
            on.setText("on " + al);
            on.setVisibility(al.isEmpty() || a.isNull("album") ? GONE : VISIBLE);
            String cv = a.isNull("cover") ? null : a.optString("cover", null);
            if (cv != null && !cv.equals(coverUrl)) {
                coverUrl = cv;
                cover.setImageDrawable(null);
                Img.into(cover, cv, T.dp(120));
            } else if (cv == null) {
                coverUrl = null;
                cover.setScaleType(ImageView.ScaleType.CENTER);
                cover.setImageDrawable(Ic.get("ppl.spotify", 26, GREEN));
            }
            if (cv != null) cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            long now = playing ? System.currentTimeMillis() : (a.isNull("pausedAt") ? System.currentTimeMillis() : a.optLong("pausedAt"));
            long d = a.optLong("durationMs", 0), p = Math.max(0, now - a.optLong("startedAt", now));
            if (d > 0) p = Math.min(p, d);
            pos.setText(fmt(p));
            bar.setVisibility(VISIBLE);
            dur.setVisibility(d > 0 ? VISIBLE : GONE);
            dur.setText(fmt(d));
            final float frac = d > 0 ? p / (float) d : 0;
            if (d <= 0) bar.setBackground(null);
            else bar.setBackground(W.round(T.mixA(T.text, .14f), 2));
            fill.setVisibility(d > 0 ? VISIBLE : INVISIBLE);
            bar.post(() -> {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                lp.width = Math.round(bar.getWidth() * frac);
                fill.setLayoutParams(lp);
            });
            if (playing) postDelayed(tick, 1000);
        }
    }
}
