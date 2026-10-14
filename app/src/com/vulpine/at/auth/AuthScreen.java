package com.vulpine.at.auth;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Files;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.BgView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Screen;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import com.vulpine.core.VCrypto;
import org.json.JSONObject;

public class AuthScreen extends Screen {
    FrameLayout content;
    W.Segmented tabs;
    LinearLayout card;
    ScrollView sv;

    public View build() {
        Context c = a;
        FrameLayout f = new FrameLayout(c);
        f.setFocusableInTouchMode(true);
        f.requestFocus();
        f.addView(new BgView(c, false), new FrameLayout.LayoutParams(W.MP, W.MP));
        sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        FrameLayout center = new FrameLayout(c);
        center.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(16));
        card = W.col(c);
        card.setBackground(W.round(T.surface, 16));
        card.setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(20));
        tabs = new W.Segmented(c, new String[]{"Create an account", "Connect me"}, 0, i -> show(i));
        card.addView(tabs, W.lpm(W.MP, W.WC, 0, 0, 0, 24));
        content = new FrameLayout(c);
        card.addView(content, W.lp(W.MP, W.WC));
        FrameLayout.LayoutParams cp = W.flp(W.MP, W.WC, Gravity.CENTER);
        center.addView(card, cp);
        card.setLayoutParams(cp);
        sv.addView(center, new FrameLayout.LayoutParams(W.MP, W.MP));
        f.addView(sv, new FrameLayout.LayoutParams(W.MP, W.MP));
        show(0);
        return f;
    }

    public void onInsets(int top, int bottom, int ime) {
        sv.setPadding(0, top, 0, Math.max(bottom, ime));
    }

    void swap(View v) {
        content.removeAllViews();
        content.addView(v);
        v.setAlpha(0);
        v.setTranslationY(T.dp(12));
        v.animate().alpha(1).translationY(0).setDuration(180).setInterpolator(T.EASE).start();
    }

    void show(int i) {
        swap(i == 0 ? register() : connect());
    }

    TextView err(Context c) {
        TextView e = W.text(c, "", 13, 400, T.danger);
        e.setVisibility(View.GONE);
        return e;
    }

    static void setErr(TextView e, String s) {
        e.setText(s);
        e.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    View register() {
        Context c = a;
        LinearLayout form = W.col(c);
        W.Field handle = new W.Field(c, "Handle", "@");
        handle.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        handle.input.addTextChangedListener(new android.text.TextWatcher() {
            boolean busy;

            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(android.text.Editable e) {
                if (busy) return;
                String v = e.toString(), n = v.toLowerCase().replaceAll("[^a-z0-9_]", "");
                if (!v.equals(n)) {
                    busy = true;
                    e.replace(0, e.length(), n);
                    busy = false;
                }
            }
        });
        W.Field name = new W.Field(c, "Display name", null);
        name.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        form.addView(handle, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        form.addView(name, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        TextView e = err(c);
        form.addView(e, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        TextView[] go = new TextView[1];
        String[] cap = new String[1];
        CapWidget cw = new CapWidget(c, t -> {
            cap[0] = t;
            W.enable(go[0], t != null);
        });
        LinearLayout.LayoutParams cwp = W.lpm(T.dp(260), T.dp(58), 0, 0, 0, 24);
        cwp.gravity = Gravity.CENTER_HORIZONTAL;
        form.addView(cw, cwp);
        go[0] = W.btn(c, "Create identity", true, v -> {
            String h = handle.value().trim(), n = name.value().trim();
            if (h.isEmpty() || n.isEmpty()) return;
            if (cap[0] == null) {
                setErr(e, "Please complete the security check.");
                return;
            }
            W.hideKb(v);
            setErr(e, null);
            View creating = creating(c);
            swap(creating);
            tabs.setVisibility(View.GONE);
            String capTok = cap[0];
            App.bg(() -> {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException x) {
                }
                VCrypto.Identity id = VCrypto.generate();
                Http.Resp r = Http.raw("POST", Http.api("/register"), App.jo("handle", h, "displayName", n, "bio", "", "signPubkey", id.signPubkey, "encPubkey", id.encPubkey, "signPrivkey", id.signPrivkey, "encPrivkey", id.encPrivkey, "mnemonic", id.mnemonic, "captchaToken", capTok), null, 30000);
                App.ui(() -> {
                    if (r.status == 201 || r.ok()) {
                        JSONObject o = r.obj();
                        reveal(o.optString("userId"), o.optString("handle", h), n, o.optString("token"), id);
                    } else {
                        tabs.setVisibility(View.VISIBLE);
                        swap(form);
                        cw.reset();
                        setErr(e, regErr(r));
                    }
                });
            });
        });
        W.enable(go[0], false);
        form.addView(go[0], W.lp(W.MP, T.dp(36)));
        return form;
    }

    static String regErr(Http.Resp r) {
        String code = r.error();
        switch (code) {
            case "handle_taken": return "That handle is already taken.";
            case "captcha_failed": return "Verification expired - please verify again.";
            case "account_limit_reached": return "You can only create one account per device.";
            case "network": return "Could not connect.";
        }
        if (code.isEmpty()) return "Registration failed";
        if (code.startsWith("http_")) return "Request failed (" + r.status + ")";
        return code;
    }

    View creating(Context c) {
        LinearLayout col = W.col(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, T.dp(32), 0, T.dp(32));
        TextView av = W.text(c, "F", 20, 600, T.accentFg);
        av.setGravity(Gravity.CENTER);
        av.setBackground(W.oval(T.accent));
        col.addView(av, W.lp(T.dp(56), T.dp(56)));
        android.animation.ObjectAnimator pa = android.animation.ObjectAnimator.ofPropertyValuesHolder(av, android.animation.PropertyValuesHolder.ofFloat("scaleX", .85f, 1.08f, .85f), android.animation.PropertyValuesHolder.ofFloat("scaleY", .85f, 1.08f, .85f), android.animation.PropertyValuesHolder.ofFloat("alpha", .5f, 1f, .5f));
        pa.setDuration(900);
        pa.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pa.start();
        TextView t = W.text(c, "Generating your encrypted identity…", 14, 400, T.textMuted);
        col.addView(t, W.lpm(W.WC, W.WC, 0, 16, 0, 0));
        return col;
    }

    View secret(Context c, String label, String value, String copyLabel) {
        LinearLayout col = W.col(c);
        col.addView(W.text(c, label, 13, 400, T.textSecondary), W.lpm(W.WC, W.WC, 0, 0, 0, 4));
        LinearLayout row = W.row(c);
        row.setGravity(Gravity.TOP);
        TextView box = W.text(c, value, 13, 400, T.text);
        box.setTypeface(Typeface.MONOSPACE);
        box.setTextIsSelectable(true);
        box.setBackground(W.round(T.surface2, 12));
        box.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        row.addView(box, W.lp(0, W.WC, 1));
        android.widget.ImageView cp = W.img(c, Svg.stroke(18, T.textSecondary, 2, "M8 8h11v11H8z", "M16 8V5H5v11h3"));
        cp.setContentDescription(copyLabel);
        cp.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
        cp.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            cp.setImageDrawable(Svg.stroke(18, T.success, 2.4f, "M20 6 9 17l-5-5"));
            App.H.postDelayed(() -> cp.setImageDrawable(Svg.stroke(18, T.textSecondary, 2, "M8 8h11v11H8z", "M16 8V5H5v11h3")), 1500);
        });
        row.addView(cp, W.lpm(T.dp(36), T.dp(36), 6, 0, 0, 0));
        col.addView(row);
        return col;
    }

    void reveal(String userId, String handle, String name, String token, VCrypto.Identity id) {
        Context c = a;
        LinearLayout col = W.col(c);
        col.addView(secret(c, "Recovery phrase", id.mnemonic, "Copy phrase"), W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        col.addView(secret(c, "Account token", token, "Copy token"), W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        TextView dl = W.btn(c, "Download account info (.txt)", false, v -> {
            String txt = "Vulpine - Account Recovery\n===========================\n\nHandle: @" + handle + "\nDisplay name: " + name + "\n\nRecovery phrase (24 words - keep secret):\n" + id.mnemonic + "\n\nAccount token (shown once - keep secret):\n" + token + "\n\nKeep this file somewhere safe. Anyone with this info can access your account.";
            Files.saveText(a, "vulpine-" + handle + ".txt", txt);
        });
        col.addView(dl, W.lpm(W.MP, T.dp(36), 0, 0, 0, 16));
        TextView ok = W.btn(c, "I've saved my recovery phrase", true, v -> {
            JSONObject s = App.jo("token", token, "userId", userId, "handle", handle, "displayName", name, "bio", "", "signPubkey", id.signPubkey, "encPubkey", id.encPubkey, "handles", new org.json.JSONArray().put(handle), "avatarUrl", JSONObject.NULL, "avatarColor", "", "settings", Sync.defaults());
            Session.login(s, id);
            App.afterLogin(a);
        });
        col.addView(ok, W.lp(W.MP, T.dp(36)));
        swap(col);
    }

    View connect() {
        Context c = a;
        LinearLayout form = W.col(c);
        boolean[] phrase = {false};
        W.Field tok = new W.Field(c, "Account token", null);
        tok.input.setTypeface(Typeface.MONOSPACE);
        tok.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        W.Field ph = new W.Field(c, "Recovery phrase", null);
        ph.input.setTypeface(Typeface.MONOSPACE);
        ph.input.setSingleLine(false);
        ph.input.setMinLines(3);
        ph.input.setGravity(Gravity.TOP);
        ph.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        ph.setVisibility(View.GONE);
        form.addView(tok, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        form.addView(ph, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        TextView link = W.text(c, "Use the recovery phrase instead", 13, 500, T.accent);
        TextView e = err(c);
        link.setOnClickListener(v -> {
            phrase[0] = !phrase[0];
            tok.setVisibility(phrase[0] ? View.GONE : View.VISIBLE);
            ph.setVisibility(phrase[0] ? View.VISIBLE : View.GONE);
            link.setText(phrase[0] ? "Use the token instead" : "Use the recovery phrase instead");
            setErr(e, null);
        });
        form.addView(link, W.lpm(W.WC, W.WC, 0, 0, 0, 16));
        form.addView(e, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
        TextView[] go = new TextView[1];
        go[0] = W.btn(c, "Connect", true, v -> {
            W.hideKb(v);
            setErr(e, null);
            if (!phrase[0]) {
                String t = tok.value().trim();
                if (t.isEmpty()) {
                    setErr(e, "Enter your account token.");
                    return;
                }
                busy(go[0], true);
                App.bg(() -> loginToken(t, msg -> App.ui(() -> {
                    busy(go[0], false);
                    setErr(e, msg);
                })));
            } else {
                String p = VCrypto.normalizePhrase(ph.value());
                if (!VCrypto.validMnemonic(p)) {
                    setErr(e, "Recovery phrase is invalid. Check the 24 words and try again.");
                    return;
                }
                busy(go[0], true);
                App.bg(() -> loginPhrase(p, msg -> App.ui(() -> {
                    busy(go[0], false);
                    setErr(e, msg);
                })));
            }
        });
        form.addView(go[0], W.lp(W.MP, T.dp(36)));
        return form;
    }

    static void busy(TextView b, boolean on) {
        b.setText(on ? "Connecting…" : "Connect");
        W.enable(b, !on);
    }

    interface Err {
        void on(String m);
    }

    static String connErr(Http.Resp r) {
        switch (r.error()) {
            case "invalid_token": return "This token is invalid or unknown.";
            case "identity_unavailable": return "No stored keys for this account - sign in with your recovery phrase instead.";
            case "account_locked": return "This account is temporarily locked.";
            case "network": return "Could not connect.";
        }
        return r.error();
    }

    void loginToken(String token, Err err) {
        Http.Resp r = Http.raw("POST", Http.api("/auth/token-login"), App.jo("token", token), null, 20000);
        if (!r.ok()) {
            err.on(connErr(r));
            return;
        }
        JSONObject tl = r.obj();
        VCrypto.Identity id = VCrypto.Identity.from(tl);
        finish(token, tl, id, err);
    }

    void loginPhrase(String phrase, Err err) {
        VCrypto.Identity id = VCrypto.recover(phrase);
        Http.Resp ch = Http.raw("GET", Http.api("/auth/challenge"), null, null, 15000);
        if (!ch.ok()) {
            err.on("Could not reach server.");
            return;
        }
        String chal = ch.obj().optString("challenge");
        String sig = VCrypto.b64(VCrypto.edSign(VCrypto.utf8(chal), VCrypto.unb64(id.signPrivkey)));
        Http.Resp r = Http.raw("POST", Http.api("/recover"), App.jo("challenge", chal, "signature", sig, "signPubkey", id.signPubkey), null, 20000);
        if (!r.ok()) {
            err.on(connErr(r));
            return;
        }
        JSONObject o = r.obj();
        finish(o.optString("token"), o, id, err);
    }

    void finish(String token, JSONObject base, VCrypto.Identity id, Err err) {
        Http.Resp me = Http.raw("GET", Http.api("/me"), null, token, 20000);
        if (!me.ok()) {
            err.on(connErr(me));
            return;
        }
        JSONObject m = me.obj();
        JSONObject s = Sync.defaults();
        JSONObject ms = m.optJSONObject("settings");
        if (ms != null) {
            java.util.Iterator<String> it = ms.keys();
            while (it.hasNext()) {
                String k = it.next();
                try {
                    s.put(k, ms.get(k));
                } catch (Exception x) {
                }
            }
        }
        JSONObject sess = new JSONObject();
        try {
            java.util.Iterator<String> it = m.keys();
            while (it.hasNext()) {
                String k = it.next();
                sess.put(k, m.get(k));
            }
            sess.put("token", token);
            sess.put("userId", m.optString("id", base.optString("userId")));
            if (!sess.has("handle")) sess.put("handle", base.optString("handle"));
            if (!sess.has("displayName")) sess.put("displayName", base.optString("displayName"));
            sess.put("signPubkey", m.optString("signPubkey", base.optString("signPubkey")));
            sess.put("encPubkey", m.optString("encPubkey", base.optString("encPubkey")));
            sess.put("settings", s);
        } catch (Exception x) {
        }
        App.ui(() -> {
            if (m.has("hasPin")) App.kv.put("vulpine.pinState", App.jo("u", sess.optString("userId"), "p", m.optBoolean("hasPin") ? 1 : 0).toString());
            Session.login(sess, id);
            com.vulpine.at.Pin.unlocked = false;
            App.afterLogin(a);
        });
    }
}
