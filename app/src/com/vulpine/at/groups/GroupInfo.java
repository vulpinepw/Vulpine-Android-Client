package com.vulpine.at.groups;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.home.Badges;
import com.vulpine.at.people.Fx;
import com.vulpine.at.people.Listen;
import com.vulpine.at.people.Pres;
import com.vulpine.at.people.Profile;
import com.vulpine.at.people.U;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class GroupInfo extends Page {
    static final String[] ALL_PERMS = {"editProfile", "addMembers", "removeMembers", "banMembers", "manageRoles", "manageInvite", "pinMessages", "deleteMessages", "muteMembers", "sendAttachments"};
    final String cid;
    JSONObject g;
    boolean edit, saving, keyOk;
    int shown = 30;
    String memberQ = "";
    TextView editTitle;
    LinearLayout col, membersBox, actions, stick;
    View ph;
    int insBottom;

    public void onInsets(int top, int bottom, int ime) {
        super.onInsets(top, bottom, ime);
        insBottom = bottom;
        placeStick();
    }

    void placeStick() {
        if (stick == null || ph == null || frame == null) return;
        boolean on = actions.getChildCount() > 0;
        stick.setVisibility(on ? View.VISIBLE : View.GONE);
        ph.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on || frame.getHeight() == 0) return;
        if (stick.getPaddingBottom() != T.dp(40) + insBottom + T.dp(40)) stick.setPadding(T.dp(16), T.dp(24), T.dp(16), T.dp(40) + insBottom + T.dp(40));
        int[] f = new int[2], p = new int[2];
        frame.getLocationInWindow(f);
        ph.getLocationInWindow(p);
        int natural = p[1] - f[1] + T.dp(8);
        int max = frame.getHeight() - T.dp(36) - Math.max(insBottom, T.dp(16)) - T.dp(16);
        stick.setTranslationY(Math.min(natural, max) - T.dp(24) - frame.getPaddingTop());
    }
    U.Field fName, fDesc, fWords, fSlug, fSearch;
    String slugDraft;
    static String txTo;
    static long txAt;
    boolean memBusy;

    GroupInfo(String cid) {
        super(null);
        this.cid = cid;
    }

    public static void open(MainActivity a, String cid) {
        if (a == null || cid == null) return;
        a.push(new GroupInfo(cid));
    }

    protected void content(LinearLayout body) {
        Context c = a;
        editTitle = U.txt(c, "Edit group", 20, 600, T.text);
        W.ellipsize(editTitle);
        editTitle.setVisibility(View.GONE);
        bar.addView(editTitle, W.lpm(0, W.WC, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) editTitle.getLayoutParams()).weight = 1;
        ((LinearLayout.LayoutParams) bar.getLayoutParams()).bottomMargin = T.dp(8);
        Chats.Conv cv = Chats.I.conv(cid);
        g = cv == null ? null : cv.o;
        col = W.col(c);
        col.setPadding(0, 0, T.dp(4), 0);
        body.addView(col, W.lp(W.MP, W.WC));
        actions = W.row(c);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        stick = W.col(c);
        stick.setBackgroundColor(T.bg);
        stick.setPadding(T.dp(16), T.dp(24), T.dp(16), T.dp(40));
        stick.addView(actions, W.lp(W.MP, T.dp(36)));
        stick.setClickable(true);
        frame.addView(stick, W.flp(W.MP, W.WC, Gravity.TOP));
        ph = new View(c);
        body.addView(ph, W.lpm(W.MP, T.dp(72), 0, 16, 0, 0));
        sv.getViewTreeObserver().addOnScrollChangedListener(this::placeStick);
        sv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeStick());
        body.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeStick());
        if (g != null) render();
        else {
            TextView l = U.muted(c, "Loading…", 14);
            l.setGravity(Gravity.CENTER);
            l.setPadding(T.dp(24), T.dp(24), T.dp(24), T.dp(24));
            col.addView(l, W.lp(W.MP, W.WC));
        }
        sv.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (sv.getChildCount() == 0) return;
            int rest = sv.getChildAt(0).getHeight() - sv.getScrollY() - sv.getHeight();
            if (rest < T.dp(160)) more();
        });
        Rt.I.on(ws);
        Pres.start();
        App.bg(() -> {
            if (cv != null) keyOk = Chats.I.key(cv) != null;
            Http.Resp r = Http.get("/conversations/" + cid + "?memberLimit=200&memberOffset=0");
            if (r.ok()) {
                JSONObject o = r.obj();
                Chats.Conv c2 = Chats.I.conv(cid);
                if (c2 != null) {
                    if (c2.o.has("lastMessage") && !o.has("lastMessage")) try {
                        o.put("lastMessage", c2.o.get("lastMessage"));
                        o.put("lastMessageAt", c2.o.opt("lastMessageAt"));
                    } catch (Exception e) {
                    }
                    c2.o = o;
                    keyOk = Chats.I.key(c2) != null;
                }
                loadAct(o);
                App.ui(() -> {
                    g = o;
                    render();
                });
            } else if (g == null) App.ui(() -> Ov.toast(U.err(r, "Could not load group"), true));
        });
    }

    void loadAct(JSONObject o) {
        JSONArray m = o.optJSONArray("members");
        List<String> ids = new ArrayList<>();
        if (m != null) for (int i = 0; i < m.length() && i < 200; i++) ids.add(m.optJSONObject(i).optString("id"));
        Pres.loadActivity(ids);
    }

    final Rt.Listener ws = m -> {
        if ("group.created".equals(m.optString("type"))) {
            JSONObject c = m.optJSONObject("conversation");
            if (c != null && GroupInfo.this.cid.equals(c.optString("id"))) App.ui(() -> update(c));
        }
    };

    final Pres.L pl = id -> {
        if (membersBox != null && g != null) renderMembers();
    };

    public void onShow() {
        Pres.on(pl);
    }

    public void onHide() {
        Pres.off(pl);
    }

    public void onDestroy() {
        Rt.I.off(ws);
        Pres.off(pl);
    }

    public boolean onBack() {
        if (edit) {
            setEdit(false);
            return true;
        }
        return false;
    }

    protected void onBackButton() {
        if (edit) setEdit(false);
        else a.onBackPressed();
    }

    void update(JSONObject o) {
        if (o == null) return;
        JSONArray old = g == null ? null : g.optJSONArray("members");
        JSONArray nw = o.optJSONArray("members");
        if (old != null && nw != null && old.length() > nw.length() && o.optInt("memberTotal", 0) > nw.length()) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < nw.length(); i++) ids.add(nw.optJSONObject(i).optString("id"));
            for (int i = 0; i < old.length(); i++) if (!ids.contains(old.optJSONObject(i).optString("id"))) nw.put(old.optJSONObject(i));
        }
        Chats.Conv c = Chats.I.conv(cid);
        if (c != null) {
            if (c.o.has("lastMessage") && !o.has("lastMessage")) try {
                o.put("lastMessage", c.o.get("lastMessage"));
                o.put("lastMessageAt", c.o.opt("lastMessageAt"));
            } catch (Exception e) {
            }
            c.o = o;
        }
        g = o;
        render();
        com.vulpine.at.home.Home.refreshLists();
    }

    String me() {
        return Session.userId();
    }

    JSONObject member(String uid) {
        JSONArray m = g.optJSONArray("members");
        if (m != null) for (int i = 0; i < m.length(); i++) if (uid.equals(m.optJSONObject(i).optString("id"))) return m.optJSONObject(i);
        return null;
    }

    String myRole() {
        JSONObject m = member(me());
        return m == null ? null : m.optString("role");
    }

    boolean isCr() {
        return "creator".equals(myRole());
    }

    JSONObject perms() {
        JSONObject p = new JSONObject();
        if (isCr()) {
            try {
                for (String k : ALL_PERMS) p.put(k, true);
            } catch (Exception e) {
            }
            return p;
        }
        JSONObject r = role(myRole());
        JSONObject x = r == null ? null : r.optJSONObject("permissions");
        return x == null ? p : x;
    }

    JSONObject role(String id) {
        JSONArray rs = g.optJSONArray("roles");
        if (id != null && rs != null) for (int i = 0; i < rs.length(); i++) if (id.equals(rs.optJSONObject(i).optString("id"))) return rs.optJSONObject(i);
        return null;
    }

    boolean can(String k) {
        return perms().optBoolean(k);
    }

    String link() {
        String s = g.isNull("inviteSlug") ? "" : g.optString("inviteSlug", "");
        if (s.isEmpty()) s = g.isNull("inviteToken") ? "" : g.optString("inviteToken", "");
        return s;
    }

    void setEdit(boolean on) {
        edit = on;
        if (on) slugDraft = g.isNull("inviteSlug") ? "" : g.optString("inviteSlug", "");
        editTitle.setVisibility(on ? View.VISIBLE : View.GONE);
        render();
        sv.scrollTo(0, 0);
    }

    void render() {
        if (g == null) return;
        Context c = a;
        col.removeAllViews();
        boolean canEdit = can("editProfile");
        JSONObject boost = g.optJSONObject("boost");
        String gname = g.optString("name");
        LinearLayout head = W.row(c);
        View headWrap = head;
        int fg = T.text;
        String font = boost == null ? "" : boost.optString("font", "");
        String eff = boost == null ? "" : boost.optString("effect", "");
        if (!edit && !eff.isEmpty()) {
            FrameLayout cover = new FrameLayout(c);
            cover.setClipToOutline(true);
            cover.setBackground(W.round(0, 16));
            Fx fx = Fx.make(c, eff, boost.optJSONObject("effectConfig"), T.text);
            if (fx != null) cover.addView(fx, new FrameLayout.LayoutParams(W.MP, W.MP));
            head.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(16));
            cover.addView(head, new FrameLayout.LayoutParams(W.MP, W.WC));
            headWrap = cover;
        }
        View av = avatar(c, 64);
        if (edit) head.setMinimumHeight(Math.round(T.dpf(83.5f)));
        if (edit && canEdit) {
            av.setOnClickListener(v -> pickAvatar());
            W.press(av, .96f);
        }
        head.addView(av, W.lp(T.dp(64), T.dp(64)));
        LinearLayout hm = W.col(c);
        if (edit) {
            fName = new U.Field(c, "Name", null, 64, false);
            fName.set(gname);
            hm.addView(fName, W.lp(T.dp(214), W.WC));
            int total0 = g.has("memberTotal") && !g.isNull("memberTotal") ? g.optInt("memberTotal") : (g.optJSONArray("members") == null ? 0 : g.optJSONArray("members").length());
            hm.addView(U.muted(c, total0 + " members", 13));
        } else {
            LinearLayout nr = W.row(c);
            TextView nm = U.txt(c, gname, 20, 600, fg);
            if (!font.isEmpty()) nm.setTypeface(U.font(font, 600));
            nr.addView(nm, new LinearLayout.LayoutParams(0, W.WC, 1f) {{
                weight = 0;
                width = W.WC;
            }});
            String badge = Badges.forGroup(cid);
            List<View> extra = new ArrayList<>();
            if (badge != null) {
                ImageView b = new ImageView(c);
                b.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Img.anim(b, badge, T.dp(36), true);
                nr.addView(b, W.lpm(T.dp(18), T.dp(18), 6, 0, 0, 0));
                com.vulpine.at.people.BadgeInfo.tappable(b, badge);
                extra.add(b);
            }
            int lv = boost == null ? 0 : boost.optInt("level");
            if (lv > 0) {
                TextView p = U.txt(c, "Lv " + lv, 11, 700, 0xffffffff);
                p.setPadding(T.dp(7), T.dp(1), T.dp(7), T.dp(1));
                p.setBackground(lv >= 10 ? new U.Grad(new int[]{0xfff97316, 0xffec4899, 0xff8b5cf6, 0xff3b82f6, 0xff22c55e, 0xfff97316}, 90, T.dpf(999)) : new U.Grad(new int[]{0xffa78bfa, 0xff7c3aed}, 160, T.dpf(999)));
                nr.addView(p, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
                com.vulpine.at.people.BadgeInfo.tappable(p, "lv:" + lv);
                extra.add(p);
            }
            nm.setMaxWidth(c.getResources().getDisplayMetrics().widthPixels - T.dp(32 + 64 + 12 + (headWrap != head ? 32 : 0)) - extra.size() * T.dp(46));
            hm.addView(nr);
            int total = g.has("memberTotal") && !g.isNull("memberTotal") ? g.optInt("memberTotal") : (g.optJSONArray("members") == null ? 0 : g.optJSONArray("members").length());
            TextView sub = U.txt(c, total + " members", 13, 400, headWrap != head ? T.text : T.textMuted);
            if (headWrap != head) sub.setAlpha(.78f);
            if (!font.isEmpty()) sub.setTypeface(U.font(font, 400));
            hm.addView(sub);
        }
        head.addView(hm, W.lpm(0, W.WC, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) hm.getLayoutParams()).weight = 1;
        add(headWrap, 0);
        String desc = g.isNull("description") ? "" : g.optString("description", "");
        if (edit) {
            fDesc = new U.Field(c, "Description", null, 280, true);
            fDesc.set(desc);
            add(fDesc, 8);
        } else add(U.txt(c, desc.isEmpty() ? "No description" : desc, 14, 400, T.text), 12);
        if (!edit) add(boostRow(c), 12);
        if (edit) {
            fWords = new U.Field(c, "Blocked words (comma separated)", null, 500, true);
            fWords.set(g.isNull("wordBlacklist") ? "" : g.optString("wordBlacklist", ""));
            add(fWords, 12);
        }
        if (!edit && !link().isEmpty()) add(inviteView(c), 12);
        if (canEdit && edit) {
            add(hideRow(c), 12);
            add(inviteCard(c), 12);
        }
        if (can("manageRoles") && edit) add(rolesCard(c), 12);
        add(U.muted(c, "Members", 13), 12);
        boolean hidden = g.optBoolean("hideMembers") && !canEdit && !isCr();
        if (!hidden) {
            fSearch = new U.Field(c, "Search members…", null, 0, false);
            fSearch.set(memberQ);
            fSearch.onText(() -> {
                memberQ = fSearch.value();
                renderMembers();
            });
            add(fSearch, 12);
            ((LinearLayout.LayoutParams) fSearch.getLayoutParams()).topMargin = T.dp(16);
            ((LinearLayout.LayoutParams) fSearch.getLayoutParams()).bottomMargin = T.dp(4);
            membersBox = W.col(c);
            add(membersBox, 12);
            renderMembers();
        } else {
            TextView t = U.txt(c, "The members list is hidden by the group admins.", 13, 400, T.textMuted);
            t.setTypeface(android.graphics.Typeface.create(T.r, android.graphics.Typeface.ITALIC));
            t.setPadding(0, T.dp(4), 0, T.dp(4));
            add(t, 12);
            membersBox = null;
        }
        if (edit && can("banMembers")) bannedCard(c);
        renderActions();
    }

    void add(View v, int topDp) {
        LinearLayout.LayoutParams lp = W.lp(W.MP, W.WC);
        if (col.getChildCount() > 0) lp.topMargin = T.dp(topDp);
        col.addView(v, lp);
    }

    View avatar(Context c, int size) {
        String url = g.isNull("avatarUrl") ? null : g.optString("avatarUrl", null);
        String n = g.optString("name", "?");
        FrameLayout f = new FrameLayout(c);
        f.setBackground(W.oval(T.accent));
        f.setClipToOutline(true);
        f.setOutlineProvider(new android.view.ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        TextView t = U.txt(c, (n.isEmpty() ? "?" : n.substring(0, Math.min(2, n.length()))).toUpperCase(), 24, 700, T.accentFg);
        t.setGravity(Gravity.CENTER);
        f.addView(t, new FrameLayout.LayoutParams(W.MP, W.MP));
        if (url != null && !url.isEmpty() && !"null".equals(url)) {
            f.setBackground(W.oval(T.surface2));
            t.setVisibility(View.INVISIBLE);
            ImageView iv = new U.RoundImg(c, 999);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP));
            if (url.toLowerCase().contains(".gif") && !T.power) Img.anim(iv, url, T.dp(size * 2), true);
            else Img.into(iv, url, T.dp(size * 2));
        }
        Deco.attachGroupSafe(f, cid, T.dp(size));
        return f;
    }

    static final class Deco {
        static void attachGroupSafe(FrameLayout f, String cid, int px) {
            try {
                com.vulpine.at.deco.Deco.attachGroup(f, cid, px, true);
            } catch (Throwable e) {
            }
        }
    }

    View boostRow(Context c) {
        JSONObject b = g.optJSONObject("boost");
        int lv = b == null ? 0 : b.optInt("level"), n = b == null ? 0 : b.optInt("boosts"), next = b == null ? 1 : b.optInt("next", 0), prev = b == null ? 0 : b.optInt("prev");
        LinearLayout r = W.row(c);
        r.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, W.round(T.mix(T.surface2, T.text, .08f), 12));
        s.addState(new int[]{}, W.round(T.surface2, 12));
        r.setBackground(s);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(new U.Grad(new int[]{0xffa78bfa, 0xff7c3aed}, 160, T.dpf(11)));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(Ic.get("ppl.rocket", 20, 0xffffffff));
        ic.addView(iv, W.flp(T.dp(20), T.dp(20), Gravity.CENTER));
        r.addView(ic, W.lp(T.dp(36), T.dp(36)));
        LinearLayout t = W.col(c);
        TextView h = W.text(c, lv > 0 ? "Boosts · Level " + lv : "Boost this group", 14, 600, T.text);
        T.lh(h, 18.2f);
        t.addView(h);
        TextView sb = U.muted(c, n + " boost" + (n == 1 ? "" : "s") + (next > 0 ? " · " + (next - n) + " more for Level " + (lv + 1) : " · Max level"), 13);
        T.lh(sb, 17);
        W.ellipsize(sb);
        t.addView(sb);
        r.addView(t, W.lpm(0, W.WC, 12, 0, 12, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1;
        FrameLayout mini = new FrameLayout(c);
        mini.setBackground(W.round(T.mixA(T.text, .12f), 3));
        mini.setClipToOutline(true);
        float pct = next <= 0 ? 1 : Math.max(0, Math.min(1, (n - prev) / (float) Math.max(1, next - prev)));
        View fill = new View(c);
        fill.setBackground(new U.Grad(new int[]{0xffa78bfa, 0xff7c3aed}, 90, T.dpf(3)));
        mini.addView(fill, new FrameLayout.LayoutParams(Math.round(T.dp(54) * pct), W.MP));
        r.addView(mini, W.lp(T.dp(54), T.dp(6)));
        r.setOnClickListener(v -> com.vulpine.at.pro.Boosts.open(a, cid));
        W.press(r, .99f);
        return r;
    }

    View inviteView(Context c) {
        LinearLayout l = W.col(c);
        l.addView(U.muted(c, "Invite link", 13), W.lpm(W.WC, W.WC, 0, 0, 0, 2));
        String mode = g.optString("joinMode");
        l.addView(U.muted(c, "closed".equals(mode) ? "Group is invite-only - the link only works after opening joining." : "request".equals(mode) ? "Anyone with this link can ask to join the group." : "Anyone with this link can join the group.", 13), W.lpm(W.WC, W.WC, 0, 0, 0, 2));
        String url = "https://vulpine.pw/join/" + link();
        TextView lk = U.txt(c, "vulpine.pw/join/" + link(), 14, 600, T.accent);
        lk.setPaintFlags(lk.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        lk.setOnClickListener(v -> {
            if (U.copy(url)) Ov.toast("Link copied");
            else Ov.toast("Copy failed - select the link manually", true);
        });
        l.addView(lk);
        TextView share = U.btn(c, "Share", U.GHOST, 24, 13, v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, g.optString("name", "Vulpine"));
            i.putExtra(Intent.EXTRA_TEXT, "Join " + (g.optString("name").isEmpty() ? "my group" : g.optString("name")) + " on Vulpine " + url);
            try {
                a.startActivity(Intent.createChooser(i, g.optString("name", "Vulpine")));
            } catch (Exception e) {
                if (U.copy(url)) Ov.toast("Link copied");
            }
        });
        share.setPadding(T.dp(8), 0, T.dp(8), 0);
        l.addView(share, W.lp(W.WC, T.dp(24)));
        return l;
    }

    View hideRow(Context c) {
        LinearLayout r = W.row(c);
        LinearLayout t = W.col(c);
        t.addView(U.txt(c, "Hide members list", 14, 500, T.text));
        t.addView(U.muted(c, "Members won't see the member list - only you and admins.", 13));
        r.addView(t, W.lp(0, W.WC, 1));
        U.Switch sw = new U.Switch(c, g.optBoolean("hideMembers"), v -> act(Http::patch, "/conversations/" + cid, App.jo("hideMembers", v), "Could not change"));
        sw.setContentDescription("Hide members list");
        LinearLayout.LayoutParams lp = W.lp(T.dp(42), T.dp(24));
        lp.leftMargin = T.dp(12);
        r.addView(sw, lp);
        return r;
    }

    View inviteCard(Context c) {
        LinearLayout l = W.col(c);
        l.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
        l.setBackground(W.round(T.surface2, 12));
        l.addView(U.muted(c, "Invite link", 13));
        String mode = g.optString("joinMode", "closed");
        String[] keys = {"closed", "request", "public"};
        int sel = java.util.Arrays.asList(keys).indexOf(mode);
        W.Segmented seg = new W.Segmented(c, new String[]{"Closed", "On request", "Public"}, Math.max(0, sel), i -> act(Http::post, "/conversations/" + cid + "/invite", App.jo("joinMode", keys[i]), "Could not change"));
        l.addView(seg, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        if ("public".equals(mode)) l.addView(U.txt(c, "⚠ Public groups are less secure - anyone with the link can read messages.", 13, 400, T.danger), W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        if (!"closed".equals(mode) && !link().isEmpty()) {
            LinearLayout r = W.row(c);
            String url = "https://vulpine.pw/join/" + link();
            TextView t = U.txt(c, url, 13, 400, T.text);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(t, W.lp(0, W.WC, 1));
            r.addView(U.btn(c, "Copy", U.GHOST, v -> Ov.toast(U.copy(url) ? "Link copied" : "Copy failed")), lpBtn(4));
            r.addView(U.btn(c, "New link", U.GHOST, v -> act(Http::post, "/conversations/" + cid + "/invite", App.jo("regenerateToken", true), "Could not regenerate")), lpBtn(4));
            l.addView(r, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        }
        LinearLayout r2 = W.row(c);
        fSlug = new U.Field(c, "Custom link", null, 32, false);
        fSlug.set(slugDraft);
        fSlug.onText(() -> slugDraft = fSlug.value());
        r2.addView(fSlug, W.lp(0, W.WC, 1));
        r2.addView(U.btn(c, "Set", U.GHOST, v -> {
            JSONObject b = new JSONObject();
            try {
                b.put("slug", fSlug.value());
            } catch (Exception e) {
            }
            act(Http::post, "/conversations/" + cid + "/invite", b, "Could not set");
        }), lpBtn(4));
        if (!g.isNull("inviteSlug") && !g.optString("inviteSlug", "").isEmpty()) r2.addView(U.btn(c, "Clear", U.GHOST, v -> {
            slugDraft = "";
            JSONObject b = new JSONObject();
            try {
                b.put("slug", JSONObject.NULL);
            } catch (Exception e) {
            }
            act(Http::post, "/conversations/" + cid + "/invite", b, "Could not clear");
        }), lpBtn(4));
        r2.setGravity(Gravity.BOTTOM);
        l.addView(r2, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        return l;
    }

    LinearLayout.LayoutParams lpBtn(int ml) {
        LinearLayout.LayoutParams p = W.lp(W.WC, T.dp(36));
        p.leftMargin = T.dp(ml);
        return p;
    }

    View rolesCard(Context c) {
        LinearLayout l = W.col(c);
        l.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
        l.setBackground(W.round(T.surface2, 12));
        LinearLayout h = W.row(c);
        h.addView(U.muted(c, "Roles", 13), W.lp(0, W.WC, 1));
        h.addView(U.btn(c, "+ Role", U.GHOST, v -> RoleEdit.open(this, null)));
        l.addView(h);
        JSONArray rs = g.optJSONArray("roles");
        if (rs == null || rs.length() == 0) l.addView(U.muted(c, "No custom roles", 13), W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        else for (int i = 0; i < rs.length(); i++) {
            JSONObject rr = rs.optJSONObject(i);
            LinearLayout r = W.row(c);
            Integer col = U.hex(rr.optString("color", ""));
            if (col != null) {
                View d = new View(c);
                d.setBackground(W.oval(col));
                r.addView(d, W.lpm(T.dp(12), T.dp(12), 0, 0, 8, 0));
            }
            r.addView(U.txt(c, rr.optString("name"), 14, 400, T.text));
            if ("member".equals(rr.optString("id"))) {
                TextView tag = U.txt(c, "default", 9, 400, T.textSecondary);
                tag.setPadding(T.dp(6), T.dp(1), T.dp(6), T.dp(1));
                tag.setBackground(W.round(T.dark ? 0x14ffffff : 0x14000000, 8));
                r.addView(tag, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
            }
            r.addView(W.flex(c));
            r.addView(U.btn(c, "Edit", U.GHOST, v -> RoleEdit.open(this, rr)));
            l.addView(r, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        }
        return l;
    }

    void bannedCard(Context c) {
        LinearLayout w = W.col(c);
        w.setPadding(T.dp(10), T.dp(10), T.dp(10), T.dp(10));
        w.setBackground(W.round(T.surface2, 10));
        TextView hd = U.txt(c, "Banned members", 12, 400, T.text);
        hd.setAlpha(.75f);
        w.addView(hd, W.lpm(W.WC, W.WC, 0, 0, 0, 8));
        TextView ld = U.txt(c, "Loading…", 13, 400, T.text);
        w.addView(ld);
        LinearLayout.LayoutParams lp = W.lpm(W.MP, W.WC, 0, 10, 0, 10);
        col.addView(w, lp);
        loadBanned(w);
    }

    void loadBanned(LinearLayout w) {
        App.bg(() -> {
            Http.Resp r = Http.get("/conversations/" + cid + "/banned");
            App.ui(() -> {
                if (!r.ok()) {
                    if (w.getParent() != null) ((LinearLayout) w.getParent()).removeView(w);
                    return;
                }
                Context c = w.getContext();
                while (w.getChildCount() > 1) w.removeViewAt(1);
                JSONArray b = r.obj().optJSONArray("banned");
                if (b == null || b.length() == 0) {
                    TextView t = U.txt(c, "No banned members.", 13, 400, T.text);
                    t.setAlpha(.7f);
                    w.addView(t);
                    return;
                }
                for (int i = 0; i < b.length(); i++) {
                    JSONObject u = b.optJSONObject(i);
                    LinearLayout row = W.row(c);
                    row.setPadding(0, T.dp(6), 0, T.dp(6));
                    String nm = u.optString("displayName", "");
                    if (nm.isEmpty()) nm = u.optString("handle", "Unknown");
                    String col = u.isNull("avatarColor") ? "" : u.optString("avatarColor", "");
                    AvatarView av = U.avatar(c, nm, col.isEmpty() ? "#7c3aed" : col, u.isNull("avatarUrl") ? null : u.optString("avatarUrl", null), 28, 12);
                    row.addView(av);
                    TextView t = U.txt(c, nm + " @" + u.optString("handle", "?"), 13, 400, T.text);
                    W.ellipsize(t);
                    row.addView(t, W.lpm(0, W.WC, 10, 0, 10, 0));
                    ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1;
                    TextView ub = U.txt(c, "Unban", 12, 400, 0xffffffff);
                    ub.setPadding(T.dp(10), T.dp(5), T.dp(10), T.dp(5));
                    ub.setBackground(W.ripple(W.round(T.accent, 8), 8));
                    String uid = u.optString("userId");
                    ub.setOnClickListener(v -> App.bg(() -> {
                        Http.Resp x = Http.call("DELETE", "/conversations/" + cid + "/ban/" + uid, null);
                        App.ui(() -> {
                            if (x.ok()) {
                                Ov.toast("Unbanned");
                                loadBanned(w);
                            } else Ov.toast("Could not unban", true);
                        });
                    }));
                    row.addView(ub);
                    w.addView(row);
                }
            });
        });
    }

    List<JSONObject> filteredMembers() {
        List<JSONObject> out = new ArrayList<>();
        JSONArray m = g.optJSONArray("members");
        String q = memberQ.toLowerCase();
        if (m != null) for (int i = 0; i < m.length(); i++) {
            JSONObject x = m.optJSONObject(i);
            if (x == null) continue;
            if (!q.isEmpty() && !x.optString("displayName").toLowerCase().contains(q) && !x.optString("handle").toLowerCase().contains(q)) continue;
            out.add(x);
        }
        return out;
    }

    void renderMembers() {
        if (membersBox == null) return;
        Context c = a;
        membersBox.removeAllViews();
        List<JSONObject> l = filteredMembers();
        for (int i = 0; i < l.size() && i < shown; i++) {
            JSONObject mm = l.get(i);
            LinearLayout.LayoutParams lp = W.lp(W.MP, W.WC);
            if (i > 0) lp.topMargin = T.dp(4);
            membersBox.addView(memberRow(c, mm), lp);
        }
    }

    View memberRow(Context c, JSONObject mm) {
        LinearLayout r = W.row(c);
        r.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        U.pressBg(r, 12);
        String dn = mm.optString("displayName", "");
        String handle = mm.optString("handle");
        String col = mm.isNull("avatarColor") ? null : mm.optString("avatarColor", null);
        if (col != null && col.isEmpty()) col = null;
        r.addView(U.avatar(c, dn.isEmpty() ? handle : dn, col, mm.isNull("avatarUrl") ? null : mm.optString("avatarUrl", null), 32, 13));
        LinearLayout m = W.col(c);
        LinearLayout nr = W.row(c);
        TextView nm = U.txt(c, dn, 14, 500, T.text);
        W.ellipsize(nm);
        nr.addView(nm, new LinearLayout.LayoutParams(0, W.WC, 1f) {{
            weight = 0;
            width = W.WC;
        }});
        LinearLayout bd = W.row(c);
        U.badges(bd, mm.optString("id"), handle, false, 14, 4, false);
        nr.addView(bd);
        m.addView(nr);
        JSONObject act = Pres.activity(mm.optString("id"));
        if (act != null) m.addView(Listen.line(c, act, T.textMuted, 13));
        else {
            TextView h = U.muted(c, "@" + handle, 13);
            W.ellipsize(h);
            if (mm.optBoolean("isBot")) {
                LinearLayout hr = W.row(c);
                hr.addView(h, new LinearLayout.LayoutParams(0, W.WC, 1f) {{
                    weight = 0;
                    width = W.WC;
                }});
                TextView tag = W.text(c, mm.optBoolean("botReadsAll") ? "reads all messages" : "sees commands only", 11, 600, T.textMuted);
                tag.setSingleLine(true);
                tag.setPadding(T.dp(7), T.dp(1), T.dp(7), T.dp(1));
                tag.setBackground(W.round(T.mixA(T.text, .08f), 999));
                hr.addView(tag, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
                m.addView(hr);
            } else m.addView(h);
        }
        r.addView(m, W.lpm(0, W.WC, 8, 0, 8, 0));
        ((LinearLayout.LayoutParams) m.getLayoutParams()).weight = 1;
        String role = mm.optString("role");
        TextView tag = null;
        if ("creator".equals(role)) tag = U.txt(c, "creator", 13, 400, T.accent);
        else if (!"member".equals(role) && role(role) != null) {
            JSONObject rr = role(role);
            Integer rc = U.hex(rr.optString("color", ""));
            tag = U.txt(c, rr.optString("name"), 13, 400, rc != null ? rc : T.textSecondary);
        } else if (mm.optString("id").equals(me())) tag = U.muted(c, "you", 13);
        if (tag != null) r.addView(tag);
        r.setOnClickListener(v -> Profile.open(a, handle));
        r.setOnLongClickListener(v -> {
            memberMenu(mm, v);
            return true;
        });
        return r;
    }

    void memberMenu(JSONObject mm, View anchor) {
        String uid = mm.optString("id");
        if (uid.equals(me()) || "creator".equals(mm.optString("role"))) return;
        Ov.Menu m = new Ov.Menu(a);
        int n = 0;
        if (can("manageRoles")) {
            m.item("Assign role", Svg.stroke(15, T.text, 1.8f, "M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z"), false, () -> AssignRole.open(this, uid));
            n++;
        }
        if (can("removeMembers")) {
            m.item("Kick", Svg.stroke(15, T.danger, 1.8f, "M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M5 21c0-4 3-6 7-6", "M16 13l5 5", "M21 13l-5 5"), true, () -> act((p, b) -> Http.call("DELETE", p, b), "/conversations/" + cid + "/members/" + uid, null, "Could not kick"));
            n++;
        }
        if (can("banMembers")) {
            m.item("Ban", Svg.stroke(15, T.danger, 1.8f, "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0", "M6 6l12 12"), true, () -> act(Http::post, "/conversations/" + cid + "/ban/" + uid, null, "Could not ban"));
            n++;
        }
        if (isCr()) {
            boolean armed = uid.equals(txTo) && System.currentTimeMillis() - txAt < 6000;
            m.item(armed ? "Confirm transfer?" : "Transfer ownership", Svg.stroke(15, T.danger, 1.8f, "M5 20L3.5 9l5 3L12 6l3.5 6 5-3L19 20z"), true, () -> {
                if (!(uid.equals(txTo) && System.currentTimeMillis() - txAt < 6000)) {
                    txTo = uid;
                    txAt = System.currentTimeMillis();
                    Ov.toast("Right-click again to confirm transfer");
                } else {
                    txTo = null;
                    act(Http::post, "/conversations/" + cid + "/transfer/" + uid, null, "Could not transfer");
                }
            });
            n++;
        }
        if (n == 0) return;
        anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        m.showAt(l[0] + anchor.getWidth() / 2, l[1] + anchor.getHeight() / 2, false);
    }

    interface Call {
        Http.Resp go(String path, Object body);
    }

    void act(Call fn, String path, Object body, String fail) {
        App.bg(() -> {
            Http.Resp r = fn.go(path, body);
            App.ui(() -> {
                if (r.ok()) {
                    JSONObject o = r.obj();
                    if ("group".equals(o.optString("type"))) update(o);
                    else App.bg(() -> {
                        Http.Resp x = Http.get("/conversations/" + cid + "?memberLimit=200&memberOffset=0");
                        if (x.ok()) App.ui(() -> update(x.obj()));
                    });
                } else {
                    Ov.toast(U.err(r, fail), true);
                    render();
                }
            });
        });
    }

    void more() {
        if (membersBox == null || g == null) return;
        int all = filteredMembers().size();
        if (shown < all) {
            shown = Math.min(all, shown + 30);
            renderMembers();
            return;
        }
        JSONArray m = g.optJSONArray("members");
        int cur = m == null ? 0 : m.length();
        int tot = g.has("memberTotal") && !g.isNull("memberTotal") ? g.optInt("memberTotal") : cur;
        if (cur == 0 || cur >= tot || memBusy) return;
        memBusy = true;
        App.bg(() -> {
            Http.Resp r = Http.get("/conversations/" + cid + "?memberLimit=30&memberOffset=" + cur);
            App.ui(() -> {
                memBusy = false;
                if (!r.ok()) return;
                JSONObject o = r.obj();
                JSONArray nw = o.optJSONArray("members");
                if (nw == null || nw.length() == 0) return;
                JSONArray merged = new JSONArray();
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (int i = 0; i < nw.length(); i++) ids.add(nw.optJSONObject(i).optString("id"));
                JSONArray old = g.optJSONArray("members");
                for (int i = 0; i < old.length(); i++) if (!ids.contains(old.optJSONObject(i).optString("id"))) merged.put(old.optJSONObject(i));
                for (int i = 0; i < nw.length(); i++) merged.put(nw.optJSONObject(i));
                try {
                    o.put("members", merged);
                } catch (Exception e) {
                }
                g = o;
                shown = Math.min(merged.length(), shown + 30);
                renderMembers();
                App.bg(() -> loadAct(o));
            });
        });
    }

    void renderActions() {
        Context c = a;
        actions.removeAllViews();
        boolean canEdit = can("editProfile");
        if (!edit) {
            if (canEdit && keyOk) {
                actions.addView(U.btn(c, "Add bot", U.GHOST, v -> addBot()));
                actions.addView(U.btn(c, "Add members", U.GHOST, v -> AddMembers.open(this)), lpBtn(8));
            }
            if (canEdit) actions.addView(U.btn(c, "Edit", U.GHOST, v -> {
                memberQ = "";
                setEdit(true);
            }), lpBtn(8));
        } else {
            if (canEdit && keyOk) actions.addView(U.btn(c, "Add bot", U.GHOST, v -> addBot()));
            actions.addView(U.btn(c, "Cancel", U.GHOST, v -> setEdit(false)), lpBtn(8));
            TextView s = U.btn(c, saving ? "Saving…" : "Save", U.PRIMARY, v -> save());
            U.enable(s, !saving);
            actions.addView(s, lpBtn(8));
        }
        actions.setVisibility(actions.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        actions.post(this::placeStick);
    }

    void save() {
        if (saving) return;
        saving = true;
        renderActions();
        String n = fName.value().trim();
        JSONObject b = new JSONObject();
        try {
            if (!n.isEmpty() && !n.equals(g.optString("name"))) b.put("name", n);
            if (!fDesc.value().equals(g.optString("description", ""))) b.put("description", fDesc.value());
            if (!fWords.value().equals(g.optString("wordBlacklist", ""))) b.put("wordBlacklist", fWords.value());
        } catch (Exception e) {
        }
        if (b.length() == 0) {
            saving = false;
            edit = false;
            setEdit(false);
            return;
        }
        App.bg(() -> {
            Http.Resp r = Http.patch("/conversations/" + cid, b);
            App.ui(() -> {
                saving = false;
                if (r.ok()) {
                    edit = false;
                    editTitle.setVisibility(View.GONE);
                    update(r.obj());
                    Ov.toast("Group updated");
                } else {
                    Ov.toast(U.err(r, "Could not update"), true);
                    renderActions();
                }
            });
        });
    }

    void addBot() {
        Context c = a;
        Ov.Sheet s = new Ov.Sheet(c, false);
        s.title("Add bot to group");
        U.Field f = new U.Field(c, "Bot handle (@…_bot)", null, 0, false);
        f.input.setHint("e.g. helper_bot");
        f.input.setHintTextColor(0);
        f.input.setOnFocusChangeListener((v, has) -> f.input.setHintTextColor(has ? T.textMuted : 0));
        s.body.addView(f, W.lp(W.MP, W.WC));
        Runnable submit = () -> {
            String h = f.value().trim().replaceFirst("^@", "").toLowerCase();
            if (!h.endsWith("_bot")) {
                Ov.toast("Bot handles end with _bot", true);
                return;
            }
            s.close();
            App.bg(() -> {
                JSONObject b = new JSONObject();
                try {
                    b.put("memberHandles", new JSONArray().put(h));
                } catch (Exception e) {
                }
                Http.Resp r = Http.post("/conversations/" + cid + "/members", b);
                App.ui(() -> {
                    if (r.ok()) {
                        Ov.toast("Bot added");
                        if ("group".equals(r.obj().optString("type"))) update(r.obj());
                    } else {
                        String e = r.obj().optString("error", "");
                        Ov.toast("bots_creator_or_permission".equals(e) ? "Only the creator (or addBots permission) can add bots" : (e.isEmpty() ? "Failed" : e), true);
                    }
                });
            });
        };
        f.input.setOnEditorActionListener((v, x, e) -> {
            submit.run();
            return true;
        });
        TextView ok = U.txt(c, "OK", 12, 400, T.text);
        ok.setPadding(T.dp(12), T.dp(6), T.dp(12), T.dp(6));
        ok.setBackground(W.ripple(W.round(T.surface2, 9, T.border, 1), 9));
        ok.setOnClickListener(v -> submit.run());
        s.body.addView(ok, W.lpm(W.WC, W.WC, 0, 16, 0, 0));
        s.show();
        App.H.postDelayed(() -> W.showKb(f.input), 300);
    }

    void pickAvatar() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        App.startForResult(a, i, (res, data) -> {
            if (res != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
            Uri u = data.getData();
            App.bg(() -> uploadAvatar(u));
        });
    }

    void uploadAvatar(Uri u) {
        try {
            String mime = a.getContentResolver().getType(u);
            File dir = new File(a.getCacheDir(), "gav");
            dir.mkdirs();
            File raw = new File(dir, "raw");
            try (InputStream in = a.getContentResolver().openInputStream(u); FileOutputStream o = new FileOutputStream(raw)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
            }
            JSONObject boost = g.optJSONObject("boost");
            boolean gif = "image/gif".equals(mime) && boost != null && boost.optInt("level") >= 4;
            File up;
            String name, type;
            if (gif) {
                up = raw;
                name = "group.gif";
                type = "image/gif";
            } else {
                android.graphics.Bitmap bm = Img.decode(raw, 2048);
                if (bm == null) throw new Exception("Upload failed");
                int s = Math.min(bm.getWidth(), bm.getHeight());
                android.graphics.Bitmap sq = android.graphics.Bitmap.createBitmap(bm, (bm.getWidth() - s) / 2, (bm.getHeight() - s) / 2, s, s);
                android.graphics.Bitmap out = android.graphics.Bitmap.createScaledBitmap(sq, Math.min(512, s), Math.min(512, s), true);
                up = new File(dir, "group.jpg");
                try (FileOutputStream o = new FileOutputStream(up)) {
                    out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, o);
                }
                name = "group.jpg";
                type = "image/jpeg";
            }
            Http.Resp r = Http.upload("/messages/" + cid + "/attachment", "file", up, name, type, null);
            if (!r.ok()) throw new Exception(U.err(r, "Upload failed"));
            String url = r.obj().optString("url");
            Http.Resp p = Http.patch("/conversations/" + cid, App.jo("avatarUrl", url));
            if (!p.ok()) throw new Exception(U.err(p, "Upload failed"));
            App.ui(() -> {
                update(p.obj());
                Ov.toast("Photo updated");
            });
        } catch (Exception e) {
            String m = e.getMessage() == null ? "Upload failed" : e.getMessage();
            App.ui(() -> Ov.toast(m, true));
        }
    }
}
