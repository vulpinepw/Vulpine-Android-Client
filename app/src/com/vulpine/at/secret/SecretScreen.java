package com.vulpine.at.secret;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.chat.Attach;
import com.vulpine.at.chat.Bubble;
import com.vulpine.at.chat.ChatScreen;
import com.vulpine.at.chat.Emoji;
import com.vulpine.at.chat.Jumbo;
import com.vulpine.at.chat.Links;
import com.vulpine.at.chat.Md;
import com.vulpine.at.chat.Media;
import com.vulpine.at.chat.MsgView;
import com.vulpine.at.chat.Panels;
import com.vulpine.at.chat.Reactions;
import com.vulpine.at.chat.Viewer;
import com.vulpine.at.chat.Voice;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.BgView;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.Screen;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecretScreen extends Screen implements Panels.Host {
    public static SecretScreen I;
    public final String id;
    Sec.Chat chat;
    FrameLayout root, header, body;
    AvatarView hav;
    TextView title, sub;
    ImageView trash;
    ListView list;
    final List<Object> items = new ArrayList<>();
    final Map<String, View> cache = new HashMap<>();
    final Map<String, String> sig = new HashMap<>();
    Adapter ad;
    boolean stick = true;
    View topFade, bottomFade;
    LinearLayout wrap, form, bar, recRow;
    TextView banner, barTitle, barSub;
    EditText input;
    ImageView attach, gifBtn, emojiBtn, sendBtn;
    TextView gifTxt;
    Panels panels;
    Sec.Msg replyTo, editing;
    long lastTy;
    int navH, imeH;
    TextView recTime;
    MediaPlayer player;
    String playing;
    final Runnable recTick = new Runnable() {
        public void run() {
            if (recTime == null || !Voice.Recorder.active) return;
            long s = Voice.Recorder.elapsed() / 1000;
            recTime.setText(s / 60 + ":" + String.format(java.util.Locale.US, "%02d", s % 60));
            App.H.postDelayed(this, 500);
        }
    };
    final Runnable typingTick = this::refreshHeader;

    public SecretScreen(String id) {
        this.id = id;
    }

    static int vsec() {
        return 0xff22c55e;
    }

    static android.graphics.drawable.Drawable lock(int px, int color) {
        return new Svg.Icon(0, 0, 24, 24, T.dp(px), color, 2.4f).add("M4.5 10.5h15v10.5h-15z", false).add("M7.5 10.5V7.5a4.5 4.5 0 0 1 9 0v3", false);
    }

    public Context ctx() {
        return a;
    }

    public FrameLayout root() {
        return root;
    }

    public String cid() {
        return "secret:" + id;
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

    public void sendRaw(String body) {
        if (!live()) {
            offline();
            return;
        }
        stick = true;
        Sec.sendText(chat, body, null);
    }

    public View build() {
        I = this;
        Sec.wire();
        Sec.active = id;
        chat = Sec.chats.get(id);
        Context c = a;
        root = new Page.EdgeFrame(c, this);
        root.setBackgroundColor(T.bg);
        root.addView(new BgView(c, false), new FrameLayout.LayoutParams(W.MP, W.MP));
        body = new FrameLayout(c);
        root.addView(body, new FrameLayout.LayoutParams(W.MP, W.MP));
        topFade = new View(c);
        topFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{T.mixA(T.bg, .92f), T.mixA(T.bg, .55f), 0}));
        root.addView(topFade, new FrameLayout.LayoutParams(W.MP, T.dp(96)));
        buildHeader(c);
        Sec.on(sl);
        render();
        if (chat != null && chat.unlocked()) Sec.markRead(chat);
        return root;
    }

    final Sec.L sl = (ev, cid) -> {
        if ("closed".equals(ev) && SecretScreen.this.id.equals(cid)) {
            if (Sec.closedByMe.contains(SecretScreen.this.id)) return;
            if (a != null && a.top() == this) a.pop();
            return;
        }
        if (cid != null && !SecretScreen.this.id.equals(cid)) return;
        Sec.Chat now = Sec.chats.get(SecretScreen.this.id);
        boolean was = chat != null && chat.unlocked(), is = now != null && now.unlocked();
        if (now != chat || was != is || (body.getChildCount() > 0 && body.getChildAt(0).getTag() == null && now != null && now.unlocked())) {
            chat = now;
            render();
            return;
        }
        chat = now;
        refreshHeader();
        if ("msgs".equals(ev) || "progress".equals(ev) || "unlocked".equals(ev)) {
            rebuild();
            if (chat != null && chat.unlocked() && App.fg && "msgs".equals(ev)) Sec.markRead(chat);
        }
        if ("presence".equals(ev) || "list".equals(ev)) refreshComposer();
        if ("typing".equals(ev)) {
            App.H.removeCallbacks(typingTick);
            App.H.postDelayed(typingTick, 5100);
        }
    };

    String name() {
        return chat == null ? "" : chat.peerName();
    }

    boolean live() {
        return chat != null && chat.live();
    }

    void offline() {
        Ov.toast(name() + " is not in the chat right now. Secret messages only go through while you are both here.");
    }

    void buildHeader(Context c) {
        header = new FrameLayout(c);
        header.setBackground(W.round(T.glassBg, 999, T.glassStroke, .5f));
        header.setElevation(T.dpf(6));
        LinearLayout row = W.row(c);
        row.setPadding(T.dp(10), T.dp(5), T.dp(10), T.dp(5));
        ImageView back = new ImageView(c);
        back.setImageDrawable(Ic.has("chat.back") ? Ic.get("chat.back", 22, T.text) : Svg.stroke(22, T.text, 2, "M15 18l-6-6 6-6"));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("Back to chats");
        back.setOnClickListener(v -> a.onBackPressed());
        W.press(back, .88f);
        row.addView(back, W.lp(T.dp(36), T.dp(36)));
        LinearLayout pill = W.row(c);
        pill.setPadding(T.dp(8), T.dp(3), T.dp(12), T.dp(3));
        FrameLayout aw = new FrameLayout(c);
        aw.setClipChildren(false);
        hav = new AvatarView(c);
        hav.textSize(13);
        aw.addView(hav, new FrameLayout.LayoutParams(T.dp(32), T.dp(32)));
        aw.addView(lockBadge(c), lockLp());
        pill.addView(aw, W.lp(T.dp(32), T.dp(32)));
        LinearLayout tx = W.col(c);
        LinearLayout tr = W.row(c);
        ImageView tl = new ImageView(c);
        tl.setImageDrawable(lock(13, vsec()));
        tr.addView(tl, W.lpm(T.dp(13), T.dp(13), 0, 0, 4, 0));
        title = W.text(c, "", 14, 600, T.text);
        W.ellipsize(title);
        T.lh(title, 21);
        tr.addView(title);
        tx.addView(tr);
        sub = W.text(c, "", 13, 400, T.textMuted);
        W.ellipsize(sub);
        T.lh(sub, 20);
        tx.addView(sub);
        pill.addView(tx, W.lpm(0, W.WC, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
        row.addView(pill, W.lp(0, W.WC, 1));
        trash = new ImageView(c);
        trash.setImageDrawable(Svg.stroke(20, T.textSecondary, 2, "M4 7h16", "M9 7V4.8h6V7", "M6.5 7l1 13h9l1-13"));
        trash.setScaleType(ImageView.ScaleType.CENTER);
        trash.setContentDescription("Close secret chat");
        trash.setOnClickListener(v -> askClose());
        W.press(trash, .92f);
        row.addView(trash, W.lpm(T.dp(38), T.dp(38), 8, 0, 0, 0));
        header.addView(row, new FrameLayout.LayoutParams(W.MP, W.WC));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.TOP);
        hp.leftMargin = hp.rightMargin = T.dp(10);
        root.addView(header, hp);
    }

    static View lockBadge(Context c) {
        ImageView l = new ImageView(c);
        l.setImageDrawable(lock(9, 0xffffffff));
        l.setScaleType(ImageView.ScaleType.CENTER);
        l.setBackground(W.oval(vsec()));
        l.setElevation(T.dpf(2));
        return l;
    }

    static FrameLayout.LayoutParams lockLp() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(T.dp(16), T.dp(16), Gravity.BOTTOM | Gravity.END);
        lp.rightMargin = -T.dp(3);
        lp.bottomMargin = -T.dp(3);
        return lp;
    }

    void refreshHeader() {
        if (chat == null) {
            hav.set("?", null, null);
            title.setText("");
            sub.setText("");
            trash.setVisibility(View.GONE);
            return;
        }
        trash.setVisibility(View.VISIBLE);
        JSONGet o = new JSONGet(chat);
        hav.set(name().isEmpty() ? "?" : name(), o.color(), o.url());
        title.setText(name());
        String s;
        boolean green = false;
        if (!chat.unlocked()) s = "Secret chat";
        else if (chat.pending()) s = "Waiting for " + name() + " to join";
        else if (chat.typing()) {
            s = "typing...";
            green = false;
        } else if (chat.peerOnline) s = "in the secret chat";
        else s = "not in the chat";
        sub.setText(s);
        sub.setTextColor(green ? vsec() : T.textMuted);
    }

    static final class JSONGet {
        final Sec.Chat c;

        JSONGet(Sec.Chat c) {
            this.c = c;
        }

        String color() {
            String v = c.o.optString("peerAvatarColor", "");
            return v.isEmpty() ? null : v;
        }

        String url() {
            String v = c.o.optString("peerAvatarUrl", "");
            return v.isEmpty() ? null : v;
        }
    }

    void askClose() {
        if (chat == null) return;
        Sec.Chat c = chat;
        Ov.confirm("Close secret chat?", "All messages and files of this secret chat are deleted on both devices. This cannot be undone.", "Close chat", true, () -> App.bg(() -> {
            boolean ok = Sec.close(c);
            App.ui(() -> {
                if (ok) {
                    Ov.toast("Secret chat closed");
                    if (a != null && a.top() == this) a.pop();
                } else Ov.toast("Could not close the secret chat", true);
            });
        }));
    }

    void render() {
        body.removeAllViews();
        cache.clear();
        sig.clear();
        wrap = null;
        list = null;
        if (panels != null) panels.close();
        panels = null;
        refreshHeader();
        Context c = a;
        if (chat == null) {
            LinearLayout e = W.col(c);
            e.setGravity(Gravity.CENTER);
            e.setPadding(T.dp(24), 0, T.dp(24), 0);
            TextView t = W.text(c, Sec.loaded ? "This secret chat is closed" : "Loading...", 15, 600, T.text);
            t.setGravity(Gravity.CENTER);
            e.addView(t);
            TextView h = W.text(c, "Secret messages stay only on the devices where they were received.", 13, 400, T.textMuted);
            h.setGravity(Gravity.CENTER);
            e.addView(h, W.lpm(W.WC, W.WC, 0, 6, 0, 0));
            body.addView(e, new FrameLayout.LayoutParams(W.MP, W.MP));
            if (!Sec.loaded) App.bg(Sec::fetch);
            return;
        }
        if (!chat.unlocked()) {
            body.addView(gate(c), new FrameLayout.LayoutParams(W.MP, W.MP));
            return;
        }
        buildChat(c);
    }

    View gate(Context c) {
        FrameLayout f = new FrameLayout(c);
        f.setPadding(T.dp(16), 0, T.dp(16), 0);
        LinearLayout card = W.col(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(W.round(T.surface, 16));
        card.setElevation(T.dpf(4));
        card.setPadding(T.dp(20), T.dp(24), T.dp(20), T.dp(20));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(lock(30, vsec()));
        ic.setScaleType(ImageView.ScaleType.CENTER);
        ic.setBackground(W.oval(T.mixA(vsec(), .18f)));
        card.addView(ic, W.lp(T.dp(60), T.dp(60)));
        boolean invite = chat.pending() && !chat.mineCreated();
        TextView t = W.text(c, invite ? name() + " invited you to a secret chat" : "Secret chat with " + name(), 17, 600, T.text);
        t.setGravity(Gravity.CENTER);
        card.addView(t, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        TextView d = W.text(c, invite ? "Enter the password you agreed on with " + name() + ". Messages are only kept on this device." : "Enter the password to open this chat on this device.", 14, 400, T.textSecondary);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(0, 1.3f);
        card.addView(d, W.lpm(W.WC, W.WC, 0, 8, 0, 8));
        W.Field pw = new W.Field(c, "Password", null);
        pw.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pw.input.setImeOptions(EditorInfo.IME_ACTION_GO);
        card.addView(pw, W.lp(W.MP, W.WC));
        TextView err = W.text(c, " ", 13, 400, T.danger);
        err.setGravity(Gravity.CENTER);
        card.addView(err, W.lpm(W.MP, W.WC, 0, 8, 0, 8));
        TextView go = W.btn(c, invite ? "Join" : "Unlock", true, null);
        W.enable(go, false);
        card.addView(go, W.lp(W.MP, T.dp(40)));
        String label = invite ? "Join" : "Unlock";
        boolean[] busy = {false};
        Runnable submit = () -> {
            String p = pw.value();
            if (busy[0] || p.isEmpty()) return;
            busy[0] = true;
            go.setText("Checking...");
            W.enable(go, false);
            err.setText(" ");
            Sec.Chat ch = chat;
            App.bg(() -> {
                int r;
                try {
                    r = Sec.unlock(ch, p);
                } catch (Throwable e) {
                    r = 2;
                }
                int rr = r;
                App.ui(() -> {
                    busy[0] = false;
                    go.setText(label);
                    W.enable(go, !pw.value().isEmpty());
                    if (rr == 0) {
                        W.hideKb(pw.input);
                        return;
                    }
                    err.setText(rr == 1 ? "Wrong password" : "Could not open the secret chat");
                    err.setAlpha(0);
                    err.animate().alpha(1).setDuration(180).start();
                    if (rr == 1) shake(pw);
                });
            });
        };
        go.setOnClickListener(v -> submit.run());
        pw.input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int b, int n) {
            }

            public void onTextChanged(CharSequence s, int st, int b, int n) {
            }

            public void afterTextChanged(Editable e) {
                err.setText(" ");
                if (!busy[0]) W.enable(go, e.length() > 0);
            }
        });
        pw.input.setOnEditorActionListener((v, act, e) -> {
            submit.run();
            return true;
        });
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Math.min(T.dp(380), c.getResources().getDisplayMetrics().widthPixels - T.dp(32)), W.WC, Gravity.CENTER);
        f.addView(card, cp);
        card.setAlpha(0);
        card.setTranslationY(T.dp(12));
        card.setScaleX(.98f);
        card.setScaleY(.98f);
        card.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(360).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .3f, 1.1f)).start();
        f.setTag(null);
        pw.post(() -> {
            pw.input.requestFocus();
            W.showKb(pw.input);
        });
        return f;
    }

    static void shake(View v) {
        android.animation.ObjectAnimator an = android.animation.ObjectAnimator.ofFloat(v, "translationX", 0, -T.dp(6), T.dp(6), -T.dp(4), T.dp(4), -T.dp(2), 0);
        an.setDuration(420);
        an.start();
    }

    void buildChat(Context c) {
        FrameLayout f = new FrameLayout(c);
        f.setTag("chat");
        list = new ListView(c) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return super.onTouchEvent(e);
            }
        };
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setStackFromBottom(true);
        list.setItemsCanFocus(true);
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
        ad = new Adapter();
        list.setAdapter(ad);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int st) {
                if (st == SCROLL_STATE_IDLE) stick = atBottom();
            }

            public void onScroll(AbsListView v, int first, int vis, int total) {
                if (list.getChildCount() > 0 && vis > 0 && first + vis < total) stick = false;
                else stick = atBottom();
            }
        });
        list.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (stick) scrollBottom();
        });
        f.addView(list, new FrameLayout.LayoutParams(W.MP, W.MP));
        bottomFade = new View(c);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{T.mixA(T.bg, .92f), T.mixA(T.bg, .55f), 0}));
        f.addView(bottomFade, new FrameLayout.LayoutParams(W.MP, T.dp(128), Gravity.BOTTOM));
        buildComposer(c);
        f.addView(wrap, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.BOTTOM));
        body.addView(f, new FrameLayout.LayoutParams(W.MP, W.MP));
        panels = new Panels(this);
        rebuild();
        refreshComposer();
        onInsets(a.insetTop, a.insetBottom, a.insetIme);
    }

    public boolean atBottom() {
        if (list == null || list.getChildCount() == 0) return true;
        if (list.getLastVisiblePosition() < ad.getCount() - 1) return false;
        View last = list.getChildAt(list.getChildCount() - 1);
        return last.getBottom() - (list.getHeight() - list.getPaddingBottom()) < T.dp(80);
    }

    public void scrollBottom() {
        if (list != null && ad != null && ad.getCount() > 0) list.setSelection(ad.getCount() - 1);
    }

    void rebuild() {
        if (list == null || chat == null) return;
        items.clear();
        items.add("intro");
        long prev = 0;
        for (Sec.Msg m : chat.msgs) {
            if (prev == 0 || !sameDay(prev, m.at)) items.add(new long[]{m.at});
            prev = m.at;
            items.add(m);
        }
        boolean was = stick;
        ad.notifyDataSetChanged();
        if (was) list.post(this::scrollBottom);
    }

    static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(), y = Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    class Adapter extends BaseAdapter {
        public int getCount() {
            return items.size();
        }

        public Object getItem(int i) {
            return items.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public int getItemViewType(int i) {
            return IGNORE_ITEM_VIEW_TYPE;
        }

        public View getView(int i, View cv, ViewGroup p) {
            Object o = items.get(i);
            if ("intro".equals(o)) return cached("intro", "1", () -> intro(a));
            if (o instanceof long[]) {
                long at = ((long[]) o)[0];
                return cached("d" + at, "1", () -> {
                    View d = MsgView.date(a, at);
                    d.setPadding(0, T.dp(12), 0, T.dp(4));
                    return d;
                });
            }
            Sec.Msg m = (Sec.Msg) o;
            String s = m.status + "|" + m.text + "|" + m.edited + "|" + m.rx + "|" + (m.local != null) + "|" + Math.round(m.progress * 50) + "|" + (m.id.equals(playing));
            return cached(m.id, s, () -> message(m));
        }
    }

    interface Make {
        View make();
    }

    View cached(String k, String s, Make mk) {
        View v = cache.get(k);
        if (v != null && s.equals(sig.get(k))) {
            if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
            return v;
        }
        v = mk.make();
        v.setLayoutParams(new AbsListView.LayoutParams(W.MP, W.WC));
        cache.put(k, v);
        sig.put(k, s);
        return v;
    }

    View intro(Context c) {
        FrameLayout f = new FrameLayout(c);
        f.setPadding(0, T.dp(8), 0, T.dp(12));
        LinearLayout card = W.col(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(12));
        card.setBackground(W.round(T.dark ? 0x0fffffff : 0x0d000000, 16));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(lock(22, vsec()));
        card.addView(ic, W.lpm(T.dp(22), T.dp(22), 0, 0, 0, 6));
        TextView t = W.text(c, "Secret chat", 14, 600, T.text);
        card.addView(t, W.lpm(W.WC, W.WC, 0, 0, 0, 4));
        TextView d = W.text(c, "End-to-end encrypted with your shared password. Messages are kept only on this device and never stored on Vulpine.", 13, 400, T.textSecondary);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(0, 1.3f);
        card.addView(d);
        f.addView(card, new FrameLayout.LayoutParams(Math.min(T.dp(320), c.getResources().getDisplayMetrics().widthPixels - T.dp(32)), W.WC, Gravity.CENTER_HORIZONTAL));
        return f;
    }

    View message(Sec.Msg m) {
        Context c = a;
        boolean mine = m.mine();
        String mime = m.file == null ? "" : m.file.optString("mime");
        boolean media = m.file != null && m.ready() && (mime.startsWith("image/") || mime.startsWith("video/"));
        int jumbo = m.file == null && m.text != null && !m.text.isEmpty() ? Md.jumboCount(m.text) : 0;
        Bubble b = new Bubble(c);
        int sw = c.getResources().getDisplayMetrics().widthPixels - T.dp(32);
        b.maxW = Math.round(sw * .75f);
        if (jumbo > 0) b.fill(0);
        else if (mine) {
            int[] d = MsgView.dmBubble();
            if (d.length == 4) b.gradient(new int[]{d[1], d[2]}, d[3]);
            else b.fill(d[0]);
        } else b.fill(T.bubbleIn);
        b.corners(16, 16, 16, 16);
        LinearLayout col = new com.vulpine.at.chat.WrapCol(c);
        int ph = media || jumbo > 0 ? 0 : T.dp(12), pv = media || jumbo > 0 ? 0 : T.dp(8);
        col.setPadding(ph, pv, ph, pv);
        b.addView(col, new FrameLayout.LayoutParams(W.WC, W.WC));
        LinearLayout meta = W.row(c);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView time = W.text(c, Fmt.time(m.at) + (m.edited > 0 ? " · edited" : ""), 9, 400, T.text);
        time.setAlpha(.6f);
        time.setShadowLayer(T.dpf(3), 0, T.dpf(1), 0x73000000);
        time.setSingleLine(true);
        meta.addView(time);
        if (mine) {
            if ("failed".equals(m.status)) {
                TextView fx = W.text(c, "!", 10, 700, 0xffffffff);
                fx.setGravity(Gravity.CENTER);
                fx.setBackground(W.oval(T.danger));
                fx.setContentDescription("Not delivered. Tap to retry");
                fx.setOnClickListener(v -> {
                    if (!live()) {
                        offline();
                        return;
                    }
                    Sec.retry(chat, m);
                });
                meta.addView(fx, W.lpm(T.dp(14), T.dp(14), 3, 0, 0, 0));
            } else {
                android.graphics.drawable.Drawable st;
                float al;
                if ("sending".equals(m.status)) {
                    st = new Svg.Icon(0, 0, 16, 16, T.dp(11), T.text, 1.8f).add("M8 1.8a6.2 6.2 0 1 0 0 12.4 6.2 6.2 0 0 0 0-12.4z", false).add("M8 4.6V8l2.3 1.5", false);
                    al = .55f;
                } else if ("read".equals(m.status)) {
                    st = new Svg.Icon(0, 0, 18, 13, T.dp(11), T.text, 2.5f).add("M1 6.5L5.5 11L13 2", false).add("M6 6.5L10.5 11L18 2", false);
                    al = .9f;
                } else {
                    st = new Svg.Icon(0, 0, 18, 13, T.dp(11), T.text, 2.5f).add("M1 6.5L5.5 11L13 2", false);
                    al = .55f;
                }
                ImageView si = new ImageView(c);
                si.setImageDrawable(st);
                si.setAlpha(al);
                si.setScaleType(ImageView.ScaleType.FIT_CENTER);
                meta.addView(si, W.lpm(T.dp(11), T.dp(11), 3, 0, 0, 0));
            }
        }
        meta.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int timeW = meta.getMeasuredWidth() + T.dp(10);
        if (m.reply != null) col.addView(quote(m, media));
        if (m.file != null) col.addView(file(m));
        if (m.text != null && !m.text.isEmpty()) {
            if (jumbo > 0) {
                TextView j = new TextView(c);
                j.setText(m.text);
                j.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 44);
                j.setLineSpacing(0, 1.25f);
                j.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(18));
                col.addView(j);
            } else {
                TextView t = MsgView.newBody(c);
                Md.Out out = Md.render(m.text, t, mdLinks);
                for (String g : out.gifs) col.addView(gif(g));
                if (out.sb.length() > 0 || out.gifs.isEmpty()) {
                    MsgView.finishBody(t, out.sb, timeW);
                    if (media) t.setPadding(T.dp(12), T.dp(4), T.dp(12), T.dp(8));
                    col.addView(t);
                }
            }
        } else if (m.file == null || media) {
            View sp = new View(c);
            col.addView(sp, W.lp(timeW, T.dp(media ? 0 : 14)));
        }
        if (!m.rx.isEmpty()) col.addView(rxRow(m, media || jumbo > 0));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.BOTTOM | Gravity.END);
        tp.rightMargin = T.dp(6);
        tp.bottomMargin = T.dp(3);
        b.addView(meta, tp);
        b.setOnClickListener(v -> menu(m, v));
        b.setOnLongClickListener(v -> {
            menu(m, v);
            return true;
        });
        LinearLayout row = W.row(c);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(0, T.dp(4), 0, T.dp(4));
        row.addView(b, W.lp(W.WC, W.WC));
        row.setTag(m.id);
        if (!seen.contains(m.id)) {
            seen.add(m.id);
            if (initDone) {
                b.setAlpha(0);
                b.setTranslationY(T.dp(6));
                b.setScaleX(.98f);
                b.setScaleY(.98f);
                b.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(240).setInterpolator(new android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f)).start();
            }
        }
        return row;
    }

    final java.util.Set<String> seen = new java.util.HashSet<>();
    boolean initDone;

    final Md.Links mdLinks = new Md.Links() {
        public void url(String url, boolean ext) {
            Links.open(a, url);
        }

        public void mention(String handle) {
        }
    };

    View quote(Sec.Msg m, boolean media) {
        Context c = a;
        FrameLayout w = new FrameLayout(c);
        LinearLayout q = W.col(c);
        q.setPadding(T.dp(8), 0, 0, 0);
        String who = Session.userId().equals(m.reply.optString("from")) ? "You" : name();
        TextView au = W.text(c, who, 12, 600, T.text);
        W.ellipsize(au);
        TextView sn = W.text(c, m.reply.optString("text"), 12, 400, T.text);
        W.ellipsize(sn);
        q.addView(au);
        q.addView(sn);
        w.addView(q, new FrameLayout.LayoutParams(W.WC, W.WC));
        View line = new View(c);
        line.setBackgroundColor(T.text);
        w.addView(line, new FrameLayout.LayoutParams(T.dp(3), W.MP));
        w.setAlpha(.75f);
        LinearLayout.LayoutParams lp = W.lp(W.WC, W.WC);
        lp.bottomMargin = T.dp(4);
        if (media) {
            lp.leftMargin = lp.rightMargin = T.dp(12);
            lp.topMargin = T.dp(8);
        }
        w.setLayoutParams(lp);
        String rid = m.reply.optString("id");
        w.setOnClickListener(v -> jump(rid));
        return w;
    }

    void jump(String mid) {
        for (int i = 0; i < items.size(); i++) {
            Object o = items.get(i);
            if (o instanceof Sec.Msg && ((Sec.Msg) o).id.equals(mid)) {
                int pos = i;
                stick = false;
                list.smoothScrollToPositionFromTop(pos, list.getHeight() / 3, 300);
                list.postDelayed(() -> {
                    View v = cache.get(mid);
                    if (v instanceof LinearLayout && ((LinearLayout) v).getChildCount() > 0 && ((LinearLayout) v).getChildAt(0) instanceof Bubble) {
                        View bb = ((LinearLayout) v).getChildAt(0);
                        bb.animate().cancel();
                        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0, 1, 0);
                        va.setDuration(1000);
                        va.addUpdateListener(x -> {
                            float f = (float) x.getAnimatedValue();
                            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
                            float s = 1 + .35f * f;
                            cm.setScale(s, s, s, 1);
                            android.graphics.Paint p = new android.graphics.Paint();
                            p.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
                            bb.setLayerType(f > 0 ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, f > 0 ? p : null);
                        });
                        va.start();
                    }
                }, 350);
                return;
            }
        }
    }

    View gif(String url) {
        Context c = a;
        Media.RoundImage iv = new Media.RoundImage(c);
        iv.corners(12, 12, 12, 12);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = W.lpm(T.dp(200), T.dp(150), 0, 4, 0, 4);
        iv.setLayoutParams(lp);
        String src = Media.gifLite(url, "200w");
        Img.get(Http.abs(src), T.dp(220), true, (d, bm) -> {
            int w = d != null ? d.getIntrinsicWidth() : bm != null ? bm.getWidth() : 0;
            int h = d != null ? d.getIntrinsicHeight() : bm != null ? bm.getHeight() : 0;
            if (w > 0 && h > 0) {
                float sc = Math.min(T.dp(200) / (float) w, T.dp(220) / (float) h);
                sc = Math.min(sc, Math.max(1f, T.density * .9f));
                lp.width = Math.round(w * sc);
                lp.height = Math.round(h * sc);
                iv.setLayoutParams(lp);
            }
            Media.setDrawable(iv, d, bm, true);
        });
        List<Fmt.Att> one = new ArrayList<>();
        Fmt.Att at = new Fmt.Att();
        at.name = "GIF";
        at.mime = "image/gif";
        at.url = url;
        one.add(at);
        iv.setOnClickListener(v -> Viewer.open(a, one, 0, null));
        return iv;
    }

    View rxRow(Sec.Msg m, boolean pad) {
        Context c = a;
        LinearLayout row = new Reactions.FlowRow(c);
        if (pad) row.setPadding(T.dp(8), 0, T.dp(8), T.dp(18));
        String me = Session.userId();
        for (Map.Entry<String, List<String>> e : new ArrayList<>(m.rx.entrySet())) {
            String emo = e.getKey();
            List<String> users = e.getValue();
            LinearLayout pill = W.row(c);
            pill.setPadding(T.dp(8), T.dp(1), T.dp(8), T.dp(1));
            pill.setBackground(W.round(users.contains(me) ? T.accentSoft : 0x2e7f7f7f, 999));
            ImageView ei = new ImageView(c);
            ei.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Img.get(Emoji.pngUrl(emo), T.dp(36), false, (d, bm) -> {
                if (bm != null) ei.setImageBitmap(bm);
                else if (d != null) ei.setImageDrawable(d);
            });
            pill.addView(ei, W.lp(T.dp(18), T.dp(20)));
            if (users.size() > 1) {
                TextView n = W.text(c, String.valueOf(users.size()), 11, 600, T.text);
                n.setAlpha(.8f);
                pill.addView(n, W.lpm(W.WC, W.WC, 4, 0, 0, 0));
            }
            pill.setOnClickListener(v -> {
                if (!live()) {
                    offline();
                    return;
                }
                Sec.react(chat, m, emo);
            });
            W.press(pill, .92f);
            row.addView(pill);
        }
        LinearLayout.LayoutParams lp = W.lp(W.WC, W.WC);
        lp.topMargin = T.dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return Math.round(b / 1024.0) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
    }

    View progressBar(float p) {
        Context c = a;
        FrameLayout bar = new FrameLayout(c);
        bar.setBackground(W.round(0x477f7f7f, 99));
        View fill = new View(c);
        fill.setBackground(W.round(T.accent, 99));
        fill.setPivotX(0);
        fill.setScaleX(Math.max(.04f, p));
        bar.addView(fill, new FrameLayout.LayoutParams(W.MP, W.MP));
        bar.setLayoutParams(W.lpm(W.MP, T.dp(3), 0, 6, 0, 0));
        bar.setClipToOutline(true);
        return bar;
    }

    View file(Sec.Msg m) {
        Context c = a;
        String mime = m.file.optString("mime"), fname = m.file.optString("name", "file");
        long size = m.file.optLong("size");
        float pr = m.progress;
        if (!m.ready()) return fileCard(m, fname, pr >= 0 ? Math.round(pr * 100) + "% of " + size(size) : size(size), pr >= 0 ? pr : -1, false);
        if (mime.startsWith("image/")) {
            LinearLayout w = W.col(c);
            Media.RoundImage iv = new Media.RoundImage(c);
            iv.corners(16, 16, 16, 16);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int maxW = Math.round((c.getResources().getDisplayMetrics().widthPixels - T.dp(32)) * .75f);
            LinearLayout.LayoutParams lp = W.lp(Math.min(maxW, T.dp(240)), T.dp(180));
            iv.setLayoutParams(lp);
            plain(m, f -> Img.get("file:" + f.getPath(), Math.max(maxW, T.dp(360)), "image/gif".equals(mime), (d, bm) -> {
                int iw = d != null ? d.getIntrinsicWidth() : bm != null ? bm.getWidth() : 0;
                int ih = d != null ? d.getIntrinsicHeight() : bm != null ? bm.getHeight() : 0;
                if (iw > 0 && ih > 0) {
                    float sc = Math.min(maxW / (float) iw, T.dp(360) / (float) ih);
                    lp.width = Math.max(T.dp(80), Math.round(iw * sc));
                    lp.height = Math.max(T.dp(60), Math.round(ih * sc));
                    iv.setLayoutParams(lp);
                }
                Media.setDrawable(iv, d, bm, true);
            }));
            iv.setOnClickListener(v -> plain(m, f -> {
                List<Fmt.Att> one = new ArrayList<>();
                Fmt.Att at = new Fmt.Att();
                at.name = fname;
                at.mime = mime;
                at.url = "file:" + f.getPath();
                one.add(at);
                Viewer.open(a, one, 0, null);
            }));
            iv.setOnLongClickListener(v -> {
                menu(m, v);
                return true;
            });
            w.addView(iv);
            if (m.mine() && m.progress >= 0) w.addView(progressBar(m.progress));
            return w;
        }
        if (mime.startsWith("video/")) {
            FrameLayout fl = new FrameLayout(c);
            int maxW = Math.round((c.getResources().getDisplayMetrics().widthPixels - T.dp(32)) * .75f);
            fl.setLayoutParams(W.lp(Math.min(maxW, T.dp(260)), T.dp(180)));
            fl.setBackground(W.round(0xff000000, 16));
            ImageView th = new Media.RoundImage(c);
            ((Media.RoundImage) th).corners(16, 16, 16, 16);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            fl.addView(th, new FrameLayout.LayoutParams(W.MP, W.MP));
            plain(m, f -> App.bg(() -> {
                android.graphics.Bitmap bm = null;
                try {
                    android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                    r.setDataSource(f.getPath());
                    bm = r.getFrameAtTime(0);
                    r.release();
                } catch (Throwable e) {
                }
                android.graphics.Bitmap fb = bm;
                if (fb != null) App.ui(() -> th.setImageBitmap(fb));
            }));
            ImageView play = new ImageView(c);
            play.setImageDrawable(Svg.fill(26, 0xffffffff, "M8 5v14l11-7z"));
            play.setScaleType(ImageView.ScaleType.CENTER);
            play.setBackground(W.oval(0x73000000));
            fl.addView(play, new FrameLayout.LayoutParams(T.dp(52), T.dp(52), Gravity.CENTER));
            fl.setOnClickListener(v -> plain(m, f -> {
                List<Fmt.Att> one = new ArrayList<>();
                Fmt.Att at = new Fmt.Att();
                at.name = fname;
                at.mime = mime;
                at.url = "file:" + f.getPath();
                one.add(at);
                Viewer.open(a, one, 0, null);
            }));
            fl.setOnLongClickListener(v -> {
                menu(m, v);
                return true;
            });
            if (m.mine() && m.progress >= 0) {
                LinearLayout w = W.col(c);
                w.addView(fl);
                w.addView(progressBar(m.progress));
                return w;
            }
            return fl;
        }
        if (mime.startsWith("audio/")) return audio(m);
        View card = fileCard(m, fname, size(size), m.mine() && m.progress >= 0 ? m.progress : -1, true);
        card.setOnClickListener(v -> plain(m, f -> com.vulpine.at.Files.saveFile(a, f, fname, mime)));
        card.setOnLongClickListener(v -> {
            menu(m, v);
            return true;
        });
        return card;
    }

    View fileCard(Sec.Msg m, String name, String line, float pr, boolean ready) {
        Context c = a;
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumWidth(T.dp(200));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Svg.stroke(20, T.text, 2, "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6"));
        ic.setScaleType(ImageView.ScaleType.CENTER);
        ic.setBackground(W.round(0x2e000000, 12));
        r.addView(ic, W.lp(T.dp(38), T.dp(38)));
        LinearLayout tx = W.col(c);
        TextView n = W.text(c, name, 14, 400, T.text);
        W.ellipsize(n);
        tx.addView(n);
        TextView s = W.text(c, line, 13, 400, T.text);
        s.setAlpha(.7f);
        tx.addView(s, W.lpm(W.WC, W.WC, 0, 1, 0, 0));
        if (pr >= 0) tx.addView(progressBar(pr));
        r.addView(tx, W.lpm(0, W.WC, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
        r.setLayoutParams(new LinearLayout.LayoutParams(Math.min(T.dp(300), Math.round((a.getResources().getDisplayMetrics().widthPixels - T.dp(32)) * .75f) - T.dp(24)), W.WC));
        if (!ready) {
            r.setOnClickListener(v -> menu(m, v));
        }
        return r;
    }

    View audio(Sec.Msg m) {
        Context c = a;
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        boolean on = m.id.equals(playing) && player != null;
        ImageView pb = new ImageView(c);
        pb.setImageDrawable(on && player.isPlaying() ? Svg.fill(18, T.accentFg, "M6 5h4v14H6z", "M14 5h4v14h-4z") : Svg.fill(18, T.accentFg, "M8 5v14l11-7z"));
        pb.setScaleType(ImageView.ScaleType.CENTER);
        pb.setBackground(W.oval(T.accent));
        r.addView(pb, W.lp(T.dp(36), T.dp(36)));
        SeekBar sb = new SeekBar(c);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(T.accent));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(T.accent));
        sb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(T.mixA(T.text, .3f)));
        r.addView(sb, W.lp(0, W.WC, 1));
        TextView tm = W.text(c, "0:00", 12, 400, T.text);
        tm.setAlpha(.7f);
        tm.setFontFeatureSettings("tnum");
        r.addView(tm, W.lpm(W.WC, W.WC, 4, 0, 0, 0));
        r.setLayoutParams(new LinearLayout.LayoutParams(Math.min(T.dp(280), Math.round((c.getResources().getDisplayMetrics().widthPixels - T.dp(32)) * .75f) - T.dp(24)), W.WC));
        LinearLayout w = W.col(c);
        w.addView(r);
        if (m.mine() && m.progress >= 0) w.addView(progressBar(m.progress));
        w.setPadding(0, 0, 0, T.dp(12));
        if (on) {
            sb.setMax(Math.max(1, player.getDuration()));
            Runnable[] tick = new Runnable[1];
            tick[0] = () -> {
                if (player == null || !m.id.equals(playing) || !sb.isAttachedToWindow()) return;
                try {
                    sb.setProgress(player.getCurrentPosition());
                    tm.setText(Fmt.dur(player.getCurrentPosition() / 1000.0));
                } catch (Exception e) {
                }
                sb.postDelayed(tick[0], 250);
            };
            sb.post(tick[0]);
        } else {
            plain(m, f -> App.bg(() -> {
                try {
                    android.media.MediaMetadataRetriever mr = new android.media.MediaMetadataRetriever();
                    mr.setDataSource(f.getPath());
                    String d = mr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    mr.release();
                    if (d != null) App.ui(() -> tm.setText(Fmt.dur(Long.parseLong(d) / 1000.0)));
                } catch (Throwable e) {
                }
            }));
        }
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && player != null && m.id.equals(playing)) player.seekTo(p);
            }

            public void onStartTrackingTouch(SeekBar s) {
            }

            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        pb.setOnClickListener(v -> {
            if (m.id.equals(playing) && player != null) {
                if (player.isPlaying()) player.pause();
                else player.start();
                rebuildOne(m);
                return;
            }
            plain(m, f -> {
                stopPlayer();
                try {
                    Voice.stop();
                    MediaPlayer mp = new MediaPlayer();
                    mp.setDataSource(f.getPath());
                    mp.setOnCompletionListener(x -> {
                        stopPlayer();
                        rebuildOne(m);
                    });
                    mp.prepare();
                    mp.start();
                    player = mp;
                    playing = m.id;
                } catch (Exception e) {
                    Ov.toast("Could not play this file", true);
                }
                rebuildOne(m);
            });
        });
        return w;
    }

    void rebuildOne(Sec.Msg m) {
        sig.remove(m.id);
        if (ad != null) ad.notifyDataSetChanged();
    }

    void stopPlayer() {
        MediaPlayer p = player;
        player = null;
        String was = playing;
        playing = null;
        if (p != null) try {
            p.release();
        } catch (Exception e) {
        }
        if (was != null) sig.remove(was);
    }

    interface FileCb {
        void on(File f);
    }

    static File tmpDir() {
        File d = new File(App.ctx.getCacheDir(), "sec-tmp");
        d.mkdirs();
        return d;
    }

    public static void clearTmp() {
        File[] fs = new File(App.ctx.getCacheDir(), "sec-tmp").listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    void plain(Sec.Msg m, FileCb cb) {
        if (m.file == null) return;
        String ext = "";
        String n = m.file.optString("name");
        int dot = n.lastIndexOf('.');
        if (dot > 0 && n.length() - dot <= 6) ext = n.substring(dot).replaceAll("[^A-Za-z0-9.]", "");
        File out = new File(tmpDir(), Sec.safe(m.file.optString("f")) + ext);
        if (out.exists() && out.length() > 0) {
            cb.on(out);
            return;
        }
        Sec.Chat ch = chat;
        App.bg(() -> {
            byte[] b = Sec.fileBytes(ch, m);
            if (b == null) return;
            try (FileOutputStream o = new FileOutputStream(out)) {
                o.write(b);
            } catch (Exception e) {
                return;
            }
            App.ui(() -> cb.on(out));
        });
    }

    void menu(Sec.Msg m, View anchor) {
        if (chat == null) return;
        boolean own = m.mine();
        Ov.Menu mn = new Ov.Menu(a);
        mn.item("Reply", Svg.stroke(18, T.text, 2, "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5V20"), false, () -> {
            replyTo = m;
            editing = null;
            showBar();
            input.requestFocus();
            W.showKb(input);
        });
        if (own && m.file == null && m.text != null && !m.text.isEmpty()) mn.item("Edit", Svg.stroke(18, T.text, 2, "M12 20h9", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z"), false, () -> {
            if (!live()) {
                offline();
                return;
            }
            editing = m;
            replyTo = null;
            input.setText(m.text);
            input.setSelection(input.length());
            showBar();
            input.requestFocus();
            W.showKb(input);
        });
        mn.item("React", Svg.stroke(18, T.text, 2, "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M8 14s1.5 2 4 2 4-2 4-2", "M9 9h.01", "M15 9h.01"), false, () -> {
            if (!live()) {
                offline();
                return;
            }
            reactPicker(m, anchor);
        });
        if (m.text != null && !m.text.isEmpty()) mn.item("Copy", Svg.stroke(18, T.text, 2, "M8 8h11v11H8z", "M16 8V5H5v11h3"), false, () -> {
            ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("text", m.text));
            Ov.toast("Copied");
        });
        if (own && "failed".equals(m.status)) mn.item("Retry", Svg.stroke(18, T.text, 2, "M1 4v6h6", "M3.51 15a9 9 0 1 0 2.13-9.36L1 10"), false, () -> {
            if (!live()) {
                offline();
                return;
            }
            Sec.retry(chat, m);
        });
        mn.item("Delete", Svg.stroke(18, T.danger, 2, "M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6"), true, () -> {
            if (!live()) {
                offline();
                return;
            }
            Ov.confirm("Delete message?", "This message is deleted on both devices.", "Delete", true, () -> {
                Sec.Chat ch = chat;
                App.bg(() -> {
                    boolean ok = Sec.delete(ch, m);
                    if (!ok) App.ui(() -> Ov.toast("Could not delete the message", true));
                });
            });
        });
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        mn.showAt(own ? l[0] + anchor.getWidth() : l[0], l[1] + anchor.getHeight() / 2, own);
    }

    void reactPicker(Sec.Msg m, View anchor) {
        Context c = a;
        Ov.Sheet s = new Ov.Sheet(c, false);
        LinearLayout r = W.row(c);
        r.setGravity(Gravity.CENTER);
        for (String e : Emoji.REACTIONS) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(T.dp(6), T.dp(6), T.dp(6), T.dp(6));
            Img.get(Emoji.pngUrl(e), T.dp(64), false, (d, bm) -> {
                if (bm != null) iv.setImageBitmap(bm);
                else if (d != null) iv.setImageDrawable(d);
            });
            iv.setContentDescription(e);
            iv.setOnClickListener(v -> {
                s.close();
                Sec.react(chat, m, e);
            });
            W.press(iv, .88f);
            r.addView(iv, W.lp(0, T.dp(44), 1));
        }
        s.body.addView(r, W.lp(W.MP, W.WC));
        s.show();
    }

    void buildComposer(Context c) {
        wrap = W.col(c);
        wrap.setPadding(T.dp(10), 0, T.dp(10), T.dp(10));
        banner = W.text(c, "", 13, 400, T.textSecondary);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(T.dp(14), T.dp(6), T.dp(14), T.dp(6));
        banner.setBackground(W.round(T.mixA(T.surface, .8f), 999));
        LinearLayout.LayoutParams bp = W.lpm(W.WC, W.WC, 0, 0, 0, 8);
        bp.gravity = Gravity.CENTER_HORIZONTAL;
        wrap.addView(banner, bp);
        bar = W.row(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(T.dp(12), T.dp(8), T.dp(8), T.dp(8));
        bar.setBackground(W.round(T.glassBg, 12, T.glassStroke, .5f));
        View line = new View(c);
        line.setBackground(W.round(T.accent, 99));
        bar.addView(line, W.lp(T.dp(3), W.MP));
        LinearLayout bt = W.col(c);
        barTitle = W.text(c, "", 12, 600, T.accent);
        barSub = W.text(c, "", 12, 400, T.textSecondary);
        W.ellipsize(barTitle);
        W.ellipsize(barSub);
        bt.addView(barTitle);
        bt.addView(barSub);
        bar.addView(bt, W.lpm(0, W.WC, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) bt.getLayoutParams()).weight = 1;
        ImageView bx = new ImageView(c);
        bx.setImageDrawable(Svg.stroke(16, T.textSecondary, 2.2f, "M6 6l12 12", "M18 6L6 18"));
        bx.setScaleType(ImageView.ScaleType.CENTER);
        bx.setOnClickListener(v -> clearBar());
        bar.addView(bx, W.lp(T.dp(32), T.dp(32)));
        bar.setVisibility(View.GONE);
        wrap.addView(bar, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        form = W.row(c);
        form.setGravity(Gravity.BOTTOM);
        form.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
        form.setBackground(W.round(T.glassBg, 22, T.glassStroke, .5f));
        form.setElevation(T.dpf(6));
        attach = iconBtn(c, Ic.has("chat.attach") ? Ic.get("chat.attach", 22, T.textSecondary) : Svg.stroke(22, T.textSecondary, 2, "M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"), "Attach file", v -> pickFile());
        form.addView(attach, W.lp(T.dp(40), T.dp(40)));
        input = new EditText(c);
        input.setBackground(null);
        T.text(input, 14, 400, T.text);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14 * MsgView.msgScale());
        if (android.os.Build.VERSION.SDK_INT >= 28) input.setLineHeight(T.dp(21));
        input.setPadding(T.dp(8), T.dp(9), T.dp(8), T.dp(10));
        input.setMinHeight(T.dp(40));
        input.setMaxHeight(T.dp(120));
        input.setHintTextColor(T.textMuted);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        input.setVerticalScrollBarEnabled(false);
        if (android.os.Build.VERSION.SDK_INT >= 29) input.setTextCursorDrawable(com.vulpine.at.ui.W.cursor(T.accent));
        input.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN && !e.isShiftPressed() && e.getDevice() != null && !e.getDevice().isVirtual()) {
                submit();
                return true;
            }
            if (code == KeyEvent.KEYCODE_ESCAPE && e.getAction() == KeyEvent.ACTION_DOWN && (editing != null || replyTo != null)) {
                clearBar();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int b, int n) {
            }

            public void onTextChanged(CharSequence s, int st, int b, int n) {
            }

            public void afterTextChanged(Editable e) {
                updateSend();
                long now = System.currentTimeMillis();
                if (live() && e.length() > 0 && now - lastTy > 2500) {
                    lastTy = now;
                    Sec.typing(chat);
                }
            }
        });
        input.setOnFocusChangeListener((v, f) -> {
            if (f && panels != null && panels.open()) panels.closeKeepKb();
        });
        form.addView(input, W.lp(0, W.WC, 1));
        gifBtn = new ImageView(c);
        gifTxt = W.text(c, "GIF", 11, 800, T.textSecondary);
        gifTxt.setLetterSpacing(.04f);
        gifTxt.setPadding(T.dp(4), T.dp(2), T.dp(4), T.dp(2));
        gifTxt.setBackground(W.round(0, 5, T.textSecondary, 1.8f));
        FrameLayout gw = new FrameLayout(c);
        gw.addView(gifTxt, new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.CENTER));
        gw.setContentDescription("GIFs");
        gw.setOnClickListener(v -> {
            if (!live()) {
                offline();
                return;
            }
            panels.toggle(Panels.GIF);
        });
        W.press(gw, .92f);
        form.addView(gw, W.lp(T.dp(40), T.dp(40)));
        emojiBtn = iconBtn(c, Svg.stroke(22, T.textSecondary, 2, "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M8 14s1.5 2 4 2 4-2 4-2", "M9 9h.01", "M15 9h.01"), "Emoji", v -> panels.toggle(Panels.EMOJI));
        form.addView(emojiBtn, W.lp(T.dp(40), T.dp(40)));
        sendBtn = new ImageView(c);
        sendBtn.setScaleType(ImageView.ScaleType.CENTER);
        sendBtn.setBackground(W.oval(T.accent));
        sendBtn.setOnClickListener(v -> {
            if (input.getText().toString().trim().isEmpty()) startRec();
            else submit();
        });
        W.press(sendBtn, .92f);
        form.addView(sendBtn, W.lpm(T.dp(40), T.dp(40), 4, 0, 0, 0));
        recRow = W.row(c);
        recRow.setGravity(Gravity.CENTER_VERTICAL);
        recRow.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
        recRow.setBackground(W.round(T.glassBg, 22, T.glassStroke, .5f));
        recRow.setElevation(T.dpf(6));
        ImageView cx = iconBtn(c, Svg.stroke(20, T.textSecondary, 2.2f, "M6 6l12 12", "M18 6L6 18"), "Cancel recording", v -> stopRec(true));
        recRow.addView(cx, W.lp(T.dp(40), T.dp(40)));
        LinearLayout rc = W.row(c);
        rc.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(c);
        dot.setBackground(W.oval(T.danger));
        rc.addView(dot, W.lp(T.dp(10), T.dp(10)));
        android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofFloat(dot, "alpha", 1f, .25f);
        pulse.setDuration(600);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.start();
        recTime = W.text(c, "0:00", 14, 400, T.text);
        recTime.setFontFeatureSettings("tnum");
        rc.addView(recTime, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        recRow.addView(rc, W.lpm(0, W.WC, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) rc.getLayoutParams()).weight = 1;
        ImageView rs = new ImageView(c);
        rs.setImageDrawable(Svg.fill(18, T.accentFg, "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"));
        rs.setScaleType(ImageView.ScaleType.CENTER);
        rs.setBackground(W.oval(T.accent));
        rs.setContentDescription("Send voice message");
        rs.setOnClickListener(v -> stopRec(false));
        W.press(rs, .92f);
        recRow.addView(rs, W.lp(T.dp(40), T.dp(40)));
        recRow.setVisibility(View.GONE);
        FrameLayout fs = new FrameLayout(c);
        fs.addView(form, new FrameLayout.LayoutParams(W.MP, W.WC));
        fs.addView(recRow, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.BOTTOM));
        wrap.addView(fs, W.lpm(W.MP, W.WC, 0, 0, 0, 28));
        wrap.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) root.post(this::applyPadding);
        });
        updateSend();
    }

    static ImageView iconBtn(Context c, android.graphics.drawable.Drawable d, String desc, View.OnClickListener on) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(d);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription(desc);
        b.setOnClickListener(on);
        W.press(b, .92f);
        return b;
    }

    void updateSend() {
        if (sendBtn == null) return;
        boolean has = !input.getText().toString().trim().isEmpty();
        sendBtn.setImageDrawable(has ? Svg.fill(18, T.accentFg, "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z") : Svg.stroke(20, T.accentFg, 2, "M9 3h6v11H9z", "M5 11a7 7 0 0 0 14 0M12 18v3"));
        sendBtn.setContentDescription(has ? "Send secret message" : "Record voice message");
        boolean lv = live();
        sendBtn.setEnabled(lv);
        sendBtn.setAlpha(lv ? 1f : .4f);
    }

    void refreshComposer() {
        if (wrap == null || chat == null) return;
        String b = chat.pending() ? "Waiting for " + name() + " to join with the password." : !chat.peerOnline ? name() + " is not in the chat right now. Messages go through while you are both here." : null;
        if (b == null) banner.setVisibility(View.GONE);
        else {
            boolean was = banner.getVisibility() == View.VISIBLE && b.contentEquals(banner.getText());
            banner.setText(b);
            banner.setVisibility(View.VISIBLE);
            if (!was) {
                banner.setAlpha(0);
                banner.setTranslationY(T.dp(6));
                banner.animate().alpha(1).translationY(0).setDuration(220).start();
            }
        }
        boolean lv = live();
        attach.setVisibility(lv ? View.VISIBLE : View.GONE);
        input.setHint(lv ? "Secret message..." : chat.pending() ? "Waiting for " + name() + "..." : name() + " is not here");
        updateSend();
    }

    void showBar() {
        if (editing != null) {
            barTitle.setText("Editing message, Esc to cancel");
            barSub.setVisibility(View.GONE);
        } else if (replyTo != null) {
            barTitle.setText("Replying to " + (replyTo.mine() ? "yourself" : name()));
            barSub.setText(Sec.preview(replyTo));
            barSub.setVisibility(View.VISIBLE);
        }
        bar.setVisibility(editing != null || replyTo != null ? View.VISIBLE : View.GONE);
    }

    void clearBar() {
        if (editing != null) input.setText("");
        editing = null;
        replyTo = null;
        bar.setVisibility(View.GONE);
    }

    void submit() {
        String t = input.getText().toString();
        if (t.trim().isEmpty() || chat == null) return;
        if (!live()) {
            offline();
            return;
        }
        if (editing != null) {
            Sec.Msg m = editing;
            editing = null;
            bar.setVisibility(View.GONE);
            input.setText("");
            Sec.Chat ch = chat;
            App.bg(() -> {
                if (!Sec.edit(ch, m, t)) App.ui(() -> Ov.toast("Could not edit the message", true));
            });
            return;
        }
        Sec.Msg r = replyTo;
        replyTo = null;
        bar.setVisibility(View.GONE);
        input.setText("");
        stick = true;
        Sec.sendText(chat, t, r);
    }

    void pickFile() {
        if (!live()) {
            offline();
            return;
        }
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        App.startForResult(a, Intent.createChooser(i, null), (res, data) -> {
            if (res != Activity.RESULT_OK || data == null) return;
            List<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) for (int k = 0; k < cd.getItemCount() && k < 10; k++) uris.add(cd.getItemAt(k).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            if (uris.isEmpty()) return;
            Sec.Msg r = replyTo;
            replyTo = null;
            bar.setVisibility(View.GONE);
            stick = true;
            App.bg(() -> {
                for (Uri u : uris) {
                    Attach.Item it = Attach.copy(a, u);
                    if (it == null) continue;
                    sendFile(it.file, it.name, it.mime, r);
                }
            });
        });
    }

    void sendFile(File f, String name, String mime, Sec.Msg r) {
        Sec.Chat ch = chat;
        if (ch == null) return;
        if (!ch.live()) {
            App.ui(this::offline);
            return;
        }
        String err = Sec.sendFile(ch, f, name, mime, r);
        if (err != null) App.ui(() -> Ov.toast(err, true));
    }

    void startRec() {
        if (!live()) {
            offline();
            return;
        }
        App.requestPerms(a, new String[]{Manifest.permission.RECORD_AUDIO}, ok -> {
            if (!ok) {
                Ov.toast("Could not access your microphone", true);
                return;
            }
            if (!Voice.Recorder.start(a)) return;
            form.setVisibility(View.INVISIBLE);
            recRow.setVisibility(View.VISIBLE);
            recTime.setText("0:00");
            App.H.post(recTick);
        });
    }

    void stopRec(boolean cancel) {
        if (!Voice.Recorder.active) return;
        App.H.removeCallbacks(recTick);
        File f = Voice.Recorder.stop(!cancel);
        recRow.setVisibility(View.GONE);
        form.setVisibility(View.VISIBLE);
        if (f == null || cancel) return;
        boolean ogg = f.getName().endsWith(".ogg");
        Sec.Msg r = replyTo;
        replyTo = null;
        bar.setVisibility(View.GONE);
        stick = true;
        sendFile(f, "Voice message." + (ogg ? "ogg" : "m4a"), ogg ? "audio/ogg" : "audio/mp4", r);
    }

    void applyPadding() {
        if (list == null || wrap == null) return;
        int ch = wrap.getHeight();
        int top = Math.max(a.insetTop, T.dp(24)) + T.dp(60);
        boolean st = stick;
        list.setPadding(T.dp(16), top, T.dp(16), ch + T.dp(10));
        if (bottomFade != null) {
            FrameLayout.LayoutParams bf = (FrameLayout.LayoutParams) bottomFade.getLayoutParams();
            bf.height = ch + T.dp(34);
            bottomFade.setLayoutParams(bf);
        }
        if (st) scrollBottom();
        initDone = true;
    }

    public void onInsets(int bottom, int ime) {
        onInsets(a.insetTop, bottom, ime);
    }

    @Override
    public void onInsets(int top, int bottom, int ime) {
        if (header != null) {
            FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams();
            hp.topMargin = Math.max(top, T.dp(24)) + T.dp(14);
            header.setLayoutParams(hp);
            ViewGroup.LayoutParams tf = topFade.getLayoutParams();
            tf.height = Math.max(top, T.dp(24)) + T.dp(96);
            topFade.setLayoutParams(tf);
        }
        navH = bottom;
        imeH = ime;
        if (body != null) {
            View g = body.getChildCount() > 0 ? body.getChildAt(0) : null;
            if (g != null && g.getTag() == null) g.setPadding(0, 0, 0, ime > 0 ? ime : bottom);
        }
        if (wrap == null) return;
        boolean kb = ime > 0 || (panels != null && panels.open());
        wrap.setPadding(T.dp(10), 0, T.dp(10), kb ? T.dp(6) : T.dp(10));
        View fs = wrap.getChildAt(wrap.getChildCount() - 1);
        LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) fs.getLayoutParams();
        int want = kb ? 0 : Math.max(T.dp(28), bottom);
        if (fp.bottomMargin != want) {
            fp.bottomMargin = want;
            fs.setLayoutParams(fp);
        }
        FrameLayout.LayoutParams wp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
        int bm = ime > 0 ? ime : (panels != null && panels.open() ? panels.height() + bottom : 0);
        if (wp.bottomMargin != bm) {
            wp.bottomMargin = bm;
            wrap.setLayoutParams(wp);
        }
        root.post(this::applyPadding);
    }

    @Override
    public void onShow() {
        Sec.active = id;
        if (chat != null && chat.unlocked()) Sec.markRead(chat);
    }

    @Override
    public boolean onBack() {
        if (panels != null && panels.open()) {
            panels.close();
            return true;
        }
        if (Voice.Recorder.active) {
            stopRec(true);
            return true;
        }
        if (editing != null || replyTo != null) {
            clearBar();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        Sec.off(sl);
        if (id.equals(Sec.active)) Sec.active = null;
        App.H.removeCallbacks(recTick);
        App.H.removeCallbacks(typingTick);
        if (Voice.Recorder.active) Voice.Recorder.stop(false);
        stopPlayer();
        clearTmp();
        if (I == this) I = null;
    }
}
