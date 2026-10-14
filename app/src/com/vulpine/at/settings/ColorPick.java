package com.vulpine.at.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class ColorPick {
    public interface Cb {
        void on(int color);
    }

    public static void show(Context c, int initial, Cb cb) {
        Ov.Sheet s = new Ov.Sheet(c, false);
        float[] hsv = new float[3];
        Color.colorToHSV(initial | 0xff000000, hsv);
        int[] cur = {initial | 0xff000000};
        View prev = new View(c);
        prev.setBackground(W.round(cur[0], 12));
        W.Field hex = new W.Field(c, "Hex", null);
        hex.input.setText(S.hex(cur[0]));
        boolean[] lock = {false};
        View[] sv = new View[2];
        Runnable upd = () -> {
            cur[0] = Color.HSVToColor(hsv);
            prev.setBackground(W.round(cur[0], 12));
            if (!lock[0]) {
                lock[0] = true;
                hex.input.setText(S.hex(cur[0]));
                lock[0] = false;
            }
            for (View v : sv) if (v != null) v.invalidate();
        };
        View square = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            protected void onDraw(Canvas cv) {
                float w = getWidth(), h = getHeight();
                Shader a = new LinearGradient(0, 0, w, 0, 0xffffffff, Color.HSVToColor(new float[]{hsv[0], 1, 1}), Shader.TileMode.CLAMP);
                Shader b = new LinearGradient(0, 0, 0, h, 0x00000000, 0xff000000, Shader.TileMode.CLAMP);
                p.setShader(new ComposeShader(a, b, PorterDuff.Mode.SRC_OVER));
                cv.drawRoundRect(new RectF(0, 0, w, h), T.dpf(12), T.dpf(12), p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(2.5f));
                p.setColor(0xffffffff);
                cv.drawCircle(hsv[1] * w, (1 - hsv[2]) * h, T.dpf(9), p);
                p.setStyle(Paint.Style.FILL);
            }

            public boolean onTouchEvent(MotionEvent e) {
                getParent().requestDisallowInterceptTouchEvent(true);
                hsv[1] = Math.max(0, Math.min(1, e.getX() / getWidth()));
                hsv[2] = 1 - Math.max(0, Math.min(1, e.getY() / getHeight()));
                upd.run();
                return true;
            }
        };
        View hue = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            protected void onDraw(Canvas cv) {
                float w = getWidth(), h = getHeight();
                int[] cols = new int[7];
                for (int i = 0; i < 7; i++) cols[i] = Color.HSVToColor(new float[]{i * 60f % 360, 1, 1});
                p.setShader(new LinearGradient(0, 0, w, 0, cols, null, Shader.TileMode.CLAMP));
                cv.drawRoundRect(new RectF(0, h / 2 - T.dpf(7), w, h / 2 + T.dpf(7)), T.dpf(7), T.dpf(7), p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(2.5f));
                p.setColor(0xffffffff);
                cv.drawCircle(hsv[0] / 360f * w, h / 2, T.dpf(9), p);
                p.setStyle(Paint.Style.FILL);
            }

            public boolean onTouchEvent(MotionEvent e) {
                getParent().requestDisallowInterceptTouchEvent(true);
                hsv[0] = Math.max(0, Math.min(359.9f, e.getX() / getWidth() * 360f));
                upd.run();
                return true;
            }
        };
        sv[0] = square;
        sv[1] = hue;
        hex.input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence x, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence x, int a, int b, int d) {
            }

            public void afterTextChanged(Editable e) {
                if (lock[0]) return;
                String v = e.toString().trim();
                if (!v.matches("^#?[0-9a-fA-F]{6}$")) return;
                Integer col = Css.color(v.startsWith("#") ? v : "#" + v);
                if (col == null) return;
                Color.colorToHSV(col, hsv);
                lock[0] = true;
                upd.run();
                lock[0] = false;
            }
        });
        s.body.addView(square, W.lp(W.MP, T.dp(200)));
        S.add(s.body, hue, 16).getLayoutParams().height = T.dp(28);
        LinearLayout row = W.row(c);
        row.addView(prev, W.lp(T.dp(44), T.dp(36)));
        LinearLayout.LayoutParams hp = W.lp(0, W.WC, 1);
        hp.leftMargin = T.dp(12);
        row.addView(hex, hp);
        TextView0.add(row, S.btn(c, "Done", S.PRIMARY, v -> {
            s.close();
            cb.on(cur[0]);
        }));
        row.setGravity(Gravity.BOTTOM);
        S.add(s.body, row, 12);
        s.show();
    }

    static final class TextView0 {
        static void add(LinearLayout row, View v) {
            LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
            p.leftMargin = T.dp(12);
            row.addView(v, p);
        }
    }
}
