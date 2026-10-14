package com.vulpine.at.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public final class T {
    public static boolean dark = true;
    public static float density = 3f, scale = 1f;
    public static Typeface r, m, sb, b, icons;
    public static int bg, surface, surface2, surface3, border, borderStrong, text, textSecondary, textMuted, accent, accentHover, accentSoft, accentFg, danger, dangerSoft, success, bubbleIn, bubbleOut, glassBg, glassBgStrong, glassStroke, vdCard, vdBtn, iconColor;
    public static int[] accentGrad;
    public static final Interpolator EASE = new PathInterpolator(.32f, .72f, 0f, 1f);
    public static final Interpolator POP = new PathInterpolator(.34f, 1.36f, .64f, 1f);
    public static final Interpolator EASE_IN = new PathInterpolator(.4f, 0f, 1f, 1f);
    public static final int DUR_FAST = 160, DUR = 240, DUR_SLOW = 380;
    public static boolean lowfx, power;

    public static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        if (r == null) {
            r = Typeface.createFromAsset(c.getAssets(), "fonts/inter-400.ttf");
            m = Typeface.createFromAsset(c.getAssets(), "fonts/inter-500.ttf");
            sb = Typeface.createFromAsset(c.getAssets(), "fonts/inter-600.ttf");
            b = Typeface.createFromAsset(c.getAssets(), "fonts/inter-700.ttf");
            icons = Typeface.createFromAsset(c.getAssets(), "fonts/icons.ttf");
        }
    }

    public static void apply(boolean isDark, String accentCss) {
        dark = isDark;
        if (dark) {
            bg = 0xff121212;
            surface = 0xff1e1e1e;
            surface2 = 0xff262626;
            surface3 = 0xff303030;
            border = 0x14ffffff;
            borderStrong = 0x24ffffff;
            text = 0xfff5f5f5;
            textSecondary = 0xffa0a0a0;
            textMuted = 0xff8b8b93;
            accent = 0xfff97316;
            accentHover = 0xffea6a0c;
            danger = 0xffef4444;
            dangerSoft = 0x1fef4444;
            success = 0xff22c55e;
            bubbleIn = surface2;
            vdCard = 0xff161618;
            vdBtn = 0xff2a2a2e;
            iconColor = 0xffffffff;
        } else {
            bg = 0xfff1f0ee;
            surface = 0xfffaf9f7;
            surface2 = 0xffefeeec;
            surface3 = 0xffe3e2df;
            border = 0xffd6d3ce;
            borderStrong = 0xffccc9c4;
            text = 0xff26262a;
            textSecondary = 0xff55545c;
            textMuted = 0xff66656d;
            accent = 0xffc2410c;
            accentHover = 0xff9a3412;
            danger = 0xffdc2626;
            dangerSoft = 0x14dc2626;
            success = 0xff16a34a;
            bubbleIn = 0xffffffff;
            vdCard = 0xffffffff;
            vdBtn = 0xffe3e2df;
            iconColor = 0xff000000;
        }
        accentGrad = null;
        if (accentCss != null && !accentCss.isEmpty()) {
            int[] g = Css.gradientColors(accentCss);
            Integer one = Css.color(accentCss);
            if (one != null) accent = one | 0xff000000;
            else if (g != null && g.length > 0) {
                accent = g[0];
                accentGrad = g;
            }
            accentHover = mix(accent, 0xff000000, .1f);
        }
        accentFg = fgFor(accent);
        accentSoft = (accent & 0x00ffffff) | (dark ? 0x26000000 : 0x1a000000);
        bubbleOut = bubbleIn;
        glassBg = lowfx ? mixA(surface, .94f) : (dark ? mixA(surface, .62f) : 0xa8ffffff);
        glassBgStrong = lowfx ? surface : (dark ? mixA(surface, .82f) : 0xe0ffffff);
        glassStroke = dark ? mixA(text, .14f) : 0x14000000;
    }

    public static int fgFor(int c) {
        double L = .2126 * lin(Color.red(c)) + .7152 * lin(Color.green(c)) + .0722 * lin(Color.blue(c));
        return 1.05 / (L + .05) >= 2.7 ? 0xffffffff : 0xff1a1206;
    }

    static double lin(int v) {
        double x = v / 255.0;
        return x <= .03928 ? x / 12.92 : Math.pow((x + .055) / 1.055, 2.4);
    }

    public static double lum(int c) {
        return .2126 * lin(Color.red(c)) + .7152 * lin(Color.green(c)) + .0722 * lin(Color.blue(c));
    }

    public static int mix(int a, int b, float t) {
        return Color.argb(Math.round(Color.alpha(a) * (1 - t) + Color.alpha(b) * t), Math.round(Color.red(a) * (1 - t) + Color.red(b) * t), Math.round(Color.green(a) * (1 - t) + Color.green(b) * t), Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    public static int mixA(int c, float a) {
        return (c & 0x00ffffff) | (Math.round(a * 255) << 24);
    }

    public static int dp(float v) {
        return Math.round(v * density);
    }

    public static float dpf(float v) {
        return v * density;
    }

    public static float sp(float v) {
        return v * scale;
    }

    public static void lh(android.widget.TextView t, float lineDp) {
        android.graphics.Paint.FontMetricsInt fm = t.getPaint().getFontMetricsInt();
        int natural = fm.descent - fm.ascent;
        int target = dp(lineDp * scale);
        int pad = Math.max(0, (target - natural) / 2);
        t.setPadding(t.getPaddingLeft(), pad, t.getPaddingRight(), Math.max(0, target - natural - pad));
    }

    public static Typeface w(int weight) {
        return weight >= 700 ? b : weight >= 600 ? sb : weight >= 500 ? m : r;
    }

    public static void text(android.widget.TextView t, float px, int weight, int color) {
        t.setTypeface(w(weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, px * scale);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
    }
}
