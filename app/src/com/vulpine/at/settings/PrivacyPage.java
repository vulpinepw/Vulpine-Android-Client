package com.vulpine.at.settings;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Pin;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import org.json.JSONArray;
import org.json.JSONObject;

public class PrivacyPage extends SetPage {
    static final String[] VALS = {"everyone", "contacts", "nobody"};
    static final String[] LABS = {"Everyone", "Contacts", "No one"};
    JSONObject me;
    JSONArray blocked;
    boolean saving;

    public PrivacyPage() {
        super("privacy", "Privacy");
    }

    public void onShow() {
        App.bg(() -> {
            Http.Resp r = Http.get("/me");
            Http.Resp b = Http.get("/me/blocked");
            App.ui(() -> {
                if (!alive) return;
                if (r.ok()) {
                    me = r.obj();
                    pinState(me.optBoolean("hasPin"));
                }
                if (b.ok()) blocked = b.obj().optJSONArray("blocked");
                rebuild();
            });
        });
    }

    static void pinState(boolean has) {
        App.kv.put("vulpine.pinState", App.jo("u", Session.userId(), "p", has ? 1 : 0).toString());
    }

    protected void content(LinearLayout body) {
        Context c = a;
        JSONObject pv = Session.s == null ? null : Session.s.optJSONObject("privacy");
        String[][] rows = {{"viewProfile", "View my profile", "Who can see your name, bio, and avatar.", "everyone"}, {"sendMessage", "Send me messages", "Who can start a direct message with you.", "contacts"}, {"addToGroups", "Add me to groups", "Who can add you to a group conversation.", "contacts"}};
        for (String[] r : rows) {
            LinearLayout card = S.card(c);
            card.addView(S.label(c, r[1], 500));
            S.add(card, S.muted(c, r[2]), 2);
            String cur = pv == null ? r[3] : pv.optString(r[0], r[3]);
            int sel = 0;
            for (int i = 0; i < 3; i++) if (VALS[i].equals(cur)) sel = i;
            S.add(card, S.seg(c, LABS, sel, 0, i -> setPriv(r[0], VALS[i])), 12);
            add(card);
        }
        if (!Pin.fake) pinSection(c);
        add(blockedCard(c), Pin.fake ? 12 : 12);
    }

    void setPriv(String key, String val) {
        if (saving) return;
        saving = true;
        JSONObject pv = Session.s.optJSONObject("privacy");
        String old = pv == null ? null : pv.optString(key, null);
        try {
            if (pv == null) {
                pv = new JSONObject();
                Session.s.put("privacy", pv);
            }
            pv.put(key, val);
        } catch (Exception e) {
        }
        Http.Resp[] out = new Http.Resp[1];
        Sync.patchMe(App.jo("privacy", App.jo(key, val)), out, () -> {
            saving = false;
            if (out[0].ok()) Ov.toast("Privacy updated");
            else {
                try {
                    Session.s.optJSONObject("privacy").put(key, old);
                } catch (Exception e) {
                }
                Ov.toast(S.err(out[0], "Could not update privacy"), true);
            }
            if (alive) rebuild();
        });
    }

