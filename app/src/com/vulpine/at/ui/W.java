package com.vulpine.at.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class W {
    public static final int MP = ViewGroup.LayoutParams.MATCH_PARENT, WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static GradientDrawable cursor(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setSize(Math.max(2, T.dp(2)), T.dp(20));
        return g;
    }

    public static GradientDrawable round(int color, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(T.dpf(rDp));
        return g;
    }

    public static GradientDrawable round(int color, float rDp, int strokeColor, float strokeDp) {
        GradientDrawable g = round(color, rDp);
        if (strokeDp > 0) g.setStroke(Math.max(1, Math.round(T.dpf(strokeDp))), strokeColor);
        return g;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    public static GradientDrawable grad(int[] cols, float angleCss, float rDp) {
        GradientDrawable.Orientation o;
        float a = ((angleCss % 360) + 360) % 360;
        if (a < 22.5 || a >= 337.5) o = GradientDrawable.Orientation.BOTTOM_TOP;
        else if (a < 67.5) o = GradientDrawable.Orientation.BL_TR;
        else if (a < 112.5) o = GradientDrawable.Orientation.LEFT_RIGHT;
        else if (a < 157.5) o = GradientDrawable.Orientation.TL_BR;
        else if (a < 202.5) o = GradientDrawable.Orientation.TOP_BOTTOM;
        else if (a < 247.5) o = GradientDrawable.Orientation.TR_BL;
        else if (a < 292.5) o = GradientDrawable.Orientation.RIGHT_LEFT;
        else o = GradientDrawable.Orientation.BR_TL;
        GradientDrawable g = new GradientDrawable(o, cols.length == 1 ? new int[]{cols[0], cols[0]} : cols);
        g.setCornerRadius(T.dpf(rDp));
        return g;
    }

    public static Drawable accentBg(float rDp) {
        if (T.accentGrad != null && T.accentGrad.length > 1) return grad(T.accentGrad, 135, rDp);
        return round(T.accent, rDp);
    }

    public static Drawable ripple(Drawable content, float rDp) {
        GradientDrawable mask = round(Color.WHITE, rDp);
        return new RippleDrawable(ColorStateList.valueOf(T.mixA(T.text, .10f)), content, mask);
    }

    public static TextView text(Context c, CharSequence s, float px, int weight, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        T.text(t, px, weight, color);
        return t;
    }

    public static TextView icon(Context c, String liga, float px, int color) {
        TextView t = new TextView(c);
        t.setTypeface(T.icons);
        t.setText(liga);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, px);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public static ImageView img(Context c, Drawable d) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(d);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return v;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(T.dp(l), T.dp(t), T.dp(r), T.dp(b));
        return p;
    }

    public static FrameLayout.LayoutParams flp(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    public static View space(Context c, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, T.dp(hDp)));
        return v;
    }

    public static View flex(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    public static void press(View v, float scale) {
        v.setOnTouchListener((x, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) x.animate().scaleX(scale).scaleY(scale).setDuration(90).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1).scaleY(1).setDuration(160).setInterpolator(T.POP).start();
            return false;
        });
    }

    public static TextView btn(Context c, String label, boolean primary, View.OnClickListener on) {
        TextView t = text(c, label, 14, 500, primary ? T.accentFg : T.textSecondary);
        t.setGravity(Gravity.CENTER);
        t.setPadding(T.dp(20), 0, T.dp(20), 0);
        t.setMinHeight(T.dp(36));
        t.setBackground(ripple(primary ? accentBg(999) : new ColorDrawable(0), 999));
        t.setOnClickListener(on);
        t.setOnTouchListener((x, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) x.setTranslationY(T.dpf(1));
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) x.setTranslationY(0);
            return false;
        });
        return t;
    }

    public static void enable(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : .5f);
    }

    public static class Field extends FrameLayout {
        public final EditText input;
        final TextView label;
        TextView prefix;
        boolean floated;
        final View line;

        public Field(Context c, String lab, String pre) {
            super(c);
            setPadding(0, T.dp(18), 0, 0);
            setClipToPadding(false);
            LinearLayout r = row(c);
            if (pre != null) {
                prefix = text(c, pre, 14, 600, T.accentGrad != null ? T.text : T.accent);
                prefix.setPadding(T.dp(2), 0, 0, 0);
                r.addView(prefix);
            }
            input = new EditText(c);
            input.setBackground(null);
            input.setMinHeight(0);
            input.setMinimumHeight(0);
            T.text(input, 14, 400, T.text);
            android.graphics.Paint.FontMetricsInt ffm = input.getPaint().getFontMetricsInt();
            int fx = Math.max(0, T.dp(21 * T.scale) - (ffm.descent - ffm.ascent));
            input.setPadding(T.dp(2), T.dp(8) + fx / 2, T.dp(2), T.dp(8) + fx - fx / 2 + Math.max(1, T.dp(1)));
            input.setSingleLine(true);
            input.setHintTextColor(T.textMuted);
            if (android.os.Build.VERSION.SDK_INT >= 29) input.setTextCursorDrawable(cursor(T.accent));
            r.addView(input, lp(0, WC, 1));
            addView(r, flp(MP, WC, Gravity.BOTTOM));
            line = new View(c);
            line.setBackgroundColor(T.borderStrong);
            addView(line, flp(MP, Math.max(1, T.dp(1)), Gravity.BOTTOM));
            label = text(c, lab, 14, 400, T.textMuted);
            T.lh(label, 21);
            label.setPivotX(0);
            label.setPivotY(0);
            addView(label, flp(WC, WC, Gravity.TOP | Gravity.START));
            label.setTranslationY(T.dp(8));
            label.setTranslationX(T.dp(2) + (prefix != null ? T.dp(16) : 0));
            input.setOnFocusChangeListener((v, f) -> update(true));
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int d) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int d) {
                }

                public void afterTextChanged(Editable e) {
                    update(true);
                }
            });
            update(false);
        }

        void update(boolean anim) {
            boolean f = input.hasFocus() || input.length() > 0;
            line.setBackgroundColor(input.hasFocus() ? T.accent : T.borderStrong);
            if (f == floated && anim) return;
            floated = f;
            float ty = f ? -T.dp(18) : T.dp(8), sc = f ? .93f : 1f, tx = f ? T.dp(2) : T.dp(2) + (prefix != null ? T.dp(16) : 0);
            label.setTextColor(f ? (T.accentGrad != null ? T.text : T.accent) : T.textMuted);
            if (anim) label.animate().translationY(ty).translationX(tx).scaleX(sc).scaleY(sc).setDuration(T.DUR).setInterpolator(T.EASE).start();
            else {
                label.setTranslationY(ty);
                label.setTranslationX(tx);
                label.setScaleX(sc);
                label.setScaleY(sc);
            }
        }

        public String value() {
            return input.getText().toString();
        }
    }

    public static class Segmented extends FrameLayout {
        public interface On {
            void on(int i);
        }

        final LinearLayout row;
        final View pill;
        final TextView[] tabs;
        int sel;
        public On on;

        public Segmented(Context c, String[] labels, int initial, On on) {
            super(c);
            this.on = on;
            setBackground(round(T.surface2, 12));
            setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
            pill = new View(c);
            pill.setBackground(round(T.surface, 999));
            addView(pill, new LayoutParams(0, T.dp(34)));
            row = row(c);
            tabs = new TextView[labels.length];
            for (int i = 0; i < labels.length; i++) {
                final int k = i;
                TextView t = text(c, labels[i], 14, 500, T.textSecondary);
                t.setGravity(Gravity.CENTER);
                t.setOnClickListener(v -> select(k, true));
                tabs[i] = t;
                LinearLayout.LayoutParams p = lp(0, T.dp(34), 1);
                if (i > 0) p.leftMargin = T.dp(4);
                row.addView(t, p);
            }
            addView(row, new LayoutParams(MP, T.dp(34)));
            sel = initial;
            addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place(false));
        }

        void place(boolean anim) {
            TextView t = tabs[sel];
            if (t.getWidth() == 0) return;
            ViewGroup.LayoutParams p = pill.getLayoutParams();
            if (p.width != t.getWidth()) {
                p.width = t.getWidth();
                pill.setLayoutParams(p);
            }
            for (int i = 0; i < tabs.length; i++) tabs[i].setTextColor(i == sel ? T.text : T.textSecondary);
            if (anim) pill.animate().translationX(t.getLeft()).setDuration(T.DUR).setInterpolator(T.EASE).start();
            else pill.setTranslationX(t.getLeft());
        }

        public void select(int i, boolean fire) {
            if (i == sel && fire) return;
            sel = i;
            place(true);
            if (fire && on != null) on.on(i);
        }

        public int selected() {
            return sel;
        }
    }

    public static class Spin extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float a;

        public Spin(Context c, int color, float strokeDp) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(T.dpf(strokeDp));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);
        }

        protected void onDraw(Canvas c) {
            float s = p.getStrokeWidth();
            c.drawArc(s, s, getWidth() - s, getHeight() - s, a, 270, false, p);
            a = (a + 10) % 360;
            if (isShown()) postInvalidateOnAnimation();
        }
    }

    public static class LoadBar extends FrameLayout {
        public LoadBar(Context c) {
            super(c);
            setBackground(round(T.mixA(T.text, .08f), 99));
            View thumb = new View(c);
            thumb.setBackground(round(T.accent, 99));
            addView(thumb, new LayoutParams(T.dp(52), T.dp(3)));
            ValueAnimator va = ValueAnimator.ofFloat(-1f, 3.25f);
            va.setDuration(1000);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            va.addUpdateListener(x -> thumb.setTranslationX((float) x.getAnimatedValue() * T.dp(52)));
            va.start();
            setClipToOutline(true);
            setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        }
    }

    public static void hideKb(View v) {
        InputMethodManager im = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    public static void showKb(View v) {
        v.requestFocus();
        InputMethodManager im = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }

    public static Drawable pressBg(float a, float rDp) {
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, round(T.mixA(T.text, a), rDp));
        sl.addState(new int[]{}, new ColorDrawable(0));
        return sl;
    }

    public static void pressNow(View v, float scale) {
        v.setOnTouchListener((x, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                x.setScaleX(scale);
                x.setScaleY(scale);
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                x.setScaleX(1);
                x.setScaleY(1);
            }
            return false;
        });
    }

    public static void lines(TextView t, float lineDp) {
        android.graphics.Paint.FontMetricsInt fm = t.getPaint().getFontMetricsInt();
        int nat = fm.descent - fm.ascent;
        int extra = Math.max(0, T.dp(lineDp * T.scale) - nat);
        t.setLineSpacing(extra, 1f);
        t.setPadding(t.getPaddingLeft(), extra / 2, t.getPaddingRight(), extra - extra / 2);
    }

    public static void ellipsize(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }

    public static void multiline(EditText e) {
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }

    public static class Pill extends Drawable {
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        final int fill, ring, hi;
        final float ringDp;
        final android.graphics.Path a = new android.graphics.Path(), b = new android.graphics.Path();
        final android.graphics.RectF r = new android.graphics.RectF();

        float radiusDp = -1;
        public int shadowColor;
        public float shadowBlurDp, shadowDyDp;

        public Pill(int fill, int ring, float ringDp, int hi) {
            this.fill = fill;
            this.ring = ring;
            this.ringDp = ringDp;
            this.hi = hi;
        }

        public Pill(int fill, int ring, float ringDp, int hi, float radiusDp) {
            this(fill, ring, ringDp, hi);
            this.radiusDp = radiusDp;
        }

        public Pill shadow(int color, float blurDp, float dyDp) {
            shadowColor = color;
            shadowBlurDp = blurDp;
            shadowDyDp = dyDp;
            return this;
        }

        public void draw(android.graphics.Canvas c) {
            android.graphics.Rect bd = getBounds();
            float rad = radiusDp >= 0 ? Math.min(T.dpf(radiusDp), Math.min(bd.width(), bd.height()) / 2f) : Math.min(bd.width(), bd.height()) / 2f;
            r.set(bd);
            p.setStyle(android.graphics.Paint.Style.FILL);
            if (shadowColor != 0) {
                p.setColor(0xff000000 | fill);
                p.setShadowLayer(T.dpf(shadowBlurDp) * .82f, 0, T.dpf(shadowDyDp), shadowColor);
                c.save();
                a.reset();
                a.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW);
                c.clipOutPath(a);
                c.drawRoundRect(r, rad, rad, p);
                c.restore();
                p.clearShadowLayer();
            }
            p.setColor(fill);
            c.drawRoundRect(r, rad, rad, p);
            if (hi != 0) {
                a.reset();
                a.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW);
                b.reset();
                b.addRoundRect(new android.graphics.RectF(r.left, r.top + T.dpf(1), r.right, r.bottom + T.dpf(1)), rad, rad, android.graphics.Path.Direction.CW);
                a.op(b, android.graphics.Path.Op.DIFFERENCE);
                p.setColor(hi);
                c.drawPath(a, p);
            }
            if (ring != 0 && ringDp > 0) {
                float sw = T.dpf(ringDp);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(sw);
                p.setColor(ring);
                r.inset(sw / 2f, sw / 2f);
                c.drawRoundRect(r, rad - sw / 2f, rad - sw / 2f, p);
            }
        }

        public void setAlpha(int x) {
        }

        public void setColorFilter(android.graphics.ColorFilter f) {
        }

        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
