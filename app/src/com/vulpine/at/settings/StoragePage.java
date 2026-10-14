package com.vulpine.at.settings;

import android.animation.ValueAnimator;
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
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

public class StoragePage extends SetPage {
    static final long MB = 1048576L;
    static final long[] LIMITS = {128 * MB, 256 * MB, 512 * MB, 1024 * MB, 2048 * MB};
    static final String[] LLAB = {"128 MB", "256 MB", "512 MB", "1 GB", "2 GB"};
    Gauge gauge;
    TextView num, sub, files;
    float shownBytes = -1;

    public StoragePage() {
        super("storage", "Storage");
    }

    public static File dir() {
        return new File(App.ctx.getCacheDir(), "img");
    }

    public static boolean on() {
        return App.prefs.getInt("mc.on", 1) != 0;
    }

    public static long limit() {
        return App.prefs.getLong("mc.limit", 256 * MB);
    }

    public static boolean auto() {
        return App.prefs.getInt("mc.auto", 1) != 0;
    }

    static long[] measure() {
        File[] fs = dir().listFiles();
        long b = 0, n = 0;
        if (fs != null) for (File f : fs) if (f.isFile() && !f.getName().endsWith(".tmp")) {
            b += f.length();
            n++;
        }
        return new long[]{b, n};
    }

