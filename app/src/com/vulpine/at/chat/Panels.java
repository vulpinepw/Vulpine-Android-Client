package com.vulpine.at.chat;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Blur;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Panels {
    public static final int EMOJI = 1, GIF = 2, STICKERS = 3;
    public interface Host {
        Context ctx();

        FrameLayout root();

        boolean atBottom();

        void scrollBottom();

        String cid();

        int navH();

        int imeH();

        android.widget.EditText input();

        ImageView emojiBtn();

        ImageView gifBtn();

        void onInsets(int bottom, int ime);

        void sendRaw(String body);
    }

    final Host cp;
    FrameLayout panel;
    int kind;
    static JSONArray sets;

    public Panels(Host cp) {
        this.cp = cp;
    }

    static void learn(int ime) {
        int full = App.ctx.getResources().getDisplayMetrics().heightPixels;
        if (ime > 150 * T.density && ime < .7f * full) App.prefs.edit().putInt("kbh", ime).apply();
    }

    public int height() {
        int H = App.ctx.getResources().getDisplayMetrics().heightPixels;
        int h = App.prefs.getInt("kbh", 0);
        if (h >= T.dp(200) && h <= .6f * H) return h;
        return Math.round(Math.min(T.dp(340), Math.max(T.dp(260), .38f * H)));
    }

    public boolean open() {
        return panel != null;
    }

    public static int last = App.prefs.getInt("panel.last", EMOJI);
    LinearLayout body;
    FrameLayout content;
    LinearLayout tabs;
    FrameLayout tabWrap;
    View tabInd;
    TextView[] tabBtns;
    int ime;

    public void toggle(int k) {
        if (panel != null) {
            closeKeepKb();
            return;
        }
        kind = k;
        W.hideKb(cp.input());
        cp.input().clearFocus();
        Context c = cp.ctx();
        panel = new FrameLayout(c);
        panel.setBackground(W.round(T.glassBgStrong, 18, T.glassStroke, .5f));
        Blur.apply(panel, new float[]{18, 18, 0, 0}, .72f, sources());
        int h = height();
        panel.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(4) + cp.navH());
        panel.setClickable(true);
        panel.setElevation(T.dpf(20));
        body = W.col(c);
        content = new FrameLayout(c);
        body.addView(content, W.lp(W.MP, 0, 1));
        tabs = W.row(c);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(T.dp(3), T.dp(3), T.dp(3), T.dp(3));
        tabWrap = new FrameLayout(c);
        tabWrap.setBackground(W.round(T.dark ? 0x14ffffff : 0x0f000000, 999));
        tabInd = new View(c);
        tabInd.setBackground(W.accentBg(999));
        tabWrap.addView(tabInd, new FrameLayout.LayoutParams(0, 0));
        tabWrap.addView(tabs, new FrameLayout.LayoutParams(W.WC, W.WC));
        tabBtns = new TextView[3];
        String[] names = {"Emoji", "GIFs", "Stickers"};
        for (int i = 0; i < 3; i++) {
            int kk = i + 1;
            TextView t = W.text(c, names[i], 13, 600, T.textSecondary);
            t.setGravity(Gravity.CENTER);
            t.setPadding(T.dp(18), T.dp(6), T.dp(18), T.dp(6));
            t.setOnClickListener(v -> show(kk));
            tabBtns[i] = t;
            tabs.addView(t, W.lp(W.WC, W.WC));
        }
        LinearLayout.LayoutParams tl = W.lpm(W.WC, W.WC, 0, 6, 0, 2);
        tl.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(tabWrap, tl);
        panel.addView(body, new FrameLayout.LayoutParams(W.MP, W.MP));
        show(k);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W.MP, h + cp.navH(), Gravity.BOTTOM);
        cp.root().addView(panel, lp);
        panel.setTranslationY(T.dp(24));
        panel.setAlpha(0);
        panel.animate().alpha(1).translationY(0).setDuration(160).start();
        ImageView btn = cp.emojiBtn();
        btn.setImageDrawable(Svg.stroke(21, T.accent, 2, "M2 6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z", "M6 9h.01", "M10 9h.01", "M14 9h.01", "M18 9h.01", "M6 13h.01", "M18 13h.01", "M8 16h8"));
        boolean stuck = cp.atBottom();
        cp.onInsets(cp.navH(), 0);
        if (stuck) cp.root().post(cp::scrollBottom);
    }

    View[] sources() {
        FrameLayout r = cp.root();
        java.util.List<View> v = new java.util.ArrayList<>();
        for (int i = 0; i < r.getChildCount() && i < 2; i++) v.add(r.getChildAt(i));
        return v.toArray(new View[0]);
    }

    void show(int k) {
        int prev = content.getChildCount() > 0 ? kind : 0;
        kind = k;
        last = k;
        App.prefs.edit().putInt("panel.last", k).apply();
        content.removeAllViews();
        View nv = k == EMOJI ? emoji(cp.ctx()) : k == GIF ? gif(cp.ctx()) : stickers(cp.ctx());
        content.addView(nv, new FrameLayout.LayoutParams(W.MP, W.MP));
        if (prev != 0 && prev != k) {
            nv.setAlpha(0f);
            nv.setTranslationX(T.dp(28) * (k > prev ? 1 : -1));
            nv.animate().alpha(1f).translationX(0).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }
        for (int i = 0; i < 3; i++) {
            boolean on = i + 1 == k;
            tabBtns[i].setTextColor(on ? T.accentFg : T.textSecondary);
        }
        moveInd(k, prev != 0 && prev != k);
    }

    void moveInd(int k, boolean anim) {
        TextView t = tabBtns[k - 1];
        if (t.getWidth() == 0) {
            t.post(() -> moveInd(k, false));
            return;
        }
        int x = tabs.getLeft() + t.getLeft(), y = tabs.getTop() + t.getTop(), w = t.getWidth(), h = t.getHeight();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tabInd.getLayoutParams();
        if (!anim || lp.width == 0) {
            lp.width = w;
            lp.height = h;
            tabInd.setLayoutParams(lp);
            tabInd.setTranslationX(x);
            tabInd.setTranslationY(y);
            return;
        }
        float x0 = tabInd.getTranslationX();
        int w0 = lp.width;
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(300);
        va.setInterpolator(new android.view.animation.PathInterpolator(.4f, 0f, .2f, 1f));
        va.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            FrameLayout.LayoutParams q = (FrameLayout.LayoutParams) tabInd.getLayoutParams();
            q.width = Math.round(w0 + (w - w0) * f);
            q.height = h;
            tabInd.setLayoutParams(q);
            tabInd.setTranslationX(x0 + (x - x0) * f);
            tabInd.setTranslationY(y);
        });
        va.start();
    }

    public int searchHeight() {
        return Math.min(height(), T.dp(230));
    }

    public void onIme(int im) {
        if (panel == null || ime == im) return;
        ime = im;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        lp.height = im > 0 ? searchHeight() : height() + cp.navH();
        lp.bottomMargin = im;
        panel.setLayoutParams(lp);
        panel.setPadding(T.dp(8), T.dp(8), T.dp(8), im > 0 ? T.dp(6) : T.dp(4) + cp.navH());
        tabWrap.setVisibility(im > 0 ? View.GONE : View.VISIBLE);
    }

    void restoreIcons() {
        cp.emojiBtn().setImageDrawable(com.vulpine.at.ui.Ic.has("chat.emoji") ? com.vulpine.at.ui.Ic.get("chat.emoji", 21, T.textSecondary) : Svg.stroke(21, T.textSecondary, 2, "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z"));
        cp.gifBtn().setImageDrawable(com.vulpine.at.ui.Ic.has("chat.gif") ? com.vulpine.at.ui.Ic.get("chat.gif", 21, T.textSecondary) : Svg.stroke(21, T.textSecondary, 2, "M3 5h18v14H3z"));
    }

    public void close() {
        if (panel == null) return;
        FrameLayout p = panel;
        panel = null;
        cp.root().removeView(p);
        ime = 0;
        restoreIcons();
        cp.onInsets(cp.navH(), cp.imeH());
    }

    public void closeKeepKb() {
        close();
        W.showKb(cp.input());
    }

    void insert(String t) {
        Editable e = cp.input().getText();
        int a = Math.max(0, cp.input().getSelectionStart());
        e.insert(a, t);
    }

    static JSONObject names;

    EditText search(Context c, String hint) {
        EditText q = new EditText(c);
        q.setHint(hint);
        q.setHintTextColor(T.textMuted);
        T.text(q, 14, 400, T.text);
        q.setSingleLine(true);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        q.setBackground(W.round(T.dark ? 0x14ffffff : 0x0f000000, 12));
        android.graphics.drawable.Drawable ic = Svg.stroke(16, T.textMuted, 2, "M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M21 21l-4.35-4.35");
        android.graphics.drawable.InsetDrawable in = new android.graphics.drawable.InsetDrawable(ic, T.dp(10), 0, 0, 0);
        q.setCompoundDrawablesRelativeWithIntrinsicBounds(in, null, null, null);
        q.setPadding(T.dp(2), T.dp(8), T.dp(10), T.dp(8));
        q.setCompoundDrawablePadding(T.dp(8));
        if (android.os.Build.VERSION.SDK_INT >= 29) q.setTextCursorDrawable(W.cursor(T.accent));
        return q;
    }

    static void loadNames() {
        if (names != null) return;
        try (InputStream in = App.ctx.getAssets().open("emoji-names.json")) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            names = new JSONObject(new String(bo.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            names = new JSONObject();
        }
    }

    View emoji(Context c) {
        LinearLayout outer = W.col(c);
        EditText q = search(c, "Search emoji");
        outer.addView(q, W.lpm(W.MP, W.WC, 0, 0, 0, 6));
        FrameLayout stack = new FrameLayout(c);
        outer.addView(stack, W.lp(W.MP, 0, 1));
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = W.col(c);
        sv.addView(col);
        stack.addView(sv, new FrameLayout.LayoutParams(W.MP, W.MP));
        ScrollView rs = new ScrollView(c);
        rs.setVerticalScrollBarEnabled(false);
        LinearLayout rcol = W.col(c);
        rs.addView(rcol);
        rs.setVisibility(View.GONE);
        stack.addView(rs, new FrameLayout.LayoutParams(W.MP, W.MP));
        Runnable[] deb = new Runnable[1];
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence x, int a, int b, int cc) {
            }

            public void onTextChanged(CharSequence x, int a, int b, int cc) {
            }

            public void afterTextChanged(Editable e) {
                if (deb[0] != null) q.removeCallbacks(deb[0]);
                deb[0] = () -> filter(c, e.toString().trim().toLowerCase(java.util.Locale.ROOT), sv, rs, rcol);
                q.postDelayed(deb[0], 120);
            }
        });
        if (sets == null) {
            try (InputStream in = App.ctx.getAssets().open("emoji-sets.json")) {
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                sets = new JSONArray(new String(bo.toByteArray(), "UTF-8"));
            } catch (Exception e) {
                sets = new JSONArray();
            }
        }
        java.util.List<String[]> custom = new java.util.ArrayList<>();
        custom.add(new String[]{"sob", "/assets/emoji-sob.png"});
        custom.add(new String[]{"skully", "/assets/emoji-skully.png"});
        Map<String, String> g = Emoji.group(cp.cid());
        if (g != null) for (Map.Entry<String, String> e : g.entrySet()) custom.add(new String[]{e.getKey(), e.getValue()});
        col.addView(header(c, "Custom"));
        GridLayout cg = grid(c);
        for (String[] e : custom) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(T.dp(5), T.dp(5), T.dp(5), T.dp(5));
            Img.anim(iv, e[1], T.dp(44), false);
            iv.setContentDescription(":" + e[0] + ":");
            iv.setOnClickListener(v -> insert(":" + e[0] + ":"));
            W.press(iv, .9f);
            cg.addView(iv, cell());
        }
        pad(c, cg);
        col.addView(cg);
        for (int i = 0; i < sets.length(); i++) {
            JSONArray set = sets.optJSONArray(i);
            col.addView(header(c, set.optString(0)));
            GridLayout gr = grid(c);
            JSONArray list = set.optJSONArray(1);
            for (int k = 0; k < list.length(); k++) {
                String e = list.optString(k);
                if (e.isEmpty()) continue;
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setPadding(T.dp(5), T.dp(5), T.dp(5), T.dp(5));
                iv.setContentDescription(e);
                lazyPng(iv, e);
                iv.setOnClickListener(v -> insert(e));
                W.press(iv, .9f);
                gr.addView(iv, cell());
            }
            col.addView(gr);
        }
        return outer;
    }

    void filter(Context c, String term, ScrollView sv, ScrollView rs, LinearLayout rcol) {
        rcol.removeAllViews();
        if (term.isEmpty()) {
            rs.setVisibility(View.GONE);
            sv.setVisibility(View.VISIBLE);
            return;
        }
        loadNames();
        sv.setVisibility(View.GONE);
        rs.setVisibility(View.VISIBLE);
        GridLayout gr = grid(c);
        int n = 0;
        java.util.List<String> starts = new java.util.ArrayList<>(), rest = new java.util.ArrayList<>();
        Map<String, String> g = Emoji.group(cp.cid());
        java.util.List<String[]> custom = new java.util.ArrayList<>();
        custom.add(new String[]{"sob", "/assets/emoji-sob.png"});
        custom.add(new String[]{"skully", "/assets/emoji-skully.png"});
        if (g != null) for (Map.Entry<String, String> e : g.entrySet()) custom.add(new String[]{e.getKey(), e.getValue()});
        for (String[] e : custom) {
            if (!e[0].toLowerCase(java.util.Locale.ROOT).contains(term)) continue;
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(T.dp(5), T.dp(5), T.dp(5), T.dp(5));
            Img.anim(iv, e[1], T.dp(44), false);
            iv.setContentDescription(":" + e[0] + ":");
            iv.setOnClickListener(v -> insert(":" + e[0] + ":"));
            W.press(iv, .9f);
            gr.addView(iv, cell());
            n++;
        }
        for (int i = 0; i < sets.length(); i++) {
            JSONArray list = sets.optJSONArray(i).optJSONArray(1);
            for (int k = 0; k < list.length(); k++) {
                String e = list.optString(k);
                if (e.isEmpty()) continue;
                String nm = names.optString(e, "");
                if (nm.isEmpty()) continue;
                if (nm.startsWith(term) || nm.contains(" " + term)) {
                    if (!starts.contains(e)) starts.add(e);
                } else if (nm.contains(term) && !rest.contains(e)) rest.add(e);
            }
        }
        starts.addAll(rest);
        for (String e : starts) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(T.dp(5), T.dp(5), T.dp(5), T.dp(5));
            iv.setContentDescription(e);
            lazyPng(iv, e);
            iv.setOnClickListener(v -> insert(e));
            W.press(iv, .9f);
            gr.addView(iv, cell());
            n++;
        }
        if (n == 0) {
            TextView t = W.text(c, "No emoji found", 13, 400, T.textMuted);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, T.dp(24), 0, T.dp(24));
            rcol.addView(t, W.lp(W.MP, W.WC));
            return;
        }
        pad(c, gr);
        rcol.addView(gr);
    }

    static void pad(Context c, GridLayout g) {
        while (g.getChildCount() % 8 != 0) g.addView(new View(c), cell());
    }

    static void lazyPng(ImageView iv, String e) {
        iv.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {
                if (iv.getDrawable() != null) return;
                Img.get(Emoji.pngUrl(e), T.dp(44), false, (d, b) -> {
                    if (b != null) iv.setImageBitmap(b);
                    else Img.get(Emoji.pngUrl(Emoji.noFe0f(e)), T.dp(44), false, (d2, b2) -> {
                        if (b2 != null) iv.setImageBitmap(b2);
                    });
                });
            }

            public void onViewDetachedFromWindow(View v) {
            }
        });
    }

    static TextView header(Context c, String t) {
        TextView h = W.text(c, t.toUpperCase(), 10, 700, T.textMuted);
        h.setLetterSpacing(.06f);
        h.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(4));
        return h;
    }

    static GridLayout grid(Context c) {
        GridLayout g = new GridLayout(c);
        g.setColumnCount(8);
        return g;
    }

    static GridLayout.LayoutParams cell() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = T.dp(36);
        return lp;
    }

    View gif(Context c) {
        LinearLayout col = W.col(c);
        EditText q = search(c, "Search GIFs…");
        col.addView(q, W.lpm(W.MP, W.WC, 0, 0, 0, 6));
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout body = W.col(c);
        sv.addView(body);
        col.addView(sv, W.lp(W.MP, 0, 1));
        TextView note = W.text(c, "Loading…", 12, 400, T.textMuted);
        note.setGravity(Gravity.CENTER);
        String[] cur = {""};
        int[] page = {0};
        boolean[] busy = {false}, done = {false};
        GridLayout[] results = new GridLayout[1];
        Runnable[] load = new Runnable[1];
        Runnable[] reset = new Runnable[1];
        reset[0] = () -> {
            body.removeAllViews();
            page[0] = 0;
            done[0] = false;
            JSONArray favs = GifFav.list();
            if (favs.length() > 0 && cur[0].isEmpty()) {
                body.addView(header(c, "Favourites"));
                GridLayout fg = new GridLayout(c);
                fg.setColumnCount(3);
                for (int i = 0; i < favs.length(); i++) {
                    JSONObject o = favs.optJSONObject(i);
                    String u = o.optString("u");
                    fg.addView(tile(c, Media.gifLite(u, "100w"), u, true), tileLp());
                }
                body.addView(fg);
                body.addView(header(c, "Results"));
            } else if (cur[0].isEmpty()) {
                TextView hint = W.text(c, "Long-press a GIF to add it here", 12, 400, T.textMuted);
                hint.setPadding(T.dp(4), 0, 0, T.dp(6));
                body.addView(hint);
            }
            results[0] = new GridLayout(c);
            results[0].setColumnCount(3);
            body.addView(results[0]);
            body.addView(note, W.lpm(W.MP, W.WC, 0, 8, 0, 8));
            load[0].run();
        };
        load[0] = () -> {
            if (busy[0] || done[0]) return;
            busy[0] = true;
            note.setText("Loading…");
            String query = cur[0];
            int off = page[0] * 9;
            App.bg(() -> {
                Http.Resp r;
                try {
                    r = Http.get(query.isEmpty() ? "/gif/trending?&limit=9&offset=" + off : "/gif/search?q=" + URLEncoder.encode(query.length() > 50 ? query.substring(0, 50) : query, "UTF-8") + "&limit=9&offset=" + off);
                } catch (Exception e) {
                    busy[0] = false;
                    return;
                }
                App.ui(() -> {
                    busy[0] = false;
                    if (!query.equals(cur[0])) return;
                    if (r.status == 503) {
                        note.setText("Coming soon.");
                        done[0] = true;
                        return;
                    }
                    if (!r.ok()) {
                        note.setText("GIF search unavailable (HTTP " + r.status + ")");
                        return;
                    }
                    JSONArray d = r.obj().optJSONArray("data");
                    if (d == null || d.length() == 0) {
                        if (page[0] == 0) note.setText("No GIFs found");
                        else note.setText("");
                        done[0] = true;
                        return;
                    }
                    note.setText("");
                    for (int i = 0; i < d.length(); i++) {
                        JSONObject im = d.optJSONObject(i).optJSONObject("images");
                        if (im == null) continue;
                        String send = first(im, "downsized_medium", "url", "fixed_width", "url", "original", "url");
                        String th = first(im, "fixed_width_small", "webp", "preview_gif", "url", "fixed_width", "url");
                        if (send == null) continue;
                        send = send.replaceAll("\\?.*$", "");
                        results[0].addView(tile(c, th == null ? send : th, send, false), tileLp());
                    }
                    page[0]++;
                });
            });
        };
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            if (body.getHeight() - (y + sv.getHeight()) < T.dp(160)) load[0].run();
        });
        Runnable[] deb = new Runnable[1];
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int cc) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int cc) {
            }

            public void afterTextChanged(Editable e) {
                if (deb[0] != null) q.removeCallbacks(deb[0]);
                deb[0] = () -> {
                    cur[0] = e.toString().trim();
                    reset[0].run();
                };
                q.postDelayed(deb[0], 300);
            }
        });
        reset[0].run();
        return col;
    }

    static JSONArray packs;
    static long packsAt;
    static int curPack;

    View stickers(Context c) {
        FrameLayout root = new FrameLayout(c);
        TextView wait = W.text(c, "Loading…", 13, 400, T.textMuted);
        wait.setGravity(Gravity.CENTER);
        root.addView(wait, new FrameLayout.LayoutParams(W.MP, W.MP));
        Runnable show = () -> {
            root.removeAllViews();
            if (packs == null || packs.length() == 0) {
                LinearLayout e = W.col(c);
                e.setGravity(Gravity.CENTER);
                e.setPadding(T.dp(24), 0, T.dp(24), 0);
                ImageView ic = new ImageView(c);
                ic.setImageDrawable(Svg.stroke(40, T.textMuted, 1.8f, "M15.5 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h9.5L21 14.5V5a2 2 0 0 0-2-2z", "M15 21v-4a2 2 0 0 1 2-2h4", "M8 13s1.5 2 4 2", "M9 9h.01", "M15 9h.01"));
                ic.setAlpha(.7f);
                e.addView(ic, W.lp(T.dp(44), T.dp(44)));
                TextView t = W.text(c, "No sticker packs yet", 14, 600, T.text);
                t.setGravity(Gravity.CENTER);
                e.addView(t, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
                TextView d = W.text(c, "Sticker packs are coming soon.", 13, 400, T.textMuted);
                d.setGravity(Gravity.CENTER);
                e.addView(d, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
                root.addView(e, new FrameLayout.LayoutParams(W.MP, W.MP));
                return;
            }
            if (curPack >= packs.length()) curPack = 0;
            LinearLayout col = W.col(c);
            EditText sq = search(c, "Search stickers");
            col.addView(sq, W.lpm(W.MP, W.WC, 0, 0, 0, 6));
            String[] query = {""};
            android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout strip = W.row(c);
            hs.addView(strip);
            col.addView(hs, W.lpm(W.MP, W.WC, 0, 0, 0, 6));
            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            LinearLayout body = W.col(c);
            sv.addView(body);
            col.addView(sv, W.lp(W.MP, 0, 1));
            Runnable[] fill = new Runnable[1];
            fill[0] = () -> {
                strip.removeAllViews();
                body.removeAllViews();
                for (int i = 0; i < packs.length(); i++) {
                    JSONObject pk = packs.optJSONObject(i);
                    ImageView cv = new ImageView(c);
                    cv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    cv.setPadding(T.dp(5), T.dp(5), T.dp(5), T.dp(5));
                    cv.setBackground(i == curPack ? W.round(T.dark ? 0x1fffffff : 0x14000000, 10) : null);
                    String cover = pk.optString("cover", "");
                    if (cover.isEmpty()) cover = pk.optJSONArray("stickers").optString(0);
                    Img.anim(cv, cover, T.dp(72), true);
                    int idx = i;
                    cv.setOnClickListener(v -> {
                        curPack = idx;
                        query[0] = "";
                        if (sq.length() > 0) sq.setText("");
                        fill[0].run();
                    });
                    strip.addView(cv, W.lpm(T.dp(40), T.dp(40), i == 0 ? 0 : 4, 0, 0, 0));
                }
                String q = query[0].trim().toLowerCase(java.util.Locale.ROOT);
                java.util.List<String> urls = new java.util.ArrayList<>();
                if (q.isEmpty()) {
                    JSONArray st0 = packs.optJSONObject(curPack).optJSONArray("stickers");
                    for (int k = 0; k < st0.length(); k++) urls.add(st0.optString(k));
                } else for (int i = 0; i < packs.length(); i++) {
                    JSONObject p2 = packs.optJSONObject(i);
                    JSONObject nm = p2.optJSONObject("names");
                    JSONArray st0 = p2.optJSONArray("stickers");
                    for (int k = 0; k < st0.length(); k++) {
                        String u = st0.optString(k);
                        String hay = ((nm == null ? "" : nm.optString(u, "")) + " " + p2.optString("name", "")).toLowerCase(java.util.Locale.ROOT);
                        if (hay.contains(q)) urls.add(u);
                    }
                }
                body.addView(header(c, q.isEmpty() ? packs.optJSONObject(curPack).optString("name", "Stickers") : "Results"));
                if (urls.isEmpty()) {
                    TextView none = W.text(c, "No stickers found", 13, 400, T.textMuted);
                    none.setGravity(Gravity.CENTER);
                    none.setPadding(0, T.dp(24), 0, T.dp(24));
                    body.addView(none, W.lp(W.MP, W.WC));
                    return;
                }
                GridLayout g = new GridLayout(c);
                g.setColumnCount(4);
                for (String u : urls) {
                    ImageView iv = new ImageView(c);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
                    Img.anim(iv, u, T.dp(160), true);
                    iv.setContentDescription("Sticker");
                    iv.setOnClickListener(v -> {
                        close();
                        cp.sendRaw("📎|sticker.webp|image/webp|" + (u.startsWith("http") || u.startsWith("/") ? u : "/" + u));
                    });
                    W.press(iv, .9f);
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
                    lp.width = 0;
                    lp.height = T.dp(84);
                    g.addView(iv, lp);
                }
                while (g.getChildCount() % 4 != 0) {
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
                    lp.width = 0;
                    lp.height = T.dp(84);
                    g.addView(new View(c), lp);
                }
                body.addView(g);
            };
            fill[0].run();
            sq.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence x, int a, int b, int cc) {
                }

                public void onTextChanged(CharSequence x, int a, int b, int cc) {
                }

                public void afterTextChanged(Editable e) {
                    if (query[0].equals(e.toString())) return;
                    query[0] = e.toString();
                    fill[0].run();
                }
            });
            root.addView(col, new FrameLayout.LayoutParams(W.MP, W.MP));
        };
        if (packs != null && System.currentTimeMillis() - packsAt < 600000) show.run();
        else App.bg(() -> {
            JSONArray out = new JSONArray();
            try {
                byte[] b = Http.bytes(Http.origin + "/stickers/packs.json", 512 * 1024);
                JSONArray a = new JSONObject(new String(b, "UTF-8")).optJSONArray("packs");
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    JSONObject pk = a.optJSONObject(i);
                    if (pk != null && pk.optJSONArray("stickers") != null && pk.optJSONArray("stickers").length() > 0) out.put(pk);
                }
            } catch (Exception e) {
            }
            App.ui(() -> {
                packs = out;
                packsAt = System.currentTimeMillis();
                if (root.isAttachedToWindow() || root.getParent() != null) show.run();
            });
        });
        return root;
    }

    static String path(String u) {
        if (u == null) return "";
        String x = u.replaceFirst("^https?://[^/]+", "");
        int q = x.indexOf('?');
        return q >= 0 ? x.substring(0, q) : x;
    }

    public static void stickerInfo(FrameLayout root, String url) {
        Runnable show = () -> {
            String want = path(url), title = "Sticker", pack = null;
            if (packs != null) for (int i = 0; i < packs.length(); i++) {
                JSONObject pk = packs.optJSONObject(i);
                JSONArray st = pk.optJSONArray("stickers");
                for (int k = 0; st != null && k < st.length(); k++) {
                    if (!path(st.optString(k)).equals(want)) continue;
                    pack = pk.optString("name", "");
                    JSONObject nm = pk.optJSONObject("names");
                    String kw = nm == null ? "" : nm.optString(st.optString(k), "").trim();
                    if (!kw.isEmpty()) {
                        String w = kw.split("\\s+")[0];
                        title = Character.toUpperCase(w.charAt(0)) + w.substring(1);
                    }
                }
            }
            Tip.show(root, title, pack == null || pack.isEmpty() ? "An animated sticker." : "From the " + pack + " sticker pack.");
        };
        if (packs != null && System.currentTimeMillis() - packsAt < 600000) {
            show.run();
            return;
        }
        App.bg(() -> {
            JSONArray out = new JSONArray();
            try {
                byte[] b = Http.bytes(Http.origin + "/stickers/packs.json", 512 * 1024);
                JSONArray a = new JSONObject(new String(b, "UTF-8")).optJSONArray("packs");
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    JSONObject pk = a.optJSONObject(i);
                    if (pk != null && pk.optJSONArray("stickers") != null && pk.optJSONArray("stickers").length() > 0) out.put(pk);
                }
            } catch (Exception e) {
            }
            App.ui(() -> {
                packs = out;
                packsAt = System.currentTimeMillis();
                show.run();
            });
        });
    }

    static String first(JSONObject im, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) {
            JSONObject o = im.optJSONObject(kv[i]);
            if (o != null && !o.optString(kv[i + 1]).isEmpty()) return o.optString(kv[i + 1]);
        }
        return null;
    }

    GridLayout.LayoutParams tileLp() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.height = T.dp(96);
        lp.setMargins(T.dp(2), T.dp(2), T.dp(2), T.dp(2));
        return lp;
    }

    View tile(Context c, String thumb, String send, boolean fav) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(W.round(T.surface2, 8));
        Media.RoundImage iv = new Media.RoundImage(c);
        iv.corners(8, 8, 8, 8);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP));
        Img.get(thumb, T.dp(140), true, (d, b) -> Media.setDrawable(iv, d, b, true));
        if (fav || GifFav.has(send)) {
            ImageView st = new ImageView(c);
            st.setImageDrawable(Svg.fill(12, 0xffffd75e, "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z"));
            st.setScaleType(ImageView.ScaleType.CENTER);
            st.setBackground(W.round(0x8c000000, 6));
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(T.dp(18), T.dp(18), Gravity.TOP | Gravity.END);
            sp.topMargin = sp.rightMargin = T.dp(4);
            f.addView(st, sp);
        }
        f.setOnClickListener(v -> {
            close();
            cp.sendRaw(send);
        });
        f.setOnLongClickListener(v -> {
            boolean has = GifFav.has(send);
            if (has) GifFav.remove(send);
            else GifFav.add(send, thumb);
            Ov.toast(has ? "Removed from favourites" : "Added to favourites");
            return true;
        });
        return f;
    }
}
