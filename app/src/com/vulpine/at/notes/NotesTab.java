package com.vulpine.at.notes;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Sync;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class NotesTab extends Home.Tab {

    FrameLayout root;
    ScrollView sv;
    LinearLayout col, rows, form;
    W.Field nameField;
    TextView createB;
    String selected, renaming;
    final Sync.L syncL = w -> {
        if ("me".equals(w) || "settings".equals(w)) refresh();
    };

    public View build(Context c) {
        root = new FrameLayout(c);
        sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        col = W.col(c);
        sv.addView(col, new FrameLayout.LayoutParams(W.MP, W.WC));
        root.addView(sv, new FrameLayout.LayoutParams(W.MP, W.MP));
        LinearLayout head = W.row(c);
        head.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
        TextView title = W.text(c, "Notes", 16, 600, T.text);
        T.lh(title, 24);
        head.addView(title, W.lp(0, W.WC, 1));
        TextView plus = W.text(c, "+", 14, 500, T.textSecondary);
        plus.setGravity(Gravity.CENTER);
        plus.setPadding(T.dp(20), 0, T.dp(20), 0);
        plus.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 999));
        plus.setOnClickListener(v -> toggleForm());
        head.addView(plus, W.lp(W.WC, T.dp(36)));
        col.addView(head, W.lp(W.MP, T.dp(52)));
        form = W.col(c);
        form.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        form.setVisibility(View.GONE);
        nameField = new W.Field(c, "Notebook name", null);
        nameField.input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameField.input.setOnEditorActionListener((v, id, e) -> {
            create();
            return true;
        });
        form.addView(nameField, W.lp(W.MP, T.dp(56)));
        LinearLayout br = W.row(c);
        createB = small(c, "Create", true, v -> create());
        br.addView(createB);
        TextView cancel = small(c, "Cancel", false, v -> toggleForm());
        br.addView(cancel, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        form.addView(br, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        nameField.input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(Editable e) {
                W.enable(createB, !e.toString().trim().isEmpty());
            }
        });
        W.enable(createB, false);
        col.addView(form, W.lp(W.MP, W.WC));
        rows = W.col(c);
        rows.setPadding(0, T.dp(8), 0, 0);
        col.addView(rows, W.lp(W.MP, W.WC));
        Sync.on(syncL);
        refresh();
        return root;
    }

    static TextView small(Context c, String label, boolean primary, View.OnClickListener on) {
        TextView t = W.btn(c, label, primary, on);
        T.text(t, 13, 500, primary ? T.accentFg : T.textSecondary);
        t.setMinHeight(T.dp(30));
        t.setMinimumHeight(T.dp(30));
        t.setLayoutParams(W.lp(W.WC, T.dp(30)));
        return t;
    }

    void toggleForm() {
        boolean show = form.getVisibility() != View.VISIBLE;
        form.setVisibility(show ? View.VISIBLE : View.GONE);
        nameField.input.setText("");
        if (show) nameField.input.post(() -> W.showKb(nameField.input));
        else W.hideKb(nameField.input);
    }

    void create() {
        String n = nameField.value().trim();
        if (n.isEmpty()) return;
        JSONObject nb = Notes.create(n);
        selected = nb.optString("id");
        toggleForm();
        refresh();
        open(nb);
    }

    public void onInsets(int top, int bottom) {
        sv.setPadding(0, top, 0, bottom);
    }

    public void onShow() {
        refresh();
    }

    public boolean onBack() {
        if (form != null && form.getVisibility() == View.VISIBLE) {
            toggleForm();
            return true;
        }
        return false;
    }

    void refresh() {
        if (rows == null) return;
        rows.removeAllViews();
        Context c = rows.getContext();
        List<JSONObject> list = Notes.sorted();
        for (JSONObject nb : list) rows.addView(row(c, nb, list.size()));
    }

    View row(Context c, JSONObject nb, int total) {
        String id = nb.optString("id");
        boolean pinned = Notes.pinned(id);
        LinearLayout r = W.row(c);
        r.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.ColorDrawable(T.mixA(T.text, .09f)));
        sl.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
        r.setBackground(sl);
        r.setTag(id);
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Ic.get("nt.notestab.nb", 20, T.accent));
        ic.setScaleType(ImageView.ScaleType.CENTER);
        r.addView(ic, W.lp(T.dp(32), T.dp(32)));
        LinearLayout t = W.col(c);
        if (id.equals(renaming)) {
            EditText in = new EditText(c);
            in.setText(nb.optString("name"));
            T.text(in, 14, 400, T.text);
            in.setSingleLine(true);
            in.setBackground(W.round(T.surface2, 4));
            in.setPadding(T.dp(4), T.dp(2), T.dp(4), T.dp(2));
            in.setImeOptions(EditorInfo.IME_ACTION_DONE);
            boolean[] fin = {false};
            Runnable save = () -> {
                if (fin[0]) return;
                fin[0] = true;
                String v = in.getText().toString().trim();
                renaming = null;
                if (!v.isEmpty()) Notes.rename(nb, v);
                W.hideKb(in);
                refresh();
            };
            in.setOnEditorActionListener((v, aid, e) -> {
                save.run();
                return true;
            });
            in.setOnFocusChangeListener((v, has) -> {
                if (!has) save.run();
            });
            in.setOnKeyListener((v, k, e) -> {
                if (k == KeyEvent.KEYCODE_ESCAPE && e.getAction() == KeyEvent.ACTION_UP) {
                    fin[0] = true;
                    renaming = null;
                    W.hideKb(in);
                    refresh();
                    return true;
                }
                return false;
            });
            t.addView(in, W.lp(W.MP, W.WC));
            in.post(() -> {
                in.requestFocus();
                in.setSelection(in.length());
                W.showKb(in);
            });
        } else {
            TextView nm = W.text(c, nb.optString("name"), 14, 500, T.text);
            W.ellipsize(nm);
            T.lh(nm, 21);
            t.addView(nm);
        }
        TextView sub = W.text(c, Notes.count(nb), 13, 400, T.textMuted);
        W.ellipsize(sub);
        T.lh(sub, 19.5f);
        t.addView(sub);
        t.setPadding(0, T.dp(2), 0, 0);
        r.addView(t, W.lpm(0, W.WC, 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1;
        if (pinned) {
            LinearLayout tr = W.row(c);
            ImageView pi = new ImageView(c);
            pi.setImageDrawable(Ic.get("nt.notestab.pin", 14, T.accent));
            pi.setContentDescription("Drag to reorder pinned notebooks");
            tr.addView(pi, W.lp(T.dp(14), T.dp(14)));
            ImageView gr = new ImageView(c);
            gr.setImageDrawable(Ic.get("nt.notestab.grip", 14, T.textSecondary));
            gr.setAlpha(.4f);
            gr.setScaleType(ImageView.ScaleType.CENTER);
            tr.addView(gr, W.lpm(T.dp(30), T.dp(30), 2, 0, -8, 0));
            r.addView(tr, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
            gripDrag(gr, r, id);
        }
        r.setOnClickListener(v -> {
            selected = id;
            open(nb);
        });
        r.setOnLongClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            menu(v, nb, total);
            return true;
        });
        return r;
    }

    void gripDrag(View grip, View row, String id) {
        final float[] st = new float[2];
        grip.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    st[0] = e.getRawY();
                    row.setAlpha(.4f);
                    row.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    row.setTranslationY(e.getRawY() - st[0]);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    row.setAlpha(1);
                    float dy = e.getRawY() - st[0];
                    row.setTranslationY(0);
                    int h = Math.max(1, row.getHeight());
                    int shift = Math.round(dy / h);
                    if (shift != 0) {
                        JSONArray p = Notes.pinnedIds();
                        List<String> order = new ArrayList<>();
                        for (int i = 0; i < p.length(); i++) order.add(p.optString(i));
                        int from = order.indexOf(id);
                        if (from >= 0) {
                            order.remove(from);
                            int to = Math.max(0, Math.min(order.size(), from + shift));
                            order.add(to, id);
                            Notes.movePinned(order);
                        }
                    }
                    refresh();
                    return true;
                }
            }
            return false;
        });
    }

    void menu(View anchor, JSONObject nb, int total) {
        Context c = anchor.getContext();
        String id = nb.optString("id");
        boolean pinned = Notes.pinned(id);
        Ov.Menu m = new Ov.Menu(c);
        m.item(pinned ? "Unpin notebook" : "Pin notebook", Ic.get("nt.notestab.m_pin", 15, T.text), false, () -> {
            Notes.togglePin(id);
            refresh();
        });
        m.item("Rename", Ic.get("nt.notestab.m_ren", 15, T.text), false, () -> {
            renaming = id;
            refresh();
        });
        m.item("Export notebook", Ic.get("nt.notestab.m_exp", 15, T.text), false, () -> export(nb));
        m.item("Delete notebook", Ic.get("nt.notestab.m_del", 15, T.danger), true, () -> {
            if (total <= 1) return;
            Ov.confirm("Delete notebook?", "This will permanently delete the notebook and all its notes. This cannot be undone.", "Delete", true, () -> {
                Notes.delete(id);
                if (id.equals(selected)) selected = null;
                refresh();
            });
        });
        if (total <= 1) {
            View last = m.card.getChildAt(m.card.getChildCount() - 1);
            last.setEnabled(false);
        }
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        m.showAt(l[0] + anchor.getWidth() - T.dp(20), l[1] + anchor.getHeight() / 2, true);
    }

    static void export(JSONObject nb) {
        MainActivity a = MainActivity.cur;
        if (a == null) return;
        String name = Notes.fileName(nb), text = Notes.export(nb);
        android.content.Context ac = a.getApplicationContext();
        com.vulpine.at.App.bg(() -> {
            boolean ok;
            try {
                byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues v = new android.content.ContentValues();
                    v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/markdown");
                    v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Vulpine");
                    android.net.Uri u = ac.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u == null) throw new Exception("insert");
                    try (java.io.OutputStream o = ac.getContentResolver().openOutputStream(u)) {
                        o.write(data);
                    }
                } else {
                    java.io.File d = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Vulpine");
                    d.mkdirs();
                    try (java.io.OutputStream o = new java.io.FileOutputStream(new java.io.File(d, name))) {
                        o.write(data);
                    }
                }
                ok = true;
            } catch (Throwable e) {
                ok = false;
            }
            boolean done = ok;
            com.vulpine.at.App.ui(() -> {
                if (done) Ov.toast("Notebook exported");
                else Ov.toast("Export failed", true);
            });
        });
    }

    void open(JSONObject nb) {
        MainActivity a = MainActivity.cur;
        if (a == null) return;
        a.push(new NotebookScreen(nb.optString("id")));
    }
}
