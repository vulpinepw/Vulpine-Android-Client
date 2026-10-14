package com.vulpine.at.pro;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Sync;
import com.vulpine.at.settings.S;
import com.vulpine.at.settings.SetPage;
import com.vulpine.at.shop.Flow;
import com.vulpine.at.shop.K;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class ProBox extends SetPage {
    static final String[][] PERKS = {{"Larger uploads", "Up to 100MB files & videos (30MB free)."}, {"Auto away message", "Automatically reply when you are offline."}, {"Business hours", "Show your availability on your profile."}, {"Extra @handles", "Up to three handles (one free)."}, {"Custom & gradient backgrounds", "Image and gradient chat and profile backgrounds."}, {"Pro badge", "A Pro badge on your profile."}, {"Longer messages", "Up to 4000 characters per message (2000 free)."}, {"Custom accent color", "Personalize your message bubbles with a custom color or gradient."}, {"Fast actions", "Use shortcuts like /clear to delete all your messages at once, and more."}, {"Custom ringtone & sounds", "Upload your own .mp3 ringtone and notification sounds."}, {"Custom profile font", "Pick from curated fonts for your profile."}, {"GIF profile picture", "Use an animated GIF as your profile picture."}, {"Custom message background", "Your group messages use your profile background color."}, {"Fake PIN (duress)", "Set a decoy PIN that opens a fake account if you are ever forced to unlock."}, {"Dynamic profile effects", "Animated waves, blinks, pixel and ASCII art effects, plus fixed or moving textures on your profile card. Tune their color, speed and opacity."}};
    static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    LinearLayout proCardHost;
    String cardKey;
    boolean lastPro;
    final Rt.Listener ws = m -> {
        if ("pro.updated".equals(m.optString("type"))) App.ui(() -> {
            Pro.cache = null;
            Ov.toast("Pro Box updated 🎉");
            App.bg(() -> Sync.refreshMe(false));
            refreshCard(true);
        });
    };
    final Sync.L syncL = w -> {
        if ("me".equals(w) && alive) {
            boolean pro = Session.s != null && Session.s.optBoolean("pro");
            if (pro != lastPro) rebuild();
        }
    };

    public ProBox() {
        super("pro", "Pro Box");
    }

    public static void open(MainActivity a) {
        if (a != null) a.push(new ProBox());
    }

    @Override
    protected void content(LinearLayout body) {
        Context c = a;
        Rt.I.on(ws);
        Sync.on(syncL);
        boolean pro = Session.s != null && Session.s.optBoolean("pro");
        lastPro = pro;
        LinearLayout hero = W.col(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xffFF6B9D, 0xffFFC371});
        g.setCornerRadius(T.dpf(16));
        GradientDrawable o = new GradientDrawable();
        o.setColor(0x57140a19);
        o.setCornerRadius(T.dpf(16));
        hero.setBackground(new LayerDrawable(new Drawable[]{g, o}));
        hero.setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(20));
        LinearLayout head = W.row(c);
        ImageView badge = new ImageView(c);
        badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Img.anim(badge, "asset:pro-badge.gif", T.dp(40), true);
        head.addView(badge, W.lp(T.dp(20), T.dp(20)));
        TextView ht = K.text(c, "Pro Box", K.MD, 600, 0xffffffff);
        head.addView(ht, W.lpm(0, W.WC, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) ht.getLayoutParams()).weight = 1;
        if (pro) head.addView(K.text(c, "ACTIVE", K.XS, 600, 0xffffffff));
        hero.addView(head, W.lp(W.MP, W.WC));
        TextView intro = K.text(c, "Unlock the full Vulpine experience. Pro Box works across all your devices.", K.XS, 400, 0xffffffff);
        hero.addView(intro, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        LinearLayout perks = W.col(c);
        for (int i = 0; i < PERKS.length; i++) {
            LinearLayout r = W.row(c);
            r.setGravity(Gravity.TOP);
            ImageView ck = new ImageView(c);
            ck.setImageDrawable(Svg.stroke(14, 0xffffffff, 2, "M20 6L9 17l-5-5"));
            LinearLayout.LayoutParams cp = W.lp(T.dp(14), T.dp(14));
            cp.topMargin = T.dp(2);
            r.addView(ck, cp);
            LinearLayout tx = W.col(c);
            tx.addView(K.text(c, PERKS[i][0], K.SM, 500, 0xffffffff));
            tx.addView(K.text(c, PERKS[i][1], K.XS, 400, 0xffffffff));
            r.addView(tx, W.lpm(0, W.WC, 8, 0, 0, 0));
            ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
            perks.addView(r, W.lpm(W.MP, W.WC, 0, i == 0 ? 0 : 8, 0, 0));
        }
        hero.addView(perks, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        proCardHost = W.col(c);
        hero.addView(proCardHost, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        if (pro) {
            TextView de = K.btn(c, "Deactivate Pro Box", "ghost", v -> setActive(false));
            de.setTextColor(0xffffffff);
            hero.addView(de, W.lpm(W.MP, W.WC, 0, 0, 0, 0));
        }
        S.add(body, hero, 16);
        cardKey = null;
        refreshCard(false);
        if (pro) {
            S.add(body, hours(c), 16);
            S.add(body, away(c), 16);
            S.add(body, com.vulpine.at.settings.HandlesCard.build(c), 16);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Rt.I.off(ws);
        Sync.off(syncL);
    }

    void setActive(boolean on) {
        App.bg(() -> {
            Http.Resp r = Http.post("/me/pro", App.jo("active", on));
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast(on ? (S.err(r, "Could not resume")) : S.err(r, "Could not deactivate"), true);
                    return;
                }
                try {
                    Session.s.put("pro", r.obj().optBoolean("pro"));
                    Session.save();
                } catch (Exception e) {
                }
                Pro.cache = null;
                if (!on) Ov.toast("Pro Box deactivated");
                else Ov.toast("Pro Box resumed");
                App.bg(() -> Sync.refreshMe(false));
                rebuild();
            });
        });
    }

    static String fmtDate(long ms) {
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(ms));
    }

    static String fmtDur(long ms) {
        long d = Math.max(0, (long) Math.ceil((ms - System.currentTimeMillis()) / 86400000.0));
        return d + " day" + (d == 1 ? "" : "s") + " left";
    }

    void refreshCard(boolean force) {
        Pro.status(force, d -> {
            if (!alive || d == null || proCardHost == null) return;
            String key = d.optBoolean("active") + "|" + d.optBoolean("pro") + "|" + d.opt("expiresAt") + "|" + d.optBoolean("lifetime") + "|" + (d.optJSONObject("pending") != null);
            if (key.equals(cardKey)) return;
            cardKey = key;
            proCardHost.removeAllViews();
            proCardHost.addView(proCard(a, d), W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        });
    }

    View proCard(Context c, JSONObject d) {
        LinearLayout card = W.col(c);
        card.setBackground(W.round(T.surface2, 12));
        card.setPadding(T.dp(16), T.dp(12), T.dp(16), T.dp(12));
        int fg = 0xffffffff;
        Flow row = new Flow(c, T.dp(12), T.dp(8));
        LinearLayout left = W.col(c);
        long exp = d.optLong("expiresAt", 0);
        boolean hasExp = !d.isNull("expiresAt") && exp != 0;
        boolean active = d.optBoolean("active");
        String btn;
        if (d.optBoolean("lifetime")) {
            left.addView(K.text(c, "Lifetime Pro", K.SM, 600, fg));
            left.addView(K.text(c, "Early supporter - yours forever, no renewal needed.", K.XS, 400, T.textSecondary));
            btn = "Gift months?";
        } else if (active && hasExp) {
            left.addView(K.text(c, "Active until " + fmtDate(exp), K.SM, 600, fg));
            left.addView(K.text(c, fmtDur(exp), K.XS, 400, T.textSecondary));
            btn = "Renew / Extend";
        } else if (!active && hasExp && exp > System.currentTimeMillis()) {
            left.addView(K.text(c, "Paused · " + fmtDur(exp), K.SM, 600, fg));
            left.addView(K.text(c, "Resume anytime - your paid time keeps counting down.", K.XS, 400, T.textSecondary));
            btn = "Renew / Extend";
        } else {
            left.addView(K.text(c, K.usd(d.optDouble("pricePerMonth", 3.99)) + "/month", K.SM, 600, fg));
            btn = hasExp ? "Renew / Extend" : "Get Pro Box";
        }
        row.addView(left);
        View sp = new View(c);
        Flow.LP sl = new Flow.LP(0, 1);
        sl.grow = true;
        row.addView(sp, sl);
        row.addView(vproBtn(c, btn, false, v -> ProBuy.open(a)));
        card.addView(row, W.lp(W.MP, W.WC));
        if (!active && hasExp && exp > System.currentTimeMillis()) {
            card.addView(vproBtn(c, "Resume Pro Box", true, v -> setActive(true)), W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        }
        return card;
    }

    static TextView vproBtn(Context c, String label, boolean ghost, View.OnClickListener on) {
        TextView t = W.text(c, label, K.SM, 600, ghost ? T.text : T.accentFg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(T.dp(16), T.dp(8), T.dp(16), T.dp(8));
        T.lh(t, 21);
        t.setPadding(T.dp(16), t.getPaddingTop() + T.dp(8), T.dp(16), t.getPaddingBottom() + T.dp(8));
        t.setBackground(W.ripple(ghost ? W.round(T.surface3, 12) : W.accentBg(12), 12));
        t.setOnClickListener(on);
        K.pressBtn(t);
        return t;
    }

    static JSONObject parseHours(String s) {
        try {
            if (s != null && !s.isEmpty()) return new JSONObject(s);
        } catch (Exception e) {
        }
        return App.jo("open", "09:00", "close", "17:00", "days", new JSONArray());
    }

    View timeBox(Context c, String val, S.Str on) {
        TextView t = K.text(c, val, K.SM, 400, T.text);
        t.setBackground(W.ripple(W.round(T.surface2, 8), 8));
        t.setPadding(T.dp(8), T.dp(6), T.dp(8), T.dp(6));
        t.setOnClickListener(v -> {
            String[] p = t.getText().toString().split(":");
            int hh = 9, mm = 0;
            try {
                hh = Integer.parseInt(p[0]);
                mm = Integer.parseInt(p[1]);
            } catch (Exception e) {
            }
            new android.app.TimePickerDialog(a, (tp, h, m) -> {
                String s = String.format(Locale.US, "%02d:%02d", h, m);
                t.setText(s);
                on.on(s);
            }, hh, mm, true).show();
        });
        return t;
    }

    View hours(Context c) {
        JSONObject st = parseHours(Session.s.optString("businessHours", ""));
        String[] open = {st.optString("open", "09:00")}, close = {st.optString("close", "17:00")};
        java.util.TreeSet<Integer> days = new java.util.TreeSet<>();
        JSONArray da = st.optJSONArray("days");
        for (int i = 0; da != null && i < da.length(); i++) days.add(da.optInt(i));
        LinearLayout card = S.card(c);
        card.addView(K.text(c, "Business hours", K.SM, 600, T.text));
        LinearLayout r = W.row(c);
        r.addView(K.text(c, "Open", K.XS, 400, T.textMuted));
        r.addView(timeBox(c, open[0], s -> open[0] = s), W.lpm(W.WC, W.WC, 12, 0, 0, 0));
        r.addView(K.text(c, "Close", K.XS, 400, T.textMuted), W.lpm(W.WC, W.WC, 12, 0, 0, 0));
        r.addView(timeBox(c, close[0], s -> close[0] = s), W.lpm(W.WC, W.WC, 12, 0, 0, 0));
        card.addView(r, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        card.addView(K.text(c, "Open days", K.XS, 400, T.textMuted), W.lpm(W.MP, W.WC, 0, 12, 0, 8));
        Flow f = new Flow(c, T.dp(4), T.dp(4));
        for (int i = 0; i < 7; i++) {
            int dn = i + 1;
            TextView b = W.text(c, DAYS[i], K.XS, 400, T.text);
            b.setGravity(Gravity.CENTER);
            b.setMinWidth(T.dp(44));
            b.setPadding(T.dp(8), 0, T.dp(8), 0);
            Runnable paint = () -> {
                boolean on = days.contains(dn);
                b.setBackground(W.round(on ? T.accent : T.surface2, 12));
                b.setTextColor(on ? T.accentFg : T.text);
                b.setTypeface(on ? T.sb : T.r);
                b.setAlpha(on ? 1 : .7f);
            };
            paint.run();
            b.setOnClickListener(v -> {
                if (days.contains(dn)) days.remove(dn);
                else days.add(dn);
                paint.run();
            });
            f.addView(b, new Flow.LP(W.WC, T.dp(32)));
        }
        card.addView(f, W.lp(W.MP, W.WC));
        TextView save = S.btn(c, "Save hours", S.SECONDARY, v -> {
            String val = "";
            if (!days.isEmpty()) {
                JSONArray a2 = new JSONArray();
                for (int x : days) a2.put(x);
                val = App.jo("open", open[0], "close", close[0], "days", a2).toString();
            }
            patch(App.jo("businessHours", val), "Business hours saved");
        });
        S.addW(card, save, 12);
        return card;
    }

    void patch(JSONObject f, String ok) {
        Http.Resp[] out = new Http.Resp[1];
        Sync.patchMe(f, out, () -> {
            if (out[0] != null && out[0].ok()) Ov.toast(ok);
            else Ov.toast(S.err(out[0], "Save failed"), true);
        });
    }

    View away(Context c) {
        LinearLayout card = S.card(c);
        card.addView(K.text(c, "Away message", K.SM, 600, T.text));
        W.Field f = new W.Field(c, "Away message", null);
        W.multiline(f.input);
        f.input.setMinLines(2);
        f.input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        f.input.setText(Session.s.optString("awayMessage", ""));
        card.addView(f, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        card.addView(K.text(c, "When to auto-reply", K.XS, 400, T.textMuted), W.lpm(W.MP, W.WC, 0, 12, 0, 8));
        String[] modes = {"", "disconnected", "away"};
        String cur = Session.s.optString("awayMode", "");
        int sel = Math.max(0, java.util.Arrays.asList(modes).indexOf(cur));
        int[] m = {sel};
        W.Segmented seg = new W.Segmented(c, new String[]{"Off", "Disconnected", "Not on app"}, sel, i -> m[0] = i);
        card.addView(seg, W.lp(W.MP, T.dp(42)));
        TextView save = S.btn(c, "Save", S.SECONDARY, v -> patch(App.jo("awayMessage", f.value(), "awayMode", modes[m[0]]), "Away message saved"));
        S.addW(card, save, 12);
        return card;
    }

    View handles(Context c) {
        LinearLayout card = S.card(c);
        card.addView(K.text(c, "Additional handles", K.SM, 600, T.text));
        String h0 = Session.s.optString("handle", "");
        card.addView(K.text(c, "Up to 2 extra @handles that all resolve to your account. Shown on your profile as \"@" + h0 + " also …\".", K.XS, 400, T.textMuted), W.lpm(W.MP, W.WC, 0, 12, 0, 12));
        LinearLayout pr = W.row(c);
        pr.addView(K.mono(c, "@" + h0, K.SM, 400, T.text), W.lp(0, W.WC, 1));
        pr.addView(K.text(c, "primary", K.XS, 400, T.textMuted));
        card.addView(pr, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        JSONArray hs = Session.s.optJSONArray("handles");
        int n = hs == null ? 1 : hs.length();
        for (int i = 1; hs != null && i < hs.length(); i++) {
            String h = hs.optString(i);
            LinearLayout r = W.row(c);
            r.addView(K.mono(c, "@" + h, K.SM, 400, T.text), W.lp(0, W.WC, 1));
            r.addView(K.btn(c, "Remove", "ghost", 28, K.XS, v -> handleOp("DELETE", h, "@" + h + " removed", "Could not remove handle")));
            card.addView(r, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        }
        if (n < 3) {
            LinearLayout r = W.row(c);
            r.setGravity(Gravity.BOTTOM);
            W.Field f = new W.Field(c, "New handle", "@");
            f.input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
            r.addView(f, W.lp(0, W.WC, 1));
            TextView add = S.btn(c, "Add", S.PRIMARY, null);
            W.enable(add, false);
            f.input.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence x, int a1, int b, int d) {
                }

                public void onTextChanged(CharSequence x, int a1, int b, int d) {
                }

                public void afterTextChanged(android.text.Editable e) {
                    W.enable(add, !e.toString().trim().isEmpty());
                }
            });
            add.setOnClickListener(v -> {
                String y = f.value().toLowerCase(Locale.ROOT).replaceAll("^@", "").replaceAll("[^a-z0-9_]", "");
                if (y.isEmpty()) return;
                handleOp("POST", y, "@" + y + " added", "Could not add handle");
            });
            r.addView(add, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
            card.addView(r, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        }
        return card;
    }

    void handleOp(String method, String h, String ok, String fail) {
        App.bg(() -> {
            Http.Resp r = "POST".equals(method) ? Http.post("/me/handles", App.jo("handle", h)) : Http.delete("/me/handles/" + android.net.Uri.encode(h), null);
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast(S.err(r, fail), true);
                    return;
                }
                JSONArray hs = r.obj().optJSONArray("handles");
                try {
                    if (hs != null) Session.s.put("handles", hs);
                    Session.save();
                } catch (Exception e) {
                }
                Ov.toast(ok);
                rebuild();
            });
        });
    }
}
