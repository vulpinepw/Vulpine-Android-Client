package com.vulpine.at.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Prefs;
import com.vulpine.at.Sync;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import org.json.JSONObject;

public class AppearancePage extends SetPage {
    public static final String[] JO = {"#F97316", "#EF4444", "#F59E0B", "#22C55E", "#06B6D4", "#3B82F6", "#8B5CF6", "#EC4899", "#64748B"};
    public static final String[][] AH = {{"Mango", "#F97316"}, {"Cherry", "#EF4444"}, {"Peach", "#F59E0B"}, {"Kiwi", "#22C55E"}, {"Berry", "#06B6D4"}, {"Blueberry", "#3B82F6"}, {"Plum", "#8B5CF6"}, {"Guava", "#EC4899"}, {"Lychee", "linear-gradient(135deg,#FF6B9D,#FFC371)"}, {"Dragonfruit", "linear-gradient(135deg,#FF0080,#7928CA)"}, {"Watermelon", "linear-gradient(135deg,#84CC16,#EF4444)"}, {"Coconut", "linear-gradient(135deg,#F5F5DC,#A0522D)"}, {"Passion", "linear-gradient(135deg,#6B21A8,#F97316)"}, {"Apricot", "linear-gradient(135deg,#FBBF24,#F97316)"}};
    static final String[] ICPRE = {"#8c8ca8", "#ffffff", "#f97316", "#ec4899", "#8b5cf6", "#3b82f6", "#22c55e", "#eab308", "#ef4444", "#14b8a6"};
    String wpMode, wpSolid, wpFrom, wpTo;
    int wpDeg;
    String bubMode, bubColor, bubFrom, bubTo;
    int bubDeg;
    boolean bubBusy;

    public AppearancePage() {
        super("appearance", "Appearance");
        String wp = Session.s == null ? "" : Session.s.optString("chatWallpaper", "");
        String[] pb = parseBg(wp);
        wpMode = wp.startsWith("linear-gradient") ? "gradient" : "color";
        wpSolid = pb[0].isEmpty() ? "#1e293b" : pb[0];
        wpFrom = pb[1].isEmpty() ? "#F97316" : pb[1];
        wpTo = pb[2].isEmpty() ? (pb[1].isEmpty() ? "#8B5CF6" : pb[1]) : pb[2];
        wpDeg = Integer.parseInt(pb[3]);
        JSONObject b = Session.settings().optJSONObject("dmBubble");
        if (b == null) b = new JSONObject();
        String m = b.optString("mode");
        bubMode = m.equals("color") || m.equals("gradient") ? m : "accent";
        bubColor = hex6(b.optString("color")) ? b.optString("color") : "#3B82F6";
        bubFrom = hex6(b.optString("from")) ? b.optString("from") : "#F97316";
        bubTo = hex6(b.optString("to")) ? b.optString("to") : "#8B5CF6";
        bubDeg = b.has("angle") ? b.optInt("angle", 0) : 135;
    }

    public static boolean hex6(String s) {
        return s != null && s.matches("^#[0-9a-fA-F]{6}$");
    }

