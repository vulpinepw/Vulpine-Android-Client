package com.vulpine.at.shop;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import org.json.JSONArray;
import org.json.JSONObject;

public class Wallet extends ShopTab.Part {
    Context c;
    LinearLayout box;
    JSONObject data;
    String err;
    String impH = "", impT = "";
    boolean impB;
    W.Field fh, ft;
    final Rt.Listener ws = m -> {
        if ("fragment.updated".equals(m.optString("type"))) App.ui(() -> {
            load();
            Ov.toast("Wallet updated");
        });
    };

    @Override
    public void build(Context c, LinearLayout body) {
        this.c = c;
        box = W.col(c);
        body.addView(box, W.lp(W.MP, W.WC));
        render();
        load();
    }

    @Override
    public void onShow() {
        Rt.I.on(ws);
    }

    @Override
    public void onHide() {
        Rt.I.off(ws);
    }

    void load() {
        App.bg(() -> {
            Http.Resp r = Http.get("/shop/wallet");
            App.ui(() -> {
                if (r.ok()) {
                    boolean first = data == null;
                    data = r.obj();
                    err = null;
                    render(first);
                } else {
                    err = "Could not load your wallet.";
                    render();
                }
            });
        });
    }

    void render() {
        render(true);
    }

    interface Done {
        void on(JSONObject j);
    }

