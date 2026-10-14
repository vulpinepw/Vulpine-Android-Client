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
    public static final int EMOJI = 1, GIF = 2;
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

    public void toggle(int k) {
        if (panel != null && kind == k) {
            closeKeepKb();
            return;
        }
        close();
        kind = k;
        W.hideKb(cp.input());
        cp.input().clearFocus();
        Context c = cp.ctx();
        panel = new FrameLayout(c);
        panel.setBackground(W.round(T.glassBgStrong, 18, T.glassStroke, .5f));
        int h = height();
        panel.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8) + cp.navH());
        panel.setClickable(true);
        panel.setElevation(T.dpf(20));
        panel.addView(k == EMOJI ? emoji(c) : gif(c), new FrameLayout.LayoutParams(W.MP, W.MP));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W.MP, h + cp.navH(), Gravity.BOTTOM);
        cp.root().addView(panel, lp);
        panel.setAlpha(0);
        panel.animate().alpha(1).setDuration(140).start();
        ImageView btn = k == EMOJI ? cp.emojiBtn() : cp.gifBtn();
        btn.setImageDrawable(Svg.stroke(21, T.accent, 2, "M2 6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2z", "M6 9h.01", "M10 9h.01", "M14 9h.01", "M18 9h.01", "M6 13h.01", "M18 13h.01", "M8 16h8"));
        boolean stuck = cp.atBottom();
        cp.onInsets(cp.navH(), 0);
        if (stuck) cp.root().post(cp::scrollBottom);
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

    View emoji(Context c) {
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = W.col(c);
        sv.addView(col);
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
        return sv;
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
        EditText q = new EditText(c);
        q.setHint("Search GIFs…");
        q.setHintTextColor(T.textMuted);
        T.text(q, 14, 400, T.text);
        q.setSingleLine(true);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setPadding(T.dp(10), T.dp(8), T.dp(10), T.dp(8));
        q.setBackground(W.round(T.surface2, 8, T.surface2, 1));
        col.addView(q, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
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
