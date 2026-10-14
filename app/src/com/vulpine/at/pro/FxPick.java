package com.vulpine.at.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.deco.Fx;
import com.vulpine.at.settings.ColorPick;
import com.vulpine.at.settings.S;
import com.vulpine.at.shop.Flow;
import com.vulpine.at.shop.K;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.Iterator;
import java.util.List;
import org.json.JSONObject;

public class FxPick extends LinearLayout {
    public interface Cb {
        void on(JSONObject cfg);
    }

    static final String[] PRE = {"#8c8ca8", "#ffffff", "#f97316", "#ec4899", "#8b5cf6", "#3b82f6", "#22c55e", "#eab308", "#ef4444", "#14b8a6"};
    JSONObject cfg;
    final Cb onSave;
    int cat;
    final Runnable save = this::fire;

    public FxPick(Context c, JSONObject cfg, Cb onSave) {
        super(c);
        setOrientation(VERTICAL);
        this.cfg = cfg == null ? new JSONObject() : cfg;
        this.onSave = onSave;
        render();
    }

    static String hex(Object o) {
        if (!(o instanceof String)) return "";
        String s = (String) o;
        if (!s.matches("^#?[0-9a-fA-F]{6}$")) return "";
        return s.startsWith("#") ? s : "#" + s;
    }

    void merge(JSONObject p) {
        try {
            for (Iterator<String> it = p.keys(); it.hasNext(); ) {
                String k = it.next();
                cfg.put(k, p.get(k));
            }
            JSONObject n = new JSONObject();
            for (Iterator<String> it = cfg.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = cfg.get(k);
                if (!"".equals(v)) n.put(k, v);
            }
            cfg = n;
        } catch (Exception e) {
        }
        render();
        removeCallbacks(save);
        postDelayed(save, 500);
    }

    void fire() {
        if (onSave != null) onSave.on(cfg);
    }

