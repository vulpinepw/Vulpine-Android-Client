package com.vulpine.at.call;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Rooms {
    static final Map<String, JSONArray> rooms = new HashMap<>();
    static final Map<String, JSONObject> meta = new HashMap<>();
    static final Map<String, String> groupOf = new HashMap<>();
    static final Set<String> fetching = new HashSet<>();
    static Runnable sheetRefresh;

    public static String err(String e) {
        if ("room_limit".equals(e)) return "This group reached its call room limit - boost the group to unlock more";
        if ("forbidden".equals(e)) return "You don't have permission to do that";
        if ("name_not_allowed".equals(e)) return "That name is not allowed";
        if ("room_ended".equals(e)) return "This call room has ended";
        if ("rate_limited".equals(e)) return "Slow down a little and try again";
        return "Something went wrong";
    }

    static void put(String gid, JSONArray list) {
        if (list == null) list = new JSONArray();
        rooms.put(gid, list);
        for (int i = 0; i < list.length(); i++) {
            JSONObject r = list.optJSONObject(i);
            if (r != null) groupOf.put(r.optString("id"), gid);
        }
        if (sheetRefresh != null) sheetRefresh.run();
        if (Calls.ui != null) Calls.ui.refresh();
        com.vulpine.at.home.Home.refreshLists();
        com.vulpine.at.chat.ChatScreen cs = com.vulpine.at.chat.ChatScreen.I;
        if (cs != null) try {
            cs.refreshHeader();
        } catch (Exception e) {
        }
    }

    public static JSONArray list(String gid) {
        JSONArray a = rooms.get(gid);
        return a == null ? new JSONArray() : a;
    }

    public static boolean anyLive(String gid) {
        JSONArray a = list(gid);
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            JSONArray m = r == null ? null : r.optJSONArray("members");
            if (m != null && m.length() > 0) return true;
        }
        return false;
    }

    static boolean adminOf(String key) {
        if (key == null) return false;
        String g = key.startsWith("r:") ? groupOf.get(key.substring(2)) : key;
        if (g == null) return false;
        JSONObject m = meta.get(g);
        if (m == null) {
            fetch(g, null);
            return false;
        }
        return m.optBoolean("isAdmin");
    }

    public static void fetch(String gid, Runnable done) {
        App.bg(() -> {
            Http.Resp r = Http.get("/conversations/" + gid + "/call-rooms");
            App.H.post(() -> {
                if (r.ok()) {
                    JSONObject o = r.obj();
                    meta.put(gid, o);
                    put(gid, o.optJSONArray("rooms"));
                }
                if (done != null) done.run();
            });
        });
    }

    static void onWs(JSONObject m) {
        put(m.optString("conversationId"), m.optJSONArray("rooms"));
    }

    static JSONObject room(String id) {
        String g = groupOf.get(id);
        if (g == null) return null;
        JSONArray a = list(g);
        for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (r != null && id.equals(r.optString("id"))) return r;
        }
        return null;
    }

    static void ensure(String roomId) {
        String g = groupOf.get(roomId);
        if (g != null) {
            if (fetching.add(g)) fetch(g, () -> fetching.remove(g));
            return;
        }
        for (com.vulpine.core.Chats.Conv c : com.vulpine.core.Chats.I.convs.values())
            if (c.group() && !rooms.containsKey(c.id) && fetching.add(c.id)) fetch(c.id, null);
    }

    static boolean canRename(JSONObject r) {
        String g = groupOf.get(r.optString("id"));
        JSONObject m = g == null ? null : meta.get(g);
        return r.optString("createdBy").equals(Session.userId()) || (m != null && m.optBoolean("isAdmin"));
    }

    public static String link(JSONObject r) {
        return "https://vulpine.pw/call/" + r.optString("linkToken");
    }

    static void copy(JSONObject r) {
        try {
            ClipboardManager cm = (ClipboardManager) App.ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Room link", link(r)));
            Ov.toast("Room link copied");
        } catch (Exception e) {
            Ov.toast("Something went wrong", true);
        }
    }

    static void join(MainActivity a, String key, JSONArray members) {
        Sig s = Calls.sig;
        if (s == null) return;
        if (s.cur != null && key.equals(s.cur.cid)) {
            Calls.start(a, key, false, members);
            return;
        }
        if (s.cur != null) {
            Calls.hangUp();
            App.H.postDelayed(() -> Calls.start(a, key, false, members), 400);
        } else Calls.start(a, key, false, members);
    }

    static void create(MainActivity a, String gid, Ov.Menu sh, boolean[] busy) {
        if (busy[0]) return;
        busy[0] = true;
        App.bg(() -> {
            Http.Resp r = Http.post("/conversations/" + gid + "/call-rooms", new JSONObject());
            App.H.post(() -> {
                busy[0] = false;
                if (r.status == 0) {
                    Ov.toast("Could not create the call room", true);
                    return;
                }
                if (!r.ok()) {
                    Ov.toast(err(r.error()), true);
                    return;
                }
                JSONObject room = r.obj().optJSONObject("room");
                if (room == null) return;
                groupOf.put(room.optString("id"), gid);
                JSONArray cur = list(gid);
                boolean has = false;
                for (int i = 0; i < cur.length(); i++) if (room.optString("id").equals(cur.optJSONObject(i).optString("id"))) has = true;
                if (!has) {
                    JSONArray n = new JSONArray();
                    for (int i = 0; i < cur.length(); i++) n.put(cur.opt(i));
                    n.put(room);
                    put(gid, n);
                }
                sh.close();
                join(a, "r:" + room.optString("id"), new JSONArray());
            });
        });
    }

    public static void open(MainActivity a, String gid) {
        if (a == null || gid == null) return;
        Context c = a;
        Ov.Menu sh = new Ov.Menu(c).ctx();
        LinearLayout body = W.col(c);
        int hostW = a.host.getWidth() > 0 ? a.host.getWidth() : T.dp(390);
        int bw = Math.min(T.dp(300), hostW - T.dp(24));
        int maxH = Math.min(T.dp(420), Math.round((a.host.getHeight() > 0 ? a.host.getHeight() : T.dp(844)) * .7f));
        ScrollView sv = new ScrollView(c) {
            @Override
            protected void onMeasure(int ws, int hs) {
                super.onMeasure(ws, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            }
        };
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(body, new FrameLayout.LayoutParams(bw, W.WC));
        sh.card.addView(sv, W.lp(bw, W.WC));
        boolean[] busy = {false};
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            body.removeAllViews();
            JSONArray rs = list(gid);
            JSONObject m = meta.get(gid);
            int max = m == null ? 5 : m.optInt("max", 5);
            boolean can = m == null || m.optBoolean("canCreate", true);
            boolean full = rs.length() >= max;
            String here = Calls.sig != null && Calls.sig.cur != null ? Calls.sig.cur.cid : null;
            LinearLayout hd = W.row(c);
            hd.setPadding(T.dp(12), T.dp(6), T.dp(12), T.dp(4));
            TextView ht = W.text(c, "Call rooms", 13, 400, T.textSecondary);
            T.lh(ht, 19.5f);
            hd.addView(ht, W.lp(0, W.WC, 1));
            TextView hn = W.text(c, rs.length() + " / " + max, 13, 400, T.textSecondary);
            T.lh(hn, 19.5f);
            hd.addView(hn);
            body.addView(hd, W.lp(W.MP, W.WC));
            LinearLayout nw = W.row(c);
            nw.setGravity(Gravity.CENTER_VERTICAL);
            nw.setMinimumHeight(T.dp(44));
            nw.setPadding(T.dp(12), T.dp(4), T.dp(12), T.dp(4));
            nw.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 8));
            TextView plus = W.text(c, "+", 18, 700, 0xffffffff);
            plus.setGravity(Gravity.CENTER);
            plus.setBackground(W.oval(T.accent));
            nw.addView(plus, W.lp(T.dp(28), T.dp(28)));
            LinearLayout mid = W.col(c);
            TextView cn = W.text(c, "Create a call room", 14, 600, T.accent);
            T.lh(cn, 17.5f);
            mid.addView(cn);
            if (full || !can) mid.addView(W.text(c, full ? "Limit of " + max + " rooms reached" : "You can't create call rooms here", 13, 400, T.textSecondary));
            nw.addView(mid, W.lpm(0, W.WC, 10, 0, 0, 0));
            ((LinearLayout.LayoutParams) mid.getLayoutParams()).weight = 1;
            if (full || !can) nw.setAlpha(.55f);
            else nw.setOnClickListener(v -> create(a, gid, sh, busy));
            body.addView(nw, W.lpm(W.MP, T.dp(44), 0, 2, 0, 2));
            if (rs.length() == 0) {
                TextView em = W.text(c, "No call rooms yet", 13, 400, T.textSecondary);
                T.lh(em, 19.5f);
                em.setPadding(T.dp(12), T.dp(6), T.dp(12), T.dp(8));
                body.addView(em);
            }
            for (int i = 0; i < rs.length(); i++) {
                JSONObject r = rs.optJSONObject(i);
                if (r == null) continue;
                JSONArray ms = r.optJSONArray("members");
                if (ms == null) ms = new JSONArray();
                final JSONArray fms = ms;
                boolean me = ("r:" + r.optString("id")).equals(here);
                LinearLayout it = W.row(c);
                it.setGravity(Gravity.CENTER_VERTICAL);
                it.setMinimumHeight(T.dp(44));
                it.setPadding(T.dp(12), T.dp(4), T.dp(12), T.dp(4));
                it.setBackground(W.ripple(me ? W.round(T.mixA(T.accent, .12f), 8) : new android.graphics.drawable.ColorDrawable(0), 8));
                View dot = new View(c);
                dot.setBackground(W.oval(ms.length() > 0 ? T.success : T.mixA(T.text, .25f)));
                it.addView(dot, W.lpm(T.dp(8), T.dp(8), 10, 0, 10, 0));
                if (ms.length() > 0 && !T.lowfx) {
                    dot.animate().cancel();
                    android.animation.ObjectAnimator pa = android.animation.ObjectAnimator.ofFloat(dot, "alpha", 1f, .55f, 1f);
                    pa.setDuration(1800);
                    pa.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    pa.start();
                }
                LinearLayout md = W.col(c);
                TextView nm = W.text(c, r.optString("name"), 14, 600, T.text);
                T.lh(nm, 17.5f);
                nm.setSingleLine();
                nm.setEllipsize(TextUtils.TruncateAt.END);
                md.addView(nm);
                TextView sb = W.text(c, me ? "You are here" : ms.length() > 0 ? ms.length() + " in call" : "Empty", 13, 400, T.textSecondary);
                T.lh(sb, 16.25f);
                sb.setSingleLine();
                md.addView(sb);
                it.addView(md, W.lp(0, W.WC, 1));
                if (ms.length() > 0) it.addView(avatars(c, ms, 22, 4), W.lpm(W.WC, W.WC, 10, 0, 0, 0));
                FrameLayout lk = new FrameLayout(c);
                android.widget.ImageView li = new android.widget.ImageView(c);
                li.setImageDrawable(Ic.get("call.link", 15, T.textSecondary));
                lk.addView(li, new FrameLayout.LayoutParams(T.dp(15), T.dp(15), Gravity.CENTER));
                lk.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 999));
                lk.setContentDescription("Copy room link");
                lk.setOnClickListener(v -> copy(r));
                it.addView(lk, W.lpm(T.dp(30), T.dp(30), 10, 0, 0, 0));
                it.setOnClickListener(v -> {
                    sh.close();
                    join(a, "r:" + r.optString("id"), fms);
                });
                body.addView(it, W.lp(W.MP, W.WC));
                it.setAlpha(0);
                it.setTranslationY(T.dpf(4));
                it.animate().alpha(1).translationY(0).setStartDelay(i * 35L).setDuration(220).start();
            }
        };
        render[0].run();
        sheetRefresh = () -> {
            if (body.isAttachedToWindow()) render[0].run();
        };
        View anchor = null;
        java.util.ArrayList<View> found = new java.util.ArrayList<>();
        a.host.findViewsWithText(found, "Call rooms", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        for (View v : found) if (v.isShown()) anchor = v;
        int[] hl = new int[2];
        a.host.getLocationOnScreen(hl);
        int ax = hostW - T.dp(12), ay = a.insetTop + T.dp(66);
        if (anchor != null) {
            int[] l = new int[2];
            anchor.getLocationOnScreen(l);
            ax = l[0] - hl[0] + anchor.getWidth() + T.dp(8);
            ay = l[1] - hl[1] + anchor.getHeight() + T.dp(6);
        }
        sh.showAt(Math.min(ax, hostW - T.dp(12)), ay, true);
        fetch(gid, null);
    }

    static View avatars(Context c, JSONArray ms, int sz, int max) {
        FrameLayout f = new FrameLayout(c);
        int n = Math.min(max, ms.length());
        int x = 0;
        for (int i = 0; i < n; i++) {
            JSONObject m = ms.optJSONObject(i);
            FrameLayout w = new FrameLayout(c);
            w.setBackground(W.oval(T.surface));
            w.setPadding(T.dp(2), T.dp(2), T.dp(2), T.dp(2));
            AvatarView av = new AvatarView(c);
            av.set(m.optString("displayName"), m.isNull("avatarColor") ? null : m.optString("avatarColor", null), m.isNull("avatarUrl") ? null : m.optString("avatarUrl", null));
            w.addView(av, new FrameLayout.LayoutParams(T.dp(sz), T.dp(sz)));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(T.dp(sz + 4), T.dp(sz + 4));
            lp.leftMargin = x;
            w.setTranslationZ(n - i);
            f.addView(w, lp);
            x += T.dp(sz - 7);
        }
        if (ms.length() > n) {
            TextView mo = W.text(c, "+" + (ms.length() - n), 11, 700, T.textSecondary);
            mo.setGravity(Gravity.CENTER);
            mo.setPadding(T.dp(5), 0, T.dp(5), 0);
            mo.setMinWidth(T.dp(sz));
            mo.setBackground(W.round(T.mixA(0xff7f7f7f, .14f), 999));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W.WC, T.dp(sz));
            lp.leftMargin = x + T.dp(7 + 4);
            lp.topMargin = T.dp(2);
            f.addView(mo, lp);
        }
        return f;
    }

    static void rename(CallUi ui, JSONObject r) {
        Context c = ui.c;
        ui.titleChip.removeAllViews();
        EditText in = new EditText(c);
        in.setText(r.optString("name"));
        in.setSingleLine();
        in.setImeOptions(EditorInfo.IME_ACTION_DONE);
        in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        T.text(in, 14, 600, T.text);
        in.setPadding(T.dp(14), 0, T.dp(14), 0);
        android.graphics.drawable.GradientDrawable g = W.round(CallBg.chip(), 999, T.accent, 2);
        in.setBackground(g);
        int w = Math.min(T.dp(240), ui.layer.getWidth() - T.dp(150));
        ui.titleChip.addView(in, new FrameLayout.LayoutParams(w, T.dp(34)));
        in.requestFocus();
        in.selectAll();
        W.showKb(in);
        boolean[] done = {false};
        Runnable save = () -> {
            if (done[0]) return;
            done[0] = true;
            W.hideKb(in);
            ui.titleChip.removeAllViews();
            String v = in.getText().toString().replaceAll("\\s+", " ").trim();
            if (v.length() > 32) v = v.substring(0, 32);
            if (v.isEmpty() || v.equals(r.optString("name"))) {
                ui.refresh();
                return;
            }
            final String nv = v;
            App.bg(() -> {
                Http.Resp res = Http.patch("/call-rooms/" + r.optString("id"), App.jo("name", nv));
                App.H.post(() -> {
                    if (res.status == 0) Ov.toast("Could not rename the room", true);
                    else if (!res.ok()) Ov.toast(err(res.error()), true);
                    else try {
                        r.put("name", res.obj().optString("name", nv));
                    } catch (Exception e) {
                    }
                    ui.refresh();
                });
            });
        };
        in.setOnEditorActionListener((v, a, e) -> {
            save.run();
            return true;
        });
        in.setOnFocusChangeListener((v, f) -> {
            if (!f) save.run();
        });
    }
}
