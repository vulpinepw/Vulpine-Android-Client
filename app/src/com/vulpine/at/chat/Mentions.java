package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Mentions {
    static final Pattern AT = Pattern.compile("(^|\\s)@([a-z0-9_.]*)$");
    static final Pattern CMD = Pattern.compile("^\\s*/([a-z0-9_@]*)$");
    static final java.util.Map<String, Object[]> cmdCache = new java.util.concurrent.ConcurrentHashMap<>();

    static void check(Composer cp) {
        String t = cp.input.getText().toString();
        int caret = Math.max(0, cp.input.getSelectionStart());
        String before = t.substring(0, Math.min(caret, t.length()));
        int nl = before.lastIndexOf('\n');
        String line = before.substring(nl + 1);
        Matcher cm = CMD.matcher(before);
        if (cm.matches()) {
            commands(cp, cm.group(1));
            return;
        }
        Matcher m = AT.matcher(line);
        if (m.find() && cp.s.conv != null && cp.s.conv.group()) {
            members(cp, m.group(2));
            return;
        }
        close(cp);
    }

    static boolean close(Composer cp) {
        if (cp.popup == null) return false;
        FrameLayout p = cp.popup;
        cp.popup = null;
        cp.s.root.removeView(p);
        return true;
    }

    static void show(Composer cp, List<Object[]> rows) {
        close(cp);
        if (rows.isEmpty()) return;
        Context c = cp.s.a;
        FrameLayout p = new FrameLayout(c);
        p.setBackground(W.round(T.glassBgStrong, 10, T.glassStroke, .5f));
        p.setElevation(T.dpf(14));
        ScrollView sv = new ScrollView(c);
        LinearLayout col = W.col(c);
        sv.addView(col);
        for (Object[] r : rows) {
            LinearLayout it = W.row(c);
            it.setPadding(T.dp(10), T.dp(7), T.dp(10), T.dp(7));
            it.setBackground(W.ripple(W.round(0, 6), 6));
            if (r[0] instanceof View) it.addView((View) r[0], W.lp(T.dp(22), T.dp(22)));
            TextView h = W.text(c, (String) r[1], 13, 700, T.text);
            it.addView(h, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
            TextView n = W.text(c, (String) r[2], 13, 400, T.text);
            n.setAlpha(.65f);
            W.ellipsize(n);
            it.addView(n, W.lpm(0, W.WC, 8, 0, 0, 0));
            ((LinearLayout.LayoutParams) n.getLayoutParams()).weight = 1;
            it.setOnClickListener(v -> ((Runnable) r[3]).run());
            col.addView(it, W.lp(W.MP, W.WC));
        }
        p.addView(sv);
        int[] fl = new int[2], rl = new int[2];
        cp.form.getLocationInWindow(fl);
        cp.s.root.getLocationInWindow(rl);
        int maxH = Math.min(T.dp(340), cp.s.root.getHeight() / 2);
        col.measure(View.MeasureSpec.makeMeasureSpec(T.dp(320), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int h = Math.min(maxH, col.getMeasuredHeight());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(T.dp(320), cp.s.root.getWidth() - T.dp(16)), h);
        lp.leftMargin = Math.max(T.dp(8), fl[0] - rl[0]);
        lp.topMargin = fl[1] - rl[1] - h - T.dp(8);
        cp.s.root.addView(p, lp);
        cp.popup = p;
    }

    static void members(Composer cp, String frag) {
        JSONArray mem = cp.s.conv.o.optJSONArray("members");
        List<JSONObject> l = new ArrayList<>();
        String f = frag.toLowerCase();
        if (mem != null) for (int i = 0; i < mem.length(); i++) {
            JSONObject o = mem.optJSONObject(i);
            if (o == null || o.optString("handle").isEmpty() || o.optString("id").equals(Session.userId())) continue;
            if (o.optString("handle").toLowerCase().startsWith(f) || o.optString("displayName").toLowerCase().startsWith(f)) l.add(o);
        }
        l.sort((a, b) -> a.optString("handle").compareTo(b.optString("handle")));
        List<Object[]> rows = new ArrayList<>();
        if (cp.isAdmin()) for (String[] sp : new String[][]{{"everyone", "Notify everyone in this group"}, {"here", "Notify everyone who is online"}}) {
            if (!sp[0].startsWith(f)) continue;
            ImageView ic = new ImageView(cp.s.a);
            ic.setImageDrawable(com.vulpine.at.ui.Svg.stroke(14, T.accentFg, 2.2f, "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3.92 7.94"));
            ic.setScaleType(ImageView.ScaleType.CENTER);
            ic.setBackground(W.accentBg(999));
            rows.add(new Object[]{ic, "@" + sp[0], sp[1], (Runnable) () -> {
                android.text.Editable e = cp.input.getText();
                int caret = cp.input.getSelectionStart();
                int at = e.toString().lastIndexOf('@', caret - 1);
                if (at >= 0) e.replace(at, caret, "@" + sp[0] + " ");
                close(cp);
            }});
        }
        for (int i = 0; i < Math.min(8, l.size()); i++) {
            JSONObject o = l.get(i);
            AvatarView av = new AvatarView(cp.s.a);
            av.textSize(11);
            av.set(o.optString("displayName", o.optString("handle")), o.has("avatarColor") ? o.optString("avatarColor") : "#7c3aed", o.optString("avatarUrl", null));
            String h = o.optString("handle");
            rows.add(new Object[]{av, "@" + h, o.optString("displayName"), (Runnable) () -> {
                android.text.Editable e = cp.input.getText();
                int caret = cp.input.getSelectionStart();
                int at = e.toString().lastIndexOf('@', caret - 1);
                if (at >= 0) e.replace(at, caret, "@" + h + " ");
                close(cp);
            }});
        }
        show(cp, rows);
    }

    static boolean hasBotCommands(String cid) {
        Object[] c = cmdCache.get(cid);
        return c != null && ((JSONArray) c[1]).length() > 0;
    }

    static void loadCommands(String cid, Runnable done) {
        Object[] cached = cmdCache.get(cid);
        if (cached != null && System.currentTimeMillis() - (Long) cached[0] <= 30000) return;
        cmdCache.put(cid, new Object[]{System.currentTimeMillis(), cached == null ? new JSONArray() : cached[1]});
        App.bg(() -> {
            Http.Resp r = Http.get("/conversations/" + cid + "/commands");
            JSONArray a = r.ok() ? r.obj().optJSONArray("commands") : null;
            cmdCache.put(cid, new Object[]{System.currentTimeMillis(), a == null ? new JSONArray() : a});
            App.ui(done);
        });
    }

    static void commands(Composer cp, String frag) {
        Object[] cached = cmdCache.get(cp.s.cid);
        if (cached == null || System.currentTimeMillis() - (Long) cached[0] > 30000) {
            App.bg(() -> {
                Http.Resp r = Http.get("/conversations/" + cp.s.cid + "/commands");
                JSONArray a = r.ok() ? r.obj().optJSONArray("commands") : null;
                cmdCache.put(cp.s.cid, new Object[]{System.currentTimeMillis(), a == null ? new JSONArray() : a});
                App.ui(() -> check(cp));
            });
            cmdCache.put(cp.s.cid, new Object[]{System.currentTimeMillis(), new JSONArray()});
        }
        JSONArray cmds = (JSONArray) cmdCache.get(cp.s.cid)[1];
        List<Object[]> rows = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Map<String, Integer> count = new java.util.HashMap<>();
        for (int i = 0; i < cmds.length(); i++) count.merge(cmds.optJSONObject(i).optString("command"), 1, Integer::sum);
        for (int i = 0; i < cmds.length() && rows.size() < 30; i++) {
            JSONObject o = cmds.optJSONObject(i);
            String cmd = o.optString("command");
            String ins = count.get(cmd) > 1 ? cmd + "@" + o.optString("botHandle") : cmd;
            if (!ins.startsWith(frag)) continue;
            seen.add(cmd);
            AvatarView av = new AvatarView(cp.s.a);
            av.textSize(11);
            av.set(o.optString("botName", o.optString("botHandle")), o.has("botAvatarColor") ? o.optString("botAvatarColor") : null, o.optString("botAvatarUrl", null));
            rows.add(new Object[]{av, "/" + cmd, o.optString("description") + " · @" + o.optString("botHandle"), (Runnable) () -> pick(cp, ins)});
        }
        String[][] builtin = {{"help", "Show built-in commands"}, {"clear", "Clear all your messages in this chat (Pro)"}, {"mute", "Mute a member for X minutes - /mute @user 10 (groups)"}, {"unmute", "Remove a mute - /unmute @user (groups)"}};
        for (String[] b : builtin) {
            if (seen.contains(b[0]) || !b[0].startsWith(frag) || rows.size() >= 30) continue;
            TextView sl = W.text(cp.s.a, "/", 11, 700, T.text);
            sl.setGravity(Gravity.CENTER);
            sl.setBackground(W.oval(T.surface3));
            rows.add(new Object[]{sl, "/" + b[0], b[1], (Runnable) () -> pick(cp, b[0])});
        }
        show(cp, rows);
    }

    static void pick(Composer cp, String cmd) {
        cp.input.setText("/" + cmd + " ");
        cp.input.setSelection(cp.input.length());
        close(cp);
    }
}
