package com.vulpine.at.people;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.deco.Deco;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Profile extends Page {
    final String handle;
    JSONObject u;
    String state = "loading", connId;
    boolean busy;
    LinearLayout actions;
    TextView status;

    Profile(String handle) {
        super(null);
        this.handle = handle;
    }

    public static void open(MainActivity a, String handleOrId) {
        if (a == null || handleOrId == null || handleOrId.isEmpty()) return;
        String h = handleOrId.replaceFirst("^@", "");
        if (U.isUuid(h)) {
            String found = handleFor(h);
            if (found == null) {
                if (h.equals(Session.userId()) && Session.s != null) found = Session.s.optString("handle");
            }
            if (found == null) {
                Ov.toast("Could not open this profile", true);
                return;
            }
            h = found;
        }
        a.push(new Profile(h));
    }

    public static String handleFor(String uid) {
        for (Chats.Conv c : Chats.I.convs.values()) {
            if (!c.group() && uid.equals(c.o.optString("peerId"))) return c.o.optString("peerHandle");
            if (c.group()) {
                JSONObject m = c.member(uid);
                if (m != null) return m.optString("handle");
            }
        }
        JSONObject cc = Conns.cache;
        if (cc != null) for (String k : new String[]{"accepted", "incoming", "outgoing"}) for (Conns.P p : Conns.list(cc, k)) if (uid.equals(p.id)) return p.handle;
        return null;
    }

    protected void content(LinearLayout body) {
        ((LinearLayout.LayoutParams) bar.getLayoutParams()).bottomMargin = T.dp(8);
        status = U.txt(a, "Loading…", 14, 400, T.textMuted);
        status.setGravity(Gravity.CENTER);
        status.setPadding(T.dp(24), T.dp(24), T.dp(24), T.dp(24));
        body.addView(status, W.lp(W.MP, W.WC));
        Pres.start();
        App.bg(() -> {
            Http.Resp r = Http.get("/users/" + handle);
            JSONObject o = r.ok() ? r.obj() : null;
            if (o != null && !"_deleted".equals(o.optString("handle"))) {
                List<String> ids = new ArrayList<>();
                ids.add(o.optString("id"));
                Pres.loadActivity(ids);
            }
            App.ui(() -> {
                if (o == null) {
                    status.setText("User not found.");
                    return;
                }
                u = o;
                render();
            });
        });
    }

    boolean deleted() {
        return "_deleted".equals(u.optString("handle"));
    }

    void render() {
        Context c = a;
        body.removeAllViews();
        boolean del = deleted();
        String bgCss = u.isNull("profileBackground") ? "" : u.optString("profileBackground", "");
        Integer fgO = bgCss.isEmpty() ? null : U.cardFg(bgCss);
        int fg = fgO == null ? T.text : fgO;
        FrameLayout card = new FrameLayout(c);
        android.graphics.drawable.Drawable bg = U.cssBg(bgCss, 16);
        card.setBackground(bg != null ? bg : W.round(T.surface, 16));
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), T.dpf(16));
            }
        });
        Fx fx = Fx.make(c, u.optString("profileBgEffect", ""), u.optJSONObject("profileBgEffectConfig"), fg);
        if (fx != null) card.addView(fx, new FrameLayout.LayoutParams(W.MP, W.MP));
        LinearLayout inner = W.col(c);
        FrameLayout left = new FrameLayout(c);
        LinearLayout row = new LinearLayout(c) {
            @Override
            protected void onMeasure(int w, int h) {
                int in = View.MeasureSpec.getSize(w) - getPaddingLeft() - getPaddingRight();
                left.setMinimumWidth(Math.max(0, Math.round(in * .4f) - T.dp(8)));
                super.onMeasure(w, h);
            }
        };
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(T.dp(20), T.dp(24), T.dp(20), T.dp(16));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        left.setClipChildren(false);
        FrameLayout rel = new FrameLayout(c);
        rel.setClipChildren(false);
        FrameLayout avw = new FrameLayout(c);
        avw.setClipChildren(false);
        rel.addView(avw, W.flp(T.dp(104), T.dp(104), Gravity.TOP | Gravity.START));
        String name = u.optString("displayName", "");
        if (name.isEmpty()) name = u.optString("handle");
        AvatarView av = new AvatarView(c);
        av.animate = true;
        av.setLayoutParams(new FrameLayout.LayoutParams(T.dp(104), T.dp(104)));
        av.textSize(34);
        av.set(name, u.isNull("avatarColor") || u.optString("avatarColor").isEmpty() ? null : u.optString("avatarColor"), u.isNull("avatarUrl") ? null : u.optString("avatarUrl", null));
        avw.addView(av, new FrameLayout.LayoutParams(T.dp(104), T.dp(104)));
        String uid = u.optString("id");
        if (!del) {
            Deco.attach(avw, uid, T.dp(104), true);
            String st = Pres.status(uid);
            if (st != null) {
                View dot = new View(c);
                android.graphics.drawable.GradientDrawable g = W.oval(Pres.dotColor(st));
                g.setStroke(T.dp(2), T.surface);
                dot.setBackground(g);
                FrameLayout.LayoutParams dp = W.flp(T.dp(15), T.dp(15), Gravity.BOTTOM | Gravity.END);
                dp.bottomMargin = T.dp(2);
                dp.rightMargin = T.dp(2);
                rel.addView(dot, dp);
                dot.setOnLongClickListener(v -> {
                    Ov.toast("dnd".equals(st) ? "Do Not Disturb" : "inactive".equals(st) ? "Inactive" : "Online");
                    return true;
                });
            }
        }
        left.addView(rel, W.flp(W.WC, W.WC, Gravity.CENTER));
        if (!del) storyRing(rel, avw, av, uid);
        row.addView(left, W.lp(W.WC, W.WC));
        LinearLayout right = W.col(c);
        LinearLayout nameRow = W.row(c);
        TextView nm = U.txt(c, name, 16, 700, fg);
        nm.setTypeface(U.font(u.optString("profileFont", ""), 700));
        nameRow.addView(nm, W.lp(W.WC, W.WC));
        nm.setMaxLines(3);
        LinearLayout badges = W.row(c);
        if (!del) badgesFor(badges);
        nameRow.addView(badges, W.lpm(W.WC, W.WC, 4, 0, 0, 0));
        right.addView(nameRow);
        TextView hs = U.txt(c, "", 14, 400, fg);
        hs.setAlpha(.8f);
        hs.setTypeface(U.font(u.optString("profileFont", ""), 400));
        if (del) hs.setText("This account is no longer available.");
        else {
            hs.setText(handleSpans());
            hs.setMovementMethod(LinkMovementMethod.getInstance());
            hs.setHighlightColor(0);
        }
        right.addView(hs, W.lpm(W.WC, W.WC, 0, 2, 0, 0));
        if (!del && u.optBoolean("pro")) {
            Boolean open = businessOpen(u.optString("businessHours", ""));
            if (open != null) {
                TextView pill = U.txt(c, open ? "Open" : "Closed", 13, 600, 0xffffffff);
                pill.setPadding(T.dp(10), T.dp(2), T.dp(10), T.dp(2));
                pill.setBackground(W.round(open ? T.success : T.danger, 999));
                right.addView(pill, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
            }
        }
        if (!del) right.addView(new Listen.Card(c, uid, fg), W.lpm(W.MP, W.WC, 0, 16, 0, 0));
        row.addView(right, W.lpm(0, W.WC, 16, 0, 0, 0));
        ((LinearLayout.LayoutParams) right.getLayoutParams()).weight = 1;
        inner.addView(row, W.lp(W.MP, W.WC));
        actions = new LinearLayout(c) {
            @Override
            protected void onMeasure(int w, int h) {
                int n = getChildCount();
                if (n > 0 && View.MeasureSpec.getMode(w) != View.MeasureSpec.UNSPECIFIED) {
                    int gaps = 0;
                    int[] min = new int[n];
                    for (int i = 0; i < n; i++) {
                        View ch = getChildAt(i);
                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ch.getLayoutParams();
                        gaps += lp.leftMargin + lp.rightMargin;
                        ch.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(T.dp(36), View.MeasureSpec.EXACTLY));
                        min[i] = ch.getMeasuredWidth();
                    }
                    int avail = View.MeasureSpec.getSize(w) - getPaddingLeft() - getPaddingRight() - gaps;
                    boolean[] fixed = new boolean[n];
                    int rest = avail, free = n;
                    boolean again = true;
                    while (again && free > 0) {
                        again = false;
                        int share = rest / free;
                        for (int i = 0; i < n; i++) if (!fixed[i] && min[i] > share) {
                            fixed[i] = true;
                            rest -= min[i];
                            free--;
                            again = true;
                        }
                    }
                    int share = free > 0 ? rest / free : 0;
                    for (int i = 0; i < n; i++) {
                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) getChildAt(i).getLayoutParams();
                        lp.weight = 0;
                        lp.width = fixed[i] ? min[i] : share;
                    }
                }
                super.onMeasure(w, h);
            }
        };
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout actWrap = W.col(c);
        actWrap.setPadding(T.dp(20), 0, T.dp(20), T.dp(20));
        actWrap.addView(actions, W.lpm(W.MP, W.WC, 0, 16, 0, 0));
        inner.addView(actWrap, W.lp(W.MP, W.WC));
        card.addView(inner, new FrameLayout.LayoutParams(W.MP, W.WC));
        body.addView(card, W.lp(W.MP, W.WC));
        View infoBox = del ? null : info(c);
        if (!del) {
            card.setTag(Deco.ROOM_TAG, (Deco.Room) id -> room(card, infoBox, id));
            Deco.attachCard(card, u.optString("id"));
        }
        if (!del) body.addView(infoBox, W.lpm(W.MP, W.WC, 0, 16, 0, 0));
        if (!del) body.addView(com.vulpine.at.stories.Stories.posts(c, uid), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        if (!del && !uid.equals(Session.userId())) {
            renderActions();
            App.bg(this::loadConn);
        } else {
            state = del ? "deleted" : "self";
            actWrap.setPadding(T.dp(20), 0, T.dp(20), T.dp(4));
        }
        card.setAlpha(0);
        card.animate().alpha(1).setDuration(300).start();
    }

    static void room(View card, View infoBox, String frameId) {
        String p = frameId == null ? "" : frameId.split("-")[0];
        int t = 0, x = 0, b = 14;
        switch (p) {
            case "coder": t = 40; x = 24; b = 40; break;
            case "hacker": t = 40; x = 36; b = 40; break;
            case "dark": x = 6; b = 22; break;
            case "edgy": x = 10; b = 26; break;
            case "desktop": b = 48; break;
        }
        if (card.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
            lp.topMargin = T.dp(Math.max(0, t - 8));
            lp.leftMargin = lp.rightMargin = T.dp(x);
            card.setLayoutParams(lp);
        }
        if (infoBox != null && infoBox.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) infoBox.getLayoutParams();
            lp.topMargin = T.dp(Math.max(16, b));
            infoBox.setLayoutParams(lp);
        }
    }

    void storyRing(FrameLayout rel, FrameLayout avw, AvatarView av, String uid) {
        App.bg(() -> {
            Http.Resp r = Http.get("/stories/user/" + uid);
            if (!r.ok() || !r.obj().optBoolean("active")) return;
            App.ui(() -> {
                View band = new View(a);
                band.setBackground(W.oval(T.surface));
                rel.addView(band, 0, W.flp(T.dp(110), T.dp(110), Gravity.TOP | Gravity.START));
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) avw.getLayoutParams();
                lp.leftMargin = lp.topMargin = T.dp(3);
                avw.setLayoutParams(lp);
                rel.setMinimumHeight(T.dp(114));
                rel.setMinimumWidth(T.dp(110));
                av.setContentDescription("View story");
                av.setOnClickListener(v -> com.vulpine.at.stories.Stories.openUser(a, uid, av));
            });
        });
    }

    void badgesFor(LinearLayout row) {
        String uid = u.optString("id"), h = u.optString("handle");
        List<String> l = com.vulpine.at.home.Badges.forUser(uid, h);
        if (com.vulpine.at.home.Badges.isBot(h) || !l.isEmpty()) U.badges(row, uid, h, false, 16, 2, false);
        else if (u.optBoolean("pro")) U.badges(row, uid, h, true, 16, 0, true);
    }

    CharSequence handleSpans() {
        JSONArray a = u.optJSONArray("handles");
        List<String> hs = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) hs.add(a.optString(i));
        if (hs.isEmpty()) hs.add(u.optString("handle"));
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < hs.size(); i++) {
            if (i == 1) sb.append(" also ");
            else if (i > 1) sb.append(" and ");
            final String h = hs.get(i);
            int s0 = sb.length();
            sb.append("@").append(h);
            sb.setSpan(new ClickableSpan() {
                public void onClick(View w) {
                    if (U.copy(h)) Ov.toast("@" + h + " copied to clipboard");
                }

                public void updateDrawState(TextPaint ds) {
                    ds.setColor(T.accent);
                    ds.setUnderlineText(true);
                    ds.setFakeBoldText(false);
                    ds.setTypeface(U.font(u.optString("profileFont", ""), 600));
                }
            }, s0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    static Boolean businessOpen(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(json);
            JSONArray days = o.optJSONArray("days");
            if (days == null || days.length() == 0) return null;
            Calendar cal = Calendar.getInstance();
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            int d = dow == Calendar.SUNDAY ? 7 : dow - 1;
            boolean has = false;
            for (int i = 0; i < days.length(); i++) if (days.optInt(i) == d) has = true;
            if (!has) return false;
            int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            String[] op = o.optString("open", "09:00").split(":"), cl = o.optString("close", "17:00").split(":");
            int v = Integer.parseInt(op[0]) * 60 + Integer.parseInt(op[1]), y = Integer.parseInt(cl[0]) * 60 + Integer.parseInt(cl[1]);
            return now >= v && now <= y;
        } catch (Exception e) {
            return null;
        }
    }

    View info(Context c) {
        LinearLayout box = W.col(c);
        box.setBackground(W.round(T.surface, 16));
        box.setPadding(0, T.dp(2), 0, T.dp(2));
        boolean bot = com.vulpine.at.home.Badges.isBot(u.optString("handle")) || u.optBoolean("isBot");
        List<View> rows = new ArrayList<>();
        String bio = u.isNull("bio") ? "" : u.optString("bio", "");
        if (!bio.isEmpty()) rows.add(infoRow(c, bio, "Bio", true, null));
        JSONArray a = u.optJSONArray("handles");
        List<String> hs = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) if (!a.optString(i).isEmpty()) hs.add(a.optString(i));
        if (hs.isEmpty() && !u.optString("handle").isEmpty()) hs.add(u.optString("handle"));
        if (!hs.isEmpty()) {
            StringBuilder v = new StringBuilder();
            for (String h : hs) v.append(v.length() > 0 ? ", " : "").append("@").append(h);
            final String first = hs.get(0);
            rows.add(infoRow(c, v.toString(), hs.size() > 1 ? "Usernames" : "Username", false, () -> {
                if (U.copy("@" + first)) Ov.toast("Username copied");
            }));
        }
        String dob = u.isNull("dateOfBirth") ? "" : u.optString("dateOfBirth", "");
        if (!dob.isEmpty()) {
            try {
                java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                java.text.DateFormat out = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG);
                rows.add(infoRow(c, out.format(in.parse(dob)), "Birthday", false, null));
            } catch (Exception e) {
            }
        }
        rows.add(infoRow(c, bot ? "Bot" : "End-to-end encrypted", bot ? "Account type" : "Messages", false, null));
        for (int i = 0; i < rows.size(); i++) {
            View r = rows.get(i);
            if (i > 0) {
                android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{new android.graphics.drawable.ColorDrawable(T.mixA(T.text, .10f))});
                ld.setLayerGravity(0, Gravity.TOP | Gravity.FILL_HORIZONTAL);
                ld.setLayerHeight(0, Math.max(1, T.dp(.5f)));
                r.setForeground(ld);
            }
            box.addView(r, W.lp(W.MP, W.WC));
        }
        box.setAlpha(0);
        box.animate().alpha(1).setDuration(300).start();
        return box;
    }

    View infoRow(Context c, String val, String lab, boolean bio, Runnable tap) {
        LinearLayout r = W.col(c);
        r.setPadding(T.dp(16), T.dp(10), T.dp(16), T.dp(10));
        TextView v = U.lh(W.text(c, val, bio ? 14 : 16, 400, T.text), bio ? 14 * 1.35f : 16 * 1.35f);
        if (bio) com.vulpine.at.chat.Md.bio(v, val);
        r.addView(v);
        TextView l = U.lh(U.txt(c, lab, 13, 400, T.textMuted), 19.5f);
        r.addView(l, W.lpm(W.WC, W.WC, 0, 2, 0, 0));
        if (tap != null) {
            U.pressBg(r, 0);
            r.setOnClickListener(x -> tap.run());
        }
        return r;
    }

    void loadConn() {
        JSONObject l = Conns.loadAll();
        String me = Session.userId(), myH = Session.s == null ? null : Session.s.optString("handle");
        String st = "none", cid = null;
        if (l != null) {
            JSONObject hit = null;
            for (String k : new String[]{"incoming", "outgoing", "accepted", "blocked"}) {
                JSONArray a = l.optJSONArray(k);
                if (a != null) for (int i = 0; i < a.length() && hit == null; i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o == null) continue;
                    if (u.optString("id").equals(o.optString("requesterId")) || u.optString("id").equals(o.optString("targetId")) || u.optString("handle").equals(o.optString("requesterHandle")) || u.optString("handle").equals(o.optString("targetHandle"))) hit = o;
                }
            }
            if (hit != null) {
                cid = hit.optString("id");
                boolean iAmReq = me != null ? hit.optString("requesterId").equals(me) : myH != null ? hit.optString("requesterHandle").equals(myH) : !hit.optString("requesterHandle").equals(u.optString("handle"));
                String s = hit.optString("status");
                st = "accepted".equals(s) ? "friends" : "blocked".equals(s) ? "blocked" : iAmReq ? "outgoing" : "incoming";
            }
        }
        final String fs = st, fc = cid;
        App.ui(() -> {
            state = fs;
            connId = fc;
            renderActions();
        });
    }

    void renderActions() {
        if (actions == null) return;
        Context c = a;
        actions.removeAllViews();
        String bgCss = u.isNull("profileBackground") ? "" : u.optString("profileBackground", "");
        int[] fit = bgCss.isEmpty() ? null : U.fitBtn(bgCss);
        switch (state) {
            case "incoming": {
                TextView b = block(c, "Accept friend request", U.PRIMARY, v -> accept());
                fit(b, fit);
                U.enable(b, !busy);
                break;
            }
            case "outgoing":
                U.enable(block(c, "Friend request pending…", U.SECONDARY, null), false);
                break;
            case "friends":
                block(c, "Chat", U.PRIMARY, v -> chat());
                U.enable(block(c, "Already contacts", U.SECONDARY, null), false);
                break;
            case "blocked":
                U.enable(block(c, "Blocked", U.SECONDARY, null), false);
                break;
            default: {
                TextView b = block(c, busy ? "Sending…" : "loading".equals(state) ? "…" : "Add friend", U.PRIMARY, v -> send());
                fit(b, fit);
                U.enable(b, !busy && !"loading".equals(state));
            }
        }
        U.applyFont(actions, u.optString("profileFont", ""));
    }

    void fit(TextView b, int[] fit) {
        if (fit == null) return;
        b.setBackground(W.ripple(W.round(fit[0], 999), 999));
        b.setTextColor(fit[1]);
    }

    TextView block(Context c, String label, int variant, View.OnClickListener on) {
        TextView b = U.btn(c, label, variant, on);
        b.setPadding(T.dp(20), 0, T.dp(20), 0);
        b.setSingleLine(true);
        LinearLayout.LayoutParams lp = W.lp(0, T.dp(36), 1);
        if (actions.getChildCount() > 0) lp.leftMargin = T.dp(8);
        actions.addView(b, lp);
        return b;
    }

    void send() {
        if (busy) return;
        busy = true;
        renderActions();
        App.bg(() -> {
            Http.Resp r = Http.post("/connections/request", App.jo("handle", u.optString("handle")));
            App.ui(() -> {
                busy = false;
                if (r.ok()) {
                    Conns.cache = null;
                    Ov.toast("Friend request sent");
                    state = "outgoing";
                } else {
                    String m = U.err(r, "Could not send request");
                    if (m.toLowerCase().contains("exists")) {
                        Ov.toast("Request already pending");
                        state = "outgoing";
                    } else Ov.toast(m, true);
                }
                renderActions();
            });
        });
    }

    void accept() {
        if (busy || connId == null) return;
        busy = true;
        renderActions();
        App.bg(() -> {
            Http.Resp r = Http.post("/connections/" + connId + "/action", App.jo("action", "accept"));
            App.ui(() -> {
                busy = false;
                if (r.ok()) {
                    Conns.cache = null;
                    Ov.toast("Contact accepted - you can chat now");
                    state = "friends";
                    App.bg(() -> Chats.I.loadList(0));
                } else Ov.toast(U.err(r, "Could not accept"), true);
                renderActions();
            });
        });
    }

    void chat() {
        if (connId == null) {
            Ov.toast("Could not open chat", true);
            return;
        }
        App.bg(() -> {
            Http.Resp r = Http.get("/connections/" + connId + "/conversation");
            if (!r.ok()) {
                App.ui(() -> Ov.toast("Could not open chat.", true));
                return;
            }
            String cid = r.obj().optString("id");
            if (Chats.I.conv(cid) == null) {
                Chats.I.refresh(cid);
                if (!Chats.I.order.contains(cid)) Chats.I.order.add(0, cid);
            }
            App.ui(() -> Home.openChat(cid));
        });
    }
}
