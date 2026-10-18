package com.vulpine.at.chat;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class Composer implements Panels.Host {
    final ChatScreen s;
    LinearLayout wrap;
    LinearLayout form;
    public EditText input;
    ImageView attach, gifBtn, emojiBtn, pollBtn, rtBtn;
    TextView botBtn;
    FrameLayout sendWrap;
    ImageView send, mic;
    LinearLayout banner;
    TextView bannerTitle, bannerSub;
    Chats.Msg replyTo, editing;
    long lastTyping;
    boolean typingState;
    Panels panels;
    FrameLayout popup;
    LinearLayout tools;
    int imeH, navH;

    public Context ctx() {
        return s.a;
    }

    public FrameLayout root() {
        return s.root;
    }

    public boolean atBottom() {
        return s.atBottom();
    }

    public void scrollBottom() {
        s.scrollBottom();
    }

    public String cid() {
        return s.cid;
    }

    public int navH() {
        return navH;
    }

    public int imeH() {
        return imeH;
    }

    public EditText input() {
        return input;
    }

    public ImageView emojiBtn() {
        return emojiBtn;
    }

    public ImageView gifBtn() {
        return gifBtn;
    }

    Composer(ChatScreen s) {
        this.s = s;
        build();
    }

    public View view() {
        return wrap;
    }

    void build() {
        Context c = s.a;
        wrap = W.col(c);
        wrap.setPadding(T.dp(10), 0, T.dp(10), T.dp(10));
        banner = W.row(c);
        banner.setPadding(T.dp(12), T.dp(8), T.dp(8), T.dp(8));
        banner.setBackground(W.round(T.glassBg, 12, T.glassStroke, .5f));
        View bar = new View(c);
        bar.setBackground(W.round(T.accent, 99));
        banner.addView(bar, W.lp(T.dp(3), W.MP));
        LinearLayout bt = W.col(c);
        bannerTitle = W.text(c, "", 13, 600, T.accent);
        bannerSub = W.text(c, "", 13, 400, T.textSecondary);
        W.ellipsize(bannerTitle);
        W.ellipsize(bannerSub);
        bt.addView(bannerTitle);
        bt.addView(bannerSub);
        banner.addView(bt, W.lpm(0, W.WC, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) bt.getLayoutParams()).weight = 1;
        ImageView bx = new ImageView(c);
        bx.setImageDrawable(Svg.stroke(16, T.textSecondary, 2.2f, "M6 6l12 12", "M18 6L6 18"));
        bx.setScaleType(ImageView.ScaleType.CENTER);
        bx.setOnClickListener(v -> clearMode());
        banner.addView(bx, W.lp(T.dp(32), T.dp(32)));
        banner.setVisibility(View.GONE);
        wrap.addView(banner, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        tools = Format.toolbar(this);
        tools.setVisibility(View.GONE);
        wrap.addView(tools, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        form = W.row(c);
        form.setGravity(Gravity.BOTTOM);
        form.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
        form.setBackground(W.round(T.glassBg, 22, T.glassStroke, .5f));
        s.root.post(() -> com.vulpine.at.ui.Blur.apply(form, 22, s.root.getChildAt(0), s.root.getChildAt(1)));
        form.setElevation(T.dpf(6));
        attach = btn(c, Ic.has("chat.attach") ? Ic.get("chat.attach", 22, T.textSecondary) : Svg.stroke(22, T.textSecondary, 2, "M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"), 40, "Attach file", v -> Attach.pick(s));
        form.addView(attach, W.lp(T.dp(40), T.dp(40)));
        input = new EditText(c) {
            @Override
            public boolean onKeyPreIme(int code, KeyEvent e) {
                return super.onKeyPreIme(code, e);
            }
        };
        input.setBackground(null);
        T.text(input, 14, 400, T.text);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14 * MsgView.msgScale());
        if (android.os.Build.VERSION.SDK_INT >= 28) input.setLineHeight(T.dp(21));
        input.setPadding(T.dp(8), T.dp(9), T.dp(8), T.dp(10));
        input.setMinHeight(T.dp(40));
        input.setMaxHeight(T.dp(120));
        input.setHintTextColor(T.textMuted);
        hint("Type something...");
        input.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) v.post(() -> hint(hintText));
        });
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        input.setVerticalScrollBarEnabled(false);
        input.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (android.os.Build.VERSION.SDK_INT >= 29) input.setTextCursorDrawable(com.vulpine.at.ui.W.cursor(T.accent));
        input.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN && !e.isShiftPressed()) {
                submit();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            boolean busy;

            public void beforeTextChanged(CharSequence a, int b, int cc, int d) {
            }

            public void onTextChanged(CharSequence a, int st, int before, int count) {
                if (busy) return;
                if (count == 1 && st + 1 <= a.length() && a.charAt(st) == '\n' && !imeMultiline()) {
                    busy = true;
                    ((Editable) a).delete(st, st + 1);
                    busy = false;
                    submit();
                }
            }

            public void afterTextChanged(Editable e) {
                onText();
            }
        });
        input.setOnFocusChangeListener((v, f) -> {
            if (f && panels != null && panels.open()) panels.closeKeepKb();
        });
        botBtn = W.text(c, "/", 15, 800, T.textSecondary);
        botBtn.setGravity(Gravity.CENTER);
        botBtn.setContentDescription("Bot commands");
        botBtn.setBackground(W.ripple(W.round(0, 999), 999));
        botBtn.setOnClickListener(v -> {
            input.requestFocus();
            if (input.length() == 0) {
                input.setText("/");
                input.setSelection(1);
            }
            W.showKb(input);
        });
        botBtn.setVisibility(View.GONE);
        form.addView(botBtn, W.lpm(T.dp(36), T.dp(36), 8, 0, 0, 0));
        form.addView(input, W.lp(0, W.WC, 1));
        ((LinearLayout.LayoutParams) input.getLayoutParams()).leftMargin = T.dp(8);
        pollBtn = btn(c, Ic.has("chat.poll") ? Ic.get("chat.poll", 21, T.textSecondary) : Svg.stroke(21, T.textSecondary, 2, "M4 20V10", "M10 20V4", "M16 20v-6"), 36, "Poll", v -> com.vulpine.at.polls.Polls.create(s.a, s.cid));
        pollBtn.setVisibility(View.GONE);
        form.addView(pollBtn, W.lpm(T.dp(36), T.dp(36), 8, 0, 0, 0));
        rtBtn = btn(c, Svg.fill(21, T.textSecondary, "M5 4v3h5.5v12h3V7H19V4z"), 36, "Rich text formatting", v -> {
            boolean on = tools.getVisibility() != View.VISIBLE;
            tools.setVisibility(on ? View.VISIBLE : View.GONE);
            ((ImageView) v).setImageDrawable(Svg.fill(21, on ? T.accent : T.textSecondary, "M5 4v3h5.5v12h3V7H19V4z"));
        });
        rtBtn.setVisibility(View.GONE);
        form.addView(rtBtn, W.lpm(T.dp(36), T.dp(36), 8, 0, 0, 0));
        gifBtn = btn(c, Ic.has("chat.gif") ? Ic.get("chat.gif", 21, T.textSecondary) : Svg.stroke(21, T.textSecondary, 2, "M3 5h18v14H3z"), 36, "GIF", v -> panel(Panels.GIF));
        gifBtn.setVisibility(View.GONE);
        form.addView(gifBtn, W.lpm(T.dp(36), T.dp(36), 8, 0, 0, 0));
        emojiBtn = btn(c, Ic.has("chat.emoji") ? Ic.get("chat.emoji", 21, T.textSecondary) : Svg.stroke(21, T.textSecondary, 2, "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z"), 36, "Emoji and GIFs", v -> panel(Panels.last));
        form.addView(emojiBtn, W.lpm(T.dp(36), T.dp(36), 8, 0, 0, 0));
        sendWrap = new FrameLayout(c);
        send = new ImageView(c);
        send.setImageDrawable(Ic.has("chat.send") ? Ic.get("chat.send", 18, T.accentFg) : Svg.fill(18, T.accentFg, "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"));
        send.setScaleType(ImageView.ScaleType.CENTER);
        send.setBackground(W.ripple(W.accentBg(999), 999));
        send.setContentDescription("Send");
        send.setOnClickListener(v -> submit());
        sendWrap.addView(send, new FrameLayout.LayoutParams(T.dp(40), T.dp(40)));
        mic = new ImageView(c);
        mic.setImageDrawable(Ic.has("chat.mic") ? Ic.get("chat.mic", 20, T.accentFg) : Svg.stroke(20, T.accentFg, 2, "M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v4", "M8 23h8"));
        mic.setScaleType(ImageView.ScaleType.CENTER);
        mic.setBackground(W.accentBg(999));
        mic.setContentDescription("Record voice message");
        RecUI.attach(this, mic);
        sendWrap.addView(mic, new FrameLayout.LayoutParams(T.dp(40), T.dp(40)));
        form.addView(sendWrap, W.lpm(T.dp(40), T.dp(40), 8, 0, 0, 0));
        LinearLayout.LayoutParams fp = W.lp(W.MP, W.WC);
        fp.bottomMargin = T.dp(28);
        wrap.addView(form, fp);
        wrap.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) s.root.post(s::applyPadding);
        });
        panels = new Panels(this);
        onText();
    }

    boolean imeMultiline() {
        return false;
    }

    ImageView btn(Context c, android.graphics.drawable.Drawable d, int size, String label, View.OnClickListener on) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(d);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription(label);
        b.setBackground(W.ripple(W.oval(0), 99));
        b.setOnClickListener(on);
        return b;
    }

    public void onInsets(int bottom, int ime) {
        navH = bottom;
        imeH = ime;
        boolean kb = ime > 0 || (panels != null && panels.open());
        wrap.setPadding(T.dp(10), 0, T.dp(10), kb ? T.dp(6) : T.dp(10) + 0);
        LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) form.getLayoutParams();
        int want = kb ? 0 : Math.max(T.dp(28), bottom);
        if (fp.bottomMargin != want) {
            fp.bottomMargin = want;
            form.setLayoutParams(fp);
        }
        FrameLayout.LayoutParams wp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
        int bm = panels != null && panels.open() ? (ime > 0 ? ime + panels.searchHeight() : panels.height() + bottom) : ime;
        if (wp.bottomMargin != bm) {
            wp.bottomMargin = bm;
            wrap.setLayoutParams(wp);
        }
        if (panels != null) panels.onIme(panels.open() ? ime : 0);
        if (ime > 0 && (panels == null || !panels.open())) Panels.learn(ime);
    }

    boolean canSend() {
        Chats.Conv c = s.conv;
        if (c == null) return true;
        if (!c.group()) return true;
        JSONObject me = c.member(Session.userId());
        if (me == null) return true;
        if (me.optBoolean("isCreator") || "creator".equals(me.optString("role"))) return true;
        JSONObject perms = rolePerms(c, me.optString("role", "member"));
        return perms == null || perms.optBoolean("sendMessages", true);
    }

    static JSONObject rolePerms(Chats.Conv c, String role) {
        JSONArray rs = c.o.optJSONArray("roles");
        if (rs != null) for (int i = 0; i < rs.length(); i++) {
            JSONObject r = rs.optJSONObject(i);
            if (r != null && role.equals(r.optString("id"))) return r.optJSONObject("permissions");
        }
        return null;
    }

    boolean isAdmin() {
        Chats.Conv c = s.conv;
        if (c == null || !c.group()) return false;
        JSONObject me = c.member(Session.userId());
        if (me == null) return false;
        String r = me.optString("role");
        return me.optBoolean("isCreator") || "creator".equals(r) || "admin".equals(r);
    }

    boolean canPoll() {
        Chats.Conv c = s.conv;
        if (c == null || !c.group()) return false;
        if (isAdmin()) return true;
        JSONObject me = c.member(Session.userId());
        if (me == null) return false;
        JSONObject perms = rolePerms(c, me.optString("role", "member"));
        return perms != null && perms.optBoolean("createPolls");
    }

    String hintText = "";

    void hint(String h) {
        hintText = h;
        int w = input.getWidth() - input.getPaddingLeft() - input.getPaddingRight();
        input.setHint(w > 0 ? android.text.TextUtils.ellipsize(h, input.getPaint(), w, android.text.TextUtils.TruncateAt.END) : h);
    }

    void refresh() {
        boolean can = canSend();
        input.setEnabled(can);
        if (!can) hint("You don't have permission to send messages in this group.");
        else if (!Rt.I.authed()) hint("Connecting…");
        else hint("Type something...");
        attach.setVisibility(can ? View.VISIBLE : View.GONE);
        sendWrap.setVisibility(can ? View.VISIBLE : View.GONE);
        pollBtn.setTag(null);
        botBtn.setTag(Mentions.hasBotCommands(s.cid) ? "y" : null);
        Mentions.loadCommands(s.cid, () -> {
            boolean on = Mentions.hasBotCommands(s.cid);
            if (on == (botBtn.getTag() != null)) return;
            botBtn.setTag(on ? "y" : null);
            onText();
        });
        onText();
    }

    void onText() {
        boolean has = input.getText().toString().trim().length() > 0;
        if (input.length() == 0) {
            if (input.getMaxLines() != 1) input.setMaxLines(1);
        } else if (input.getMaxHeight() != T.dp(120)) input.setMaxHeight(T.dp(120));
        mic.setVisibility(has || editing != null ? View.GONE : View.VISIBLE);
        float target = has ? 0 : 1;
        for (View b : new View[]{botBtn, emojiBtn, pollBtn}) {
            if ((b == pollBtn || b == botBtn) && b.getTag() == null) {
                b.setVisibility(View.GONE);
                continue;
            }
            collapse(b, has);
        }
        rtBtn.setVisibility(has || tools.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
        W.enable(send, has && Rt.I.authed());
        send.setAlpha(has && Rt.I.authed() ? 1f : .4f);
        if (has && System.currentTimeMillis() - lastTyping > 2500) {
            lastTyping = System.currentTimeMillis();
            Chats.I.typing(s.cid, true);
        }
        Mentions.check(this);
    }

    void collapse(View b, boolean hide) {
        ViewGroup.LayoutParams lp = b.getLayoutParams();
        int want = hide ? 0 : T.dp(36);
        if (lp.width == want && b.getVisibility() == View.VISIBLE) return;
        b.setVisibility(View.VISIBLE);
        int from = lp.width < 0 ? T.dp(36) : lp.width;
        Ov.anim(b, from, want, 180, x -> {
            ViewGroup.LayoutParams q = b.getLayoutParams();
            q.width = Math.round((float) x.getAnimatedValue());
            float p = q.width / (float) T.dp(36);
            if (q instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) q).leftMargin = Math.round(T.dp(8) * p);
            b.setLayoutParams(q);
            b.setAlpha(p);
            b.setScaleX(.6f + .4f * p);
            b.setScaleY(.6f + .4f * p);
        });
    }

    void panel(int kind) {
        panels.toggle(kind);
    }

    public void reply(Chats.Msg m) {
        editing = null;
        replyTo = m;
        String name = m.mine() ? "yourself" : s.senderName(m);
        bannerTitle.setText("Replying to " + name);
        String snip;
        if (m.text == null) snip = "🔒 Encrypted message";
        else {
            Fmt.Parsed p = Fmt.parse(m.text);
            snip = !p.atts.isEmpty() ? "[media]" + (p.body.trim().isEmpty() ? "" : " " + p.body.trim()) : p.body;
        }
        bannerSub.setText(snip.replaceAll("\\s+", " "));
        bannerSub.setVisibility(View.VISIBLE);
        banner.setVisibility(View.VISIBLE);
        focus();
    }

    public void edit(Chats.Msg m) {
        replyTo = null;
        editing = m;
        Fmt.Parsed p = Fmt.parse(m.text);
        input.setText(p.body);
        input.setSelection(input.length());
        bannerTitle.setText("Editing message — Esc to cancel");
        bannerSub.setVisibility(View.GONE);
        banner.setVisibility(View.VISIBLE);
        onText();
        focus();
    }

    String replyPrefix() {
        if (replyTo == null) return "";
        Chats.Msg r = replyTo;
        String author = r.mine() ? "You" : s.senderName(r);
        String snip = r.text == null ? "" : r.text;
        Fmt.Parsed rp = r.text == null ? null : Fmt.parse(r.text);
        if (rp != null && rp.replyAuthor != null) snip = rp.body;
        snip = snip.replaceAll("\\s+", " ").trim();
        if (snip.length() > 120) snip = snip.substring(0, 120);
        return "»|" + author + "\n»|" + (r.id != null ? "#" + r.id + "#" : "") + snip + "\n";
    }

    public String takeReply() {
        String p = replyPrefix();
        if (replyTo != null) {
            replyTo = null;
            if (editing == null) banner.setVisibility(View.GONE);
        }
        return p;
    }

    void clearMode() {
        if (editing != null) input.setText("");
        replyTo = null;
        editing = null;
        banner.setVisibility(View.GONE);
        onText();
    }

    void focus() {
        input.postDelayed(() -> W.showKb(input), 60);
        input.postDelayed(() -> {
            input.requestFocus();
            input.setSelection(input.length());
        }, 260);
    }

    boolean onBack() {
        if (AttachSheet.close()) return true;
        if (Mentions.close(this)) return true;
        if (panels.open()) {
            panels.close();
            return true;
        }
        if (banner.getVisibility() == View.VISIBLE) {
            clearMode();
            return true;
        }
        return false;
    }

    void destroy() {
        AttachSheet.close();
        panels.close();
        RecUI.cancel(this);
    }

    public void sendText(String text) {
        input.setText(text);
        submit();
    }

    static final Pattern PING = Pattern.compile("(?:^|\\s)@(everyone|here)(?![a-z0-9_])");
    static final Pattern MENTION = Pattern.compile("(?:^|\\s)@([a-z0-9_]{3,20})");

    boolean keyWait;

    void submit() {
        String raw = input.getText().toString();
        String text = raw.trim();
        if (text.isEmpty()) return;
        Chats.Conv kc = Chats.I.conv(s.cid);
        if (kc != null && kc.key == null) {
            if (keyWait) return;
            keyWait = true;
            App.bg(() -> {
                Chats.I.key(kc);
                App.ui(() -> {
                    keyWait = false;
                    if (kc.key != null) submit();
                    else Ov.toast("Could not send message.", true);
                });
            });
            return;
        }
        if (!canSend()) {
            Ov.toast("You don't have permission to send messages in this group", true);
            return;
        }
        boolean pro = Session.s != null && Session.s.optBoolean("pro");
        int max = pro ? 4000 : 2000;
        if (text.length() > max) {
            Ov.toast("Message too long (max " + max + " characters)", true);
            return;
        }
        if (editing != null && !text.startsWith("/")) {
            Chats.Msg m = editing;
            Fmt.Parsed old = Fmt.parse(m.text);
            String full = text;
            if (old.replyAuthor != null) full = "»|" + old.replyAuthor + "\n»|" + (old.replyId != null ? "#" + old.replyId + "#" : "") + old.replySnippet + "\n" + text;
            if (!Rt.I.authed()) {
                Ov.toast("Not connected. Reconnecting…", true);
                return;
            }
            if (!Chats.I.edit(s.cid, m.id, full)) {
                Ov.toast("Could not edit message.", true);
                return;
            }
            m.text = full;
            m.editedAt = System.currentTimeMillis();
            input.setText("");
            clearMode();
            s.rebuild(false);
            return;
        }
        if (s.conv != null && s.conv.group()) {
            String bl = s.conv.o.optString("wordBlacklist", "");
            for (String w : bl.split("[,\\n]")) {
                String ww = w.trim().toLowerCase(Locale.ROOT);
                if (!ww.isEmpty() && text.toLowerCase(Locale.ROOT).matches("(?s).*\\b" + Pattern.quote(ww) + "\\b.*")) {
                    Ov.toast("This group blocks the word \"" + w.trim() + "\"", true);
                    return;
                }
            }
        }
        if (Commands.handle(this, text)) {
            input.setText("");
            return;
        }
        String body = replyPrefix() + text;
        List<String> mentions = new ArrayList<>();
        if (s.conv != null && s.conv.group()) {
            Matcher mm = MENTION.matcher(text);
            JSONArray mem = s.conv.o.optJSONArray("members");
            while (mm.find()) if (mem != null) for (int i = 0; i < mem.length(); i++) {
                JSONObject o = mem.optJSONObject(i);
                if (o != null && mm.group(1).equalsIgnoreCase(o.optString("handle")) && !mentions.contains(o.optString("id"))) mentions.add(o.optString("id"));
            }
        }
        String fb = body;
        if (text.contains("/uploads/")) App.bg(() -> Forward.grant(s.cid, fb));
        String ping = null;
        if (s.conv != null && s.conv.group() && isAdmin()) {
            Matcher pm = PING.matcher(text);
            while (pm.find()) {
                if ("everyone".equals(pm.group(1))) ping = "everyone";
                else if (ping == null) ping = "here";
            }
        }
        Chats.Msg m = Chats.I.send(s.cid, body, mentions, ping);
        if (m == null) {
            Ov.toast("Could not send message.", true);
            return;
        }
        input.setText("");
        replyTo = null;
        banner.setVisibility(View.GONE);
        s.rebuild(true);
        s.animateLast();
    }

    public void sendRaw(String body) {
        Chats.Conv kc = Chats.I.conv(s.cid);
        if (kc != null && kc.key == null) {
            App.bg(() -> {
                Chats.I.key(kc);
                App.ui(() -> {
                    if (kc.key != null) sendRaw(body);
                    else Ov.toast("Not connected - try again", true);
                });
            });
            return;
        }
        Chats.Msg m = Chats.I.send(s.cid, body, null);
        if (m == null) {
            Ov.toast("Not connected - try again", true);
            return;
        }
        s.rebuild(true);
        s.animateLast();
    }
}
