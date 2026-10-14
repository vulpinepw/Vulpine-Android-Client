package com.vulpine.at.polls;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Polls {
    static final PathInterpolator FILL = new PathInterpolator(.22f, .61f, .36f, 1f);

    static final class St {
        JSONObject poll;
        Set<String> sel;
        boolean busy;
    }

    static final Map<String, St> state = new HashMap<>();
    static final Map<String, List<Runnable>> fetching = new HashMap<>();
    static final CopyOnWriteArrayList<WeakReference<Card>> cards = new CopyOnWriteArrayList<>();
    static boolean wired;

    static synchronized void wire() {
        if (wired) return;
        wired = true;
        Rt.I.on(f -> {
            String t = f.optString("type");
            if ("poll.updated".equals(t)) App.ui(() -> {
                St st = state.get(f.optString("pollId"));
                if (st == null) return;
                try {
                    st.poll.put("closed", f.optBoolean("closed"));
                    if (f.has("counts") && !f.isNull("counts")) st.poll.put("counts", f.opt("counts"));
                    else st.poll.remove("counts");
                    if (f.has("totalVoters")) st.poll.put("totalVoters", f.opt("totalVoters"));
                    else st.poll.remove("totalVoters");
                    if (f.has("totalVotes")) st.poll.put("totalVotes", f.opt("totalVotes"));
                    else st.poll.remove("totalVotes");
                } catch (Exception e) {
                }
                renderAll(f.optString("pollId"));
            });
            else if ("poll.error".equals(t)) {
                String r = f.optString("reason");
                String m = "poll_closed".equals(r) ? "This poll has ended" : "single_choice_only".equals(r) ? "This poll allows one choice only" : "no_vote_change".equals(r) ? "This poll does not allow changing your vote" : "Poll error";
                Ov.toast(m, true);
            }
        });
    }

    public static View view(Context c, String pollId, String cid, int maxW) {
        View v = view(c, pollId, cid);
        ((Card) v).maxW = maxW;
        return v;
    }

    public static View view(Context c, String pollId, String cid) {
        wire();
        Card card = new Card(c, pollId);
        cards.add(new WeakReference<>(card));
        St st = state.get(pollId);
        if (st != null) card.render(false);
        else ensure(pollId, () -> card.render(false));
        return card;
    }

    public static void create(MainActivity a, String cid) {
        if (a == null || cid == null) return;
        a.push(new PollCreate(cid));
    }

    static void put(String pid, JSONObject poll) {
        St st = state.get(pid);
        if (st == null) {
            st = new St();
            state.put(pid, st);
        }
        st.poll = poll;
    }

    static void ensure(String pid, Runnable done) {
        List<Runnable> w = fetching.get(pid);
        if (w != null) {
            w.add(done);
            return;
        }
        w = new ArrayList<>();
        w.add(done);
        fetching.put(pid, w);
        App.bg(() -> {
            Http.Resp r = Http.get("/polls/" + pid);
            App.ui(() -> {
                List<Runnable> ws = fetching.remove(pid);
                if (!r.ok()) return;
                put(pid, r.obj());
                if (ws != null) for (Runnable x : ws) x.run();
            });
        });
    }

    static void renderAll(String pid) {
        for (WeakReference<Card> w : cards) {
            Card c = w.get();
            if (c == null) {
                cards.remove(w);
                continue;
            }
            if (pid.equals(c.pid)) c.render(true);
        }
    }

    static int[] order(JSONObject p) {
        JSONArray o = p.optJSONArray("options");
        int n = o == null ? 0 : o.length();
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) arr[i] = i;
        if (!p.optBoolean("shuffle") || n < 2) return arr;
        String uid = (Session.userId().isEmpty() ? "anon" : Session.userId()) + "|" + p.optString("id");
        int[] h = {0x811c9dc5};
        for (int j = 0; j < uid.length(); j++) {
            h[0] ^= uid.charAt(j);
            h[0] *= 16777619;
        }
        for (int k = n - 1; k > 0; k--) {
            h[0] ^= h[0] << 13;
            h[0] ^= h[0] >>> 17;
            h[0] ^= h[0] << 5;
            double rnd = (h[0] & 0xffffffffL) / 4294967296.0;
            int r = (int) Math.floor(rnd * (k + 1));
            int t = arr[k];
            arr[k] = arr[r];
            arr[r] = t;
        }
        return arr;
    }

    static int pct(JSONObject p, String oid) {
        int base = p.optBoolean("multi") ? p.optInt("totalVotes") : p.optInt("totalVoters");
        if (base <= 0) return 0;
        JSONObject c = p.optJSONObject("counts");
        int n = c == null ? 0 : c.optInt(oid);
        return (int) Math.round(n * 100.0 / base);
    }

    static List<String> myVotes(JSONObject p) {
        List<String> l = new ArrayList<>();
        JSONArray a = p.optJSONArray("myVotes");
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(a.optString(i));
        return l;
    }

    static String fmtEnds(long t) {
        Date d = new Date(t);
        boolean h24 = App.ctx != null && android.text.format.DateFormat.is24HourFormat(App.ctx);
        return new SimpleDateFormat("MMM d", Locale.getDefault()).format(d) + ", " + new SimpleDateFormat(h24 ? "HH:mm" : "hh:mm a", Locale.getDefault()).format(d);
    }

    static int color(String s, int def) {
        if (s == null || !s.matches("^#[0-9a-fA-F]{6}$")) return def;
        return 0xff000000 | Integer.parseInt(s.substring(1), 16);
    }

    static void onOpt(String pid, String oid) {
        St st = state.get(pid);
        if (st == null) return;
        JSONObject p = st.poll;
        if (p.optBoolean("closed") || st.busy) return;
        List<String> mine = myVotes(p);
        boolean voted = !mine.isEmpty();
        if (voted && !p.optBoolean("retractable")) {
            Ov.toast("This poll does not allow changing your vote", true);
            return;
        }
        if (!p.optBoolean("multi")) {
            List<String> ids = new ArrayList<>();
            if (!(voted && mine.get(0).equals(oid) && p.optBoolean("retractable"))) ids.add(oid);
            vote(pid, ids);
            return;
        }
        if (st.sel == null) st.sel = new LinkedHashSet<>();
        if (st.sel.contains(oid)) st.sel.remove(oid);
        else st.sel.add(oid);
        renderAll(pid);
    }

    static void vote(String pid, List<String> ids) {
        St st = state.get(pid);
        if (st == null) return;
        boolean ok = Rt.I.authed() && Rt.I.send(App.jo("type", "poll.vote", "pollId", pid, "optionIds", new JSONArray(ids)));
        if (!ok) {
            Ov.toast("Not connected", true);
            return;
        }
        st.busy = true;
        try {
            st.poll.put("myVotes", new JSONArray(ids));
            if (ids.isEmpty()) {
                st.poll.remove("counts");
                st.poll.remove("totalVoters");
                st.poll.remove("totalVotes");
            }
        } catch (Exception e) {
        }
        st.sel = null;
        renderAll(pid);
        App.H.postDelayed(() -> st.busy = false, 600);
    }

    static final class Ring extends View {
        int col;
        float on;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.drawable.Drawable tick;

        Ring(Context c, int col) {
            super(c);
            this.col = col;
            tick = Ic.get("pl.polls.check", 12, 0xffffffff);
        }

        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = T.dpf(10);
            if (on > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(col);
                p.setAlpha(Math.round(255 * Math.min(1, on * 1.5f)));
                c.drawCircle(cx, cy, r, p);
                p.setAlpha(255);
                int s = T.dp(12);
                float sc = .3f + .7f * on;
                c.save();
                c.scale(sc, sc, cx, cy);
                tick.setAlpha(Math.round(255 * on));
                tick.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx + s / 2f), Math.round(cy + s / 2f));
                tick.draw(c);
                c.restore();
            }
            if (on < 1) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(2));
                p.setColor(on > 0 ? col : T.textSecondary);
                c.drawCircle(cx, cy, r - T.dpf(1), p);
            }
        }

        void set(boolean v, boolean anim) {
            float to = v ? 1 : 0;
            if (!anim || on == to) {
                on = to;
                invalidate();
                return;
            }
            ValueAnimator va = ValueAnimator.ofFloat(on, to);
            va.setDuration(250);
            va.addUpdateListener(x -> {
                on = (float) x.getAnimatedValue();
                invalidate();
            });
            va.start();
        }
    }

    static final class Card extends LinearLayout {
        final String pid;
        final Map<String, Boolean> lastOn = new HashMap<>();
        final Map<String, Integer> lastPct = new HashMap<>();

        Card(Context c, String pid) {
            super(c);
            this.pid = pid;
            setOrientation(VERTICAL);
            setPadding(T.dp(2), 0, T.dp(2), T.dp(6));
            setMinimumHeight(T.dp(40));
        }

        public int maxW;

        @Override
        protected void onMeasure(int w, int h) {
            int mode = MeasureSpec.getMode(w), size = MeasureSpec.getSize(w);
            if (maxW > 0 && mode != MeasureSpec.EXACTLY) w = MeasureSpec.makeMeasureSpec(mode == MeasureSpec.UNSPECIFIED ? maxW : Math.min(size, maxW), MeasureSpec.AT_MOST);
            super.onMeasure(w, h);
        }

        void render(boolean anim) {
            St st = state.get(pid);
            if (st == null) return;
            JSONObject p = st.poll;
            removeAllViews();
            Context c = getContext();
            TextView q = W.text(c, p.optString("question"), 14.5f, 700, T.text);
            T.lh(q, 21.75f);
            addView(q, W.lpm(W.MP, W.WC, 0, 0, 0, 6));
            boolean stats = p.has("counts") && !p.isNull("counts");
            List<String> mine = myVotes(p);
            boolean voted = !mine.isEmpty();
            boolean locked = p.optBoolean("closed") || (voted && !p.optBoolean("retractable"));
            LinearLayout list = W.col(c);
            JSONArray opts = p.optJSONArray("options");
            for (int idx : order(p)) {
                JSONObject o = opts.optJSONObject(idx);
                if (o == null) continue;
                String oid = o.optString("id");
                boolean on = mine.contains(oid) || (st.sel != null && st.sel.contains(oid));
                int col = color(o.optString("color"), T.accent);
                FrameLayout row = new FrameLayout(c);
                row.setBackground(locked ? null : W.ripple(new android.graphics.drawable.ColorDrawable(0), 8));
                View fill = new View(c);
                fill.setBackground(W.round(col, 8));
                fill.setAlpha(.22f);
                row.addView(fill, W.flp(0, 0, Gravity.START));
                LinearLayout in = W.row(c);
                in.setPadding(T.dp(8), T.dp(7), T.dp(8), T.dp(7));
                Ring ring = new Ring(c, col);
                Boolean was = lastOn.get(oid);
                ring.set(was != null && was, false);
                ring.set(on, anim);
                lastOn.put(oid, on);
                in.addView(ring, W.lp(T.dp(20), T.dp(20)));
                TextView lab = W.text(c, o.optString("label"), 14, 400, T.text);
                T.lh(lab, 21);
                in.addView(lab, W.lpm(0, W.WC, 10, 0, 0, 0));
                ((LinearLayout.LayoutParams) lab.getLayoutParams()).weight = 1;
                int pc = stats ? pct(p, oid) : 0;
                TextView pt = W.text(c, stats ? pc + "%" : "", 13, 600, T.textSecondary);
                pt.setFontFeatureSettings("tnum");
                in.addView(pt, W.lpm(W.WC, W.WC, 10, 0, 0, 0));
                row.addView(in, new FrameLayout.LayoutParams(W.MP, W.WC));
                row.setOnTouchListener((v, e) -> {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) ring.animate().scaleX(.88f).scaleY(.88f).setDuration(120).start();
                    else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) ring.animate().scaleX(1).scaleY(1).setDuration(120).start();
                    return false;
                });
                row.setOnClickListener(v -> onOpt(pid, oid));
                list.addView(row, W.lp(W.MP, W.WC));
                Integer prevPct = lastPct.get(oid);
                int from = prevPct == null ? 0 : prevPct;
                lastPct.put(oid, pc);
                boolean showPct = stats;
                boolean pctWas = prevPct != null && prevPct >= 0 && anim;
                if (showPct && !pctWas) {
                    pt.setAlpha(0);
                    pt.animate().alpha(1).setStartDelay(180).setDuration(400).start();
                }
                row.post(() -> {
                    int w = row.getWidth();
                    ValueAnimator va = ValueAnimator.ofFloat(from, pc);
                    va.setDuration(550);
                    va.setInterpolator(FILL);
                    va.addUpdateListener(x -> {
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                        lp.width = Math.round(w * (float) x.getAnimatedValue() / 100f);
                        lp.height = in.getHeight();
                        fill.setLayoutParams(lp);
                    });
                    va.start();
                });
            }
            addView(list, W.lpm(W.MP, W.WC, 0, 2, 0, 0));
            if (st.sel != null && !p.optBoolean("closed")) {
                TextView vb = W.text(c, "Vote", 14, 600, 0xffffffff);
                vb.setGravity(Gravity.CENTER);
                T.lh(vb, 21);
                vb.setPadding(T.dp(8), T.dp(8) + vb.getPaddingTop(), T.dp(8), T.dp(8) + vb.getPaddingBottom());
                vb.setBackground(W.ripple(W.round(T.accent, 8), 8));
                vb.setOnClickListener(v -> vote(pid, new ArrayList<>(st.sel)));
                addView(vb, W.lpm(W.MP, W.WC, 0, 10, 0, 0));
            }
            List<String> bits = new ArrayList<>();
            if (stats) bits.add(p.optInt("totalVoters") + " voted");
            if (p.optBoolean("closed")) bits.add("Final results");
            else if (p.optLong("endsAt") > 0) bits.add("Ends " + fmtEnds(p.optLong("endsAt")));
            if (p.optBoolean("multi") && !stats) bits.add(p.optBoolean("retractable") ? "Multiple answers" : "Multiple answers · one submission");
            TextView meta = W.text(c, android.text.TextUtils.join(" · ", bits), 12.5f, 400, T.textSecondary);
            T.lh(meta, 18.75f);
            addView(meta, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
            if (bits.isEmpty()) meta.setVisibility(GONE);
        }
    }
}
