package com.vulpine.at.polls;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import java.util.Calendar;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class PollCreate extends Page {
    static final String[] PALETTE = {"#e1306c", "#0088cc", "#7c5cff", "#22c55e", "#f59e0b"};
    static final String[] PICK = {"#e1306c", "#0088cc", "#7c5cff", "#22c55e", "#f59e0b", "#ef4444", "#f97316", "#facc15", "#84cc16", "#14b8a6", "#06b6d4", "#3b82f6", "#6366f1", "#a855f7", "#ec4899", "#64748b"};
    final String cid;
    EditText q;
    LinearLayout opts;
    Tog multi, retract, shuffle;
    TextView dt, createB;
    long endsAt;
    boolean sending;

    public PollCreate(String cid) {
        super("Create poll");
        this.cid = cid;
    }

    protected void content(LinearLayout body) {
        Context c = a;
        body.setPadding(T.dp(18), 0, T.dp(18), 0);
        if (titleView != null) {
            T.text(titleView, 17, 700, T.text);
            T.lh(titleView, 25.5f);
        }
        bar.setPadding(T.dp(18), 0, T.dp(18), 0);
        q = input(c, "Ask a question", 200);
        body.addView(q, W.lp(W.MP, T.dp(39)));
        opts = W.col(c);
        body.addView(opts, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        addRow(PALETTE[0]);
        addRow(PALETTE[1]);
        TextView add = W.text(c, "+ Add option", 13, 400, T.textSecondary);
        add.setPadding(T.dp(10), T.dp(6), T.dp(10), T.dp(6));
        add.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 8));
        add.setOnClickListener(v -> {
            if (opts.getChildCount() < 5) addRow(PALETTE[opts.getChildCount() % PALETTE.length]);
        });
        body.addView(add, W.lpm(W.WC, T.dp(32), 0, 12, 0, 0));
        multi = tog(c, body, "Allow multiple answers");
        retract = tog(c, body, "Allow changing votes");
        shuffle = tog(c, body, "Shuffle option order");
        LinearLayout dr = W.row(c);
        TextView dl = W.text(c, "Ends (optional)", 13, 400, T.textSecondary);
        dr.addView(dl);
        dt = W.text(c, "", 13, 400, T.text);
        dt.setHint("Pick date & time");
        dt.setHintTextColor(T.textMuted);
        dt.setGravity(Gravity.CENTER_VERTICAL);
        dt.setPadding(T.dp(9), 0, T.dp(9), 0);
        dt.setBackground(W.ripple(W.round(T.surface2, 8), 8));
        dt.setOnClickListener(v -> pickDate());
        dr.addView(dt, W.lpm(0, T.dp(36), 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) dt.getLayoutParams()).weight = 1;
        TextView clr = xBtn(c, v -> {
            endsAt = 0;
            dt.setText("");
        });
        clr.setContentDescription("Clear");
        dr.addView(clr, W.lpm(T.dp(28), T.dp(28), 8, 0, 0, 0));
        body.addView(dr, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        LinearLayout br = W.row(c);
        br.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = W.text(c, "Cancel", 14, 600, T.textSecondary);
        T.lh(cancel, 21);
        cancel.setPadding(T.dp(16), T.dp(8) + cancel.getPaddingTop(), T.dp(16), T.dp(8) + cancel.getPaddingBottom());
        cancel.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 8));
        cancel.setOnClickListener(v -> a.onBackPressed());
        br.addView(cancel);
        createB = W.text(c, "Create", 14, 600, 0xffffffff);
        T.lh(createB, 21);
        createB.setPadding(T.dp(16), T.dp(8) + createB.getPaddingTop(), T.dp(16), T.dp(8) + createB.getPaddingBottom());
        createB.setBackground(W.ripple(W.round(T.accent, 8), 8));
        createB.setOnClickListener(v -> create());
        br.addView(createB, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        body.addView(br, W.lpm(W.MP, W.WC, 0, 14, 0, 0));
        q.post(() -> W.showKb(q));
    }

    static EditText input(Context c, String hint, int max) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(T.textMuted);
        T.text(e, 14, 400, T.text);
        e.setSingleLine(true);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        e.setBackground(W.round(T.surface2, 8));
        e.setPadding(T.dp(11), T.dp(9), T.dp(11), T.dp(9));
        if (android.os.Build.VERSION.SDK_INT >= 29) e.setTextCursorDrawable(com.vulpine.at.ui.W.cursor(T.accent));
        return e;
    }

    static TextView xBtn(Context c, View.OnClickListener on) {
        TextView x = W.text(c, "×", 17, 400, T.textSecondary);
        x.setGravity(Gravity.CENTER);
        x.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 8));
        x.setOnClickListener(on);
        return x;
    }

    void addRow(String color) {
        Context c = a;
        LinearLayout row = W.row(c);
        View sw = new View(c);
        sw.setTag(color);
        sw.setBackground(W.round(Polls.color(color, T.accent), 6));
        sw.setContentDescription("Color");
        sw.setOnClickListener(v -> pickColor(sw));
        row.addView(sw, W.lp(T.dp(32), T.dp(32)));
        EditText t = input(c, "Option", 100);
        t.setTag("label");
        row.addView(t, W.lpm(0, T.dp(39), 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1;
        TextView x = xBtn(c, v -> {
            if (opts.getChildCount() > 2) opts.removeView(row);
        });
        row.addView(x, W.lpm(T.dp(28), T.dp(28), 8, 0, 0, 0));
        opts.addView(row, W.lpm(W.MP, W.WC, 0, opts.getChildCount() > 0 ? 8 : 0, 0, 0));
    }

    void pickColor(View sw) {
        Ov.Sheet s = new Ov.Sheet(a, false);
        s.title("Color");
        android.widget.GridLayout g = new android.widget.GridLayout(a);
        g.setColumnCount(8);
        for (String hex : PICK) {
            View v = new View(a);
            v.setBackground(W.oval(Polls.color(hex, 0)));
            v.setContentDescription(hex);
            v.setOnClickListener(x -> {
                sw.setTag(hex);
                sw.setBackground(W.round(Polls.color(hex, T.accent), 6));
                s.close();
            });
            android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
            lp.width = T.dp(32);
            lp.height = T.dp(32);
            lp.setMargins(T.dp(5), T.dp(5), T.dp(5), T.dp(5));
            g.addView(v, lp);
        }
        s.body.addView(g);
        s.show();
    }

    static final class Tog extends View {
        boolean on;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Tog(Context c) {
            super(c);
        }

        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = T.dpf(9);
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? T.accent : T.surface2);
            c.drawCircle(cx, cy, r, p);
            if (!on) return;
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0xffffffff);
            p.setStrokeWidth(T.dpf(2.5f));
            p.setStrokeCap(Paint.Cap.SQUARE);
            android.graphics.Path path = new android.graphics.Path();
            float s = T.density;
            path.moveTo(cx - 3.2f * s, cy + .2f * s);
            path.lineTo(cx - 1f * s, cy + 2.6f * s);
            path.lineTo(cx + 3.6f * s, cy - 3f * s);
            c.drawPath(path, p);
        }
    }

    Tog tog(Context c, LinearLayout body, String label) {
        LinearLayout r = W.row(c);
        r.setPadding(T.dp(2), T.dp(4), T.dp(2), T.dp(4));
        Tog t = new Tog(c);
        r.addView(t, W.lp(T.dp(18), T.dp(18)));
        TextView l = W.text(c, label, 14, 400, T.text);
        T.lh(l, 21);
        r.addView(l, W.lpm(W.WC, W.WC, 9, 0, 0, 0));
        r.setOnClickListener(v -> {
            t.on = !t.on;
            t.invalidate();
        });
        body.addView(r, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        return t;
    }

    void pickDate() {
        Calendar now = Calendar.getInstance();
        long min = System.currentTimeMillis() + 3600000L;
        Calendar init = Calendar.getInstance();
        init.setTimeInMillis(endsAt > 0 ? endsAt : min);
        DatePickerDialog d = new DatePickerDialog(a, (v, y, m, day) -> {
            TimePickerDialog tp = new TimePickerDialog(a, (tv, h, mi) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(y, m, day, h, mi, 0);
                sel.set(Calendar.MILLISECOND, 0);
                long t = sel.getTimeInMillis();
                if (t < min - 60000) t = min;
                endsAt = t;
                dt.setText(new java.text.SimpleDateFormat("MM/dd/yyyy, hh:mm a", Locale.getDefault()).format(new java.util.Date(t)));
            }, init.get(Calendar.HOUR_OF_DAY), init.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(a));
            tp.show();
        }, init.get(Calendar.YEAR), init.get(Calendar.MONTH), init.get(Calendar.DAY_OF_MONTH));
        d.getDatePicker().setMinDate(now.getTimeInMillis());
        d.show();
    }

    void create() {
        if (sending) return;
        String question = q.getText().toString().trim();
        if (question.isEmpty()) {
            Ov.toast("Enter a question", true);
            return;
        }
        JSONArray os = new JSONArray();
        for (int i = 0; i < opts.getChildCount(); i++) {
            View row = opts.getChildAt(i);
            EditText t = row.findViewWithTag("label");
            String label = t.getText().toString().trim();
            String col = (String) ((LinearLayout) row).getChildAt(0).getTag();
            if (!label.isEmpty()) os.put(App.jo("id", "o" + (i + 1), "label", label, "color", col));
        }
        if (os.length() < 2) {
            Ov.toast("A poll needs at least 2 options", true);
            return;
        }
        if (!Rt.I.authed()) {
            Ov.toast("Not connected", true);
            return;
        }
        sending = true;
        W.enable(createB, false);
        JSONObject body = App.jo("question", question, "options", os, "multi", multi.on, "retractable", retract.on, "shuffle", shuffle.on, "endsAt", endsAt > 0 ? endsAt : JSONObject.NULL);
        App.bg(() -> {
            Http.Resp r = Http.post("/conversations/" + cid + "/poll", body);
            JSONObject poll = r.ok() ? r.obj() : null;
            Chats.Msg m = null;
            if (poll != null && !poll.optString("id").isEmpty()) {
                App.ui(() -> Polls.put(poll.optString("id"), poll));
                m = Chats.I.send(cid, "[poll:" + poll.optString("id") + "]", null);
            }
            boolean ok = m != null && m.state != Chats.Msg.FAILED;
            App.ui(() -> {
                if (ok) {
                    Ov.toast("Poll created");
                    if (a.top() == this) a.pop();
                    return;
                }
                sending = false;
                W.enable(createB, true);
                Ov.toast("Poll saved but the chat message failed to send - reconnect and create it again", true);
            });
        });
    }
}