    void act(String path, JSONObject body, String okMsg, Done ok, Runnable fail) {
        App.bg(() -> {
            Http.Resp r = Http.post(path, body);
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast(K.shopErr(r.error()), true);
                    if (fail != null) fail.run();
                    return;
                }
                load();
                if (okMsg != null) Ov.toast(okMsg);
                if (ok != null) ok.on(r.obj());
            });
        });
    }

    LinearLayout cardAt(int delay, boolean anim) {
        LinearLayout k = K.card(c);
        LinearLayout.LayoutParams lp = W.lp(W.MP, W.WC);
        if (box.getChildCount() > 0) lp.topMargin = T.dp(12);
        box.addView(k, lp);
        if (anim) K.in(k, delay);
        return k;
    }

    TextView sbtn(String label, String variant, View.OnClickListener on) {
        return K.btn(c, label, variant, 28, K.XS, on);
    }

    void render(boolean anim) {
        boolean keepFocus = ft != null && (ft.input.hasFocus() || fh.input.hasFocus());
        if (fh != null) impH = fh.value();
        if (ft != null) impT = ft.value();
        box.removeAllViews();
        if (data == null && err == null) {
            box.addView(K.spinCenter(c, 20, 40), W.lp(W.MP, W.WC));
            return;
        }
        if (err != null && data == null) {
            box.addView(K.empty(c, err, null), W.lp(W.MP, W.WC));
            return;
        }
        int used = data.optInt("used"), cap = data.optInt("capacity", 30);
        int pct = Math.min(100, Math.round(used * 100f / Math.max(1, cap)));
        LinearLayout k0 = cardAt(0, anim);
        LinearLayout hr = W.row(c);
        LinearLayout tl = W.col(c);
        tl.addView(K.text(c, "Wallet", K.SM, 600, T.text));
        int extra = data.optInt("extraSlots");
        tl.addView(K.text(c, used + " / " + cap + " handles" + (extra > 0 ? " (+" + extra + " bought)" : "") + " · " + data.optInt("listedCount") + "/" + data.optInt("maxListed", 15) + " listed", K.XS, 400, T.textMuted));
        hr.addView(tl, W.lp(0, W.WC, 1));
        hr.addView(K.btn(c, "Buy slots", "secondary", 32, K.XS, v -> slots()), W.lpm(W.WC, T.dp(32), 12, 0, 0, 0));
        k0.addView(hr, W.lp(W.MP, W.WC));
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(W.round(T.surface2, 999));
        bar.setClipToOutline(true);
        bar.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        View fill = new View(c);
        fill.setBackground(W.round(T.accent, 999));
        bar.addView(fill, new FrameLayout.LayoutParams(0, W.MP));
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = Math.round((r - l) * pct / 100f);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
            if (lp.width != w) {
                lp.width = w;
                fill.post(() -> fill.setLayoutParams(lp));
            }
        });
        k0.addView(bar, W.lpm(W.MP, T.dp(4), 0, 8, 0, 0));
        LinearLayout k1 = cardAt(40, anim);
        k1.addView(K.text(c, "Import a handle", K.SM, 600, T.text));
        TextView ex = K.text(c, "Permanently deletes the source account and moves the handle into this wallet. Paste the account's token to confirm ownership.", K.XS, 400, T.textMuted);
        k1.addView(ex, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        Flow f = new Flow(c, T.dp(8), T.dp(8));
        f.center = false;
        fh = new W.Field(c, "Handle", null);
        fh.input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        fh.input.setText(impH);
        Flow.LP a = new Flow.LP(W.MP, W.WC);
        f.addView(fh, a);
        ft = new W.Field(c, "Account token", null);
        ft.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ft.input.setText(impT);
        LinearLayout tr = W.row(c);
        tr.setGravity(Gravity.BOTTOM);
        tr.addView(ft, W.lp(0, W.WC, 1));
        TextView ib = K.btn(c, impB ? "Importing…" : "Import", "primary", 38, K.SM, v -> doImport());
        K.enable(ib, !impB);
        tr.addView(ib, W.lpm(W.WC, T.dp(38), 8, 0, 0, 0));
        f.addView(tr, new Flow.LP(W.MP, W.WC));
        k1.addView(f, W.lpm(W.MP, W.WC, 0, 24, 0, 0));
        if (keepFocus) ft.input.requestFocus();
        JSONArray att = data.optJSONArray("attached");
        if (att != null && att.length() > 1) {
            LinearLayout k2 = cardAt(80, anim);
            k2.addView(K.text(c, "On your profile", K.SM, 600, T.text), W.lpm(W.MP, W.WC, 0, 0, 0, 8));
            for (int i = 1; i < att.length(); i++) {
                String h = att.optString(i);
                LinearLayout r = W.row(c);
                r.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
                r.addView(K.mono(c, "@" + h, K.SM, 400, T.text), W.lp(0, W.WC, 1));
                r.addView(sbtn("Retire to wallet", "ghost", v -> act("/shop/detach", App.jo("handle", h), "Moved to wallet", null, null)), W.lpm(W.WC, T.dp(28), 8, 0, 0, 0));
                k2.addView(r, W.lpm(W.MP, W.WC, 0, i == 1 ? 0 : 4, 0, 0));
                if (anim) K.in(r, 80 + (i - 1) * 25);
            }
        }
        JSONArray wl = data.optJSONArray("wallet");
        LinearLayout k3 = cardAt(80, anim);
        if (wl == null || wl.length() == 0) {
            k3.addView(K.empty(c, "Your wallet is empty", "Import a handle, or retire one of your aliases here to trade it."), W.lp(W.MP, W.WC));
        } else {
            boolean pro = data.optBoolean("pro");
            for (int i = 0; i < wl.length(); i++) {
                JSONObject w = wl.optJSONObject(i);
                String h = w.optString("handle");
                Flow r = new Flow(c, T.dp(8), T.dp(8));
                r.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
                r.addView(K.mono(c, "@" + h, K.SM, 600, T.text));
                if (w.optBoolean("listed")) r.addView(K.text(c, "On sale · " + K.price(w.opt("priceUsd")), K.XS, 500, T.accent));
                View sp = new View(c);
                Flow.LP sl = new Flow.LP(0, 1);
                sl.grow = true;
                r.addView(sp, sl);
                if (w.optBoolean("listed")) r.addView(sbtn("Delist", "ghost", v -> delist(h)), new Flow.LP(W.WC, T.dp(28)));
                else r.addView(sbtn("Sell", "ghost", v -> sale(h, w.opt("priceUsd"))), new Flow.LP(W.WC, T.dp(28)));
                r.addView(sbtn("Transfer", "ghost", v -> transfer(h)), new Flow.LP(W.WC, T.dp(28)));
                TextView al = sbtn("Use as alias", "ghost", v -> act("/shop/alias", App.jo("handle", h), "Attached to your profile", null, null));
                al.setContentDescription("Attach as a profile alias (max 3)");
                r.addView(al, new Flow.LP(W.WC, T.dp(28)));
                if (pro) r.addView(sbtn("Set as @handle", "secondary", v -> act("/shop/primary", App.jo("handle", h), "This is now your @handle", null, null)), new Flow.LP(W.WC, T.dp(28)));
                k3.addView(r, W.lpm(W.MP, W.WC, 0, i == 0 ? 0 : 4, 0, 0));
                if (anim) K.in(r, 80 + i * 25);
            }
        }
    }

    void delist(String h) {
        App.bg(() -> {
            Http.Resp r = Http.delete("/shop/sale/" + android.net.Uri.encode(h), null);
            App.ui(() -> {
                if (r.ok()) {
                    load();
                    Ov.toast("Listing removed");
                } else Ov.toast("Could not delist", true);
            });
        });
    }

    void doImport() {
        if (impB) return;
        String h = fh.value().trim(), tk = ft.value().trim();
        if (!h.matches("(?i)^[a-z0-9_]{3,20}$") || tk.isEmpty()) {
            Ov.toast("Enter a handle and the source account token.", true);
            return;
        }
        Ov.confirm("Delete this account?", "The account @" + h + " will be permanently deleted - its conversations and messages go with it - and @" + h + " will move into your wallet. This cannot be undone.", "Delete and import", true, () -> {
            impB = true;
            impH = h;
            impT = tk;
            render(false);
            act("/shop/import", App.jo("handle", h, "token", tk), "Account deleted - @" + h + " is in your wallet", j -> {
                impB = false;
                impH = "";
                impT = "";
                fh.input.setText("");
                ft.input.setText("");
                render(false);
            }, () -> {
                impB = false;
                render(false);
            });
        });
    }

    Ov.Sheet sheet(String title, String msg) {
        Ov.Sheet s = new Ov.Sheet(c);
        TextView t = K.text(c, title, K.LG, 600, T.text);
        s.body.addView(t, W.lpm(W.MP, W.WC, 0, 0, 0, 12));
        if (msg != null) {
            TextView m = K.text(c, msg, K.SM, 400, T.textMuted);
            s.body.addView(m, W.lpm(W.MP, W.WC, 0, 0, 0, 0));
        }
        return s;
    }

    LinearLayout actions(Ov.Sheet s) {
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        s.body.addView(r, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        return r;
    }

    void sale(String h, Object cur) {
        Ov.Sheet s = sheet("Put @" + h + " on sale", "The price is display-only - buyers contact you and negotiate in chat. Leave empty to show 'Offer'. Minimum $0.001, maximum $9999.99.");
        W.Field f = new W.Field(c, "Price in USD (optional)", null);
        f.input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        f.input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (cur instanceof Number) f.input.setText(String.valueOf(cur));
        s.body.addView(f, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        LinearLayout r = actions(s);
        r.addView(K.btn(c, "Cancel", "ghost", v -> s.close()));
        TextView go = K.btn(c, "List for sale", "primary", null);
        go.setOnClickListener(v -> {
            String val = f.value().trim();
            Object pv = JSONObject.NULL;
            if (!val.isEmpty()) {
                double d;
                try {
                    d = Double.parseDouble(val);
                } catch (Exception e) {
                    d = -1;
                }
                if (!(d >= .001 && d <= 9999.99)) {
                    Ov.toast("Price must be between $0.001 and $9999.99.", true);
                    return;
                }
                pv = d;
            }
            K.enable(go, false);
            go.setText("Listing…");
            act("/shop/sale", App.jo("handle", h, "priceUsd", pv), null, j -> {
                s.close();
                Ov.toast("@" + h + " is now on sale");
            }, () -> {
                K.enable(go, true);
                go.setText("List for sale");
            });
        });
        r.addView(go, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        s.show();
    }

    void transfer(String h) {
        Ov.Sheet s = sheet("Transfer @" + h, "Moves this handle into the other user's wallet. This cannot be undone.");
        W.Field f = new W.Field(c, "Recipient @handle", null);
        f.input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        s.body.addView(f, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        LinearLayout r = actions(s);
        r.addView(K.btn(c, "Cancel", "ghost", v -> s.close()));
        TextView go = K.btn(c, "Transfer", "danger", null);
        K.enable(go, false);
        f.input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence x, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence x, int a, int b, int d) {
            }

            public void afterTextChanged(android.text.Editable e) {
                K.enable(go, e.toString().trim().matches("(?i)^[a-z0-9_]{3,20}$"));
            }
        });
        go.setOnClickListener(v -> {
            String to = f.value().trim();
            K.enable(go, false);
            go.setText("Transferring…");
            act("/shop/transfer", App.jo("handle", h, "toHandle", to), null, j -> {
                s.close();
                Ov.toast("Transferred @" + h + " to @" + to);
            }, () -> {
                K.enable(go, true);
                go.setText("Transfer");
            });
        });
        r.addView(go, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        s.show();
    }

    void slots() {
        Ov.Sheet s = sheet("Buy wallet slots", "Each slot permanently adds one handle of wallet capacity.");
        int[] n = {1};
        int[] capAt = {data == null ? 0 : data.optInt("capacity")};
        boolean[] stop = {false};
        s.onClose = () -> stop[0] = true;
        LinearLayout area = W.col(c);
        s.body.addView(area, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        Runnable[] draw = new Runnable[1];
        JSONObject[] pending = {null};
        boolean[] busy = {false};
        draw[0] = () -> {
            area.removeAllViews();
            if (pending[0] == null) {
                LinearLayout pr = W.row(c);
                for (int v : new int[]{1, 2, 3, 5, 10}) {
                    TextView p = K.btn(c, String.valueOf(v), n[0] == v ? "primary" : "ghost", 32, K.XS, x -> {
                        n[0] = v;
                        draw[0].run();
                    });
                    p.setPadding(T.dp(12), 0, T.dp(12), 0);
                    LinearLayout.LayoutParams lp = W.lp(W.WC, T.dp(32));
                    if (pr.getChildCount() > 0) lp.leftMargin = T.dp(4);
                    pr.addView(p, lp);
                }
                area.addView(pr);
                LinearLayout tr = W.row(c);
                tr.addView(K.text(c, "$" + n[0] + ".00", K.SM, 600, T.text), W.lp(0, W.WC, 1));
                TextView pay = K.btn(c, busy[0] ? "Creating invoice…" : "Pay with crypto", "primary", v -> {
                    if (busy[0]) return;
                    busy[0] = true;
                    draw[0].run();
                    App.bg(() -> {
                        Http.Resp r = Http.post("/shop/slots/purchase", App.jo("slots", n[0]));
                        App.ui(() -> {
                            busy[0] = false;
                            if (!r.ok()) {
                                Ov.toast(K.shopErr(r.error()), true);
                                draw[0].run();
                                return;
                            }
                            JSONObject inv = r.obj();
                            pending[0] = inv;
                            K.openUrl(inv.optString("paymentUrl"));
                            draw[0].run();
                            pollSlots(capAt[0], inv.optInt("slots"), stop, () -> {
                                s.close();
                                load();
                                Ov.toast("Slots activated - wallet expanded");
                            });
                        });
                    });
                });
                K.enable(pay, !busy[0]);
                tr.addView(pay);
                area.addView(tr, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
            } else {
                LinearLayout w = W.col(c);
                w.setGravity(Gravity.CENTER_HORIZONTAL);
                w.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
                w.addView(K.spin(c, 22));
                TextView t = K.text(c, "Complete the payment in the OxaPay tab. This updates automatically.", K.SM, 400, T.text);
                t.setGravity(Gravity.CENTER);
                w.addView(t, W.lpm(W.WC, W.WC, 0, 12, 0, 0));
                JSONObject p = pending[0];
                w.addView(K.btn(c, "Reopen payment page", "ghost", v -> K.openUrl(p.optString("paymentUrl"))), W.lpm(W.WC, W.WC, 0, 12, 0, 0));
                area.addView(w, W.lp(W.MP, W.WC));
            }
        };
        draw[0].run();
        s.show();
    }

    void pollSlots(int cap0, int add, boolean[] stop, Runnable done) {
        App.H.postDelayed(() -> {
            if (stop[0]) return;
            App.bg(() -> {
                Http.Resp r = Http.get("/shop/wallet");
                App.ui(() -> {
                    if (stop[0]) return;
                    if (r.ok() && r.obj().optInt("capacity") >= cap0 + add) done.run();
                    else pollSlots(cap0, add, stop, done);
                });
            });
        }, 5000);
    }
}