    void render() {
        removeAllViews();
        Context c = getContext();
        List<Object[]> cats = Fx.categories();
        Flow cp = new Flow(c, T.dp(4), T.dp(4));
        for (int i = 0; i < cats.size(); i++) {
            int n = i;
            boolean on = n == cat;
            TextView t = W.text(c, (String) cats.get(i)[0], K.XS, on ? 600 : 500, on ? T.accent : T.textSecondary);
            t.setPadding(T.dp(10), T.dp(3), T.dp(10), T.dp(3));
            t.setBackground(W.round(on ? T.accentSoft : 0, 999));
            t.setOnClickListener(v -> {
                cat = n;
                render();
            });
            cp.addView(t);
        }
        addView(cp, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        @SuppressWarnings("unchecked") List<String> ids = cats.isEmpty() ? new java.util.ArrayList<>() : (List<String>) cats.get(Math.min(cat, cats.size() - 1))[1];
        String cur = cfg.optString("icon", "");
        LinearLayout grid = W.col(c);
        int cols = 7;
        LinearLayout row = null;
        for (int i = 0; i < ids.size(); i++) {
            if (i % cols == 0) {
                row = W.row(c);
                grid.addView(row, W.lpm(W.MP, W.WC, 0, i == 0 ? 0 : 4, 0, 0));
            }
            String id = ids.get(i);
            boolean sel = id.equals(cur);
            View b = new View(c) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

                protected void onDraw(Canvas cv) {
                    Fx.drawIcon(cv, p, id, getWidth() / 2f, getHeight() / 2f, T.dpf(20), 0, sel ? T.accent : T.text, 1);
                }
            };
            GradientDrawable bg = W.round(sel ? T.accentSoft : 0, 12);
            b.setBackground(bg);
            b.setContentDescription(id);
            b.setOnClickListener(v -> merge(App.jo("icon", id)));
            LinearLayout.LayoutParams lp = W.lp(0, T.dp(40), 1);
            if (i % cols > 0) lp.leftMargin = T.dp(4);
            row.addView(b, lp);
        }
        if (row != null) for (int k = ids.size() % cols == 0 ? cols : ids.size() % cols; k < cols; k++) {
            View sp = new View(c);
            LinearLayout.LayoutParams lp = W.lp(0, 1, 1);
            lp.leftMargin = T.dp(4);
            row.addView(sp, lp);
        }
        addView(grid, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        String col = hex(cfg.opt("color")), col2 = hex(cfg.opt("color2"));
        addView(colorRow(c, "Color", "Auto", col.isEmpty(), col, false, v -> merge(v == null ? App.jo("color", "", "color2", "") : App.jo("color", v))), W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        addView(colorRow(c, "Gradient", "Off", col2.isEmpty(), col2, true, v -> merge(v == null ? App.jo("color2", "") : App.jo("color", col.isEmpty() ? "#ffffff" : col, "color2", v))), W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        Object o = cfg.opt("opacity");
        float op = o instanceof Number && ((Number) o).floatValue() >= .05f && ((Number) o).floatValue() <= 1 ? ((Number) o).floatValue() : 1;
        LinearLayout r = W.row(c);
        TextView l = W.text(c, "Opacity", K.XS, 400, T.textSecondary);
        l.setMinWidth(T.dp(52));
        r.addView(l);
        TextView val = W.text(c, Math.round(op * 100) + "%", K.XS, 400, T.textSecondary);
        S.Slider sl = new S.Slider(c, .1f, 1f, .05f, op, v -> val.setText(Math.round(v * 100) + "%"), v -> merge(App.jo("opacity", Math.round(v * 100) / 100.0)));
        sl.setContentDescription("Opacity");
        r.addView(sl, W.lpm(0, T.dp(28), 8, 0, 8, 0));
        ((LinearLayout.LayoutParams) sl.getLayoutParams()).weight = 1;
        val.setMinWidth(T.dp(36));
        val.setGravity(Gravity.END);
        r.addView(val);
        addView(r, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
    }

    interface Pick {
        void on(String v);
    }

    View colorRow(Context c, String label, String none, boolean noneOn, String cur, boolean grad, Pick pick) {
        Flow f = new Flow(c, T.dp(8), T.dp(8));
        TextView l = W.text(c, label, K.XS, 400, T.textSecondary);
        l.setMinWidth(T.dp(52));
        f.addView(l);
        TextView auto = W.text(c, none, 10, 400, T.text);
        auto.setPadding(T.dp(8), T.dp(2), T.dp(8), T.dp(2));
        GradientDrawable ab = W.round(T.surface2, 999);
        if (noneOn) ab.setStroke(T.dp(2), T.accent);
        auto.setBackground(ab);
        auto.setOnClickListener(v -> pick.on(null));
        f.addView(auto);
        String base = hex(cfg.opt("color"));
        for (String p : PRE) {
            boolean on = p.equalsIgnoreCase(cur);
            View s = new View(c) {
                final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

                protected void onDraw(Canvas cv) {
                    float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f;
                    int a = 0xff000000 | Integer.parseInt(p.substring(1), 16);
                    if (grad) {
                        int b0 = 0xff000000 | Integer.parseInt((base.isEmpty() ? "#ffffff" : base).substring(1), 16);
                        pt.setShader(new LinearGradient(0, 0, w, h, b0, a, Shader.TileMode.CLAMP));
                    } else {
                        pt.setShader(null);
                        pt.setColor(a);
                    }
                    cv.drawCircle(w / 2, h / 2, r, pt);
                    pt.setShader(null);
                    if (on) {
                        pt.setStyle(Paint.Style.STROKE);
                        pt.setStrokeWidth(T.dpf(2));
                        pt.setColor(T.accent);
                        cv.drawCircle(w / 2, h / 2, r - T.dpf(1), pt);
                        pt.setStyle(Paint.Style.FILL);
                    }
                }
            };
            s.setContentDescription(p);
            s.setOnClickListener(v -> pick.on(p));
            f.addView(s, new Flow.LP(T.dp(20), T.dp(20)));
        }
        View custom = new View(c);
        GradientDrawable cb = W.round(cur.isEmpty() ? 0xff8c8ca8 : 0xff000000 | Integer.parseInt(cur.substring(1), 16), 4);
        custom.setBackground(cb);
        custom.setContentDescription(grad ? "Gradient color" : "Custom color");
        custom.setOnClickListener(v -> ColorPick.show(c, cur.isEmpty() ? 0xff8c8ca8 : 0xff000000 | Integer.parseInt(cur.substring(1), 16), col -> pick.on(String.format("#%06x", col & 0xffffff))));
        f.addView(custom, new Flow.LP(T.dp(28), T.dp(22)));
        return f;
    }
}
