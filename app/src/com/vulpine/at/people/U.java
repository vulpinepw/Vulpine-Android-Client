package com.vulpine.at.people;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.home.Badges;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.util.List;

public final class U {
    public static final int PRIMARY = 0, SECONDARY = 1, GHOST = 2, DANGER = 3;

    public static TextView btn(Context c, String label, int variant, View.OnClickListener on) {
        return btn(c, label, variant, 36, 14, on);
    }

    public static TextView btn(Context c, String label, int variant, int hDp, float px, View.OnClickListener on) {
        TextView t = W.text(c, label, px, 500, fg(variant));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(T.dp(20), 0, T.dp(20), 0);
        t.setMinHeight(T.dp(hDp));
        t.setMinimumHeight(T.dp(hDp));
        t.setBackground(bg(variant));
        if (on != null) t.setOnClickListener(on);
        W.press(t, .96f);
        return t;
    }

    public static int fg(int variant) {
        switch (variant) {
            case PRIMARY: return T.accentFg;
            case SECONDARY: return T.text;
            case DANGER: return 0xffffffff;
            default: return T.textSecondary;
        }
    }

    public static Drawable bg(int variant) {
        switch (variant) {
            case PRIMARY: return W.ripple(W.accentBg(999), 999);
            case SECONDARY: return W.ripple(W.round(T.surface2, 999), 999);
            case DANGER: return W.ripple(W.round(T.danger, 999), 999);
            default: {
                StateListDrawable s = new StateListDrawable();
                s.addState(new int[]{android.R.attr.state_pressed}, W.round(T.surface2, 999));
                s.addState(new int[]{}, new ColorDrawable(0));
                return s;
            }
        }
    }

    public static void variant(TextView t, int variant) {
        t.setTextColor(fg(variant));
        t.setBackground(bg(variant));
    }

