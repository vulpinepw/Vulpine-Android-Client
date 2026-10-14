package com.vulpine.at.shop;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class K {
    public static final int XS = 13, SM = 14, MD = 16, LG = 20, XL = 24;

    public static TextView btn(Context c, String label, String variant, int hDp, float fs, View.OnClickListener on) {
        boolean pr = "primary".equals(variant), sec = "secondary".equals(variant), dg = "danger".equals(variant);
        int fg = pr ? T.accentFg : dg ? 0xffffffff : sec ? T.text : T.textSecondary;
        TextView t = W.text(c, label, fs, 500, fg);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(T.dp(20), 0, T.dp(20), 0);
        Drawable bg = pr ? W.accentBg(999) : dg ? W.round(T.danger, 999) : sec ? W.round(T.surface2, 999) : new ColorDrawable(0);
        t.setBackground(W.ripple(bg, 999));
        t.setMinHeight(T.dp(hDp));
        t.setMinimumHeight(T.dp(hDp));
        t.setTag(variant);
        if (on != null) t.setOnClickListener(on);
        pressBtn(t);
        return t;
    }

    public static TextView btn(Context c, String label, String variant, View.OnClickListener on) {
        return btn(c, label, variant, 36, SM, on);
    }

    public static void pressBtn(View t) {
        t.setOnTouchListener((x, e) -> {
            if (!x.isEnabled()) return false;
            if (e.getAction() == MotionEvent.ACTION_DOWN) x.animate().scaleX(.96f).scaleY(.96f).setDuration(60).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) x.animate().scaleX(1).scaleY(1).setDuration(140).start();
            return false;
        });
    }

    public static void variant(TextView t, String variant) {
        boolean pr = "primary".equals(variant), sec = "secondary".equals(variant);
        t.setTextColor(pr ? T.accentFg : sec ? T.text : T.textSecondary);
        Drawable bg = pr ? W.accentBg(999) : sec ? W.round(T.surface2, 999) : new ColorDrawable(0);
        t.setBackground(W.ripple(bg, 999));
        t.setTag(variant);
    }

    public static void enable(View v, boolean on) {
        v.setEnabled(on);
        v.setAlpha(on ? 1f : .5f);
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = W.col(c);
        l.setBackground(W.round(T.surface, 16));
        l.setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(20));
        return l;
    }

    public static TextView text(Context c, CharSequence s, float px, int weight, int color) {
        TextView t = W.text(c, s, px, weight, color);
        android.graphics.Paint.FontMetricsInt fm = t.getPaint().getFontMetricsInt();
        int extra = Math.max(0, T.dp(px * 1.5f * T.scale) - (fm.descent - fm.ascent));
        t.setLineSpacing(extra, 1f);
        t.setPadding(0, extra / 2, 0, extra - extra / 2);
        return t;
    }

    public static TextView mono(Context c, CharSequence s, float px, int weight, int color) {
        TextView t = text(c, s, px, weight, color);
        t.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, weight >= 600 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        return t;
    }

    public static class Spin extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        float a;
        final int track, top;
        final float sw;

        public Spin(Context c, float strokeDp, int track, int top) {
            super(c);
            this.sw = T.dpf(strokeDp);
            this.track = track;
            this.top = top;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
        }

        protected void onDraw(Canvas c) {
            r.set(sw / 2, sw / 2, getWidth() - sw / 2, getHeight() - sw / 2);
            p.setColor(track);
            c.drawOval(r, p);
            p.setColor(top);
            c.drawArc(r, a - 135, 90, false, p);
            a = (a + 360f / (700f / 16f)) % 360;
            if (isShown()) postInvalidateOnAnimation();
        }
    }

    public static View spin(Context c, int sizeDp) {
        Spin s = new Spin(c, 2, T.border, T.accent);
        s.setLayoutParams(W.lp(T.dp(sizeDp), T.dp(sizeDp)));
        return s;
    }

    public static View spinCenter(Context c, int sizeDp, int padDp) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(c);
        f.setPadding(0, T.dp(padDp), 0, T.dp(padDp));
        f.addView(spin(c, sizeDp), W.flp(T.dp(sizeDp), T.dp(sizeDp), Gravity.CENTER));
        return f;
    }

    public static View empty(Context c, String title, String sub) {
        LinearLayout l = W.col(c);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(T.dp(16), T.dp(40), T.dp(16), T.dp(40));
        TextView a = text(c, title, SM, 600, T.text);
        a.setGravity(Gravity.CENTER);
        l.addView(a, W.lp(W.WC, W.WC));
        if (sub != null) {
            TextView b = text(c, sub, SM, 400, T.textMuted);
            b.setGravity(Gravity.CENTER);
            l.addView(b, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
        }
        fade(l, 300);
        return l;
    }

    public static String price(Object p) {
        if (p == null || p == org.json.JSONObject.NULL) return "Offer";
        double d = p instanceof Number ? ((Number) p).doubleValue() : Double.parseDouble(String.valueOf(p));
        return "$" + String.format(Locale.US, "%.2f", Math.round(d * 100) / 100.0);
    }

    public static String usd(double d) {
        return "$" + String.format(Locale.US, "%.2f", d);
    }

    static final Map<String, String> SHOP_ERR = new HashMap<>();

    static {
        String[] e = {"invalid_token", "The source account token is wrong.", "own_main_handle", "Your main @handle can't be put on sale - retire it to the wallet first.", "attached_handle", "This handle is attached to an account and can't be listed.", "handle_not_found", "Handle not found.", "handle_taken", "That handle is already taken.", "wallet_full", "Your wallet is full - buy a slot to store more.", "target_wallet_full", "That user's wallet is full.", "too_many_listed", "Listing limit reached (15). Delist something first.", "invalid_price", "Price must be between $0.001 and $9999.99.", "pro_required", "Additional handles are a Pro Box feature.", "max_handles_reached", "You already have the maximum of 3 handles.", "user_not_found", "No account with that @handle.", "cannot_transfer_self", "You can't transfer a handle to yourself.", "own_handle", "This handle is already yours.", "cannot_import_self", "You can't import your own account.", "account_locked", "That account is currently locked - try again later.", "slow_down", "You're going too fast - try again in a moment.", "payments_unavailable", "Payments are temporarily unavailable.", "payment_provider_error", "Payment provider error - try again.", "too_many_pending_invoices", "Too many open invoices - complete or let them expire first."};
        for (int i = 0; i < e.length; i += 2) SHOP_ERR.put(e[i], e[i + 1]);
    }

    public static String shopErr(String code) {
        String m = SHOP_ERR.get(code);
        if (m != null) return m;
        return code == null || code.isEmpty() ? "Something went wrong." : code;
    }

    public static void in(View v, int delayMs) {
        if (T.lowfx && delayMs > 0) delayMs = Math.min(delayMs, 120);
        v.setAlpha(0);
        v.setTranslationY(T.dp(10));
        v.animate().alpha(1).translationY(0).setStartDelay(delayMs).setDuration(350).setInterpolator(T.EASE).start();
    }

    public static void fade(View v, int dur) {
        v.setAlpha(0);
        v.animate().alpha(1).setDuration(dur).setInterpolator(T.EASE).start();
    }

    public static boolean safeHref(String u) {
        if (u == null) return false;
        String l = u.trim().toLowerCase(Locale.ROOT);
        return l.startsWith("https://") || l.startsWith("http://");
    }

    public static void openUrl(String url) {
        if (!safeHref(url)) return;
        MainActivity a = MainActivity.cur;
        Context c = a != null ? a : App.ctx;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            if (a == null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            Ov.toast("Could not open the payment page", true);
        }
    }
}