    public static String[] parseBg(String v) {
        String solid = "", from = "", to = "";
        int deg = 135;
        String s = v == null ? "" : v;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}").matcher(s);
        java.util.List<String> hx = new java.util.ArrayList<>();
        while (m.find()) hx.add(m.group());
        if (s.startsWith("linear-gradient")) {
            from = hx.size() > 0 ? hx.get(0) : "";
            to = hx.size() > 1 ? hx.get(1) : from;
        } else solid = hx.size() > 0 ? hx.get(0) : "";
        java.util.regex.Matcher a = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)deg").matcher(s);
        if (a.find()) {
            int d = Math.round(Float.parseFloat(a.group(1)));
            if (d >= 0 && d <= 360) deg = d;
        }
        return new String[]{solid, from, to, String.valueOf(deg)};
    }

    protected void content(LinearLayout body) {
        Context c = a;
        add(themeCard(c));
        add(textCard(c), 28);
        add(accentCard(c));
        if (S.pro()) add(msgBgCard(c));
        add(wallCard(c), 28);
        add(iconsCard(c), 28);
        add(bubbleCard(c), 28);
        add(powerCard(c));
        LinearLayout tour = W.row(c);
        tour.setBackground(W.round(T.surface, 16));
        tour.setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(20));
        LinearLayout tt = W.col(c);
        tt.addView(S.label(c, "Welcome tour", 500));
        tt.addView(S.muted(c, "See the intro and the ready-made looks again."));
        tour.addView(tt, W.lp(0, W.WC, 1));
        LinearLayout.LayoutParams rp = W.lp(W.WC, W.WC);
        rp.leftMargin = T.dp(12);
        tour.addView(S.btn(c, "Replay", S.SECONDARY, v -> {
            Home h = Home.I;
            if (h == null) return;
            a.popTo(h);
            h.select("chats", false);
            App.H.postDelayed(() -> com.vulpine.at.tour.Tour.open(h), 350);
        }), rp);
        add(tour, 28);
        TextView cr = S.muted(c, "Animated emoji: Google Noto Emoji Animation, CC BY 4.0");
        add(cr, 28);
    }

    View themeCard(Context c) {
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Theme", 500));
        String t = Prefs.theme();
        int sel = t.equals("light") ? 1 : t.equals("system") ? 2 : 0;
        S.add(card, S.seg(c, new String[]{"Dark", "Light", "System"}, sel, 280, i -> setTheme(i == 1 ? "light" : i == 2 ? "system" : "dark")), 12);
        S.add(card, S.muted(c, sel == 2 ? "Follows your device’s light or dark setting." : "Dark is the default. Your choice syncs to all your devices."), 12);
        return card;
    }

    void setTheme(String p) {
        try {
            if (Session.s != null) Session.s.put("theme", p);
        } catch (Exception e) {
        }
        Session.save();
        Prefs.setTheme(p);
        App.bg(() -> Http.patch("/me", App.jo("theme", p)));
        App.H.postDelayed(() -> Settings.rerender(a, "appearance"), 260);
    }

    View textCard(Context c) {
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Message text size", 500));
        double cur = Session.settings().optDouble("textScale", 1);
        float[] vals = {.9f, 1f, 1.15f, 1.3f};
        int sel = 1;
        for (int i = 0; i < 4; i++) if (Math.abs(cur - vals[i]) < .01) sel = i;
        TextView demo = W.text(c, "The quick brown fox jumps over the lazy dog.", 14, 400, T.text);
        demo.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
        demo.setBackground(W.round(T.bubbleIn, 16));
        demo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14 * vals[sel]);
        S.lh(demo, 21 * vals[sel] / T.scale, 8, 8);
        S.add(card, S.seg(c, new String[]{"Small", "Default", "Large", "Larger"}, sel, 360, i -> {
            try {
                Session.settings().put("textScale", (double) vals[i]);
            } catch (Exception e) {
            }
            Sync.save();
            demo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14 * vals[i]);
            S.lh(demo, 21 * vals[i] / T.scale, 8, 8);
        }), 12);
        S.add(card, demo, 12);
        return card;
    }

    View accentCard(Context c) {
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Accent color", 500));
        S.add(card, S.muted(c, "Personalize your message bubble color. Gradients are Pro Box."), 12);
        String cur = Session.s == null ? "" : Session.s.optString("accentColor", "");
        String curS = cur.isEmpty() ? "#F97316" : cur;
        String[][] sol = {{"#F97316", "Orange"}, {"#3B82F6", "Blue"}, {"#22C55E", "Green"}, {"#EC4899", "Pink"}, {"#8B5CF6", "Purple"}, {"#06B6D4", "Cyan"}, {"#c3c6c9", "Silver"}};
        S.Flow f = new S.Flow(c, 8, 8);
        for (String[] s : sol) {
            S.Swatch sw = new S.Swatch(c, s[0], 36);
            sw.ringMode = 3;
            sw.selected = curS.equalsIgnoreCase(s[0]);
            sw.setContentDescription(s[1]);
            sw.setOnClickListener(v -> setAccent(s[0]));
            f.addView(sw);
        }
        S.add(card, f, 12);
        boolean pro = S.pro();
        if (pro) {
            S.add(card, S.muted(c, "Gradient presets (Pro)"), 12);
            String[][] grd = {{"linear-gradient(135deg,#FF6B9D,#FFC371)", "Lychee"}, {"linear-gradient(135deg,#3B82F6,#8B5CF6)", "Ocean"}, {"linear-gradient(135deg,#84CC16,#EF4444)", "Watermelon"}, {"linear-gradient(135deg,#6B21A8,#F97316)", "Passion"}};
            S.Flow g = new S.Flow(c, 8, 8);
            for (String[] s : grd) {
                S.Swatch sw = new S.Swatch(c, s[0], 36);
                sw.ringMode = 3;
                sw.selected = cur.equals(s[0]);
                sw.setContentDescription(s[1]);
                sw.setOnClickListener(v -> setAccent(s[0]));
                g.addView(sw);
            }
            S.add(card, g, 12);
        }
        LinearLayout row = W.row(c);
        row.setGravity(Gravity.BOTTOM);
        W.Field fld = new W.Field(c, pro ? "Custom (hex or CSS gradient)" : "Custom (hex color)", null);
        fld.input.setText(cur);
        row.addView(fld, W.lp(0, W.WC, 1));
        LinearLayout.LayoutParams bp = W.lp(W.WC, T.dp(36));
        bp.leftMargin = T.dp(8);
        row.addView(S.btn(c, "Apply", S.GHOST, v -> setAccent(fld.value().trim())), bp);
        LinearLayout.LayoutParams bp2 = W.lp(W.WC, T.dp(36));
        bp2.leftMargin = T.dp(8);
        row.addView(S.btn(c, "Reset", S.GHOST, v -> setAccent("")), bp2);
        fld.setClipChildren(false);
        row.setClipChildren(false);
        card.setClipChildren(false);
        S.add(card, row, 12);
        return card;
    }

    void setAccent(String v) {
        Http.Resp[] out = new Http.Resp[1];
        Sync.patchMe(App.jo("accentColor", v), out, () -> {
            if (out[0].ok()) {
                Ov.toast("Accent updated");
                Settings.rerender(a, "appearance");
            } else Ov.toast(S.err(out[0], "Could not update"), true);
        });
    }

    View msgBgCard(Context c) {
        LinearLayout card = W.row(c);
        card.setBackground(W.round(T.surface, 16));
        card.setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(20));
        LinearLayout tx = W.col(c);
        tx.addView(S.label(c, "Message background", 500));
        S.add(tx, S.secondary(c, "Use your profile background color for your group messages.", 13), 2);
        card.addView(tx, W.lp(0, W.WC, 1));
        boolean[] on = {Session.s != null && Session.s.optBoolean("profileBgGroups")};
        TextView pill = W.text(c, on[0] ? "On" : "Off", 13, 600, on[0] ? T.accentFg : T.text);
        pill.setPadding(T.dp(14), T.dp(5), T.dp(14), T.dp(5));
        T.lh(pill, 19.5f);
        pill.setPadding(T.dp(14), T.dp(5) + pill.getPaddingTop(), T.dp(14), T.dp(5) + pill.getPaddingBottom());
        pill.setBackground(on[0] ? W.accentBg(99) : W.round(T.surface3, 99));
        pill.setOnClickListener(v -> {
            on[0] = !on[0];
            pill.setText(on[0] ? "On" : "Off");
            pill.setTextColor(on[0] ? T.accentFg : T.text);
            pill.setBackground(on[0] ? W.accentBg(99) : W.round(T.surface3, 99));
            Sync.patchMe(App.jo("profileBgGroups", on[0]), null, null);
        });
        LinearLayout.LayoutParams pp = W.lp(W.WC, W.WC);
        pp.leftMargin = T.dp(12);
        card.addView(pill, pp);
        return card;
    }

    String wpCss() {
        return wpMode.equals("color") ? wpSolid : "linear-gradient(" + wpDeg + "deg, " + wpFrom + ", " + wpTo + ")";
    }

    public static View presetCell(Context c, String name, String css, boolean sel, boolean locked, View.OnClickListener on) {
        LinearLayout cell = W.col(c);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);
        TextView n = W.text(c, name, 10, 400, T.textSecondary);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setGravity(Gravity.CENTER);
        cell.addView(n, W.lp(W.MP, T.dp(10)));
        S.Swatch sw = new S.Swatch(c, css, 24);
        sw.ringMode = 2;
        sw.selected = sel;
        sw.lock = locked;
        if (locked) sw.setAlpha(.35f);
        cell.addView(sw, W.lpm(T.dp(24), T.dp(24), 0, 4, 0, 0));
        cell.setOnClickListener(on);
        return cell;
    }

    View wallCard(Context c) {
        LinearLayout card = S.card(c);
        boolean pro = S.pro();
        card.addView(S.label(c, "Chat background", 600));
        View seg = S.seg(c, new String[]{"Color", pro ? "Gradient" : "Gradient (Pro)"}, wpMode.equals("gradient") ? 1 : 0, 240, i -> {
            if (i == 1 && !pro) {
                S.segOf(seg0[0]).select(0, false);
                return;
            }
            wpMode = i == 1 ? "gradient" : "color";
            rebuild();
        });
        seg0[0] = seg;
        S.add(card, seg, 12);
        View prev = new View(c);
        prev.setBackground(S.cssDrawable(wpCss(), 12));
        S.add(card, prev, 12).getLayoutParams().height = T.dp(80);
        S.add(card, S.muted(c, "Presets"), 12);
        S.Grid g = new S.Grid(c, 54, 8, 8);
        g.setClipChildren(false);
        String css = wpCss();
        for (String[] p : AH) {
            boolean solid = !p[1].startsWith("linear");
            if (wpMode.equals("color") != solid) continue;
            boolean lock = !solid && !pro;
            boolean sel = solid ? css.equalsIgnoreCase(p[1]) : css.equals("linear-gradient(" + wpDeg + "deg, " + parseBg(p[1])[1] + ", " + parseBg(p[1])[2] + ")") && wpDeg == 135;
            g.addView(presetCell(c, p[0], p[1], sel, lock, v -> {
                if (lock) return;
                if (solid) wpSolid = p[1];
                else {
                    String[] q = parseBg(p[1]);
                    wpFrom = q[1];
                    wpTo = q[2];
                }
                rebuild();
            }));
        }
        S.add(card, g, 8);
        if (wpMode.equals("color")) {
            S.Flow f = new S.Flow(c, 8, 8);
            for (String col : JO) {
                S.Swatch sw = new S.Swatch(c, col, 32);
                sw.ringMode = 1;
                sw.selected = wpSolid.equalsIgnoreCase(col);
                sw.setOnClickListener(v -> {
                    wpSolid = col;
                    rebuild();
                });
                f.addView(sw);
            }
            f.addView(new S.ColorBox(c, Css.color(wpSolid) == null ? 0xff1e293b : Css.color(wpSolid), 40, 36, null, col -> {
                wpSolid = S.hex(col);
                rebuild();
            }));
            S.add(card, f, 16);
        } else S.add(card, gradRow(c, wpFrom, wpTo, wpDeg, (f, t, d) -> {
            wpFrom = f;
            wpTo = t;
            wpDeg = d;
            prev.setBackground(S.cssDrawable(wpCss(), 12));
        }), 16);
        S.Flow bt = new S.Flow(c, 8, 8);
        bt.addView(S.btn(c, "Apply", S.SECONDARY, v -> setWall(wpCss(), "Chat wallpaper updated")));
        if (pro) bt.addView(S.btn(c, "Upload image", S.GHOST, v -> Pick.file(a, new String[]{"image/png", "image/jpeg", "image/webp"}, this::uploadWall)));
        String cw = Session.s == null ? "" : Session.s.optString("chatWallpaper", "");
        if (!cw.isEmpty()) bt.addView(S.btn(c, "Clear", S.GHOST, v -> setWall("", "Chat wallpaper cleared")));
        S.add(card, bt, 12);
        return card;
    }

    final View[] seg0 = new View[1];

    interface Grad {
        void on(String from, String to, int deg);
    }

    View gradRow(Context c, String from, String to, int deg, Grad cb) {
        LinearLayout r = W.row(c);
        String[] st = {from, to};
        int[] dg = {deg};
        TextView lab = W.text(c, deg + "°", 13, 400, T.textMuted);
        lab.setMinWidth(T.dp(32));
        r.addView(new S.ColorBox(c, Css.color(from) == null ? 0xfff97316 : Css.color(from), 40, 36, null, col -> {
            st[0] = S.hex(col);
            cb.on(st[0], st[1], dg[0]);
        }));
        LinearLayout.LayoutParams p2 = W.lp(W.WC, W.WC);
        p2.leftMargin = T.dp(12);
        r.addView(new S.ColorBox(c, Css.color(to) == null ? 0xff8b5cf6 : Css.color(to), 40, 36, null, col -> {
            st[1] = S.hex(col);
            cb.on(st[0], st[1], dg[0]);
        }), p2);
        S.Slider sl = new S.Slider(c, 0, 360, 1, deg, v -> {
            dg[0] = Math.round(v);
            lab.setText(dg[0] + "°");
            cb.on(st[0], st[1], dg[0]);
        }, null);
        sl.trackDp = 28;
        LinearLayout.LayoutParams sp = W.lp(0, T.dp(28), 1);
        sp.leftMargin = T.dp(12);
        r.addView(sl, sp);
        LinearLayout.LayoutParams lp = W.lp(W.WC, W.WC);
        lp.leftMargin = T.dp(12);
        r.addView(lab, lp);
        return r;
    }

    void setWall(String css, String ok) {
        Http.Resp[] out = new Http.Resp[1];
        Sync.patchMe(App.jo("chatWallpaper", css), out, () -> {
            if (out[0].ok()) {
                Ov.toast(ok);
                if (alive) rebuild();
            } else Ov.toast(S.err(out[0], "Update failed"), true);
        });
    }

    void uploadWall(java.io.File f, String name, String mime) {
        String m = Pick.sniff(f);
        if (m == null || m.equals("image/gif")) m = mime;
        String mm = m;
        App.bg(() -> {
            Http.Resp r = Http.upload("/me/chat-wallpaper", "file", f, name, mm, null);
            f.delete();
            App.ui(() -> {
                if (r.ok()) {
                    Session.merge(r.obj());
                    Sync.patchMe(new JSONObject(), null, null);
                    Ov.toast("Wallpaper uploaded");
                    if (alive) rebuild();
                } else Ov.toast(S.err(r, "Upload failed"), true);
            });
        });
    }

    void putSet(Object... kv) {
        JSONObject st = Session.settings();
        try {
            for (int i = 0; i + 1 < kv.length; i += 2) st.put((String) kv[i], kv[i + 1]);
        } catch (Exception e) {
        }
        Sync.save();
    }

    View iconsCard(Context c) {
        LinearLayout card = S.card(c);
        JSONObject s = Session.settings();
        boolean en = s.optBoolean("bgIconsEnabled", true);
        int op = (int) Math.max(0, Math.min(100, Math.round(s.optDouble("bgIconsOpacity", 30))));
        LinearLayout hd = W.row(c);
        LinearLayout tx = W.col(c);
        tx.addView(S.label(c, "Background icons", 600));
        tx.addView(S.muted(c, "The icon pattern behind the app. Syncs across your devices."));
        hd.addView(tx, W.lp(0, W.WC, 1));
        LinearLayout.LayoutParams swp = W.lp(W.WC, W.WC);
        swp.leftMargin = T.dp(12);
        hd.addView(new S.Switch(c, en, v -> {
            putSet("bgIconsEnabled", v);
            rebuild();
        }), swp);
        card.addView(hd);
        LinearLayout or = W.row(c);
        TextView ol = W.text(c, "Opacity", 13, 400, T.textMuted);
        ol.setMinWidth(T.dp(58));
        or.addView(ol);
        TextView ov = W.text(c, op + "%", 13, 400, T.textMuted);
        ov.setMinWidth(T.dp(38));
        ov.setGravity(Gravity.END);
        S.Slider sl = new S.Slider(c, 0, 100, 1, op, v -> {
            ov.setText(Math.round(v) + "%");
            try {
                Session.settings().put("bgIconsOpacity", Math.round(v));
            } catch (Exception e) {
            }
            if (Home.I != null) Sync.save();
        }, v -> putSet("bgIconsOpacity", Math.round(v)));
        LinearLayout.LayoutParams slp = W.lp(0, T.dp(20), 1);
        slp.leftMargin = T.dp(12);
        or.addView(sl, slp);
        LinearLayout.LayoutParams ovp = W.lp(W.WC, W.WC);
        ovp.leftMargin = T.dp(12);
        or.addView(ov, ovp);
        LinearLayout grp = W.col(c);
        grp.addView(or);
        String c1 = hex6(s.optString("bgIconsColor")) ? s.optString("bgIconsColor") : "";
        String c2 = !c1.isEmpty() && hex6(s.optString("bgIconsColor2")) ? s.optString("bgIconsColor2") : "";
        double a0 = s.optDouble("bgIconsAngle", 135);
        int dg = a0 >= 0 && a0 <= 360 ? (int) Math.round(a0) : 135;
        String mode = c1.isEmpty() ? "auto" : !c2.isEmpty() ? "gradient" : "color";
        String css = mode.equals("gradient") ? "linear-gradient(" + dg + "deg, " + c1 + ", " + c2 + ")" : c1;
        S.add(grp, S.seg(c, new String[]{"Auto", "Color", "Gradient"}, mode.equals("auto") ? 0 : mode.equals("color") ? 1 : 2, 280, i -> {
            if (i == 0) putSet("bgIconsColor", "", "bgIconsColor2", "");
            else if (i == 1) putSet("bgIconsColor", c1.isEmpty() ? "#f97316" : c1, "bgIconsColor2", "");
            else putSet("bgIconsColor", c1.isEmpty() ? "#f97316" : c1, "bgIconsColor2", c2.isEmpty() ? "#8b5cf6" : c2);
            rebuild();
        }), 12);
        if (!mode.equals("auto")) {
            View pv = new View(c);
            pv.setBackground(S.cssDrawable(css, 12));
            S.add(grp, pv, 12).getLayoutParams().height = T.dp(36);
        }
        if (mode.equals("color")) {
            S.Flow f = new S.Flow(c, 8, 8);
            f.setClipChildren(false);
            for (String col : ICPRE) {
                S.Swatch sw = new S.Swatch(c, col, 24);
                sw.ringMode = 2;
                sw.selected = c1.equals(col);
                sw.setOnClickListener(v -> {
                    putSet("bgIconsColor", col);
                    rebuild();
                });
                f.addView(sw);
            }
            f.addView(new S.ColorBox(c, Css.color(c1) == null ? 0xfff97316 : Css.color(c1), 40, 36, null, col -> {
                putSet("bgIconsColor", S.hex(col));
                rebuild();
            }));
            S.add(grp, f, 12);
        } else if (mode.equals("gradient")) {
            S.Flow f = new S.Flow(c, 8, 8);
            f.setClipChildren(false);
            for (String[] p : AH) {
                if (!p[1].startsWith("linear")) continue;
                String[] q = parseBg(p[1]);
                S.Swatch sw = new S.Swatch(c, p[1], 24);
                sw.ringMode = 2;
                sw.selected = c1.equalsIgnoreCase(q[1]) && c2.equalsIgnoreCase(q[2]);
                sw.setContentDescription("Gradient preset " + p[0]);
                sw.setOnClickListener(v -> {
                    putSet("bgIconsColor", q[1].toLowerCase(), "bgIconsColor2", q[2].toLowerCase());
                    rebuild();
                });
                f.addView(sw);
            }
            S.add(grp, f, 12);
            S.Flow r = new S.Flow(c, 12, 8);
            LinearLayout fr = W.row(c);
            fr.addView(W.text(c, "From", 13, 400, T.textMuted));
            LinearLayout.LayoutParams p8 = W.lp(W.WC, W.WC);
            p8.leftMargin = T.dp(8);
            fr.addView(new S.ColorBox(c, Css.color(c1), 40, 36, null, col -> {
                putSet("bgIconsColor", S.hex(col));
                rebuild();
            }), p8);
            r.addView(fr);
            LinearLayout to = W.row(c);
            to.addView(W.text(c, "To", 13, 400, T.textMuted));
            LinearLayout.LayoutParams p9 = W.lp(W.WC, W.WC);
            p9.leftMargin = T.dp(8);
            to.addView(new S.ColorBox(c, Css.color(c2), 40, 36, null, col -> {
                putSet("bgIconsColor2", S.hex(col));
                rebuild();
            }), p9);
            r.addView(to);
            S.add(grp, r, 12);
            LinearLayout ar = W.row(c);
            TextView al = W.text(c, dg + "°", 13, 400, T.textMuted);
            al.setMinWidth(T.dp(32));
            S.Slider as = new S.Slider(c, 0, 360, 1, dg, v -> al.setText(Math.round(v) + "°"), v -> {
                putSet("bgIconsAngle", Math.round(v));
                rebuild();
            });
            ar.addView(as, W.lp(0, T.dp(28), 1));
            LinearLayout.LayoutParams alp = W.lp(W.WC, W.WC);
            alp.leftMargin = T.dp(12);
            ar.addView(al, alp);
            S.add(grp, ar, 8);
        }
        grp.setAlpha(en ? 1f : .4f);
        if (!en) disableTree(grp);
        S.add(card, grp, 12);
        return card;
    }

    static void disableTree(View v) {
        v.setEnabled(false);
        v.setClickable(false);
        if (v instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) disableTree(((android.view.ViewGroup) v).getChildAt(i));
    }

    public static int tint(int c) {
        return T.mix(T.bubbleIn, c | 0xff000000, T.dark ? .24f : .16f);
    }

    String bubCss() {
        boolean pro = S.pro();
        if (bubMode.equals("color") && hex6(bubColor)) return S.hex(tint(Css.color(bubColor)));
        if (bubMode.equals("gradient") && pro && hex6(bubFrom) && hex6(bubTo)) return "linear-gradient(" + Math.max(0, Math.min(360, bubDeg)) + "deg, " + S.hex(tint(Css.color(bubFrom))) + ", " + S.hex(tint(Css.color(bubTo))) + ")";
        return S.hex(tint(T.accent));
    }

    View bubbleCard(Context c) {
        LinearLayout card = S.card(c);
        boolean pro = S.pro();
        card.addView(S.label(c, "My messages in private chats", 600));
        S.add(card, S.muted(c, "Your own messages in DMs get a soft tint so text stays easy to read. Only you see this."), 4);
        View seg = S.seg(c, new String[]{"Accent", "Color", pro ? "Gradient" : "Gradient (Pro)"}, bubMode.equals("color") ? 1 : bubMode.equals("gradient") ? 2 : 0, 330, i -> {
            if (i == 2 && !pro) {
                rebuild();
                return;
            }
            bubMode = i == 0 ? "accent" : i == 1 ? "color" : "gradient";
            rebuild();
        });
        S.add(card, seg, 12);
        LinearLayout prev = W.col(c);
        prev.setBackground(W.round(T.bg, 12));
        prev.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        TextView in = W.text(c, "Hey! How are you?", 14, 400, T.text);
        in.setPadding(T.dp(12), 0, T.dp(12), 0);
        S.lh(in, 19.6f, 8, 8);
        in.setBackground(W.round(T.bubbleIn, 16));
        prev.addView(in, W.lp(W.WC, W.WC));
        TextView out = W.text(c, "Doing great, thanks 😊", 14, 400, T.text);
        out.setPadding(T.dp(12), 0, T.dp(12), 0);
        S.lh(out, 19.6f, 8, 8);
        out.setBackground(S.cssDrawable(bubCss(), 16));
        LinearLayout.LayoutParams op = W.lp(W.WC, W.WC);
        op.gravity = Gravity.END;
        op.topMargin = T.dp(6);
        prev.addView(out, op);
        S.add(card, prev, 12);
        if (!bubMode.equals("accent")) {
            S.add(card, S.muted(c, "Presets"), 12);
            S.Grid g = new S.Grid(c, 54, 8, 8);
            g.setClipChildren(false);
            for (String[] p : AH) {
                boolean solid = !p[1].startsWith("linear");
                if (bubMode.equals("color") != solid) continue;
                boolean lock = !solid && !pro;
                String[] q = parseBg(p[1]);
                boolean sel = solid ? bubColor.equalsIgnoreCase(q[0]) : bubFrom.equalsIgnoreCase(q[1]) && bubTo.equalsIgnoreCase(q[2]);
                g.addView(presetCell(c, p[0], p[1], sel, lock, v -> {
                    if (lock) return;
                    if (solid) bubColor = q[0];
                    else {
                        bubFrom = q[1];
                        bubTo = q[2];
                    }
                    rebuild();
                }));
            }
            S.add(card, g, 8);
        }
        if (bubMode.equals("color")) {
            S.Flow f = new S.Flow(c, 8, 8);
            f.setClipChildren(false);
            for (String col : JO) {
                S.Swatch sw = new S.Swatch(c, col, 32);
                sw.ringMode = 2;
                sw.selected = bubColor.equalsIgnoreCase(col);
                sw.setOnClickListener(v -> {
                    bubColor = col;
                    rebuild();
                });
                f.addView(sw);
            }
            f.addView(new S.ColorBox(c, Css.color(bubColor), 40, 36, null, col -> {
                bubColor = S.hex(col);
                rebuild();
            }));
            S.add(card, f, 16);
        } else if (bubMode.equals("gradient")) S.add(card, gradRow(c, bubFrom, bubTo, bubDeg, (f, t, d) -> {
            bubFrom = f;
            bubTo = t;
            bubDeg = d;
            out.setBackground(S.cssDrawable(bubCss(), 16));
        }), 16);
        S.Flow bt = new S.Flow(c, 0, 8);
        TextView ap = S.btn(c, "Apply", S.SECONDARY, v -> {
            JSONObject b = bubMode.equals("color") ? App.jo("mode", "color", "color", bubColor) : bubMode.equals("gradient") ? App.jo("mode", "gradient", "from", bubFrom, "to", bubTo, "angle", bubDeg) : App.jo("mode", "accent");
            saveBub(b);
        });
        W.enable(ap, !bubBusy && !(bubMode.equals("gradient") && !pro));
        bt.addView(ap);
        bt.addView(S.btn(c, "Reset", S.GHOST, v -> {
            bubMode = "accent";
            saveBub(App.jo("mode", "accent"));
        }));
        S.add(card, bt, 24);
        if (!pro) {
            TextView need = W.text(c, "Gradients are a Pro Box feature. Need one?", 13, 400, T.accent);
            need.setOnClickListener(v -> Settings.openPage(a, "pro"));
            S.add(card, need, 8);
        }
        return card;
    }

    void saveBub(JSONObject b) {
        if (bubBusy) return;
        bubBusy = true;
        try {
            Session.settings().put("dmBubble", b);
        } catch (Exception e) {
        }
        Sync.save();
        bubBusy = false;
        Ov.toast(!"accent".equals(b.optString("mode")) ? "Message color updated" : "Message color reset");
        if (alive) rebuild();
    }

    View powerCard(Context c) {
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Power saving", 500));
        String p = Prefs.power();
        int sel = p.equals("on") ? 1 : p.equals("off") ? 2 : 0;
        S.add(card, S.seg(c, new String[]{"Auto", "On", "Off"}, sel, 280, i -> {
            Prefs.setPower(i == 1 ? "on" : i == 2 ? "off" : "");
            App.H.postDelayed(() -> Settings.rerender(a, "appearance"), 260);
        }), 12);
        S.add(card, S.muted(c, sel == 1 ? "Blur effects are off and decorations, profile effects and GIF avatars stay still. Only this device is affected." : sel == 2 ? "All effects stay on, even if this device is slow. Only this device is affected." : "Turns blur effects off automatically on slower devices. Only this device is affected."), 12);
        return card;
    }
}
