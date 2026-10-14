package com.vulpine.at.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Files;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import org.json.JSONObject;

public class AccountPage extends SetPage {
    String newTok;
    boolean rotating;

    public AccountPage() {
        super("account", "Account");
    }

    protected void content(LinearLayout body) {
        Context c = a;
        JSONObject s = Session.s == null ? new JSONObject() : Session.s;
        add(S.valueCard(c, "Display name", s.optString("displayName"), false, null));
        add(S.valueCard(c, "Handle", "@" + s.optString("handle"), true, null));
        add(S.valueCard(c, "Account token", "••••••••••••••••", true, s.optString("token")));
        if (newTok != null) {
            add(S.valueCard(c, "New account token - copy it now", "••••••••••••••••", true, newTok));
            TextView ok = S.text(c, "Token regenerated - the old token no longer works and other devices were logged out.", 13, 400, T.success, 19.5f);
            ok.setPadding(T.dp(8), ok.getPaddingTop(), T.dp(8), ok.getPaddingBottom());
            add(ok);
        }
        TextView note = S.muted(c, "Your token authenticates this device. Keep it secret - anyone with it can log in as you.");
        note.setPadding(T.dp(8), note.getPaddingTop(), T.dp(8), note.getPaddingBottom());
        add(note);
        add(S.btn(c, "Download account info (.txt)", S.GHOST, v -> download()));
        LinearLayout rot = S.card(c, 16, 12);
        rot.addView(S.muted(c, "Mint a brand-new account token. The old one stops working immediately; other devices get logged out."));
        TextView rb = S.btn(c, "Regenerate token", S.DANGER, null);
        rb.setOnClickListener(v -> Ov.confirm("Regenerate your account token?", "The current token will stop working immediately and any other devices will be logged out. Make sure to copy the new token.", "Regenerate", true, () -> rotate(rb)));
        S.addW(rot, rb, 12);
        add(rot);
        add(statusCard(c), 28);
        add(proFree(c), 28);
    }

    void download() {
        JSONObject s = Session.s;
        if (s == null) return;
        String mn = Session.id != null && Session.id.mnemonic != null && !Session.id.mnemonic.isEmpty() ? Session.id.mnemonic : "(not loaded)";
        String t = "Vulpine Account Recovery\n===========================\n\nHandle: @" + s.optString("handle") + "\nDisplay name: " + s.optString("displayName") + "\n\nRecovery phrase (24 words keep secret):\n" + mn + "\n\nAccount token:\n" + s.optString("token") + "\n\nKeep this file somewhere safe.";
        Files.saveText(a, "vulpine-" + s.optString("handle") + ".txt", t);
    }

    void rotate(TextView b) {
        if (rotating) return;
        rotating = true;
        S.busy(b, true, "Regenerate token", "Regenerating…");
        App.bg(() -> {
            Http.Resp r = Http.post("/me/token/rotate", null);
            App.ui(() -> {
                rotating = false;
                if (!alive) return;
                S.busy(b, false, "Regenerate token", "Regenerating…");
                String tok = r.ok() ? r.obj().optString("token", "") : "";
                if (tok.isEmpty()) {
                    Ov.toast(S.err(r, "Could not regenerate token"), true);
                    return;
                }
                try {
                    Session.s.put("token", tok);
                } catch (Exception e) {
                }
                Http.token = tok;
                Session.save();
                newTok = tok;
                com.vulpine.core.Rt.I.stop();
                com.vulpine.core.Rt.I.start();
                Ov.toast("Token regenerated");
                rebuild();
            });
        });
    }