    void pinSection(Context c) {
        boolean has = me != null ? me.optBoolean("hasPin") : Pin.hasPin();
        LinearLayout c1 = S.card(c);
        c1.addView(S.label(c, "App-lock PIN", 500));
        S.add(c1, S.secondary(c, has ? "A PIN is required to open the app. Works across all your devices." : "Require a PIN to open the app. Works across all your devices.", 13), 4);
        LinearLayout r1 = W.row(c);
        if (!has) r1.addView(S.smallBtn(c, "Set up PIN", false, v -> setupPin()), W.lp(0, W.WC, 1));
        else {
            r1.addView(S.smallBtn(c, "Change PIN", false, v -> changePin()), W.lp(0, W.WC, 1));
            LinearLayout.LayoutParams p = W.lp(0, W.WC, 1);
            p.leftMargin = T.dp(8);
            r1.addView(S.smallBtn(c, "Remove", true, v -> removePin()), p);
        }
        S.add(c1, r1, 12);
        add(c1, 28);
        if (has) {
            LinearLayout c2 = S.card(c);
            c2.addView(S.label(c, "Auto-lock", 500));
            S.add(c2, S.secondary(c, "Automatically lock the app after a period of inactivity.", 13), 4);
            S.Flow f = new S.Flow(c, 6, 6);
            String cur = Session.settings().optString("autoLock", "");
            String[][] opts = {{"", "Off"}, {"1m", "1 min"}, {"5m", "5 min"}, {"30m", "30 min"}, {"1h", "1 hour"}, {"24h", "Daily"}};
            for (String[] o : opts) {
                boolean on = o[0].equals(cur);
                TextView b = W.text(c, o[1], 13, 600, on ? T.accentFg : T.text);
                b.setPadding(T.dp(12), T.dp(6), T.dp(12), T.dp(6));
                T.lh(b, 19.5f);
                b.setPadding(T.dp(12), T.dp(6) + b.getPaddingTop(), T.dp(12), T.dp(6) + b.getPaddingBottom());
                b.setBackground(W.ripple(on ? W.accentBg(8) : W.round(T.surface3, 8), 8));
                b.setOnClickListener(v -> autoLock(o[0]));
                f.addView(b);
            }
            S.add(c2, f, 12);
            add(c2, 12);
        }
        if (has && S.pro()) {
            boolean fk = me != null && me.optBoolean("hasFakePin");
            LinearLayout c3 = S.card(c);
            LinearLayout tr = W.row(c);
            tr.addView(S.label(c, "Fake PIN (duress)", 500));
            TextView badge = W.text(c, "PRO", 10, 700, T.accentFg);
            badge.setPadding(T.dp(6), T.dp(2), T.dp(6), T.dp(2));
            badge.setBackground(W.accentBg(4));
            LinearLayout.LayoutParams bp = W.lp(W.WC, W.WC);
            bp.leftMargin = T.dp(6);
            tr.addView(badge, bp);
            c3.addView(tr);
            S.add(c3, S.secondary(c, fk ? "A decoy PIN is set. Entering it opens a fake, empty account instead of your real one." : "Set a decoy PIN. If you're forced to unlock the app, entering this PIN opens a fake, empty account instead.", 13), 4);
            LinearLayout r3 = W.row(c);
            if (!fk) r3.addView(S.smallBtn(c, "Set up fake PIN", false, v -> fakePin(false)), W.lp(0, W.WC, 1));
            else {
                r3.addView(S.smallBtn(c, "Change", false, v -> fakePin(true)), W.lp(0, W.WC, 1));
                LinearLayout.LayoutParams p = W.lp(0, W.WC, 1);
                p.leftMargin = T.dp(8);
                r3.addView(S.smallBtn(c, "Remove", true, v -> Modal.confirm(a, "Remove fake PIN", "The fake PIN will be removed. Only your real PIN will work.", "Confirm", this::removeFake)), p);
            }
            S.add(c3, r3, 12);
            add(c3, 12);
        }
    }

    static String pinErr(Http.Resp r) {
        JSONObject e = r.obj();
        String code = e.optString("error", "");
        int wait = e.optInt("retryAfter", 0);
        String wt = wait > 0 ? " Try again in " + (wait >= 60 ? ((wait + 59) / 60) + " min." : wait + " s.") : "";
        if (code.equals("too_many_attempts")) return "Too many wrong PINs." + (wt.isEmpty() ? " Try again later." : wt);
        if (code.equals("current_pin_required")) return "Current PIN is wrong." + wt;
        return code.isEmpty() ? "Failed" : code;
    }

    void call(String method, String path, JSONObject body, String ok, Modal.Done done, Runnable after) {
        App.bg(() -> {
            Http.Resp r = Http.call(method, path, body);
            App.ui(() -> {
                if (!r.ok()) {
                    done.on(pinErr(r));
                    return;
                }
                JSONObject o = r.obj();
                if (o.has("hasPin")) {
                    me = o;
                    Session.merge(o);
                    pinState(o.optBoolean("hasPin"));
                }
                Ov.toast(ok);
                done.on(null);
                if (after != null) after.run();
                if (alive) rebuild();
            });
        });
    }

    void setupPin() {
        Modal.form(a, "Set up app-lock PIN", "Choose a 4-8 digit PIN. You'll need it every time you open the app on any device.", new String[][]{{"pin", "New PIN", "4-8 digits", "tel"}, {"confirm", "Confirm PIN", "Re-enter PIN", "tel"}}, (v, cb) -> {
            if (!v.get("pin").matches("^\\d{4,8}$")) {
                cb.on("PIN must be 4-8 digits.");
                return;
            }
            if (!v.get("pin").equals(v.get("confirm"))) {
                cb.on("PINs don't match.");
                return;
            }
            call("POST", "/me/pin", App.jo("pin", v.get("pin")), "PIN set", cb, () -> Pin.unlocked = true);
        });
    }

    void changePin() {
        Modal.form(a, "Change app-lock PIN", null, new String[][]{{"currentPin", "Current PIN", "Enter current PIN", "tel"}, {"pin", "New PIN", "4-8 digits", "tel"}, {"confirm", "Confirm new PIN", "Re-enter PIN", "tel"}}, (v, cb) -> {
            if (!v.get("pin").matches("^\\d{4,8}$")) {
                cb.on("New PIN must be 4-8 digits.");
                return;
            }
            if (!v.get("pin").equals(v.get("confirm"))) {
                cb.on("PINs don't match.");
                return;
            }
            call("POST", "/me/pin", App.jo("pin", v.get("pin"), "currentPin", v.get("currentPin")), "PIN changed", cb, null);
        });
    }

