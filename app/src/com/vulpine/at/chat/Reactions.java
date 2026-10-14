package com.vulpine.at.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Reactions {
    public static final Map<String, List<Object[]>> state = new ConcurrentHashMap<>();
    static final List<Runnable> ls = new CopyOnWriteArrayList<>();
    static boolean wired;
    static final Map<String, Long> fresh = new ConcurrentHashMap<>();

    public static synchronized void wire() {
        if (wired) return;
        wired = true;
        if (Rt.I.authed()) try {
            Rt.I.send(new JSONObject().put("type", "reaction.fetch"));
        } catch (Exception e) {
        }
        Rt.I.on(m -> {
            String t = m.optString("type");
            if ("auth.ok".equals(t)) {
                try {
                    Rt.I.send(new JSONObject().put("type", "reaction.fetch"));
                } catch (Exception e) {
                }
            } else if ("reaction.state".equals(t)) {
                JSONArray a = m.optJSONArray("reactions");
                Map<String, Map<String, List<String>>> tmp = new java.util.LinkedHashMap<>();
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    JSONObject r = a.optJSONObject(i);
                    if (r == null) continue;
                    tmp.computeIfAbsent(r.optString("messageId"), k -> new java.util.LinkedHashMap<>()).computeIfAbsent(r.optString("emoji"), k -> new ArrayList<>()).add(r.optString("userId"));
                }
                state.clear();
                for (Map.Entry<String, Map<String, List<String>>> e : tmp.entrySet()) {
                    List<Object[]> l = new ArrayList<>();
                    for (Map.Entry<String, List<String>> x : e.getValue().entrySet()) l.add(new Object[]{x.getKey(), x.getValue()});
                    state.put(e.getKey(), l);
                }
                fire();
            } else if ("reaction.updated".equals(t)) {
                List<Object[]> l = new ArrayList<>();
                JSONArray a = m.optJSONArray("reactions");
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    JSONObject r = a.optJSONObject(i);
                    if (r == null) continue;
                    List<String> u = new ArrayList<>();
                    JSONArray ua = r.optJSONArray("userIds");
                    if (ua != null) for (int k = 0; k < ua.length(); k++) u.add(ua.optString(k));
                    l.add(new Object[]{r.optString("emoji"), u});
                }
                String mid = m.optString("messageId");
                List<Object[]> prev = state.get(mid);
                for (Object[] x : l) if (prev == null || !has(prev, (String) x[0])) fresh.put(mid + "|" + x[0], System.currentTimeMillis());
                if (l.isEmpty()) state.remove(mid);
                else state.put(mid, l);
                fire();
            }
        });
    }

    static boolean has(List<Object[]> l, String e) {
        for (Object[] x : l) if (e.equals(x[0])) return true;
        return false;
    }

    static void fire() {
        App.ui(() -> {
            for (Runnable r : ls) r.run();
        });
    }

    public static void on(Runnable r) {
        ls.add(r);
    }

    public static void off(Runnable r) {
        ls.remove(r);
    }

    @SuppressWarnings("unchecked")
    public static void toggle(String cid, String mid, String emoji) {
        if (mid == null) return;
        String me = Session.userId();
        List<Object[]> l = state.get(mid);
        List<Object[]> n = new ArrayList<>();
        boolean found = false;
        if (l != null) for (Object[] x : l) {
            List<String> u = new ArrayList<>((List<String>) x[1]);
            if (emoji.equals(x[0])) {
                found = true;
                if (u.contains(me)) u.remove(me);
                else u.add(me);
            }
            if (!u.isEmpty()) n.add(new Object[]{x[0], u});
        }
        if (!found) {
            List<String> u = new ArrayList<>();
            u.add(me);
            n.add(new Object[]{emoji, u});
            fresh.put(mid + "|" + emoji, System.currentTimeMillis());
        }
        if (n.isEmpty()) state.remove(mid);
        else state.put(mid, n);
        fire();
        Chats.I.react(cid, mid, emoji);
    }

    @SuppressWarnings("unchecked")
    static View row(ChatScreen s, Chats.Msg m, List<Object[]> rx, boolean pad) {
        Context c = s.a;
        LinearLayout row = new FlowRow(c);
        if (pad) row.setPadding(T.dp(8), 0, T.dp(8), T.dp(18));
        String me = Session.userId();
        for (Object[] x : rx) {
            String emo = (String) x[0];
            List<String> users = (List<String>) x[1];
            LinearLayout pill = W.row(c);
            pill.setPadding(T.dp(8), T.dp(1), T.dp(8), T.dp(1));
            boolean mine = users.contains(me);
            pill.setBackground(W.round(mine ? T.accentSoft : 0x2e7f7f7f, 999));
            View e = Anim.emoji(c, emo, 18, s);
            pill.addView(e, W.lp(T.dp(18), T.dp(20)));
            if (users.size() > 1) {
                TextView n = W.text(c, String.valueOf(users.size()), 11, 600, T.text);
                n.setAlpha(.8f);
                pill.addView(n, W.lpm(W.WC, W.WC, 4, 0, 0, 0));
            }
            pill.setOnClickListener(v -> toggle(s.cid, m.id, emo));
            com.vulpine.at.ui.W.press(pill, .92f);
            Long f = fresh.get(m.id + "|" + emo);
            if (f != null && System.currentTimeMillis() - f < 900) {
                pill.setScaleX(.3f);
                pill.setScaleY(.3f);
                pill.setAlpha(0);
                pill.animate().scaleX(1).scaleY(1).alpha(1).setDuration(340).setInterpolator(T.POP).start();
                fresh.remove(m.id + "|" + emo);
            }
            row.addView(pill);
        }
        LinearLayout.LayoutParams lp = W.lp(W.WC, W.WC);
        lp.topMargin = T.dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    public static class FlowRow extends LinearLayout {
        public FlowRow(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int max = MeasureSpec.getSize(ws);
            if (MeasureSpec.getMode(ws) == MeasureSpec.UNSPECIFIED) max = Integer.MAX_VALUE;
            int x = 0, y = 0, rowH = 0, w = 0, gap = T.dp(4);
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                measureChild(ch, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), hs);
                int cw = ch.getMeasuredWidth(), chh = ch.getMeasuredHeight();
                if (x > 0 && x + cw > max - getPaddingLeft() - getPaddingRight()) {
                    x = 0;
                    y += rowH + gap;
                    rowH = 0;
                }
                x += cw + gap;
                rowH = Math.max(rowH, chh);
                w = Math.max(w, x - gap);
            }
            setMeasuredDimension(Math.min(max, w + getPaddingLeft() + getPaddingRight()), y + rowH + getPaddingTop() + getPaddingBottom());
        }

        @Override
        protected void onLayout(boolean ch, int l, int t, int r, int b) {
            int max = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, rowH = 0, gap = T.dp(4);
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                int cw = c.getMeasuredWidth(), chh = c.getMeasuredHeight();
                if (x > 0 && x + cw > max) {
                    x = 0;
                    y += rowH + gap;
                    rowH = 0;
                }
                c.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + cw, getPaddingTop() + y + chh);
                x += cw + gap;
                rowH = Math.max(rowH, chh);
            }
        }
    }
}