    public static void trim() {
        if (!auto() || !on()) return;
        File[] fs = dir().listFiles();
        if (fs == null) return;
        long total = 0;
        for (File f : fs) total += f.length();
        long lim = limit();
        if (total <= lim) return;
        Arrays.sort(fs, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
        for (File f : fs) {
            if (total <= lim * 9 / 10) break;
            if (f.getName().startsWith("asset-")) continue;
            total -= f.length();
            f.delete();
        }
    }

    public static void clear() {
        File[] fs = dir().listFiles();
        if (fs != null) for (File f : fs) if (!f.getName().startsWith("asset-")) f.delete();
    }

    static String fmt(double b) {
        if (b < 1024 * 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / MB);
        return String.format(Locale.US, "%.2f GB", b / (1024.0 * MB));
    }

    protected void content(LinearLayout body) {
        Context c = a;
        LinearLayout card = S.card(c, 16, 16);
        card.addView(S.text(c, "Media cache keeps images, avatars and files you have already viewed on this device.", 14, 400, T.textSecondary, 22.4f));
        LinearLayout ga = W.col(c);
        ga.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout box = new FrameLayout(c);
        gauge = new Gauge(c);
        box.addView(gauge, new FrameLayout.LayoutParams(W.MP, W.MP));
        LinearLayout mid = W.col(c);
        mid.setGravity(Gravity.CENTER);
        num = W.text(c, "0.0 MB", 15, 700, T.text);
        sub = W.text(c, "/" + fmt(limit()), 11, 400, T.textMuted);
        mid.addView(num);
        S.addW(mid, sub, 2);
        box.addView(mid, W.flp(W.WC, W.WC, Gravity.CENTER));
        ga.addView(box, W.lp(T.dp(124), T.dp(124)));
        files = W.text(c, "0 files cached", 12, 400, T.textSecondary);
        ga.addView(files, W.lpm(W.WC, W.WC, 0, 8, 0, 4));
        S.add(card, ga, 16);
        ((LinearLayout.LayoutParams) ga.getLayoutParams()).bottomMargin = T.dp(14);
        LinearLayout rows = W.col(c);
        S.Switch sw = new S.Switch(c, on(), null);
        rows.addView(row(c, "Media cache", true, sw));
        TextView sel = W.text(c, LLAB[idx()], 12, 400, T.text);
        sel.setPadding(T.dp(10), T.dp(6), T.dp(10), T.dp(6));
        T.lh(sel, 18);
        sel.setPadding(T.dp(10), T.dp(6) + sel.getPaddingTop(), T.dp(10), T.dp(6) + sel.getPaddingBottom());
        sel.setBackground(W.ripple(W.round(T.surface2, 8, T.border, 1), 8));
        sel.setOnClickListener(v -> {
            Ov.Menu m = new Ov.Menu(c);
            for (int i = 0; i < LIMITS.length; i++) {
                final int k = i;
                m.item(LLAB[i], i == idx() ? Svg.stroke(18, T.text, 2, "M20 6 9 17l-5-5") : new android.graphics.drawable.ColorDrawable(0), false, () -> {
                    App.prefs.edit().putLong("mc.limit", LIMITS[k]).apply();
                    sel.setText(LLAB[k]);
                    App.bg(() -> {
                        trim();
                        App.ui(this::refresh);
                    });
                });
            }
            int[] l = new int[2];
            v.getLocationInWindow(l);
            m.showAt(l[0] + v.getWidth(), l[1] + v.getHeight() + T.dp(4), true);
        });
        View limRow = row(c, "Cache limit", false, sel);
        rows.addView(limRow);
        S.Switch au = new S.Switch(c, auto(), v -> {
            App.prefs.edit().putInt("mc.auto", v ? 1 : 0).apply();
            if (v) App.bg(() -> {
                trim();
                App.ui(this::refresh);
            });
        });
        View autoRow = row(c, "Auto-clear oldest", false, au);
        rows.addView(autoRow);
        card.addView(rows);
        TextView clear = W.text(c, "Clear cache", 13, 600, T.danger);
        clear.setGravity(Gravity.CENTER);
        clear.setBackground(W.ripple(W.round(0, 999, T.danger, 1), 999));
        clear.setOnClickListener(v -> {
            clear.setEnabled(false);
            clear.setAlpha(.55f);
            clear.setText("Clearing…");
            App.bg(() -> {
                clear();
                App.ui(() -> {
                    render(0, 0);
                    App.H.postDelayed(() -> {
                        clear.setEnabled(on());
                        clear.setAlpha(on() ? 1f : .45f);
                        clear.setText("Clear cache");
                    }, 500);
                });
            });
        });
        LinearLayout.LayoutParams cp = W.lp(W.MP, T.dp(38));
        cp.topMargin = T.dp(12);
        card.addView(clear, cp);
        Runnable dim = () -> {
            boolean on = on();
            ga.setAlpha(on ? 1f : .45f);
            limRow.setAlpha(on ? 1f : .45f);
            autoRow.setAlpha(on ? 1f : .45f);
            sel.setEnabled(on);
            au.setEnabled(on);
            clear.setEnabled(on);
            clear.setAlpha(on ? 1f : .45f);
        };
        sw.cb = v -> {
            App.prefs.edit().putInt("mc.on", v ? 1 : 0).apply();
            if (!v) App.bg(() -> {
                clear();
                App.ui(() -> render(0, 0));
            });
            else refresh();
            dim.run();
        };
        dim.run();
        add(card);
        refresh();
    }

    int idx() {
        long l = limit();
        for (int i = 0; i < LIMITS.length; i++) if (LIMITS[i] == l) return i;
        return 1;
    }

    View row(Context c, String label, boolean bold, View control) {
        LinearLayout r = W.row(c);
        r.setPadding(0, T.dp(11), 0, T.dp(11));
        r.setBackground(new android.graphics.drawable.Drawable() {
            final Paint p = new Paint();

            public void draw(Canvas cv) {
                p.setColor(T.border);
                cv.drawRect(getBounds().left, getBounds().top, getBounds().right, getBounds().top + Math.max(1, T.dp(1)), p);
            }

            public void setAlpha(int a) {
            }

            public void setColorFilter(android.graphics.ColorFilter f) {
            }

            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        });
        TextView l = W.text(c, label, 13, bold ? 600 : 400, bold ? T.text : T.textSecondary);
        T.lh(l, 19.5f);
        r.addView(l, W.lp(0, W.WC, 1));
        r.addView(control);
        return r;
    }

    void refresh() {
        App.bg(() -> {
            long[] m = measure();
            App.ui(() -> {
                if (alive) render(m[0], m[1]);
            });
        });
    }

    void render(long bytes, long n) {
        sub.setText("/" + fmt(limit()));
        files.setText(n + " file" + (n == 1 ? "" : "s") + " cached");
        float frac = Math.min(1f, bytes / (float) Math.max(1, limit()));
        gauge.to(frac);
        float from = shownBytes < 0 ? 0 : shownBytes;
        ValueAnimator va = ValueAnimator.ofFloat(0, 1);
        va.setDuration(700);
        va.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        va.addUpdateListener(x -> num.setText(fmt(from + (bytes - from) * (float) x.getAnimatedValue())));
        va.start();
        shownBytes = bytes;
    }

    static class Gauge extends View {
        float frac, shown;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Gauge(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void to(float f) {
            frac = f;
            ValueAnimator va = ValueAnimator.ofFloat(shown, f);
            va.setDuration(800);
            va.setInterpolator(new android.view.animation.PathInterpolator(.22f, .61f, .36f, 1f));
            va.addUpdateListener(x -> {
                shown = (float) x.getAnimatedValue();
                invalidate();
            });
            va.start();
        }

        protected void onDraw(Canvas c) {
            float s = getWidth() / 110f;
            p.setStrokeWidth(9 * s);
            RectF r = new RectF((55 - 44) * s, (55 - 44) * s, (55 + 44) * s, (55 + 44) * s);
            p.setColor(T.surface3);
            c.drawOval(r, p);
            p.setColor(frac > .92f ? T.danger : T.accent);
            if (shown <= 0.0005f) {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(55 * s, 11 * s, 4.5f * s, p);
                p.setStyle(Paint.Style.STROKE);
            } else c.drawArc(r, -90, 360 * shown, false, p);
        }
    }
}