    void removePin() {
        Modal.form(a, "Remove app-lock PIN", "Enter your current PIN to remove the lock. The fake PIN (if set) will also be removed.", new String[][]{{"currentPin", "Current PIN", "Enter current PIN", "tel"}}, (v, cb) -> call("DELETE", "/me/pin", App.jo("currentPin", v.get("currentPin")), "PIN removed", cb, null));
    }

    void fakePin(boolean change) {
        String[][] f = change ? new String[][]{{"fakePin", "New fake PIN", "4-8 digits", "tel"}, {"displayName", "Fake display name", "e.g. Alex", "text", "64"}, {"bio", "Fake bio (optional)", "", "text", "200"}} : new String[][]{{"fakePin", "Fake PIN", "4-8 digits (different from your real PIN)", "tel"}, {"displayName", "Fake display name", "e.g. Alex", "text", "64"}, {"bio", "Fake bio (optional)", "", "text", "200"}};
        Modal.form(a, change ? "Change fake PIN" : "Set up fake PIN", change ? null : "If someone forces you to unlock the app, enter this PIN instead. It will open a fake, empty account - your real account stays hidden.", f, (v, cb) -> {
            if (!v.get("fakePin").matches("^\\d{4,8}$")) {
                cb.on("Fake PIN must be 4-8 digits.");
                return;
            }
            call("POST", "/me/fake-pin", App.jo("fakePin", v.get("fakePin"), "fakeProfile", App.jo("displayName", v.get("displayName"), "bio", v.get("bio"))), change ? "Fake PIN updated" : "Fake PIN set", cb, null);
        });
    }

    void removeFake() {
        call("DELETE", "/me/fake-pin", null, "Fake PIN removed", err -> {
            if (err != null) Ov.toast(err, true);
        }, null);
    }

    void autoLock(String v) {
        App.bg(() -> {
            Http.Resp g = Http.get("/me");
            JSONObject ns = new JSONObject();
            try {
                JSONObject srv = g.ok() ? g.obj().optJSONObject("settings") : null;
                if (srv != null) ns = new JSONObject(srv.toString());
                JSONObject loc = Session.settings();
                java.util.Iterator<String> it = loc.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    ns.put(k, loc.get(k));
                }
                ns.put("autoLock", v);
                if (!ns.has("hiddenChatIds")) ns.put("hiddenChatIds", new JSONArray());
            } catch (Exception e) {
            }
            Http.Resp r = Http.patch("/me", App.jo("settings", ns));
            App.ui(() -> {
                if (r.ok()) {
                    try {
                        Session.settings().put("autoLock", v);
                    } catch (Exception e) {
                    }
                    Session.save();
                    Pin.touch();
                    Ov.toast("Auto-lock updated");
                } else Ov.toast(S.err(r, "Failed"), true);
                if (alive) rebuild();
            });
        });
    }

    View blockedCard(Context c) {
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Blocked users", 600));
        S.add(card, S.muted(c, "Blocked users can't message you and see your profile as a deleted account."), 8);
        if (blocked == null || blocked.length() == 0) {
            S.add(card, S.text(c, "You haven't blocked anyone.", 14, 400, T.textMuted, 21), 12);
            return card;
        }
        for (int i = 0; i < blocked.length(); i++) {
            JSONObject u = blocked.optJSONObject(i);
            if (u == null) continue;
            LinearLayout r = W.row(c);
            AvatarView av = new AvatarView(c);
            r.addView(av, W.lp(T.dp(32), T.dp(32)));
            av.set(u.optString("displayName", u.optString("handle")).isEmpty() ? u.optString("handle") : u.optString("displayName"), u.optString("avatarColor", null), u.optString("avatarUrl", null));
            LinearLayout tx = W.col(c);
            tx.addView(S.text(c, u.optString("displayName"), 14, 500, T.text, 21));
            tx.addView(S.text(c, "@" + u.optString("handle"), 13, 400, T.textMuted, 19.5f));
            LinearLayout.LayoutParams tp = W.lp(0, W.WC, 1);
            tp.leftMargin = T.dp(8);
            r.addView(tx, tp);
            String h = u.optString("handle");
            r.addView(S.btn(c, "Unblock", S.GHOST, 30, 13, v -> unblock(h)), W.lp(W.WC, T.dp(30)));
            S.add(card, r, i == 0 ? 12 : 8);
        }
        return card;
    }

    void unblock(String h) {
        App.bg(() -> {
            Http.Resp r = Http.delete("/users/" + h + "/block", null);
            App.ui(() -> {
                if (r.ok()) {
                    Ov.toast("@" + h + " unblocked");
                    JSONArray n = new JSONArray();
                    for (int i = 0; blocked != null && i < blocked.length(); i++) if (!h.equals(blocked.optJSONObject(i).optString("handle"))) n.put(blocked.opt(i));
                    blocked = n;
                    if (alive) rebuild();
                } else Ov.toast(S.err(r, "Could not unblock"), true);
            });
        });
    }
}
