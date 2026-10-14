package com.vulpine.at.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class SecurityPage extends SetPage {
    List<JSONObject> sessions;
    boolean err, hidden;
    String busy;

    public SecurityPage() {
        super("security", "Security & Keys");
    }

    public void onShow() {
        load();
    }

    void load() {
        App.bg(() -> {
            Http.Resp r = Http.get("/sessions");
            App.ui(() -> {
                if (!alive) return;
                if (r.status == 404 || r.status == 405) hidden = true;
                else if (!r.ok()) err = sessions == null;
                else {
                    Object any = r.any();
                    JSONArray arr = any instanceof JSONArray ? (JSONArray) any : any instanceof JSONObject ? ((JSONObject) any).optJSONArray("sessions") : null;
                    if (arr == null) hidden = true;
                    else {
                        List<JSONObject> l = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) != null && arr.optJSONObject(i).has("id")) l.add(arr.optJSONObject(i));
                        l.sort((a, b) -> {
                            int c = Boolean.compare(b.optBoolean("current"), a.optBoolean("current"));
                            return c != 0 ? c : Long.compare(ts(b.opt("lastSeenAt")), ts(a.opt("lastSeenAt")));
                        });
                        sessions = l;
                        err = false;
                    }
                }
                rebuild();
            });
        });
    }

    static long ts(Object v) {
        if (v == null || JSONObject.NULL.equals(v)) return 0;
        long n;
        if (v instanceof Number) n = ((Number) v).longValue();
        else {
            String s = String.valueOf(v);
            if (s.matches("^\\d+$")) n = Long.parseLong(s);
            else {
                try {
                    n = java.time.Instant.parse(s).toEpochMilli();
                } catch (Exception e) {
                    try {
                        n = java.time.OffsetDateTime.parse(s.replace(' ', 'T')).toInstant().toEpochMilli();
                    } catch (Exception e2) {
                        return 0;
                    }
                }
            }
        }
        if (n <= 0) return 0;
        return n < 1000000000000L ? n * 1000 : n;
    }

    static String rel(Object v) {
        long t = ts(v);
        if (t == 0) return "";
        double d = (System.currentTimeMillis() - t) / 1000.0;
        if (d < 60) return "just now";
        if (d < 3600) return (int) Math.floor(d / 60) + " min ago";
        if (d < 86400) return (int) Math.floor(d / 3600) + " h ago";
        if (d < 86400 * 30) return (int) Math.floor(d / 86400) + " d ago";
        return date(t);
    }

    static String date(long t) {
        DateFormat f = DateFormat.getDateInstance(DateFormat.SHORT);
        if (f instanceof java.text.SimpleDateFormat) {
            String pt = ((java.text.SimpleDateFormat) f).toPattern();
            if (!pt.contains("yyyy")) f = new java.text.SimpleDateFormat(pt.replace("yy", "yyyy"));
        }
        return f.format(new Date(t));
    }

    public static String uaLabel(String ua) {
        if (ua == null || ua.isEmpty()) return "Unknown device";
        String b = "Browser", o = "";
        if (ua.matches("(?s).*;\\s*wv\\).*") || ua.contains("VulpineAndroid") || ua.contains("vulpineapk/")) b = "Vulpine app";
        else if (ua.matches("(?s).*Edg(e|A|iOS)?/.*")) b = "Edge";
        else if (ua.contains("OPR/") || ua.contains("Opera")) b = "Opera";
        else if (ua.contains("SamsungBrowser")) b = "Samsung Internet";
        else if (ua.contains("Firefox/") || ua.contains("FxiOS")) b = "Firefox";
        else if (ua.contains("Chrome/") || ua.contains("CriOS")) b = "Chrome";
        else if (ua.contains("Safari/")) b = "Safari";
        else if (ua.matches("(?is).*(curl|node|python|okhttp).*")) b = "API client";
        if (ua.contains("iPhone")) o = "iPhone";
        else if (ua.contains("iPad")) o = "iPad";
        else if (ua.contains("Android")) o = "Android";
        else if (ua.contains("Windows")) o = "Windows";
        else if (ua.contains("CrOS")) o = "ChromeOS";
        else if (ua.contains("Mac OS X") || ua.contains("Macintosh")) o = "macOS";
        else if (ua.contains("Linux")) o = "Linux";
        return o.isEmpty() ? b : b + " on " + o;
    }

    protected void content(LinearLayout body) {
        Context c = a;
        JSONObject s = Session.s == null ? new JSONObject() : Session.s;
        add(S.valueCard(c, "Identity key (Ed25519)", "••••••••••••••••", true, s.optString("signPubkey")));
        add(S.valueCard(c, "Encryption key (X25519)", "••••••••••••••••", true, s.optString("encPubkey")));
        boolean keys = Session.id != null && Session.id.encPrivkey != null && !Session.id.encPrivkey.isEmpty();
        if (keys) add(S.valueCard(c, "Recovery phrase", "•••• •••• •••• •••• •••• ••••", true, Session.id.mnemonic));
        else {
            LinearLayout k = S.card(c);
            k.addView(S.text(c, "Private keys aren't loaded on this device. Enter your recovery phrase from a chat to reveal your keys here.", 14, 400, T.textMuted, 21));
            add(k);
        }
        if (!hidden) add(sessCard(c), 28);
        LinearLayout dz = S.card(c);
        dz.addView(S.text(c, "Danger zone", 14, 600, T.danger, 21));
        S.add(dz, S.secondary(c, "Clear all local data on this device (session, settings, cache). Your account remains on the server.", 13), 8);
        S.addW(dz, S.btn(c, "Clear local data", S.DANGER, v -> Ov.confirm("Clear all local data?", "This removes your session and settings from this device. You'll need your token or recovery phrase to log back in.", "Clear data", true, SecurityPage::clearLocal)), 12);
        add(dz, 28);
        LinearLayout del = S.card(c);
        del.addView(S.text(c, "Delete account", 14, 600, T.danger, 21));
        S.add(del, S.secondary(c, "Permanently delete your account and all its data (connections, messages, profile). This cannot be undone.", 13), 8);
        S.addW(del, S.btn(c, "Delete account", S.DANGER, v -> deleteDialog()), 12);
        add(del, 28);
    }

    View sessCard(Context c) {
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Active devices", 600));
        S.add(card, S.secondary(c, "Devices signed in to your account. Terminate any session you don’t recognize.", 13), 4);
        if (sessions == null) {
            S.add(card, S.text(c, err ? "Could not load devices." : "Loading…", 14, 400, T.textMuted, 21), 12);
            return card;
        }
        LinearLayout list = W.col(c);
        int others = 0;
        for (int i = 0; i < sessions.size(); i++) {
            JSONObject x = sessions.get(i);
            boolean cur = x.optBoolean("current");
            if (!cur) others++;
            String ua = x.optString("userAgent", "");
            boolean mob = ua.matches("(?s).*(Mobi|Android|iPhone|iPad).*");
            LinearLayout r = W.row(c);
            r.setPadding(0, T.dp(8), 0, T.dp(8));
            if (i > 0) r.setBackground(topLine());
            FrameLayout ic = new FrameLayout(c);
            ic.setBackground(W.oval(T.surface2));
            Svg.Icon ico = mob ? Svg.stroke(20, T.textSecondary, 1.8f, "M11 18.5h2").rect(7, 2.5f, 10, 19, 2.5f, false) : Svg.stroke(20, T.textSecondary, 1.8f, "M8.5 20h7M12 16.5V20").rect(3, 4.5f, 18, 12, 2, false);
            ImageView iv = W.img(c, ico);
            ic.addView(iv, W.flp(T.dp(20), T.dp(20), Gravity.CENTER));
            r.addView(ic, W.lp(T.dp(36), T.dp(36)));
            LinearLayout tx = W.col(c);
            S.Flow top = new S.Flow(c, 6, 2);
            top.addView(S.text(c, uaLabel(ua), 14, 500, T.text, 21));
            if (cur) {
                TextView chip = W.text(c, "This device", 13, 600, S.accText(T.text));
                chip.setPadding(T.dp(8), T.dp(1), T.dp(8), T.dp(1));
                T.lh(chip, 19.5f);
                chip.setPadding(T.dp(8), T.dp(1) + chip.getPaddingTop(), T.dp(8), T.dp(1) + chip.getPaddingBottom());
                chip.setBackground(W.round(T.accentSoft, 999));
                top.addView(chip);
            }
            tx.addView(top);
            String seen = rel(x.opt("lastSeenAt"));
            long made = ts(x.opt("createdAt"));
            String line = (cur ? "Active now" : seen.isEmpty() ? "" : "Last active " + seen) + (made > 0 ? ((cur || !seen.isEmpty()) ? " · " : "") + "Signed in " + date(made) : "");
            S.add(tx, S.muted(c, line), 2);
            LinearLayout.LayoutParams tp = W.lp(0, W.WC, 1);
            tp.leftMargin = T.dp(12);
            r.addView(tx, tp);
            if (!cur) {
                String id = x.optString("id");
                TextView b = S.btn(c, id.equals(busy) ? "Terminating…" : "Terminate", S.GHOST, 32, 13, v -> end(id));
                W.enable(b, busy == null);
                LinearLayout.LayoutParams bp = W.lp(W.WC, T.dp(32));
                bp.leftMargin = T.dp(12);
                r.addView(b, bp);
            }
            list.addView(r, W.lp(W.MP, W.WC));
        }
        S.add(card, list, 12);
        if (others > 0) {
            TextView all = S.btn(c, "all".equals(busy) ? "Terminating…" : "Terminate all other sessions", S.DANGER, v -> Ov.confirm("Terminate other sessions?", "All other devices will be signed out. This device stays signed in.", "Terminate", true, () -> end(null)));
            W.enable(all, busy == null);
            S.addW(card, all, 12);
        }
        return card;
    }

    static android.graphics.drawable.Drawable topLine() {
        return new android.graphics.drawable.Drawable() {
            final Paint p = new Paint();

            public void draw(Canvas cv) {
                p.setColor(T.border);
                cv.drawRect(getBounds().left, getBounds().top, getBounds().right, getBounds().top + Math.max(1, T.dp(1)), p);
            }

            public void setAlpha(int a) {
            }

            public void setColorFilter(android.graphics.ColorFilter f) {
            }

            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    void end(String id) {
        busy = id == null ? "all" : id;
        rebuild();
        App.bg(() -> {
            Http.Resp r = Http.delete(id == null ? "/sessions" : "/sessions/" + id, null);
            App.ui(() -> {
                busy = null;
                if (r.ok()) Ov.toast(id != null ? "Session terminated" : "Other sessions terminated");
                else Ov.toast("Could not terminate session", true);
                if (alive) load();
            });
        });
    }

    public static void clearLocal() {
        App.logout(false);
        try {
            App.kv.clear("");
        } catch (Exception e) {
        }
        String host = App.prefs.getString("host", null);
        App.prefs.edit().clear().apply();
        if (host != null) App.prefs.edit().putString("host", host).apply();
        App.bg(StoragePage::clear);
        com.vulpine.at.Prefs.applyTheme();
        com.vulpine.at.MainActivity a = com.vulpine.at.MainActivity.cur;
        if (a != null) {
            a.applyBars();
            a.setRoot(new com.vulpine.at.auth.AuthScreen());
        }
    }

    void deleteDialog() {
        Context c = a;
        Modal m = new Modal(c);
        m.card.setBackground(W.round(T.surface, 16));
        m.card.setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(20));
        m.card.addView(S.text(c, "Delete account permanently?", 14, 600, T.danger, 21));
        S.add(m.card, S.secondary(c, "This removes your account, connections, and messages from the server. To confirm, paste your account token below.", 14), 8);
        W.Field f = new W.Field(c, "Account token", null);
        f.input.setTypeface(S.mono());
        S.add(m.card, f, 12);
        LinearLayout row = W.row(c);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(S.btn(c, "Cancel", S.GHOST, v -> m.close()));
        TextView go = S.btn(c, "Delete forever", S.DANGER, null);
        W.enable(go, false);
        f.input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence x, int a1, int b, int d) {
            }

            public void onTextChanged(CharSequence x, int a1, int b, int d) {
            }

            public void afterTextChanged(android.text.Editable e) {
                W.enable(go, e.toString().trim().length() > 0);
            }
        });
        go.setOnClickListener(v -> {
            String tok = f.value().trim();
            if (tok.isEmpty()) return;
            S.busy(go, true, "Delete forever", "Deleting…");
            App.bg(() -> {
                Http.Resp r = Http.delete("/me", App.jo("token", tok));
                App.ui(() -> {
                    if (r.ok()) {
                        m.close();
                        Ov.toast("Account deleted");
                        App.logout(false);
                    } else {
                        S.busy(go, false, "Delete forever", "Deleting…");
                        String e = r.obj().optString("error", "");
                        Ov.toast(e.equals("invalid_token") ? "invalid_token" : e.equals("token_required") ? "token_required" : S.err(r, "Deletion failed"), true);
                    }
                });
            });
        });
        LinearLayout.LayoutParams gp = W.lp(W.WC, W.WC);
        gp.leftMargin = T.dp(8);
        row.addView(go, gp);
        S.add(m.card, row, 16);
        m.show();
        f.input.postDelayed(() -> W.showKb(f.input), 150);
    }
}