    public static void enable(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : .5f);
    }

    public static TextView muted(Context c, String s, float px) {
        return txt(c, s, px, 400, T.textMuted);
    }

    public static View pressBg(View v, float rDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, W.round(T.mixA(T.text, .09f), rDp));
        s.addState(new int[]{}, new ColorDrawable(0));
        v.setBackground(s);
        return v;
    }

    public static AvatarView avatar(Context c, String name, String color, String url, int sizeDp, float textDp) {
        AvatarView av = new AvatarView(c);
        av.setLayoutParams(W.lp(T.dp(sizeDp), T.dp(sizeDp)));
        av.textSize(textDp);
        av.set(name, color, url);
        return av;
    }

    public static void badges(LinearLayout row, String uid, String handle, boolean pro, int sizeDp, int mlDp, boolean proAlways) {
        Context c = row.getContext();
        if (Badges.isBot(handle)) {
            ImageView b = new ImageView(c);
            b.setImageDrawable(Ic.svg(Badges.BOT_SVG, sizeDp, T.text));
            row.addView(b, W.lpm(T.dp(sizeDp), T.dp(sizeDp), mlDp, 0, 0, 0));
            return;
        }
        List<String> l = Badges.forUser(uid, handle);
        for (String u : l) {
            ImageView b = new ImageView(c);
            b.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Img.anim(b, u, T.dp(sizeDp * 2), true);
            row.addView(b, W.lpm(T.dp(sizeDp), T.dp(sizeDp), mlDp, 0, 0, 0));
        }
        if (pro && (proAlways || l.isEmpty())) {
            ImageView p = new ImageView(c);
            p.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Img.anim(p, "asset:pro-badge.gif", T.dp(sizeDp * 2), true);
            row.addView(p, W.lpm(T.dp(sizeDp), T.dp(sizeDp), mlDp, 0, 0, 0));
        }
    }

    public static TextView check(Context c, int sizeDp, float rDp, boolean on, float px) {
        TextView t = W.text(c, on ? "✓" : "", px, 700, 0xffffffff);
        t.setGravity(Gravity.CENTER);
        t.setBackground(W.round(on ? T.accent : (T.dark ? 0x0fffffff : 0x0f000000), rDp));
        t.setLayoutParams(W.lp(T.dp(sizeDp), T.dp(sizeDp)));
        return t;
    }

    public static void setCheck(TextView t, boolean on, float rDp) {
        t.setText(on ? "✓" : "");
        t.setBackground(W.round(on ? T.accent : (T.dark ? 0x0fffffff : 0x0f000000), rDp));
    }

    public static boolean copy(String s) {
        try {
            ClipboardManager cm = (ClipboardManager) App.ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Vulpine", s));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String err(Http.Resp r, String fallback) {
        if (r == null) return fallback;
        String e = r.obj().optString("error", null);
        if (e == null || e.isEmpty()) return r.status == 0 ? fallback : "Request failed (" + r.status + ")";
        switch (e) {
            case "group_limit_reached": return "Group limit reached (max 10 groups per account).";
            case "public_group_limit_reached": return "Public group limit reached (max 3 per account).";
            case "cannot_edit_own_role": return "You can't edit your own role.";
            case "cannot_grant_permissions_you_lack": return "You can only grant permissions you also have.";
            case "cannot_edit_default_roles": return "Only the group creator can edit the default roles.";
            case "cannot_assign_role_exceeding_own": return "You can't assign a role with permissions you don't have.";
            case "attachments_not_allowed": return "You don't have permission to send images & files in this group.";
            case "cannot_mute_creator": return "The group creator can't be muted.";
            case "creator_only_mute_admins": return "Only the creator can mute admins.";
            case "creator_only": return "Only the group creator can do this.";
            case "creator_only_admins": return "Only the group creator can moderate admins.";
            case "display_name_not_allowed": return "This display name is not allowed.";
            case "group_name_not_allowed": return "This group name is not allowed.";
            case "account_locked": return "This account is temporarily locked.";
        }
        return e;
    }

    public static void toastErr(Http.Resp r, String fallback) {
        Ov.toast(err(r, fallback), true);
    }

    public static Integer hex(String s) {
        if (s == null || s.isEmpty()) return null;
        return Css.color(s);
    }

    public static Drawable cssBg(String css, float rDp) {
        if (css == null || css.isEmpty()) return null;
        int[] g = Css.gradientColors(css);
        if (g != null && g.length > 0) return new Grad(g, Css.gradientAngle(css), T.dpf(rDp));
        Integer one = Css.color(css);
        return one == null ? null : W.round(one, rDp);
    }

    public static int luma255(int c) {
        return Math.round(.299f * ((c >> 16) & 255) + .587f * ((c >> 8) & 255) + .114f * (c & 255));
    }

    public static Integer cardFg(String bg) {
        if (bg == null || bg.isEmpty()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}").matcher(bg);
        double sum = 0;
        int n = 0;
        while (m.find()) {
            int v = Integer.parseInt(m.group().substring(1), 16);
            sum += (.299 * ((v >> 16) & 255) + .587 * ((v >> 8) & 255) + .114 * (v & 255)) / 255.0;
            n++;
        }
        if (n == 0) return 0xffffffff;
        return sum / n > .55 ? 0xff1a1a1a : 0xffffffff;
    }

    public static int[] fitBtn(String bg) {
        if (bg == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}").matcher(bg);
        int R = 0, G = 0, B = 0, n = 0;
        while (m.find()) {
            int v = Integer.parseInt(m.group().substring(1), 16);
            R += (v >> 16) & 255;
            G += (v >> 8) & 255;
            B += v & 255;
            n++;
        }
        if (n == 0) return null;
        R = Math.round(R / (float) n);
        G = Math.round(G / (float) n);
        B = Math.round(B / (float) n);
        double L = (.299 * R + .587 * G + .114 * B) / 255;
        if (L > .55) return new int[]{rgb(R * .58, G * .58, B * .58), 0xffffffff};
        if (L < .18) return new int[]{rgb(R + (255 - R) * .35, G + (255 - G) * .35, B + (255 - B) * .35), 0xff141414};
        return new int[]{rgb(R * .68, G * .68, B * .68), 0xffffffff};
    }

    static int rgb(double r, double g, double b) {
        return 0xff000000 | ((int) Math.round(r) << 16) | ((int) Math.round(g) << 8) | (int) Math.round(b);
    }

    public static final class Grad extends Drawable {
        final int[] cols;
        final float angle, r;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Grad(int[] cols, float angle, float rPx) {
            this.cols = cols.length == 1 ? new int[]{cols[0], cols[0]} : cols;
            this.angle = angle;
            this.r = rPx;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float w = b.width(), h = b.height();
            double a = Math.toRadians(angle);
            float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
            float len = (float) (Math.abs(w * Math.sin(a)) + Math.abs(h * Math.cos(a))) / 2f;
            float cx = b.left + w / 2f, cy = b.top + h / 2f;
            p.setShader(new LinearGradient(cx - dx * len, cy - dy * len, cx + dx * len, cy + dy * len, cols, null, Shader.TileMode.CLAMP));
        }

        public void draw(Canvas c) {
            Rect b = getBounds();
            c.drawRoundRect(new RectF(b), r, r, p);
        }

        public void setAlpha(int a) {
            p.setAlpha(a);
        }

        public void setColorFilter(ColorFilter f) {
            p.setColorFilter(f);
        }

        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    public static class Field extends FrameLayout {
        public final EditText input;
        final TextView label;
        TextView prefix;
        boolean floated;
        final View line;
        boolean multi;

        public Field(Context c, String lab, String pre, int max, boolean multiline) {
            super(c);
            multi = multiline;
            setPadding(0, T.dp(18), 0, 0);
            setClipToPadding(false);
            LinearLayout r = W.row(c);
            r.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
            if (pre != null) {
                prefix = W.text(c, pre, 14, 600, T.accent);
                prefix.setPadding(T.dp(2), 0, 0, 0);
                r.addView(prefix);
            }
            input = new EditText(c);
            input.setBackground(null);
            input.setMinHeight(0);
            input.setMinimumHeight(0);
            T.text(input, 14, 400, T.text);
            input.setPadding(T.dp(pre != null ? 2 : 2), T.dp(8), T.dp(2), T.dp(8));
            input.setHintTextColor(T.textMuted);
            input.setLineSpacing(0, 1.12f);
            input.setMinHeight(T.dp(37));
            input.setGravity(multiline ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL);
            if (android.os.Build.VERSION.SDK_INT >= 29) input.setTextCursorDrawable(com.vulpine.at.ui.W.cursor(T.accent));
            if (multiline) {
                W.multiline(input);
                input.setGravity(Gravity.TOP | Gravity.START);
                input.setMinHeight(T.dp(59));
            } else {
                input.setSingleLine(true);
                input.setMinHeight(T.dp(38));
            }
            if (max > 0) input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
            r.addView(input, W.lp(0, W.WC, 1));
            addView(r, new LayoutParams(W.MP, W.WC));
            line = new View(c);
            line.setBackgroundColor(T.borderStrong);
            addView(line, W.flp(W.MP, Math.max(1, T.dp(1)), Gravity.BOTTOM));
            if (multiline) {
                View grip = new View(c) {
                    final Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);

                    @Override
                    protected void onDraw(Canvas cv) {
                        float d = T.dpf(1), w = getWidth(), h = getHeight();
                        gp.setStrokeWidth(d);
                        float[][] ls = {{w - 8.5f * d, h - .5f * d, w - 2.5f * d, h - 6f * d}, {w - 4.5f * d, h - .5f * d, w - 1.5f * d, h - 3.5f * d}};
                        for (float[] l : ls) {
                            gp.setColor(T.dark ? 0xff070707 : 0xffe8e8e8);
                            cv.drawLine(l[0] - d, l[1], l[2] - d, l[3], gp);
                            gp.setColor(T.dark ? 0xffa0a0a0 : 0xff767676);
                            cv.drawLine(l[0], l[1], l[2], l[3], gp);
                        }
                    }
                };
                FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(T.dp(12), T.dp(8), Gravity.BOTTOM | Gravity.END);
                gl.bottomMargin = T.dp(2.5f);
                addView(grip, gl);
                setPadding(0, T.dp(18), 0, T.dp(5));
            }
            label = W.text(c, lab, 14, 400, T.textMuted);
            label.setPivotX(0);
            label.setPivotY(0);
            addView(label, W.flp(W.WC, W.WC, Gravity.TOP | Gravity.START));
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
            setOnClickListener(v -> W.showKb(input));
            update(false);
        }

        float restX() {
            return T.dp(2) + (prefix != null ? T.dp(16) : 0);
        }

        void update(boolean anim) {
            boolean f = input.hasFocus() || input.length() > 0;
            line.setBackgroundColor(input.hasFocus() ? T.accent : T.borderStrong);
            if (f == floated && anim) return;
            floated = f;
            float ty = f ? -T.dp(18) : T.dp(8), sc = f ? .93f : 1f, tx = f ? 0 : restX();
            label.setTextColor(f ? T.accent : T.textMuted);
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

        public void set(String s) {
            input.setText(s == null ? "" : s);
            update(false);
        }

        public void onText(Runnable r) {
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int d) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int d) {
                }

                public void afterTextChanged(Editable e) {
                    r.run();
                }
            });
        }
    }

    public static class Switch extends View {
        public boolean on;
        float pos;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public interface On {
            void on(boolean v);
        }
        On cb;

        public Switch(Context c, boolean on, On cb) {
            super(c);
            this.on = on;
            this.cb = cb;
            pos = on ? 1 : 0;
            setLayoutParams(W.lp(T.dp(42), T.dp(24)));
            setOnClickListener(v -> set(!this.on, true));
        }

        public void set(boolean v, boolean fire) {
            on = v;
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(pos, v ? 1 : 0);
            va.setDuration(T.DUR_FAST);
            va.setInterpolator(T.EASE);
            va.addUpdateListener(a -> {
                pos = (float) a.getAnimatedValue();
                invalidate();
            });
            va.start();
            if (fire && cb != null) cb.on(v);
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(T.mix(T.surface3, T.accent, pos));
            c.drawRoundRect(0, 0, w, h, h / 2, h / 2, p);
            p.setColor(0xffffffff);
            float r = T.dpf(9);
            c.drawCircle(T.dpf(3) + r + pos * T.dpf(18), h / 2, r, p);
        }
    }

    public static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(T.mixA(T.text, .10f));
        return v;
    }

    public static Path roundPath(float w, float h, float r) {
        Path p = new Path();
        p.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        return p;
    }

    public static void longPress(View v, Runnable r) {
        final float[] st = new float[2];
        final Runnable[] pend = new Runnable[1];
        final boolean[] fired = new boolean[1];
        v.setOnTouchListener((x, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    st[0] = e.getRawX();
                    st[1] = e.getRawY();
                    pend[0] = () -> {
                        pend[0] = null;
                        x.setPressed(false);
                        x.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        fired[0] = true;
                        r.run();
                    };
                    fired[0] = false;
                    x.postDelayed(pend[0], 480);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (pend[0] != null && Math.hypot(e.getRawX() - st[0], e.getRawY() - st[1]) > T.dp(10)) {
                        x.removeCallbacks(pend[0]);
                        pend[0] = null;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (pend[0] != null) {
                        x.removeCallbacks(pend[0]);
                        pend[0] = null;
                    }
                    if (fired[0]) {
                        x.setPressed(false);
                        return true;
                    }
                    break;
            }
            return false;
        });
    }

    public static android.graphics.Typeface font(String css, int weight) {
        if (css == null || css.isEmpty()) return T.w(weight);
        String l = css.toLowerCase();
        int st = weight >= 600 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL;
        String fam;
        if (l.contains("comic")) fam = "casual";
        else if (l.contains("brush") || l.contains("cursive")) fam = "cursive";
        else if (l.contains("impact")) fam = "sans-serif-condensed";
        else if (l.contains("courier") || l.contains("lucida") || l.contains("monospace")) fam = "monospace";
        else if (l.contains("papyrus") || l.contains("fantasy")) fam = "serif-monospace";
        else if (l.contains("serif") && !l.contains("sans-serif")) fam = "serif";
        else if (l.contains("sans-serif")) fam = "sans-serif";
        else return T.w(weight);
        return android.graphics.Typeface.create(fam, l.contains("impact") ? android.graphics.Typeface.BOLD : st);
    }

    public static void applyFont(View v, String css) {
        if (css == null || css.isEmpty()) return;
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            int w = t.getTypeface() == T.b ? 700 : t.getTypeface() == T.sb ? 600 : t.getTypeface() == T.m ? 500 : 400;
            t.setTypeface(font(css, w));
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyFont(g.getChildAt(i), css);
        }
    }

    public static boolean isUuid(String s) {
        return s != null && s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    public static class RoundImg extends ImageView {
        final float r;
        final Path clip = new Path();

        public RoundImg(Context c, float rDp) {
            super(c);
            r = T.dpf(rDp);
        }

        @Override
        protected void onDraw(Canvas c) {
            clip.reset();
            float rr = Math.min(r, Math.min(getWidth(), getHeight()) / 2f);
            clip.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), rr, rr, Path.Direction.CW);
            int s = c.save();
            c.clipPath(clip);
            super.onDraw(c);
            c.restoreToCount(s);
        }
    }

    public static TextView lh(TextView t, float lineDp) {
        int target = T.dp(lineDp * T.scale);
        Paint.FontMetricsInt fm = t.getPaint().getFontMetricsInt();
        int extra = Math.max(0, target - (fm.descent - fm.ascent));
        t.setLineSpacing(extra, 1f);
        t.setPadding(t.getPaddingLeft(), extra / 2, t.getPaddingRight(), extra - extra / 2);
        return t;
    }

    public static TextView txt(Context c, CharSequence s, float px, int weight, int color) {
        return lh(W.text(c, s, px, weight, color), px * 1.5f);
    }
}
