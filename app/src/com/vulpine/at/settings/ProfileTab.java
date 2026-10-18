package com.vulpine.at.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Sync;
import com.vulpine.at.home.Badges;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Css;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;

public class ProfileTab extends Home.Tab {
    ScrollView sv;
    LinearLayout body;
    boolean editing;
    String draftBg;
    String bgMode, bgSolid, bgFrom, bgTo;
    int bgDeg;
    String fx;
    JSONObject fxCfg;
    int decoCount = -1;
    final Runnable fxSave = this::saveFxCfg;
    final Sync.L syncL = w -> {
        if ("me".equals(w) && !editing && v != null && v.isShown()) render();
    };

    public static ProfileTab I;

    public void scrollToDp(int y) {
        sv.scrollTo(0, T.dp(y));
    }

    public View build(Context c) {
        I = this;
        sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        body = W.col(c);
        body.setPadding(T.dp(24), 0, T.dp(24), 0);
        sv.addView(body, new FrameLayout.LayoutParams(W.MP, W.WC));
        initDrafts();
        render();
        Sync.on(syncL);
        App.bg(this::loadExtra);
        return sv;
    }

    void initDrafts() {
        JSONObject s = me();
        String pb = s.optString("profileBackground", "");
        String[] p = AppearancePage.parseBg(pb);
        bgMode = pb.startsWith("linear-gradient") ? "gradient" : "solid";
        bgSolid = p[0].isEmpty() ? "#F97316" : p[0];
        bgFrom = p[1].isEmpty() ? "#F97316" : p[1];
        bgTo = p[2].isEmpty() ? (p[1].isEmpty() ? "#8B5CF6" : p[1]) : p[2];
        bgDeg = Integer.parseInt(p[3]);
        fx = s.optString("profileBgEffect", "");
        JSONObject cf = s.optJSONObject("profileBgEffectConfig");
        fxCfg = cf == null ? new JSONObject() : cf;
    }

    void loadExtra() {
        Http.Resp m = Http.get("/me");
        Http.Resp d = Http.get("/shop/deco");
        App.ui(() -> {
            if (m.ok()) {
                Session.merge(m.obj());
                fx = m.obj().optString("profileBgEffect", "");
                JSONObject cf = m.obj().optJSONObject("profileBgEffectConfig");
                if (cf != null) fxCfg = cf;
            }
            if (d.ok()) {
                JSONObject o = d.obj().optJSONObject("owned");
                decoCount = o == null ? 0 : o.length();
            }
            if (!editing) render();
        });
    }

    static JSONObject me() {
        return Session.s == null ? new JSONObject() : Session.s;
    }

    public void onShow() {
        if (!editing && body != null) render();
    }

