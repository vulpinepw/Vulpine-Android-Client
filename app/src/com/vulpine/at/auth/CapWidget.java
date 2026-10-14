package com.vulpine.at.auth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Cap;

public class CapWidget extends LinearLayout {
    public interface On {
        void on(String token);
    }

    final Box box;
    final TextView label;
    public String token;
    long expires;
    int state;
    On on;

    class Box extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float prog, rot;

        Box(Context c) {
            super(c);
        }

        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), d = T.dpf(1);
            RectF r = new RectF(d, d, w - d, h - d);
            if (state == 0 || state == 3) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x91fafafa);
                cv.drawRoundRect(r, T.dpf(6), T.dpf(6), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(d);
                p.setColor(state == 3 ? 0xfff55b50 : 0xd1aaaaaa);
                cv.drawRoundRect(r, T.dpf(6), T.dpf(6), p);
            } else if (state == 1) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(3));
                p.setColor(0xffeeeeee);
                float m = T.dpf(3);
                cv.drawOval(m, m, w - m, h - m, p);
                p.setColor(0xff000000);
                p.setStrokeCap(Paint.Cap.ROUND);
                cv.drawArc(m, m, w - m, h - m, -90 + rot, Math.max(20, prog * 360), false, p);
                rot = (rot + 4) % 360;
                postInvalidateOnAnimation();
            }
        }
    }

    public CapWidget(Context c, On on) {
        super(c);
        this.on = on;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(W.round(0xfffdfdfd, 14, 0x8fdddddd, 1));
        setPadding(T.dp(14), T.dp(14), T.dp(14), T.dp(14));
        FrameLayout bw = new FrameLayout(c);
        box = new Box(c);
        bw.addView(box, new FrameLayout.LayoutParams(T.dp(25), T.dp(25)));
        addView(bw, W.lp(T.dp(25), T.dp(25)));
        label = W.text(c, "Verify you're human", 15, 500, 0xff212121);
        label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL);
        LinearLayout.LayoutParams lp = W.lp(0, W.WC, 1);
        lp.leftMargin = T.dp(15);
        addView(label, lp);
        TextView cr = W.text(c, "Cap", 12, 400, 0xff777777);
        cr.setPaintFlags(cr.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        addView(cr);
        setOnClickListener(v -> solve());
        W.press(this, .97f);
    }

    void solve() {
        if (state == 1 || state == 2) return;
        state = 1;
        box.invalidate();
        label.setText("Verifying...");
        App.bg(() -> {
            try {
                String t = Cap.obtain();
                App.ui(() -> {
                    state = 2;
                    token = t;
                    expires = System.currentTimeMillis() + 20 * 60000L;
                    FrameLayout bw = (FrameLayout) box.getParent();
                    bw.removeAllViews();
                    View ok = W.img(getContext(), Svg.stroke(25, 0xff00a67d, 2.6f, "M20 6 9 17l-5-5"));
                    bw.addView(ok, new FrameLayout.LayoutParams(T.dp(25), T.dp(25)));
                    ok.setScaleX(.4f);
                    ok.setScaleY(.4f);
                    ok.animate().scaleX(1).scaleY(1).setInterpolator(T.POP).setDuration(300).start();
                    label.setText("You're a human");
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                    on.on(t);
                    App.H.postDelayed(this::reset, 20 * 60000L);
                });
            } catch (Exception e) {
                App.ui(() -> {
                    state = 3;
                    box.invalidate();
                    label.setText("Error. Try again.");
                });
            }
        });
    }

    public void reset() {
        if (!isAttachedToWindow()) return;
        token = null;
        state = 0;
        FrameLayout bw = (FrameLayout) getChildAt(0);
        bw.removeAllViews();
        bw.addView(box, new FrameLayout.LayoutParams(T.dp(25), T.dp(25)));
        box.invalidate();
        label.setText("I'm a human");
        on.on(null);
    }
}
