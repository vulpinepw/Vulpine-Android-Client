package com.vulpine.at.chat;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Sync;
import com.vulpine.at.home.Badges;
import com.vulpine.at.home.ChatRow;
import com.vulpine.at.home.Home;
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
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public class ChatScreen extends Screen {
    public static ChatScreen I;
    public String cid;
    public Chats.Conv conv;
    FrameLayout root, header, listWrap;
    public ListView list;
    BgView bg;
    View topFade, bottomFade;
    Composer composer;
    final List<MsgView.Item> items = new ArrayList<>();
    Adapter ad;
    AvatarView hav;
    TextView title, sub;
    LinearLayout titleRow;
    ImageView callBtn;
    View presenceDot;
    TextView timerTag;
    FrameLayout pinBar;
    FrameLayout toBottom;
    FrameLayout voicePill;
    View empty, skeleton;
    public final Set<String> selected = new HashSet<>();
    public boolean selectMode;
    LinearLayout selTop, selBottom;
    final Map<String, List<Object[]>> reactions = Reactions.state;
    final Map<String, Long> typing = new ConcurrentHashMap<>();
    String typingName;
    public boolean loadedOnce;
    boolean loadingOlder;
    int pinCycle;
    final Set<View> anims = new HashSet<>();
    String jumpPending;
    long userAt;
    public final Md.Links links = new Md.Links() {
        public void url(String url, boolean ext) {
            Links.open(a, url);
        }

        public void mention(String handle) {
            openProfile(handle);
        }
    };

    static final String SPOTIFY = "<svg viewBox=\"0 0 24 24\"><path fill=\"currentColor\" d=\"M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301c-.6.179-1.2-.181-1.38-.721-.18-.601.18-1.2.72-1.381 4.26-1.26 11.28-1.02 15.721 1.621.539.3.719 1.02.419 1.56-.299.421-1.02.599-1.559.3z\"/></svg>";

    public ChatScreen(String cid) {
        this.cid = cid;
    }

    public View composerView() {
        return composer.view();
    }

    public static int wpLine() {
        String wp = BgView.wallpaper();
        if (wp == null || wp.isEmpty()) return T.border;
        return BgView.wallpaperLum(wp) > .45 ? 0x1f000000 : 0x1fffffff;
    }

    public static int wpMuted() {
        String wp = BgView.wallpaper();
        if (wp == null || wp.isEmpty()) return T.textSecondary;
        return BgView.wallpaperLum(wp) > .45 ? 0x99000000 : 0xb3ffffff;
    }

    public View build() {
        I = this;
        Context c = a;
        Chats.I.activeCid = cid;
        Emoji.cid = cid;
        Reactions.wire();
        Bots.wire();
        Presence.wire();
        conv = Chats.I.conv(cid);
        root = new Page.EdgeFrame(c, this);
        root.setBackgroundColor(T.bg);
        bg = new BgView(c, false);
        root.addView(bg, new FrameLayout.LayoutParams(W.MP, W.MP));
        listWrap = new FrameLayout(c);
        list = new ListView(c) {
            final GestureDetector gd = new GestureDetector(c, new Gestures());

            @Override
            public boolean dispatchTouchEvent(MotionEvent e) {
                int act = e.getActionMasked();
                if (act == MotionEvent.ACTION_DOWN) {
                    longFired = false;
                    userAt = System.currentTimeMillis();
                } else if (act == MotionEvent.ACTION_MOVE) userAt = System.currentTimeMillis();
                if (!Swipe.active()) gd.onTouchEvent(e);
                if (selectMode && !longFired) return onTouchEvent(e);
                if (longFired && act == MotionEvent.ACTION_UP) {
                    MotionEvent ce = MotionEvent.obtain(e);
                    ce.setAction(MotionEvent.ACTION_CANCEL);
                    boolean r = super.dispatchTouchEvent(ce);
                    ce.recycle();
                    return r;
                }
                return super.dispatchTouchEvent(e);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (Swipe.intercept(ChatScreen.this, e)) return true;
                return super.onInterceptTouchEvent(e);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (Swipe.active()) return Swipe.touch(ChatScreen.this, e);
                return super.onTouchEvent(e);
            }
        };
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setStackFromBottom(true);
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
        list.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (pinBottom && System.currentTimeMillis() - userAt > 600) list.setSelection(Math.max(0, ad.getCount() - 1));
        });
        list.setItemsCanFocus(true);
        ad = new Adapter();
        list.setAdapter(ad);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int st) {
                if (st != SCROLL_STATE_IDLE) pinBottom = false;
                else pinBottom = atBottom();
            }

            public void onScroll(AbsListView v, int first, int vis, int total) {
                if (toBottom == null || voicePill == null || composer == null || header == null) return;
                if (first <= 2 && total > 0 && !loadingOlder && !Boolean.FALSE.equals(Chats.I.hasOlder.get(cid)) && loadedOnce) loadOlder();
                updateToBottom();
                markRead();
                updateVoicePill();
                pauseOffscreenAnims();
            }
        });
        listWrap.addView(list, new FrameLayout.LayoutParams(W.MP, W.MP));
        root.addView(listWrap, new FrameLayout.LayoutParams(W.MP, W.MP));
        topFade = new View(c);
        topFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{T.mixA(T.bg, .92f), T.mixA(T.bg, .55f), 0}));
        root.addView(topFade, new FrameLayout.LayoutParams(W.MP, T.dp(96)));
        bottomFade = new View(c);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{T.mixA(T.bg, .92f), T.mixA(T.bg, .55f), 0}));
        root.addView(bottomFade, new FrameLayout.LayoutParams(W.MP, T.dp(128), Gravity.BOTTOM));
        buildHeader(c);
        pinBar = new FrameLayout(c);
        pinBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pbp = new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.TOP);
        pbp.leftMargin = pbp.rightMargin = T.dp(10);
        root.addView(pinBar, pbp);
        composer = new Composer(this);
        root.addView(composer.view(), new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.BOTTOM));
        buildToBottom(c);
        buildVoicePill(c);
        skeleton = Skeleton.view(c);
        root.addView(skeleton, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.BOTTOM));
        Chats.I.on(cl);
        Rt.I.on(ws);
        Reactions.on(rxl);
        Voice.on(voiceL);
        Presence.on(presL);
        Chats.I.restoreMsgs(cid);
        rebuild(true);
        refreshHeader();
        App.bg(() -> {
            if (conv == null || conv.group() && conv.o.optJSONArray("members") == null) conv = Chats.I.refresh(cid);
            else App.bg(() -> {
                Chats.Conv r = Chats.I.refresh(cid);
                if (r != null) App.ui(() -> {
                    conv = r;
                    refreshHeader();
                    applyGroupLook();
                    ad.notifyDataSetChanged();
                });
            });
            Chats.I.loadMessages(cid);
            App.ui(() -> {
                loadedOnce = true;
                rebuild(true);
                refreshHeader();
                applyGroupLook();
            });
        });
        if (conv != null && conv.group()) Emoji.load(cid, () -> ad.notifyDataSetChanged());
        loadBoosts();
        return root;
    }

    final Chats.Listener cl = (ev, id, d) -> {
        if (!cid.equals(id)) return;
        App.ui(() -> {
            boolean stuck = atBottom();
            if ("new".equals(ev) && d instanceof Chats.Msg) {
                Chats.Msg m = (Chats.Msg) d;
                typing.remove(m.senderId);
                rebuild(stuck || m.mine());
                if (stuck || m.mine()) animateLast();
                markRead();
            } else if ("older".equals(ev)) prependKeep();
            else rebuild(stuck);
            if ("conv".equals(ev) && d instanceof Chats.Conv) {
                conv = (Chats.Conv) d;
                refreshHeader();
            }
        });
    };

    final Rt.Listener ws = m -> {
        String t = m.optString("type");
        if (!cid.equals(m.optString("conversationId"))) {
            if ("auth.ok".equals(t)) App.bg(() -> Chats.I.loadMessages(cid));
            return;
        }
        switch (t) {
            case "typing": {
                String uid = m.optString("userId");
                if (uid.equals(Session.userId())) return;
                App.ui(() -> {
                    if (m.optBoolean("isTyping", true)) {
                        typing.put(uid, System.currentTimeMillis());
                        typingName = m.optString("displayName", "Someone");
                    } else typing.remove(uid);
                    refreshSub();
                    App.H.postDelayed(this::refreshSub, 3600);
                });
                break;
            }
            case "message.status": {
                String mid = m.optString("messageId");
                App.ui(() -> {
                    List<Chats.Msg> ml = Chats.I.list(cid);
                    synchronized (ml) {
                        for (Chats.Msg x : ml) if (mid.equals(x.id)) x.readAt = m.optLong("at", System.currentTimeMillis());
                    }
                    ad.notifyDataSetChanged();
                });
                break;
            }
            case "message.pinned": {
                App.ui(() -> {
                    String mid = m.optString("messageId");
                    List<Chats.Msg> ml = Chats.I.list(cid);
                    synchronized (ml) {
                        for (Chats.Msg x : ml) if (mid.equals(x.id)) x.pinned = m.optBoolean("pinned");
                    }
                    rebuild(false);
                });
                break;
            }
            case "system.message": {
                App.ui(() -> {
                    String from = m.optString("fromDisplayName", "");
                    addSystem("💬 " + (from.isEmpty() ? "" : from + ": ") + m.optString("text"));
                });
                break;
            }
            case "group.boosts":
                App.ui(() -> {
                    boostsAt = 0;
                    loadBoosts();
                });
                break;
            case "group.emojis.updated":
                Emoji.invalidate(cid);
                Emoji.load(cid, () -> ad.notifyDataSetChanged());
                break;
            case "call.state":
            case "call.handled":
                App.H.postDelayed(this::refreshHeader, 120);
                break;
            case "rate_limited":
                App.ui(() -> Ov.toast(m.optString("text", "Slow down! You're sending messages too fast."), true));
                break;
        }
    };

    final Runnable rxl = () -> ad.notifyDataSetChanged();
    final Runnable voiceL = this::updateVoicePill;
    final Runnable presL = () -> refreshHeader();
    final List<String> systemMsgs = new ArrayList<>();
    final List<Long> systemAt = new ArrayList<>();

    void addSystem(String text) {
        systemMsgs.add(text);
        systemAt.add(System.currentTimeMillis());
        rebuild(atBottom());
    }

    public void onInsets(int top, int bottom, int ime) {
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams();
        hp.topMargin = Math.max(top, T.dp(24)) + T.dp(14) - T.dp(24) + T.dp(0);
        hp.topMargin = Math.max(top, T.dp(24)) + T.dp(14);
        header.setLayoutParams(hp);
        FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) pinBar.getLayoutParams();
        pp.topMargin = Math.max(top, T.dp(24)) + T.dp(80);
        pinBar.setLayoutParams(pp);
        ViewGroup.LayoutParams tf = topFade.getLayoutParams();
        tf.height = Math.max(top, T.dp(24)) + T.dp(96);
        topFade.setLayoutParams(tf);
        composer.onInsets(bottom, ime);
        root.post(this::applyPadding);
    }

    void applyPadding() {
        int ch = composer.view().getHeight() + ((FrameLayout.LayoutParams) composer.view().getLayoutParams()).bottomMargin;
        int top = Math.max(a.insetTop, T.dp(24)) + T.dp(60);
        boolean stuck = atBottom();
        list.setPadding(T.dp(16), top, T.dp(16), ch + T.dp(26));
        FrameLayout.LayoutParams bf = (FrameLayout.LayoutParams) bottomFade.getLayoutParams();
        bf.height = ch + T.dp(34);
        bottomFade.setLayoutParams(bf);
        FrameLayout.LayoutParams tb = (FrameLayout.LayoutParams) toBottom.getLayoutParams();
        tb.bottomMargin = T.dp(88) + ((FrameLayout.LayoutParams) composer.view().getLayoutParams()).bottomMargin;
        toBottom.setLayoutParams(tb);
        FrameLayout.LayoutParams sk = (FrameLayout.LayoutParams) skeleton.getLayoutParams();
        sk.bottomMargin = ch + T.dp(10);
        skeleton.setLayoutParams(sk);
        if (stuck) scrollBottom();
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
        presenceDot = new View(c);
        presenceDot.setVisibility(View.GONE);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(T.dp(11), T.dp(11), Gravity.BOTTOM | Gravity.END);
        dp.rightMargin = -T.dp(1);
        dp.bottomMargin = -T.dp(1);
        aw.addView(presenceDot, dp);
        timerTag = W.text(c, "", 9, 700, T.accentFg);
        timerTag.setBackground(W.round(T.accent, 999));
        timerTag.setPadding(T.dp(3), 0, T.dp(4), 0);
        timerTag.setGravity(Gravity.CENTER);
        timerTag.setVisibility(View.GONE);
        timerTag.setCompoundDrawablesWithIntrinsicBounds(Svg.stroke(9, T.accentFg, 2.6f, "M10 2h4", "M12 14l3-3", "M12 22a8 8 0 1 0 0-16 8 8 0 0 0 0 16z"), null, null, null);
        timerTag.setCompoundDrawablePadding(T.dp(2));
        FrameLayout.LayoutParams tt = new FrameLayout.LayoutParams(W.WC, T.dp(15), Gravity.BOTTOM | Gravity.END);
        tt.rightMargin = -T.dp(8);
        tt.bottomMargin = -T.dp(4);
        aw.addView(timerTag, tt);
        pill.addView(aw, W.lp(T.dp(32), T.dp(32)));
        LinearLayout tx = W.col(c);
        titleRow = W.row(c);
        title = W.text(c, "Loading…", 14, 600, T.text);
        W.ellipsize(title);
        T.lh(title, 21);
        titleRow.addView(title);
        tx.addView(titleRow);
        sub = W.text(c, "", 13, 400, T.textMuted);
        W.ellipsize(sub);
        T.lh(sub, 20);
        tx.addView(sub);
        pill.addView(tx, W.lpm(0, W.WC, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
        pill.setOnClickListener(v -> {
            if (conv == null) return;
            if (conv.group()) com.vulpine.at.groups.GroupInfo.open(a, cid);
            else openProfile(conv.o.optString("peerHandle"));
        });
        row.addView(pill, W.lp(0, W.WC, 1));
        callBtn = new ImageView(c);
        callBtn.setScaleType(ImageView.ScaleType.CENTER);
        callBtn.setOnClickListener(v -> {
            if (conv == null) return;
            if (conv.group()) com.vulpine.at.call.Calls.openRooms(a, cid);
            else com.vulpine.at.call.Calls.start(a, cid, false);
        });
        row.addView(callBtn, W.lpm(T.dp(36), T.dp(36), 8, 0, 0, 0));
        header.addView(row, new FrameLayout.LayoutParams(W.MP, W.WC));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.TOP);
        hp.leftMargin = hp.rightMargin = T.dp(10);
        root.addView(header, hp);
    }

    public void refreshHeader() {
        if (conv == null) conv = Chats.I.conv(cid);
        if (conv == null) return;
        JSONObject o = conv.o;
        String name = conv.group() ? o.optString("name") : o.optString("peerDisplayName", o.optString("peerHandle"));
        String hc = conv.avatarColor();
        if (!conv.group() && hc != null && hc.isEmpty()) hc = null;
        hav.set(name, hc, conv.avatarUrl());
        title.setText(name);
        while (titleRow.getChildCount() > 1) titleRow.removeViewAt(1);
        if (!conv.group()) {
            String h = o.optString("peerHandle");
            for (String u : Badges.forUser(o.optString("peerId"), h)) {
                ImageView b = new ImageView(a);
                Img.anim(b, u, T.dp(32), true);
                titleRow.addView(b, W.lpm(T.dp(16), T.dp(16), 4, 0, 0, 0));
            }
            if (Badges.isBot(h) || o.optBoolean("peerIsBot")) {
                ImageView b = new ImageView(a);
                b.setImageDrawable(Ic.svg(Badges.BOT_SVG, 16, T.dark ? 0xffffffff : 0x8c000000));
                titleRow.addView(b, W.lpm(T.dp(16), T.dp(16), 4, 0, 0, 0));
            }
            if (o.optBoolean("peerPro")) {
                ImageView b = new ImageView(a);
                Img.anim(b, "asset:pro-badge.gif", T.dp(30), true);
                titleRow.addView(b, W.lpm(T.dp(15), T.dp(15), 4, 0, 0, 0));
            }
            String pid = o.optString("peerId");
            int pc = Presence.color(pid);
            presenceDot.setVisibility(pc != 0 ? View.VISIBLE : View.GONE);
            if (pc != 0) {
                GradientDrawable g = W.oval(pc);
                g.setStroke(T.dp(2), T.surface);
                presenceDot.setBackground(g);
            }
        } else {
            String cb = Badges.forGroup(cid);
            if (cb != null) {
                ImageView b = new ImageView(a);
                Img.anim(b, cb, T.dp(32), true);
                titleRow.addView(b, W.lpm(T.dp(16), T.dp(16), 10, 0, 0, 0));
            }
            presenceDot.setVisibility(View.GONE);
        }
        long ms = o.optLong("autoDeleteMs");
        String st = ChatRow.shortTimer(ms);
        timerTag.setVisibility(ms > 0 && !st.isEmpty() ? View.VISIBLE : View.GONE);
        timerTag.setText(st);
        boolean bot = !conv.group() && (Badges.isBot(o.optString("peerHandle")) || o.optBoolean("peerIsBot") || "vulpine-alerts-bot".equals(o.optString("peerId")));
        callBtn.setVisibility(bot ? View.GONE : View.VISIBLE);
        boolean active = com.vulpine.at.call.Calls.activeIn(cid) || com.vulpine.at.home.ChatsTab.callConvs.contains(cid);
        callBtn.setImageDrawable(Svg.fill(18, T.accent, "M20.01 15.38c-1.23 0-2.42-.2-3.53-.56-.35-.12-.74-.03-1.01.24l-1.57 1.97c-2.83-1.35-5.48-3.9-6.89-6.83l1.95-1.66c.27-.28.35-.67.24-1.02-.37-1.11-.56-2.3-.56-3.53 0-.54-.45-.99-.99-.99H4.19C3.65 3 3 3.24 3 3.99 3 13.28 10.73 21 20.01 21c.71 0 .99-.63.99-1.18v-3.45c0-.54-.45-.99-.99-.99z"));
        callBtn.setContentDescription(conv.group() ? "Call rooms" : active ? "Join call" : "Start call");
        if (conv.group() && com.vulpine.at.call.Calls.roomsLive(cid)) {
            GradientDrawable hd = W.oval(T.success);
            hd.setStroke(T.dp(2), T.surface);
            android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{hd});
            ld.setLayerInset(0, T.dp(19), T.dp(4), T.dp(4), T.dp(19));
            callBtn.setForeground(ld);
        } else callBtn.setForeground(null);
        com.vulpine.at.deco.Deco.attach((FrameLayout) hav.getParent(), conv.group() ? null : o.optString("peerId"), T.dp(32), false);
        refreshSub();
        composer.refresh();
    }

    void refreshSub() {
        if (conv == null) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : new ArrayList<>(typing.entrySet())) if (now - e.getValue() > 3500) typing.remove(e.getKey());
        JSONObject o = conv.o;
        sub.setTextColor(T.textMuted);
        sub.setCompoundDrawables(null, null, null, null);
        if (!typing.isEmpty()) {
            sub.setText(conv.group() ? (typingName == null ? "Someone" : typingName) + " is typing..." : "typing...");
            return;
        }
        if (conv.group()) {
            int n = o.optInt("memberTotal", o.optJSONArray("members") == null ? 0 : o.optJSONArray("members").length());
            sub.setText(n + " members");
        } else {
            String l = com.vulpine.at.home.ChatsTab.listening.get(o.optString("peerId"));
            sub.setText(l != null ? "Listening to " + l : "@" + o.optString("peerHandle"));
            sub.setCompoundDrawablesWithIntrinsicBounds(l != null ? Ic.svg(SPOTIFY, 12, 0xff1db954) : null, null, null, null);
            sub.setCompoundDrawablePadding(T.dp(4));
            return;
        }
    }

    void applyGroupLook() {
        if (conv == null || !conv.group()) return;
        JSONObject b = boostLook != null ? boostLook : conv.o.optJSONObject("boost");
        if (b == null) return;
        String wp = b.optString("wallpaper", "");
        String icons = b.optString("icons", "");
        bg.setGroupWallpaper(wp.isEmpty() ? null : wp, icons.isEmpty() ? null : icons);
    }

    final Map<String, Integer> boosters = new ConcurrentHashMap<>();
    JSONObject boostLook;
    long boostsAt;

    void loadBoosts() {
        if (conv == null || !conv.group() || System.currentTimeMillis() - boostsAt < 30000) return;
        boostsAt = System.currentTimeMillis();
        App.bg(() -> {
            Http.Resp r = Http.get("/conversations/" + cid + "/boosts");
            if (!r.ok()) return;
            JSONObject o = r.obj();
            JSONArray a = o.optJSONArray("boosters");
            Map<String, Integer> m = new java.util.HashMap<>();
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null && x.optInt("count") > 0) m.put(x.optString("id"), x.optInt("count"));
            }
            JSONObject look = o.optJSONObject("boost");
            App.ui(() -> {
                boolean ch = !m.equals(boosters);
                boosters.clear();
                boosters.putAll(m);
                if (look != null) boostLook = look;
                applyGroupLook();
                if (ch) ad.notifyDataSetChanged();
            });
        });
    }

    public int boostCount(String uid) {
        if (conv == null || !conv.group()) return 0;
        Integer n = boosters.get(uid);
        if (n != null) return n;
        JSONObject b = conv.o.optJSONObject("boost");
        JSONObject by = b == null ? null : b.optJSONObject("byUser");
        return by == null ? 0 : by.optInt(uid);
    }

    public boolean isBotSender(Chats.Msg m) {
        if ("vulpine-alerts-bot".equals(m.senderId)) return true;
        JSONObject s = m.sender;
        if (s != null && (s.optBoolean("isBot") || Badges.isBot(s.optString("handle")))) return true;
        if (conv != null && !conv.group() && !m.mine()) return Badges.isBot(conv.o.optString("peerHandle")) || conv.o.optBoolean("peerIsBot");
        if (conv != null && conv.group()) {
            JSONObject mem = conv.member(m.senderId);
            return mem != null && (mem.optBoolean("isBot") || Badges.isBot(mem.optString("handle")));
        }
        return false;
    }

    public String senderName(Chats.Msg m) {
        if (m.mine()) return "You";
        if (conv != null && !conv.group()) return conv.o.optString("peerDisplayName", "Voice message");
        JSONObject s = m.sender != null ? m.sender : conv == null ? null : conv.member(m.senderId);
        return s == null ? "Voice message" : s.optString("displayName", s.optString("handle", "Voice message"));
    }

    public void openProfile(String handle) {
        if (handle == null || handle.isEmpty()) return;
        com.vulpine.at.people.Profile.open(a, handle);
    }

    void rebuild(boolean stick) {
        List<Chats.Msg> src = new ArrayList<>(Chats.I.list(cid));
        JSONArray hidden = Session.settings().optJSONArray("hiddenMsgIds");
        items.clear();
        long lastDay = -1;
        String lastSender = null;
        int si = 0;
        List<MsgView.Item> tmp = new ArrayList<>();
        for (Chats.Msg m : src) {
            if (m.id != null && Sync.contains(hidden, m.id)) continue;
            while (si < systemAt.size() && systemAt.get(si) <= m.createdAt) {
                MsgView.Item s = new MsgView.Item();
                s.type = 1;
                s.sys = systemMsgs.get(si);
                s.key = "sys" + si;
                tmp.add(s);
                si++;
            }
            long day = dayOf(m.createdAt);
            if (day != lastDay) {
                MsgView.Item d = new MsgView.Item();
                d.type = 0;
                d.at = m.createdAt;
                d.key = "d" + day;
                tmp.add(d);
                lastDay = day;
                lastSender = null;
            }
            if (m.raw != null && m.raw.optString("ciphertext", "").isEmpty() && m.text == null && m.id != null && !m.decryptFailed) continue;
            MsgView.Item it = new MsgView.Item();
            it.type = 2;
            it.m = m;
            it.key = m.id != null ? m.id : m.tempId;
            it.prevSame = m.senderId.equals(lastSender);
            tmp.add(it);
            lastSender = m.senderId;
        }
        while (si < systemAt.size()) {
            MsgView.Item s = new MsgView.Item();
            s.type = 1;
            s.sys = systemMsgs.get(si);
            s.key = "sys" + si;
            tmp.add(s);
            si++;
        }
        for (int i = 0; i < tmp.size(); i++) {
            MsgView.Item it = tmp.get(i);
            if (it.type == 2 && i + 1 < tmp.size() && tmp.get(i + 1).type == 2 && tmp.get(i + 1).m.senderId.equals(it.m.senderId)) it.nextSame = true;
        }
        items.addAll(tmp);
        ad.notifyDataSetChanged();
        boolean any = false;
        for (MsgView.Item it : items) if (it.type == 2) any = true;
        updateEmpty(any);
        skeleton.setVisibility(!any && !loadedOnce && !App.kv.keys("vulpine.msgs." + cid).isEmpty() == false && !loadedOnce ? View.VISIBLE : View.GONE);
        if (any || loadedOnce) skeleton.setVisibility(View.GONE);
        updatePinBar();
        if (stick) scrollBottom();
        if (jumpPending != null) {
            String j = jumpPending;
            if (indexOf(j) >= 0) {
                jumpPending = null;
                list.post(() -> jumpTo(j));
            }
        }
    }

    void updateEmpty(boolean any) {
        if (any || !loadedOnce) {
            if (empty != null) empty.setVisibility(View.GONE);
            return;
        }
        if (empty == null) {
            empty = EmptyCard.view(this);
            root.addView(empty, 1 + root.indexOfChild(listWrap), new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.CENTER));
        }
        empty.setVisibility(View.VISIBLE);
    }

    static long dayOf(long at) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        return c.get(Calendar.YEAR) * 1000L + c.get(Calendar.DAY_OF_YEAR);
    }

    int indexOf(String mid) {
        for (int i = 0; i < items.size(); i++) {
            MsgView.Item it = items.get(i);
            if (it.type == 2 && mid.equals(it.m.id)) return i;
        }
        return -1;
    }

    public boolean atBottom() {
        if (list.getChildCount() == 0) return true;
        int last = list.getLastVisiblePosition();
        if (last < ad.getCount() - 1) return false;
        View v = list.getChildAt(list.getChildCount() - 1);
        return v.getBottom() - (list.getHeight() - list.getPaddingBottom()) < T.dp(80);
    }

    boolean pinBottom = true;

    public void showIndex(int i) {
        pinBottom = false;
        userAt = System.currentTimeMillis();
        list.setSelection(i);
    }

    void scrollBottom() {
        pinBottom = true;
        list.post(() -> list.setSelection(Math.max(0, ad.getCount() - 1)));
    }

    void animateLast() {
        list.post(() -> {
            View v = list.getChildAt(list.getChildCount() - 1);
            if (v == null) return;
            v.setAlpha(0);
            v.setTranslationY(T.dp(18));
            v.setScaleX(.985f);
            v.setScaleY(.985f);
            v.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(300).setInterpolator(new android.view.animation.PathInterpolator(.25f, 1f, .4f, 1f)).start();
        });
    }

    void loadOlder() {
        loadingOlder = true;
        App.bg(() -> {
            Chats.I.loadOlder(cid);
            App.ui(() -> loadingOlder = false);
        });
    }

    void prependKeep() {
        int first = list.getFirstVisiblePosition();
        View v = list.getChildAt(0);
        int top = v == null ? 0 : v.getTop();
        String key = first < items.size() ? items.get(first).key : null;
        rebuild(false);
        if (key != null) for (int i = 0; i < items.size(); i++) if (key.equals(items.get(i).key)) {
            list.setSelectionFromTop(i, top);
            break;
        }
    }

    void markRead() {
        if (!App.fg) return;
        List<String> ids = new ArrayList<>();
        int f = list.getFirstVisiblePosition(), l = list.getLastVisiblePosition();
        for (int i = Math.max(0, f); i <= l && i < items.size(); i++) {
            MsgView.Item it = items.get(i);
            if (it.type == 2 && !it.m.mine() && it.m.readAt == null && it.m.id != null && it.m.id.length() > 20 && !readSent.contains(it.m.id)) {
                ids.add(it.m.id);
                readSent.add(it.m.id);
            }
        }
        if (!ids.isEmpty()) Chats.I.read(cid, ids);
    }

    final Set<String> readSent = new HashSet<>();

    public void jumpTo(String mid) {
        int i = indexOf(mid);
        if (i < 0) {
            jumpPending = mid;
            if (!Boolean.FALSE.equals(Chats.I.hasOlder.get(cid)) && !loadingOlder) loadOlder();
            return;
        }
        userAt = System.currentTimeMillis() + 900;
        pinBottom = false;
        list.setSelectionFromTop(i, list.getHeight() / 3);
        list.postDelayed(() -> {
            for (int k = 0; k < list.getChildCount(); k++) {
                View v = list.getChildAt(k);
                if (mid.equals(v.getTag())) {
                    View b = v.findViewWithTag("bubble");
                    if (b instanceof Bubble) ((Bubble) b).flashNow();
                }
            }
        }, 80);
    }

    public static void gotoMsg(String cid, String mid) {
        MainActivity a = MainActivity.cur;
        if (a == null) return;
        ChatScreen s = I;
        if (s == null || !s.cid.equals(cid)) {
            Home.openChat(cid);
            s = I;
        }
        if (s != null && mid != null) {
            ChatScreen fs = s;
            App.H.postDelayed(() -> fs.jumpTo(mid), 400);
        }
    }

    void updatePinBar() {
        List<Chats.Msg> pins = new ArrayList<>();
        for (MsgView.Item it : items) if (it.type == 2 && it.m.pinned) pins.add(it.m);
        if (pins.isEmpty()) {
            pinBar.setVisibility(View.GONE);
            return;
        }
        pinBar.setVisibility(View.VISIBLE);
        PinBar.render(this, pinBar, pins);
    }

    void buildToBottom(Context c) {
        toBottom = new FrameLayout(c);
        toBottom.setBackground(W.round(T.glassBgStrong, 12, T.glassStroke, .5f));
        toBottom.setElevation(T.dpf(6));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Svg.stroke(20, T.textSecondary, 2.2f, "M12 5v14", "m19 12-7 7-7-7"));
        toBottom.addView(ic, new FrameLayout.LayoutParams(T.dp(20), T.dp(20), Gravity.CENTER));
        toBottom.setContentDescription("Back to bottom");
        toBottom.setOnClickListener(v -> list.smoothScrollToPosition(Math.max(0, ad.getCount() - 1)));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(T.dp(40), T.dp(40), Gravity.BOTTOM | Gravity.END);
        p.rightMargin = T.dp(16);
        root.addView(toBottom, p);
        toBottom.setAlpha(0);
        toBottom.setVisibility(View.INVISIBLE);
    }

    boolean tbShown;

    void updateToBottom() {
        int remain = 0;
        int last = list.getLastVisiblePosition();
        if (last < ad.getCount() - 1) remain = T.dp(9999);
        else if (list.getChildCount() > 0) remain = list.getChildAt(list.getChildCount() - 1).getBottom() - list.getHeight();
        boolean show = remain > T.dp(600) || last < ad.getCount() - 6;
        if (show == tbShown) return;
        tbShown = show;
        if (show) {
            toBottom.setVisibility(View.VISIBLE);
            toBottom.setTranslationY(T.dp(8));
            toBottom.animate().alpha(1).translationY(0).setDuration(240).setInterpolator(T.EASE).start();
        } else toBottom.animate().alpha(0).translationY(T.dp(8)).setDuration(240).setInterpolator(T.EASE).withEndAction(() -> {
            if (!tbShown) toBottom.setVisibility(View.INVISIBLE);
        }).start();
    }

    void buildVoicePill(Context c) {
        voicePill = new FrameLayout(c);
        voicePill.setVisibility(View.GONE);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.TOP);
        p.leftMargin = p.rightMargin = T.dp(10);
        root.addView(voicePill, p);
    }

    void updateVoicePill() {
        Voice.Track t = Voice.cur;
        boolean onScreen = false;
        if (t != null && t.mid != null && cid.equals(t.cid)) {
            for (int k = 0; k < list.getChildCount(); k++) {
                View v = list.getChildAt(k);
                if (t.mid.equals(v.getTag()) && v.getBottom() > list.getPaddingTop() && v.getTop() < list.getHeight() - list.getPaddingBottom()) onScreen = true;
            }
        }
        boolean show = t != null && !onScreen;
        if (!show) {
            voicePill.setVisibility(View.GONE);
            return;
        }
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) voicePill.getLayoutParams();
        int top = Math.max(a.insetTop, T.dp(24)) + T.dp(80);
        if (pinBar.getVisibility() == View.VISIBLE && pinBar.getHeight() > 0) top = Math.max(top, ((FrameLayout.LayoutParams) pinBar.getLayoutParams()).topMargin + pinBar.getHeight() + T.dp(6));
        if (p.topMargin != top) {
            p.topMargin = top;
            voicePill.setLayoutParams(p);
        }
        boolean was = voicePill.getVisibility() == View.VISIBLE;
        VoicePill.render(this, voicePill, t);
        voicePill.setVisibility(View.VISIBLE);
        if (!was) {
            voicePill.setAlpha(0);
            voicePill.setTranslationY(-T.dp(4));
            voicePill.animate().alpha(1).translationY(0).setDuration(160).start();
        }
    }

    public List<String[]> voiceAfter(String url) {
        List<String[]> out = new ArrayList<>();
        boolean seen = false;
        for (MsgView.Item it : items) {
            if (it.type != 2 || it.m.text == null) continue;
            Fmt.Parsed p = Fmt.parse(it.m.text);
            for (Fmt.Att a2 : p.atts) {
                if (!a2.audio()) continue;
                if (seen) out.add(new String[]{a2.url, it.m.id, senderName(it.m), a2.voice() ? "Voice message" : "Audio"});
                if (a2.url.equals(url)) seen = true;
            }
        }
        return out;
    }

    public void trackAnim(View v) {
        anims.add(v);
    }

    void pauseOffscreenAnims() {
        if (android.os.Build.VERSION.SDK_INT < 28) return;
        for (View v : new ArrayList<>(anims)) {
            if (!v.isAttachedToWindow()) {
                anims.remove(v);
                continue;
            }
            if (!(v instanceof ImageView)) continue;
            android.graphics.drawable.Drawable d = ((ImageView) v).getDrawable();
            if (!(d instanceof android.graphics.drawable.AnimatedImageDrawable)) continue;
            android.graphics.Rect r = new android.graphics.Rect();
            boolean vis = v.getGlobalVisibleRect(r) && r.height() > 0;
            android.graphics.drawable.AnimatedImageDrawable ad2 = (android.graphics.drawable.AnimatedImageDrawable) d;
            if (vis && !ad2.isRunning()) ad2.start();
            else if (!vis && ad2.isRunning()) ad2.stop();
        }
    }

    public void menuFor(Chats.Msg m, View anchor, String gifUrl) {
        if (selectMode) {
            toggleSel(m);
            return;
        }
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        MsgMenu.show(this, m, l[0] + anchor.getWidth() / 2, l[1] + anchor.getHeight() / 2, gifUrl);
    }

    public void toggleSel(Chats.Msg m) {
        String k = m.id != null ? m.id : m.tempId;
        if (selected.contains(k)) selected.remove(k);
        else selected.add(k);
        if (selected.isEmpty()) exitSelect();
        else {
            Selection.update(this);
            ad.notifyDataSetChanged();
        }
    }

    public void enterSelect(Chats.Msg m) {
        selectMode = true;
        selected.clear();
        selected.add(m.id != null ? m.id : m.tempId);
        Selection.enter(this);
        ad.notifyDataSetChanged();
    }

    public void exitSelect() {
        selectMode = false;
        selected.clear();
        Selection.exit(this);
        ad.notifyDataSetChanged();
    }

    public List<Chats.Msg> selectedMsgs() {
        List<Chats.Msg> out = new ArrayList<>();
        for (MsgView.Item it : items) if (it.type == 2 && selected.contains(it.m.id != null ? it.m.id : it.m.tempId)) out.add(it.m);
        return out;
    }

    Chats.Msg msgAt(float x, float y) {
        int pos = list.pointToPosition((int) x, (int) y);
        if (pos < 0) {
            int best = -1;
            float bd = T.dp(90);
            for (int k = 0; k < list.getChildCount(); k++) {
                View v = list.getChildAt(k);
                float d = y < v.getTop() ? v.getTop() - y : y > v.getBottom() ? y - v.getBottom() : 0;
                if (d < bd) {
                    bd = d;
                    best = list.getFirstVisiblePosition() + k;
                }
            }
            pos = best;
        }
        if (pos < 0 || pos >= items.size()) return null;
        MsgView.Item it = items.get(pos);
        return it.type == 2 ? it.m : null;
    }

    View bubbleAt(float x, float y) {
        int pos = list.pointToPosition((int) x, (int) y);
        if (pos < 0) return null;
        View row = list.getChildAt(pos - list.getFirstVisiblePosition());
        return row == null ? null : row.findViewWithTag("bubble");
    }

    boolean longFired;

    boolean tapOwnedByChild(float x, float y) {
        int[] ll = new int[2];
        list.getLocationOnScreen(ll);
        return owned(list, x + ll[0], y + ll[1]);
    }

    boolean owned(View v, float rx, float ry) {
        if (v.getVisibility() != View.VISIBLE) return false;
        int[] l = new int[2];
        v.getLocationOnScreen(l);
        if (rx < l[0] || ry < l[1] || rx >= l[0] + v.getWidth() || ry >= l[1] + v.getHeight()) return false;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) if (owned(g.getChildAt(i), rx, ry)) return true;
        }
        if (v == list || "bubble".equals(v.getTag())) return false;
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            CharSequence cs = t.getText();
            if (cs instanceof android.text.Spanned && t.getLayout() != null) {
                int x = (int) (rx - l[0]) - t.getTotalPaddingLeft() + t.getScrollX(), y = (int) (ry - l[1]) - t.getTotalPaddingTop() + t.getScrollY();
                android.text.Layout lay = t.getLayout();
                int line = lay.getLineForVertical(y);
                int off = lay.getOffsetForHorizontal(line, x);
                android.text.style.ClickableSpan[] sp = ((android.text.Spanned) cs).getSpans(off, off, android.text.style.ClickableSpan.class);
                return sp.length > 0;
            }
            return t.hasOnClickListeners();
        }
        return v.hasOnClickListeners();
    }

    class Gestures extends GestureDetector.SimpleOnGestureListener {
        long lastTapAt;
        String lastTapId;
        float lastTapX, lastTapY;

        public boolean onSingleTapUp(MotionEvent e) {
            Chats.Msg m = msgAt(e.getX(), e.getY());
            if (m == null) return false;
            if (selectMode) {
                toggleSel(m);
                return true;
            }
            if (tapOwnedByChild(e.getX(), e.getY())) return false;
            String k = m.id != null ? m.id : m.tempId;
            long now = System.currentTimeMillis();
            if (k.equals(lastTapId) && now - lastTapAt < 320 && Math.hypot(e.getX() - lastTapX, e.getY() - lastTapY) < T.dp(44) && m.id != null) {
                MsgMenu.dismiss();
                Reactions.toggle(cid, m.id, "❤️");
                lastTapId = null;
                return true;
            }
            lastTapId = k;
            lastTapAt = now;
            lastTapX = e.getX();
            lastTapY = e.getY();
            int[] l = new int[2];
            list.getLocationInWindow(l);
            MsgMenu.show(ChatScreen.this, m, (int) e.getX() + l[0], (int) e.getY() + l[1], null);
            return true;
        }

        public void onLongPress(MotionEvent e) {
            Chats.Msg m = msgAt(e.getX(), e.getY());
            if (m == null || selectMode) return;
            longFired = true;
            list.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            enterSelect(m);
        }
    }

    public boolean onBack() {
        if (Viewer.close(true)) return true;
        if (MsgMenu.dismiss()) return true;
        if (composer.onBack()) return true;
        if (selectMode) {
            exitSelect();
            return true;
        }
        return false;
    }

    public void onShow() {
        Chats.I.activeCid = cid;
        Emoji.cid = cid;
        I = this;
    }

    public void onHide() {
        W.hideKb(root);
    }

    public void onDestroy() {
        Chats.I.off(cl);
        Rt.I.off(ws);
        Reactions.off(rxl);
        Voice.off(voiceL);
        Presence.off(presL);
        composer.destroy();
        if (cid.equals(Chats.I.activeCid)) Chats.I.activeCid = null;
        if (I == this) I = null;
        Home.refreshLists();
    }

    class Adapter extends BaseAdapter {
        public int getCount() {
            return items.size();
        }

        public Object getItem(int i) {
            return items.get(i);
        }

        public long getItemId(int i) {
            return items.get(i).key.hashCode();
        }

        public boolean hasStableIds() {
            return true;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public View getView(int i, View cv, ViewGroup p) {
            MsgView.Item it = items.get(i);
            View v;
            if (it.type == 0) v = MsgView.date(a, it.at);
            else if (it.type == 1) v = MsgView.system(a, it.sys);
            else {
                try {
                    v = MsgView.message(ChatScreen.this, it);
                } catch (Throwable e) {
                    v = MsgView.fallback(ChatScreen.this, it);
                }
            }
            int top = 0;
            if (it.type == 2) {
                boolean sib = !(conv != null && conv.group() && !it.m.mine());
                top = it.prevSame && sib ? T.dp(3) : T.dp(8);
            } else if (it.type == 1) top = T.dp(8);
            else top = T.dp(20);
            v.setPadding(0, top, 0, it.type == 0 ? T.dp(4) : 0);
            return v;
        }
    }
}