    View statusCard(Context c) {
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Status", 600));
        String cur = Session.s == null ? "online" : Session.s.optString("status", "online");
        String[][] st = {{"online", "Online"}, {"inactive", "Inactive"}, {"dnd", "Do Not Disturb"}};
        int[] cols = {T.success, 0xfff59e0b, T.danger};
        for (int i = 0; i < 3; i++) {
            String v = st[i][0];
            View b = pill(c, cols[i], null, st[i][1], v.equals(cur), true, x -> setStatus(v));
            S.add(card, b, i == 0 ? 12 : 8);
        }
        JSONObject pv = Session.s == null ? null : Session.s.optJSONObject("privacy");
        boolean masked = pv != null && "nobody".equals(pv.optString("showPresence"));
        S.addW(card, pill(c, 0xff71717a, null, "Mask my status", masked, false, x -> mask(masked)), 8);
        boolean ls = Session.settings().optBoolean("shareListening");
        S.addW(card, pill(c, 0, Ic.svg(SPOTIFY, 12, 0xff1db954), "Share what I listen to on Spotify", ls, false, x -> listen(ls)), 8);
        TextView h = S.muted(c, "Works from the Vulpine desktop app for Windows while Spotify is playing. Nothing from your Spotify account is shared.");
        h.setPadding(T.dp(4), h.getPaddingTop(), T.dp(4), h.getPaddingBottom());
        S.add(card, h, 4);
        return card;
    }

    static final String SPOTIFY = "<svg viewBox=\"0 0 24 24\"><path fill=\"currentColor\" d=\"M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301c-.6.179-1.2-.181-1.38-.721-.18-.601.18-1.2.72-1.381 4.26-1.26 11.28-1.02 15.721 1.621.539.3.719 1.02.419 1.56-.299.421-1.02.599-1.559.3z\"/></svg>";

    static View pill(Context c, int dot, android.graphics.drawable.Drawable icon, String label, boolean on, boolean full, View.OnClickListener cl) {
        LinearLayout b = W.row(c);
        b.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
        b.setBackground(W.ripple(W.round(on ? T.accentSoft : T.surface2, 12), 12));
        b.setAlpha(on ? 1f : .7f);
        if (icon != null) b.addView(W.img(c, icon), W.lp(T.dp(12), T.dp(12)));
        else {
            View d = new View(c);
            d.setBackground(W.oval(dot));
            b.addView(d, W.lp(T.dp(12), T.dp(12)));
        }
        TextView t = S.label(c, label, 500);
        LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
        p.leftMargin = T.dp(12);
        b.addView(t, p);
        b.setOnClickListener(cl);
        return b;
    }

    void setStatus(String v) {
        Http.Resp[] out = new Http.Resp[1];
        try {
            Session.s.put("status", v);
        } catch (Exception e) {
        }
        rebuild();
        Sync.patchMe(App.jo("status", v), out, () -> {
            if (out[0].ok()) Ov.toast("Status updated");
            else Ov.toast(S.err(out[0], "Update failed"), true);
            if (alive) rebuild();
        });
    }

    void mask(boolean was) {
        Http.Resp[] out = new Http.Resp[1];
        Sync.patchMe(App.jo("privacy", App.jo("showPresence", was ? "contacts" : "nobody")), out, () -> {
            if (out[0].ok()) Ov.toast(was ? "Online status visible" : "Status masked");
            else Ov.toast(S.err(out[0], "Update failed"), true);
            if (alive) rebuild();
        });
    }

    void listen(boolean was) {
        try {
            Session.settings().put("shareListening", !was);
        } catch (Exception e) {
        }
        Sync.save();
        Ov.toast(was ? "Listening status turned off" : "Listening status turned on");
        rebuild();
    }

    View proFree(Context c) {
        LinearLayout b = W.row(c);
        b.setGravity(Gravity.CENTER);
        b.setBackground(W.ripple(W.accentBg(12), 12));
        ImageView im = new ImageView(c);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        im.setClipToOutline(true);
        im.setBackground(W.round(0, 6));
        im.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        Img.anim(im, "asset:set-touchgrass.gif", T.dp(44), true);
        b.addView(im, W.lp(T.dp(22), T.dp(22)));
        TextView t = W.text(c, "GET THE PRO FOR FREE!", 14, 700, T.accentFg);
        LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
        p.leftMargin = T.dp(8);
        b.addView(t, p);
        b.setMinimumHeight(T.dp(44));
        b.setOnClickListener(v -> {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")));
            } catch (Exception e) {
            }
        });
        return b;
    }
}
