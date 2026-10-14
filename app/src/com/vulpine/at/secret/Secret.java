package com.vulpine.at.secret;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;

public final class Secret {
    public static boolean allowed(Chats.Conv c) {
        if (c == null || c.group()) return false;
        String pid = c.o.optString("peerId", ""), h = c.o.optString("peerHandle", "");
        return !pid.isEmpty() && !"_deleted".equals(h) && !h.endsWith("_bot");
    }

    public static void openOrCreate(Chats.Conv c) {
        if (!allowed(c)) return;
        Sec.wire();
        Sec.Chat s = Sec.byPeer(c.o.optString("peerId"));
        if (s != null) {
            open(s.id);
            return;
        }
        String n = c.o.optString("peerDisplayName", "");
        create(c.o.optString("peerId"), n.isEmpty() ? c.o.optString("peerHandle") : n);
    }

    public static void open(String id) {
        MainActivity a = MainActivity.cur;
        if (a == null) return;
        if (a.top() instanceof SecretScreen && id.equals(((SecretScreen) a.top()).id)) return;
        if (com.vulpine.at.home.Home.I != null) a.popTo(com.vulpine.at.home.Home.I);
        a.push(new SecretScreen(id));
    }

    public static void create(String peerId, String nm) {
        MainActivity a = MainActivity.cur;
        if (a == null) return;
        Context c = a;
        Ov.Sheet s = new Ov.Sheet(c);
        LinearLayout head = W.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(SecretScreen.lock(22, SecretScreen.vsec()));
        ic.setScaleType(ImageView.ScaleType.CENTER);
        ic.setBackground(W.oval(T.mixA(SecretScreen.vsec(), .18f)));
        head.addView(ic, W.lp(T.dp(44), T.dp(44)));
        TextView t = W.text(c, "Secret chat with " + nm, 20, 600, T.text);
        head.addView(t, W.lpm(0, W.WC, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1;
        s.body.addView(head, W.lpm(W.MP, W.WC, 0, 0, 0, 4));
        TextView msg = W.text(c, "Messages are encrypted with a password only you two know. They stay on this device and are never stored on Vulpine. Share the password with " + nm + " in person or another safe way. You will need it every time you open the app.", 14, 400, T.textSecondary);
        msg.setLineSpacing(0, 1.35f);
        s.body.addView(msg, W.lpm(W.MP, W.WC, 0, 8, 0, 4));
        W.Field p1 = new W.Field(c, "Password", null);
        p1.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        p1.input.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        s.body.addView(p1, W.lpm(W.MP, W.WC, 0, 4, 0, 0));
        W.Field p2 = new W.Field(c, "Confirm password", null);
        p2.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        p2.input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        s.body.addView(p2, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        TextView err = W.text(c, " ", 13, 400, T.danger);
        s.body.addView(err, W.lpm(W.MP, W.WC, 0, 10, 0, 6));
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = W.btn(c, "Cancel", false, null);
        TextView go = W.btn(c, "Create", true, null);
        r.addView(cancel);
        LinearLayout.LayoutParams gp = W.lp(W.WC, W.WC);
        gp.leftMargin = T.dp(8);
        r.addView(go, gp);
        s.body.addView(r);
        boolean[] busy = {false};
        cancel.setOnClickListener(v -> {
            if (!busy[0]) s.close();
        });
        Runnable submit = () -> {
            if (busy[0]) return;
            String a1 = p1.value(), a2 = p2.value();
            if (a1.length() < 6) {
                err.setText("Use at least 6 characters");
                return;
            }
            if (!a1.equals(a2)) {
                err.setText("The passwords do not match");
                return;
            }
            busy[0] = true;
            err.setText(" ");
            go.setText("Encrypting...");
            W.enable(go, false);
            App.bg(() -> {
                Object res;
                try {
                    res = Sec.create(peerId, a1);
                } catch (Throwable e) {
                    res = "failed";
                }
                Object fr = res;
                App.ui(() -> {
                    if (fr instanceof Sec.Chat) {
                        s.close();
                        Ov.toast("Secret chat created");
                        open(((Sec.Chat) fr).id);
                        return;
                    }
                    busy[0] = false;
                    go.setText("Create");
                    W.enable(go, true);
                    err.setText(Sec.errorText((String) fr, nm));
                });
            });
        };
        go.setOnClickListener(v -> submit.run());
        p2.input.setOnEditorActionListener((v, act, e) -> {
            submit.run();
            return true;
        });
        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence x, int st, int b, int n) {
            }

            public void onTextChanged(CharSequence x, int st, int b, int n) {
            }

            public void afterTextChanged(Editable e) {
                err.setText(" ");
            }
        };
        p1.input.addTextChangedListener(tw);
        p2.input.addTextChangedListener(tw);
        s.show();
        p1.post(() -> {
            p1.input.requestFocus();
            W.showKb(p1.input);
        });
    }

    public static String sub(Sec.Chat s) {
        String n = s.peerName();
        if (!s.unlocked() && s.pending() && s.mineCreated()) return "Waiting for " + n + " to join";
        if (!s.unlocked() && s.pending()) return "Invited you to a secret chat";
        if (!s.unlocked()) return "Locked, enter the password";
        if (s.pending() && s.mineCreated()) return "Waiting for " + n + " to join";
        if (s.typing()) return null;
        Sec.Msg m = s.last();
        if (m != null) {
            String t;
            if (m.file != null) {
                String mime = m.file.optString("mime");
                String k = mime.startsWith("image/") ? "Photo" : mime.startsWith("video/") ? "Video" : mime.startsWith("audio/") ? "Voice message" : "File";
                t = k + (m.text == null || m.text.isEmpty() ? "" : ": " + m.text);
            } else {
                String x = m.text == null ? "" : m.text;
                if (x.trim().matches("(?i)^https://[a-z0-9.-]*giphy\\.com/\\S+$")) t = "GIF";
                else {
                    String first = x.split("\n", 2)[0].replaceAll("[*_~`>|#]", "").trim();
                    t = first.length() > 120 ? first.substring(0, 120) : first;
                }
            }
            return (m.mine() ? "You: " : "") + t;
        }
        return "Secret chat";
    }

    public static void confirmClose(Sec.Chat s) {
        Ov.confirm("Close this secret chat?", "Its messages are deleted on both devices and cannot be recovered.", "Close chat", true, () -> App.bg(() -> {
            boolean ok = Sec.close(s);
            App.ui(() -> Ov.toast(ok ? "Secret chat closed" : "Could not close the secret chat", !ok));
        }));
    }

    public static FrameLayout.LayoutParams lockLp() {
        return SecretScreen.lockLp();
    }

    public static android.view.View lockBadge(Context c) {
        return SecretScreen.lockBadge(c);
    }
}