    public void onInsets(int top, int bottom) {
        android.view.ViewGroup.LayoutParams lp = sv.getLayoutParams();
        if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
            ((android.view.ViewGroup.MarginLayoutParams) lp).topMargin = top;
            sv.setLayoutParams(lp);
            sv.setPadding(0, T.dp(8), 0, bottom + T.dp(8));
        } else sv.setPadding(0, top + T.dp(8), 0, bottom + T.dp(8));
    }

    public void scrollTop() {
        sv.smoothScrollTo(0, 0);
    }

    void render() {
        int y = sv.getScrollY();
        body.removeAllViews();
        Context c = sv.getContext();
        View hc = header(c);
        body.addView(hc);
        if (hc instanceof FrameLayout) com.vulpine.at.deco.Deco.attachCard((FrameLayout) hc, Session.userId());
        TextView deco = S.btn(c, decoCount > 0 ? "Decorations · " + decoCount : "Decorations", S.SECONDARY, v -> openDecorations(h.a));
        S.add(body, deco, 16);
        S.add(body, bgCard(c), 16);
        S.add(body, fxCard(c), 16);
        S.add(body, fontCard(c), 16);
        sv.post(() -> sv.scrollTo(0, y));
    }

    static void openDecorations(MainActivity a) {
        try {
            Class<?> k = Class.forName("com.vulpine.at.deco.DecoSection");
            k.getMethod("open", MainActivity.class).invoke(null, a);
            return;
        } catch (Throwable e) {
        }
        if (Home.I != null) Home.I.select("shop", false);
    }

    public static Typeface font(String css) {
        if (css == null || css.isEmpty()) return null;
        String f = css.toLowerCase();
        if (f.contains("courier") || f.contains("lucida console") || f.contains("monospace")) return Typeface.MONOSPACE;
        if (f.contains("georgia") || f.contains("papyrus") || f.contains("serif\"") || f.endsWith(" serif")) return Typeface.SERIF;
        if (f.contains("comic")) return Typeface.create("casual", Typeface.NORMAL);
        if (f.contains("brush")) return Typeface.create("cursive", Typeface.NORMAL);
        if (f.contains("impact")) return Typeface.create("sans-serif-condensed", Typeface.BOLD);
        if (f.contains("trebuchet") || f.contains("verdana")) return Typeface.SANS_SERIF;
        return null;
    }

    static int fgFor(String bg) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}").matcher(bg);
        int n = 0;
        double sum = 0;
        while (m.find()) {
            int v = Integer.parseInt(m.group().substring(1), 16);
            sum += .299 * ((v >> 16) & 255) / 255.0 + .587 * ((v >> 8) & 255) / 255.0 + .114 * (v & 255) / 255.0;
            n++;
        }
        if (n == 0) return 0xffffffff;
        return sum / n > .55 ? 0xff1a1a1a : 0xffffffff;
    }

    static void face(TextView t, Typeface f, int weight) {
        if (f == null) return;
        t.setTypeface(Typeface.create(f, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL));
    }

    View header(Context c) {
        JSONObject s = me();
        String bg = draftBg != null ? draftBg : s.optString("profileBackground", "");
        int fg = bg.isEmpty() ? T.text : fgFor(bg);
        Typeface tf = font(s.optString("profileFont", ""));
        FrameLayout card = new FrameLayout(c);
        card.setBackground(bg.isEmpty() ? W.round(T.surface, 16) : S.cssDrawable(bg, 16));
        card.setClipToOutline(true);
        card.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        Fx fxl = new Fx(c, 16);
        fxl.fg = fg;
        fxl.set(fx, fxCfg);
        card.addView(fxl, new FrameLayout.LayoutParams(W.MP, W.MP));
        LinearLayout col = W.col(c);
        col.setPadding(T.dp(24), T.dp(24), T.dp(24), T.dp(24));
        LinearLayout row = new LinearLayout(c) {
            protected void onMeasure(int wm, int hm) {
                int w = MeasureSpec.getSize(wm);
                View left = getChildAt(0);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) left.getLayoutParams();
                int lw = Math.round(w * .4f) - T.dp(8);
                if (lp.width != lw) lp.width = lw;
                super.onMeasure(wm, hm);
            }
        };
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout left = new FrameLayout(c);
        left.setClipChildren(false);
        FrameLayout av = new FrameLayout(c);
        av.setClipChildren(false);
        AvatarView a = new AvatarView(c);
        a.animate = true;
        a.setLayoutParams(new FrameLayout.LayoutParams(T.dp(104), T.dp(104)));
        a.textSize(34);
        a.set(s.optString("displayName", s.optString("handle")).isEmpty() ? s.optString("handle") : s.optString("displayName"), s.optString("avatarColor", ""), s.optString("avatarUrl", null));
        av.addView(a, new FrameLayout.LayoutParams(T.dp(104), T.dp(104)));
        String st = s.optString("status", "online");
        View dot = new View(c);
        dot.setBackground(W.round(st.equals("dnd") ? T.danger : st.equals("inactive") ? 0xfff59e0b : T.success, 99, T.surface, 2));
        FrameLayout.LayoutParams dp = W.flp(T.dp(15), T.dp(15), Gravity.BOTTOM | Gravity.END);
        dp.bottomMargin = dp.rightMargin = T.dp(2);
        av.addView(dot, dp);
        TextView plus = W.text(c, "+", 14, 400, T.accentFg);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(W.round(T.accent, 99, T.surface, 2));
        av.addView(plus, W.flp(T.dp(28), T.dp(28), Gravity.BOTTOM | Gravity.END));
        av.setOnClickListener(v -> pickAvatar());
        av.setContentDescription("Change profile picture");
        left.addView(av, W.flp(T.dp(104), T.dp(104), Gravity.CENTER));
        try {
            com.vulpine.at.deco.Deco.attach(av, Session.userId(), T.dp(104), true);
        } catch (Throwable e) {
        }
        row.addView(left, W.lp(T.dp(100), T.dp(104)));
        LinearLayout right = W.col(c);
        LinearLayout nr = W.row(c);
        TextView name = W.text(c, s.optString("displayName", s.optString("handle")), 16, 700, fg);
        face(name, tf, 700);
        T.lh(name, 24);
        nr.addView(name, W.lp(W.WC, W.WC));
        if (s.optBoolean("pro")) {
            ImageView pb = new ImageView(c);
            pb.setClipToOutline(true);
            pb.setBackground(W.round(0, 4));
            pb.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            Img.anim(pb, "asset:pro-badge.gif", T.dp(32), true);
            nr.addView(pb, W.lpm(T.dp(16), T.dp(16), 4, 0, 0, 0));
        }
        for (String u : Badges.forUser(s.optString("userId"), s.optString("handle"))) {
            ImageView bi = new ImageView(c);
            bi.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Img.anim(bi, Http.abs(u), T.dp(32), true);
            nr.addView(bi, W.lpm(T.dp(16), T.dp(16), 4, 0, 0, 0));
        }
        right.addView(nr);
        TextView hs = handles(c, s, fg);
        face(hs, tf, 600);
        right.addView(hs);
        String bio = s.optString("bio", "");
        if (!bio.isEmpty()) {
            TextView b = S.text(c, bio, 14, 400, fg, 21);
            com.vulpine.at.chat.Md.bio(b, bio);
            b.setAlpha(.9f);
            face(b, tf, 400);
            S.add(right, b, 16);
        }
        String dob = s.optString("dateOfBirth", "");
        if (dob.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            LinearLayout dr = W.row(c);
            dr.setAlpha(.8f);
            dr.addView(W.img(c, Ic.get("vi.cake", 15, fg)), W.lp(T.dp(15), T.dp(15)));
            Calendar cal = Calendar.getInstance();
            cal.set(Integer.parseInt(dob.substring(0, 4)), Integer.parseInt(dob.substring(5, 7)) - 1, Integer.parseInt(dob.substring(8, 10)));
            TextView dt = S.text(c, "Born " + java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(cal.getTime()), 14, 400, fg, 21);
            face(dt, tf, 400);
            LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
            p.leftMargin = T.dp(8);
            dr.addView(dt, p);
            S.add(right, dr, 8);
        }
        if (s.optBoolean("pro")) {
            JSONObject bh = null;
            try {
                bh = new JSONObject(s.optString("businessHours", ""));
            } catch (Exception e) {
            }
            JSONArray days = bh == null ? null : bh.optJSONArray("days");
            if (days != null && days.length() > 0) {
                boolean open = openNow(bh);
                TextView pill = W.text(c, open ? "Open" : "Closed", 13, 600, 0xffffffff);
                pill.setPadding(T.dp(10), T.dp(2), T.dp(10), T.dp(2));
                pill.setBackground(W.round(open ? T.success : T.danger, 99));
                S.addW(right, pill, 4);
            }
        }
        String me = com.vulpine.core.Session.userId();
        if (!me.isEmpty()) {
            right.addView(new com.vulpine.at.people.Listen.Card(c, me, fg), W.lpm(W.MP, W.WC, 0, 16, 0, 0));
            java.util.List<String> ids = new java.util.ArrayList<>();
            ids.add(me);
            App.bg(() -> com.vulpine.at.people.Pres.loadActivity(ids));
        }
        LinearLayout.LayoutParams rp = W.lp(0, W.WC, 1);
        rp.leftMargin = T.dp(16);
        row.addView(right, rp);
        col.addView(row);
        if (editing) S.add(col, editForm(c), 16);
        else {
            TextView eb = S.btn(c, "Edit profile", S.SECONDARY, v -> {
                editing = true;
                render();
            });
            eb.setCompoundDrawablesRelativeWithIntrinsicBounds(Ic.get("profile.edit", 16, T.text), null, null, null);
            eb.setCompoundDrawablePadding(T.dp(8));
            LinearLayout.LayoutParams ep = W.lp(W.WC, T.dp(36));
            ep.gravity = Gravity.CENTER_HORIZONTAL;
            ep.topMargin = T.dp(16);
            col.addView(eb, ep);
        }
        card.addView(col, new FrameLayout.LayoutParams(W.MP, W.WC));
        return card;
    }

    static boolean openNow(JSONObject bh) {
        Calendar n = Calendar.getInstance();
        int d = n.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ? 7 : n.get(Calendar.DAY_OF_WEEK) - 1;
        JSONArray days = bh.optJSONArray("days");
        boolean has = false;
        for (int i = 0; days != null && i < days.length(); i++) if (days.optInt(i) == d) has = true;
        if (!has) return false;
        try {
            String[] o = bh.optString("open", "09:00").split(":"), cl = bh.optString("close", "17:00").split(":");
            int now = n.get(Calendar.HOUR_OF_DAY) * 60 + n.get(Calendar.MINUTE);
            return now >= Integer.parseInt(o[0]) * 60 + Integer.parseInt(o[1]) && now <= Integer.parseInt(cl[0]) * 60 + Integer.parseInt(cl[1]);
        } catch (Exception e) {
            return false;
        }
    }

    TextView handles(Context c, JSONObject s, int fg) {
        JSONArray hs = s.optJSONArray("handles");
        java.util.List<String> l = new java.util.ArrayList<>();
        for (int i = 0; hs != null && i < hs.length(); i++) l.add(hs.optString(i));
        if (l.isEmpty()) l.add(s.optString("handle"));
        SpannableStringBuilder b = new SpannableStringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i == 1) b.append(" also ");
            else if (i > 1) b.append(" and ");
            String h = l.get(i);
            int st = b.length();
            b.append("@").append(h);
            b.setSpan(new ClickableSpan() {
                public void onClick(View v) {
                    ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("handle", h));
                    Ov.toast("@" + h + " copied to clipboard");
                }

                public void updateDrawState(TextPaint ds) {
                    ds.setColor(S.accText(ds.getColor()));
                    ds.setUnderlineText(true);
                    ds.setFakeBoldText(false);
                    ds.setTypeface(T.sb);
                }
            }, st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView t = W.text(c, b, 14, 400, fg);
        T.lh(t, 21);
        t.setAlpha(.8f);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setHighlightColor(0);
        return t;
    }

    void pickAvatar() {
        MainActivity a = h.a;
        Pick.file(a, new String[]{"image/png", "image/jpeg", "image/webp", "image/gif"}, (f, name, mime) -> Crop.image(a, f, mime, 1024, (out, m) -> {
            if (out == null) return;
            Upload.run("/me/avatar", "avatar", out, m.equals("image/gif") ? "avatar.gif" : "avatar.webp", m, "Profile picture", r -> {
                if (out != f) out.delete();
                f.delete();
                if (r.ok()) {
                    Session.merge(r.obj());
                    Ov.toast("Profile picture updated");
                    render();
                    Sync.patchMe(new JSONObject(), null, null);
                } else Ov.toast(r.obj().optString("error").equals("pro_required") ? "pro_required" : S.err(r, "Upload failed"), true);
            });
        }));
    }

    View editForm(Context c) {
        JSONObject s = me();
        LinearLayout f = W.col(c);
        W.Field dn = new W.Field(c, "Display name", null);
        dn.input.setText(s.optString("displayName"));
        dn.input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        f.addView(dn);
        W.Field bio = new W.Field(c, "Bio", null);
        W.multiline(bio.input);
        bio.input.setMinLines(3);
        bio.input.setMaxLines(6);
        bio.input.setGravity(Gravity.TOP);
        bio.input.setText(s.optString("bio"));
        bio.input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
        S.add(f, bio, 16);
        W.Field dob = new W.Field(c, "Date of birth", null);
        String[] dv = {s.optString("dateOfBirth", "")};
        dob.input.setText(dv[0]);
        dob.input.setFocusable(false);
        dob.input.setClickable(true);
        dob.input.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (dv[0].matches("^\\d{4}-\\d{2}-\\d{2}$")) cal.set(Integer.parseInt(dv[0].substring(0, 4)), Integer.parseInt(dv[0].substring(5, 7)) - 1, Integer.parseInt(dv[0].substring(8, 10)));
            android.app.DatePickerDialog d = new android.app.DatePickerDialog(h.a, (dp, yy, mm, dd) -> {
                dv[0] = String.format(java.util.Locale.US, "%04d-%02d-%02d", yy, mm + 1, dd);
                dob.input.setText(dv[0]);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            Calendar min = Calendar.getInstance();
            min.set(1902, 0, 1);
            d.getDatePicker().setMinDate(min.getTimeInMillis());
            d.getDatePicker().setMaxDate(System.currentTimeMillis());
            d.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "Clear", (x, w) -> {
                dv[0] = "";
                dob.input.setText("");
            });
            d.show();
        });
        S.add(f, dob, 16);
        LinearLayout ac = W.col(c);
        TextView al = W.text(c, "Avatar color", 13, 500, T.textMuted);
        ac.addView(al);
        String[] col = {s.optString("avatarColor", "")};
        S.Flow sw = new S.Flow(c, 8, 8);
        Runnable[] paint = new Runnable[1];
        S.Swatch none = new S.Swatch(c, null, 32);
        none.dashed = true;
        none.setContentDescription("None (default)");
        none.setOnClickListener(v -> {
            col[0] = "";
            paint[0].run();
        });
        sw.addView(none);
        for (String x : AppearancePage.JO) {
            S.Swatch k = new S.Swatch(c, x, 32);
            k.setOnClickListener(v -> {
                col[0] = x;
                paint[0].run();
            });
            sw.addView(k);
        }
        paint[0] = () -> {
            for (int i = 0; i < sw.getChildCount(); i++) {
                S.Swatch k = (S.Swatch) sw.getChildAt(i);
                k.selected = i == 0 ? col[0].isEmpty() : x(k).equalsIgnoreCase(col[0]);
                k.invalidate();
            }
        };
        paint[0].run();
        S.add(ac, sw, 8);
        S.add(f, ac, 16);
        LinearLayout br = W.row(c);
        br.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        br.addView(S.btn(c, "Cancel", S.GHOST, v -> {
            editing = false;
            W.hideKb(v);
            render();
        }));
        TextView save = S.btn(c, "Save", S.PRIMARY, null);
        save.setOnClickListener(v -> {
            String n = dn.value().trim();
            if (n.isEmpty()) {
                Ov.toast("Display name can't be empty", true);
                return;
            }
            S.busy(save, true, "Save", "Saving…");
            JSONObject p = App.jo("displayName", n, "bio", bio.value().trim(), "dateOfBirth", dv[0]);
            if (col[0].matches("^#?[0-9a-fA-F]{6}$")) {
                try {
                    p.put("avatarColor", col[0]);
                } catch (Exception e) {
                }
            }
            Http.Resp[] out = new Http.Resp[1];
            Sync.patchMe(p, out, () -> {
                S.busy(save, false, "Save", "Saving…");
                if (out[0].ok()) {
                    editing = false;
                    W.hideKb(save);
                    Ov.toast("Profile saved");
                    render();
                } else Ov.toast(S.err(out[0], "Save failed"), true);
            });
        });
        LinearLayout.LayoutParams sp = W.lp(W.WC, W.WC);
        sp.leftMargin = T.dp(8);
        br.addView(save, sp);
        S.add(f, br, 16);
        return f;
    }

    static String x(S.Swatch k) {
        return k.css == null ? "" : k.css;
    }

    String bgCss() {
        return bgMode.equals("solid") ? bgSolid : "linear-gradient(" + bgDeg + "deg, " + bgFrom + ", " + bgTo + ")";
    }

    void preview() {
        draftBg = bgCss();
        render();
    }

    View bgCard(Context c) {
        LinearLayout card = S.card(c);
        boolean pro = S.pro();
        card.addView(S.label(c, "Profile background", 600));
        S.add(card, S.seg(c, new String[]{"Solid", pro ? "Gradient" : "Gradient (Pro)"}, bgMode.equals("gradient") ? 1 : 0, 240, i -> {
            bgMode = i == 1 ? "gradient" : "solid";
            preview();
        }), 12);
        FrameLayout pv = new FrameLayout(c);
        pv.setBackground(S.cssDrawable(bgCss(), 12));
        Fx pf = new Fx(c, 12);
        pf.set(fx, fxCfg);
        pv.addView(pf, new FrameLayout.LayoutParams(W.MP, W.MP));
        S.add(card, pv, 12).getLayoutParams().height = T.dp(60);
        S.add(card, S.muted(c, "Presets"), 12);
        S.Grid g = new S.Grid(c, 54, 8, 8);
        g.setClipChildren(false);
        String css = bgCss();
        for (String[] p : AppearancePage.AH) {
            boolean solid = !p[1].startsWith("linear");
            boolean lock = !solid && !pro;
            g.addView(AppearancePage.presetCell(c, p[0], p[1], css.equals(p[1]), lock, v -> {
                if (solid) {
                    bgMode = "solid";
                    bgSolid = p[1];
                } else {
                    bgMode = "gradient";
                    String[] q = AppearancePage.parseBg(p[1]);
                    bgFrom = q[1];
                    bgTo = q[2];
                }
                preview();
            }));
        }
        g.setClipChildren(false);
        S.add(card, g, 8);
        if (bgMode.equals("solid")) {
            S.Flow f = new S.Flow(c, 8, 8);
            for (String col : AppearancePage.JO) {
                S.Swatch sw = new S.Swatch(c, col, 32);
                sw.ringMode = 1;
                sw.setOnClickListener(v -> {
                    bgSolid = col;
                    preview();
                });
                f.addView(sw);
            }
            S.add(card, f, 16);
        } else {
            LinearLayout r = W.row(c);
            r.addView(new S.ColorBox(c, Css.color(bgFrom), 40, 36, null, col -> {
                bgFrom = S.hex(col);
                preview();
            }));
            LinearLayout.LayoutParams p2 = W.lp(W.WC, W.WC);
            p2.leftMargin = T.dp(12);
            r.addView(new S.ColorBox(c, Css.color(bgTo), 40, 36, null, col -> {
                bgTo = S.hex(col);
                preview();
            }), p2);
            TextView lab = W.text(c, bgDeg + "°", 13, 400, T.textMuted);
            lab.setMinWidth(T.dp(32));
            S.Slider sl = new S.Slider(c, 0, 360, 1, bgDeg, v -> {
                bgDeg = Math.round(v);
                lab.setText(bgDeg + "°");
                pv.setBackground(S.cssDrawable(bgCss(), 12));
            }, v -> preview());
            LinearLayout.LayoutParams sp = W.lp(0, T.dp(28), 1);
            sp.leftMargin = T.dp(12);
            r.addView(sl, sp);
            LinearLayout.LayoutParams lp = W.lp(W.WC, W.WC);
            lp.leftMargin = T.dp(12);
            r.addView(lab, lp);
            S.add(card, r, 16);
        }
        boolean lockApply = !pro && bgMode.equals("gradient");
        S.Flow bt = new S.Flow(c, 0, 0);
        TextView ap = S.btn(c, lockApply ? "Apply background (Pro)" : "Apply background", S.SECONDARY, v -> applyBg(bgCss()));
        W.enable(ap, !lockApply);
        bt.addView(ap);
        bt.addView(S.btn(c, "Reset", S.GHOST, v -> applyBg("")));
        S.add(card, bt, 12);
        if (lockApply) {
            TextView need = W.text(c, "You need to be a Pro Box user to use that. Need one?", 14, 600, T.accent);
            need.setPaintFlags(need.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            need.setOnClickListener(v -> Settings.openPage(h.a, "pro"));
            S.add(card, need, 8);
        }
        return card;
    }

    void applyBg(String css) {
        Http.Resp[] out = new Http.Resp[1];
        Sync.patchMe(App.jo("profileBackground", css), out, () -> {
            if (out[0].ok()) {
                Ov.toast(css.isEmpty() ? "Background reset" : "Background updated");
                draftBg = null;
                if (css.isEmpty()) {
                    bgMode = "solid";
                    bgSolid = "#F97316";
                }
                render();
            } else Ov.toast(S.err(out[0], css.isEmpty() ? "Reset failed" : "Update failed"), true);
        });
    }

    static final String[][] FXL = {{"", "None"}, {"pixels", "Pixels"}, {"ascii", "ASCII art"}, {"icons", "Icon pattern"}};
    int iconCat;

    View fxCard(Context c) {
        LinearLayout card = S.card(c);
        boolean pro = S.pro();
        boolean act = !fx.isEmpty() && pro;
        String bg = draftBg != null ? draftBg : me().optString("profileBackground", "");
        String tileBg = bg.isEmpty() ? bgCss() : bg;
        S.Grid g = new S.Grid(c, 74, 8, 8);
        g.setClipChildren(false);
        for (String[] e : FXL) {
            boolean sel = fx.equals(e[0]);
            boolean locked = !pro && !e[0].isEmpty();
            LinearLayout cell = W.col(c);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClipChildren(false);
            FrameLayout tile = new FrameLayout(c) {
                final android.graphics.Paint rp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

                protected void dispatchDraw(android.graphics.Canvas cv) {
                    super.dispatchDraw(cv);
                }
            };
            tile.setBackground(S.cssDrawable(tileBg, 12));
            tile.setClipToOutline(true);
            tile.setOutlineProvider(new android.view.ViewOutlineProvider() {
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), T.dpf(12));
                }
            });
            Fx l = new Fx(c, 12);
            l.set(e[0], fxCfg);
            tile.addView(l, new FrameLayout.LayoutParams(W.MP, W.MP));
            if (locked) {
                ImageView lk = W.img(c, com.vulpine.at.ui.Svg.stroke(12, 0xffffffff, 2, "M7 11V7a5 5 0 0 1 10 0v4").rect(3, 11, 18, 11, 2, false));
                FrameLayout.LayoutParams lp = W.flp(T.dp(12), T.dp(12), Gravity.BOTTOM | Gravity.END);
                lp.rightMargin = lp.bottomMargin = T.dp(3);
                tile.addView(lk, lp);
            }
            FrameLayout ring = new FrameLayout(c);
            ring.setClipChildren(false);
            ring.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
            if (sel) ring.setBackground(W.round(0, 16, T.accent, 2));
            ring.addView(tile, new FrameLayout.LayoutParams(W.MP, T.dp(34)));
            LinearLayout.LayoutParams rp = W.lp(W.MP, T.dp(42));
            cell.addView(ring, rp);
            TextView n = W.text(c, e[1], 10, 400, T.textSecondary);
            n.setSingleLine(true);
            cell.addView(n, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
            cell.setOnClickListener(v -> pickFx(e[0]));
            g.addView(cell);
        }
        card.addView(g);
        card.setClipChildren(false);
        if (fx.equals("icons")) S.add(card, iconPick(c, act), 8);
        LinearLayout rows = W.col(c);
        String c1 = cfgCol("color"), c2 = cfgCol("color2");
        rows.addView(colorRow(c, "Color", c1.isEmpty(), "Auto", false, c1, act, v -> cfg(v.isEmpty() ? App.jo("color", "", "color2", "") : App.jo("color", v))));
        if (fx.equals("icons")) S.add(rows, colorRow(c, "Gradient", c2.isEmpty(), "Off", true, c2, act, v -> cfg(v.isEmpty() ? App.jo("color2", "") : App.jo("color", c1.isEmpty() ? "#ffffff" : c1, "color2", v))), 8);
        if (!fx.equals("icons")) {
            double sp0 = fxCfg.optDouble("speed", 1);
            float spd = sp0 >= .25 && sp0 <= 4 ? (float) sp0 : 1f;
            TextView sv0 = W.text(c, fmtSpeed(spd), 13, 400, T.textSecondary);
            S.add(rows, sliderRow(c, "Speed", .25f, 3, .25f, spd, sv0, act, v -> {
                sv0.setText(fmtSpeed(v));
                cfg(App.jo("speed", (double) v));
            }), 8);
        }
        double op0 = fxCfg.optDouble("opacity", 1);
        float opa = op0 >= .05 && op0 <= 1 ? (float) op0 : 1f;
        TextView ov = W.text(c, Math.round(opa * 100) + "%", 13, 400, T.textSecondary);
        S.add(rows, sliderRow(c, "Opacity", .1f, 1, .05f, opa, ov, act, v -> {
            ov.setText(Math.round(v * 100) + "%");
            cfg(App.jo("opacity", (double) v));
        }), 8);
        rows.setAlpha(act ? 1f : .5f);
        S.add(card, rows, 8);
        return card;
    }

    static String fmtSpeed(float v) {
        String s = String.format(java.util.Locale.US, "%.2f", v).replaceAll("\\.?0+$", "");
        return s + "×";
    }

    String cfgCol(String k) {
        String v = fxCfg.optString(k, "");
        if (v.matches("^#?[0-9a-fA-F]{6}$")) return v.startsWith("#") ? v : "#" + v;
        return "";
    }

    View colorRow(Context c, String label, boolean autoSel, String autoLabel, boolean grad, String cur, boolean act, S.Str cb) {
        S.Flow f = new S.Flow(c, 8, 8);
        TextView l = W.text(c, label, 13, 400, T.textSecondary);
        l.setMinWidth(T.dp(52));
        f.addView(l);
        TextView au = W.text(c, autoLabel, 10, 400, T.text);
        au.setPadding(T.dp(8), T.dp(2), T.dp(8), T.dp(2));
        au.setBackground(autoSel ? W.round(T.surface2, 99, T.accent, 2) : W.round(T.surface2, 99));
        au.setOnClickListener(v -> {
            if (act) cb.on("");
        });
        f.addView(au);
        String c1 = cfgCol("color");
        for (String p : AppearancePage.ICPRE) {
            S.Swatch sw = new S.Swatch(c, grad ? "linear-gradient(135deg," + (c1.isEmpty() ? "#ffffff" : c1) + "," + p + ")" : p, 20);
            sw.ringMode = 1;
            sw.selected = p.equalsIgnoreCase(cur);
            sw.setOnClickListener(v -> {
                if (act) cb.on(p);
            });
            f.addView(sw);
        }
        S.ColorBox cbx = new S.ColorBox(c, Css.color(cur.isEmpty() ? "#8c8ca8" : cur), 28, 22, null, col -> {
            if (act) cb.on(S.hex(col));
        });
        cbx.setEnabled(act);
        f.addView(cbx);
        if (!act) AppearancePage.disableTree(f);
        return f;
    }

    View sliderRow(Context c, String label, float min, float max, float step, float val, TextView out, boolean act, S.Num cb) {
        LinearLayout r = W.row(c);
        TextView l = W.text(c, label, 13, 400, T.textSecondary);
        l.setMinWidth(T.dp(52));
        r.addView(l);
        S.Slider s = new S.Slider(c, min, max, step, val, v -> {
            if (act) cb.on(v);
        }, null);
        s.setEnabled(act);
        LinearLayout.LayoutParams sp = W.lp(0, T.dp(20), 1);
        sp.leftMargin = T.dp(8);
        r.addView(s, sp);
        out.setMinWidth(T.dp(36));
        out.setGravity(Gravity.END);
        LinearLayout.LayoutParams op = W.lp(W.WC, W.WC);
        op.leftMargin = T.dp(8);
        r.addView(out, op);
        return r;
    }

    View iconPick(Context c, boolean act) {
        LinearLayout box = W.col(c);
        String[] cats = icats();
        S.Flow pills = new S.Flow(c, 4, 4);
        for (int i = 0; i < cats.length; i++) {
            final int k = i;
            String nm = cats[i].split(":")[0];
            boolean on = i == iconCat;
            TextView p = W.text(c, nm, 13, on ? 600 : 500, on ? T.accent : T.textSecondary);
            p.setPadding(T.dp(10), T.dp(3), T.dp(10), T.dp(3));
            T.lh(p, 19.5f);
            p.setPadding(T.dp(10), T.dp(3) + p.getPaddingTop(), T.dp(10), T.dp(3) + p.getPaddingBottom());
            p.setBackground(on ? W.round(T.accentSoft, 99) : null);
            p.setOnClickListener(v -> {
                iconCat = k;
                render();
            });
            pills.addView(p);
        }
        box.addView(pills);
        if (cats.length > 0) {
            String cur = fxCfg.optString("icon", "");
            S.Grid g = new S.Grid(c, 40, 4, 4);
            g.setClipChildren(false);
            for (String id : cats[Math.min(iconCat, cats.length - 1)].split(":")[1].split(",")) {
                boolean sel = cur.equals(id);
                FrameLayout b = new FrameLayout(c);
                b.setBackground(sel ? W.round(T.accentSoft, 12, T.accent, 2) : W.ripple(new android.graphics.drawable.ColorDrawable(0), 12));
                b.addView(W.img(c, Ic.get("vi." + id, 20, sel ? T.accent : T.text)), W.flp(T.dp(20), T.dp(20), Gravity.CENTER));
                b.setMinimumHeight(T.dp(40));
                b.setContentDescription(id);
                b.setOnClickListener(v -> {
                    if (act) cfg(App.jo("icon", id));
                });
                g.addView(b);
            }
            S.add(box, g, 8);
        }
        return box;
    }

    static String[] cats;

    static synchronized String[] icats() {
        if (cats != null) return cats;
        try (java.io.InputStream in = App.ctx.getAssets().open("icons-settings.json")) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            cats = new JSONObject(new String(bo.toByteArray(), "UTF-8")).optString("vi.cats").split("\\|");
        } catch (Exception e) {
            cats = new String[0];
        }
        return cats;
    }

    void pickFx(String id) {
        if (!S.pro() && !id.isEmpty()) {
            Ov.toast("Dynamic background effects are a Pro Box feature");
            return;
        }
        String was = me().optString("profileBgEffect", "");
        fx = id;
        render();
        if (was.equals(id)) return;
        JSONObject p = App.jo("profileBgEffect", id);
        if (!id.isEmpty() && fxCfg.length() > 0) {
            try {
                p.put("profileBgEffectConfig", fxCfg);
            } catch (Exception e) {
            }
        }
        Http.Resp[] out = new Http.Resp[1];
        String label = "";
        for (String[] e : FXL) if (e[0].equals(id)) label = e[1];
        String lb = label;
        Sync.patchMe(p, out, () -> {
            if (out[0].ok()) Ov.toast(id.isEmpty() ? "Profile effect removed" : "Profile effect: " + lb);
            else Ov.toast(S.err(out[0], "Could not save effect"), true);
        });
    }

    void cfg(JSONObject patch) {
        if (!S.pro()) {
            Ov.toast("Dynamic background effects are a Pro Box feature");
            return;
        }
        if (fx.isEmpty()) return;
        try {
            JSONObject n = new JSONObject(fxCfg.toString());
            java.util.Iterator<String> it = patch.keys();
            while (it.hasNext()) {
                String k = it.next();
                n.put(k, patch.get(k));
            }
            fxCfg = n;
        } catch (Exception e) {
        }
        boolean slider = patch.has("speed") || patch.has("opacity");
        if (!slider) render();
        else refreshFx();
        App.H.removeCallbacks(fxSave);
        App.H.postDelayed(fxSave, 600);
    }

    void refreshFx() {
        refreshFx(body);
    }

    void refreshFx(View v) {
        if (v instanceof Fx) {
            Fx f = (Fx) v;
            f.set(f.fx, fxCfg);
        } else if (v instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) refreshFx(((android.view.ViewGroup) v).getChildAt(i));
    }

    void saveFxCfg() {
        JSONObject n = fxCfg;
        Sync.patchMe(App.jo("profileBgEffectConfig", n), null, null);
    }

    static final String[][] FONTS = {{"", "Default"}, {"\"Comic Sans MS\", \"Comic Sans\", cursive", "Comic Sans"}, {"\"Papyrus\", fantasy", "Papyrus"}, {"\"Courier New\", monospace", "Courier New"}, {"\"Georgia\", serif", "Georgia"}, {"\"Impact\", sans-serif", "Impact"}, {"\"Brush Script MT\", cursive", "Brush Script"}, {"\"Lucida Console\", monospace", "Lucida Console"}, {"\"Trebuchet MS\", sans-serif", "Trebuchet MS"}, {"\"Verdana\", sans-serif", "Verdana"}};

    View fontCard(Context c) {
        LinearLayout card = S.card(c);
        boolean pro = S.pro();
        String cur = me().optString("profileFont", "");
        S.Grid g = new S.Grid(c, 92, 8, 8);
        g.setClipChildren(false);
        for (String[] f : FONTS) {
            boolean sel = cur.equals(f[0]), locked = !pro && !f[0].isEmpty();
            LinearLayout cell = W.col(c);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClipChildren(false);
            FrameLayout ring = new FrameLayout(c);
            ring.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
            if (sel) ring.setBackground(W.round(0, 16, T.accent, 2));
            FrameLayout tile = new FrameLayout(c);
            tile.setBackground(W.round(T.surface2, 12));
            TextView aa = W.text(c, "Aa", 18, 400, T.text);
            Typeface tf = font(f[0]);
            if (tf != null) aa.setTypeface(tf);
            tile.addView(aa, W.flp(W.WC, W.WC, Gravity.CENTER));
            if (locked) {
                ImageView lk = W.img(c, com.vulpine.at.ui.Svg.stroke(12, T.textSecondary, 2, "M7 11V7a5 5 0 0 1 10 0v4").rect(3, 11, 18, 11, 2, false));
                FrameLayout.LayoutParams lp = W.flp(T.dp(12), T.dp(12), Gravity.BOTTOM | Gravity.END);
                lp.rightMargin = lp.bottomMargin = T.dp(4);
                tile.addView(lk, lp);
            }
            ring.addView(tile, new FrameLayout.LayoutParams(W.MP, T.dp(44)));
            LinearLayout.LayoutParams rp = W.lp(W.MP, T.dp(52));
            cell.addView(ring, rp);
            TextView n = W.text(c, f[1], 10, 400, T.textSecondary);
            n.setSingleLine(true);
            cell.addView(n, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
            cell.setOnClickListener(v -> pickFont(f[0], f[1]));
            g.addView(cell);
        }
        card.addView(g);
        card.setClipChildren(false);
        return card;
    }

    void pickFont(String id, String nm) {
        if (!S.pro() && !id.isEmpty()) {
            Ov.toast("Custom profile fonts are a Pro Box feature");
            return;
        }
        Http.Resp[] out = new Http.Resp[1];
        Sync.patchMe(App.jo("profileFont", id), out, () -> {
            if (out[0].ok()) {
                Ov.toast(nm.equals("Default") ? "Profile font reset" : "Profile font: " + nm);
                render();
            } else Ov.toast(S.err(out[0], "Could not update font"), true);
        });
    }

    public void onHide() {
        draftBg = null;
    }
}
