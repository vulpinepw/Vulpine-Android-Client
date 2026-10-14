package com.vulpine.at.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Folders {
    public static void delete(String id, Runnable after) {
        JSONArray f = Session.settings().optJSONArray("chatFolders");
        if (f == null) return;
        JSONArray n = new JSONArray();
        for (int i = 0; i < f.length(); i++) if (!id.equals(f.optJSONObject(i).optString("id"))) n.put(f.optJSONObject(i));
        try {
            Session.settings().put("chatFolders", n);
        } catch (Exception e) {
        }
        Sync.save();
        if (after != null) after.run();
    }

    static View check(Context c, boolean on, float sizeDp) {
        boolean big = sizeDp > 17;
        TextView ck = W.text(c, on ? "\u2713" : "", big ? 11 : 10, 700, 0xffffffff);
        ck.setGravity(Gravity.CENTER);
        ck.setBackground(W.round(on ? T.accent : big ? 0x0fffffff : 0x2e7f7f7f, big ? 6 : 5));
        return ck;
    }

    static final class Flow extends android.view.ViewGroup {
        final int min, gap, h;

        Flow(Context c, int minDp, int gapDp, int hDp) {
            super(c);
            min = T.dp(minDp);
            gap = T.dp(gapDp);
            h = T.dp(hDp);
        }

        int[][] plan(int w) {
            int n = getChildCount(), per = Math.max(1, (w + gap) / (min + gap));
            int rows = (n + per - 1) / per;
            int[][] r = new int[n][4];
            for (int i = 0; i < n; i++) {
                int row = i / per, k = Math.min(per, n - row * per), col = i % per;
                int cw = (w - gap * (k - 1)) / k;
                r[i] = new int[]{col * (cw + gap), row * (h + gap), cw, h};
            }
            return r;
        }

        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws) - getPaddingLeft() - getPaddingRight();
            int[][] r = plan(w);
            int bottom = 0;
            for (int i = 0; i < r.length; i++) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(r[i][2], MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                bottom = Math.max(bottom, r[i][1] + h);
            }
            setMeasuredDimension(MeasureSpec.getSize(ws), bottom + getPaddingTop() + getPaddingBottom());
        }

        protected void onLayout(boolean ch, int l, int t, int rr, int b) {
            int[][] r = plan(rr - l - getPaddingLeft() - getPaddingRight());
            for (int i = 0; i < r.length; i++) getChildAt(i).layout(getPaddingLeft() + r[i][0], getPaddingTop() + r[i][1], getPaddingLeft() + r[i][0] + r[i][2], getPaddingTop() + r[i][1] + r[i][3]);
        }
    }

    static TextView label(Context c, String t) {
        TextView v = W.text(c, t, 13, 400, T.textMuted);
        T.lh(v, 19.5f);
        return v;
    }

    public static void create(MainActivity a) {
        a.push(new Page("New folder") {
            Set<String> types = new LinkedHashSet<>();
            Set<String> chats = new LinkedHashSet<>();
            W.Field name;
            TextView go;
            Flow typeRow;
            LinearLayout list;
            TextView chatsLabel;

            protected void content(LinearLayout body) {
                Context c = a;
                name = new W.Field(c, "Folder name", null);
                name.input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
                name.input.addTextChangedListener(new android.text.TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int x, int y, int z) {
                    }

                    public void onTextChanged(CharSequence s, int x, int y, int z) {
                    }

                    public void afterTextChanged(android.text.Editable e) {
                        upd();
                    }
                });
                body.addView(name, W.lpm(W.MP, W.WC, 0, 0, 0, 12));
                body.addView(label(c, "Show in this folder"), W.lpm(W.WC, W.WC, 0, 0, 0, 12));
                typeRow = new Flow(c, 96, 4, 34);
                typeRow.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
                typeRow.setBackground(W.round(T.surface2, 12));
                body.addView(typeRow, W.lpm(W.MP, W.WC, 0, 0, 0, 12));
                String[][] ts = {{"contacts", "Contacts"}, {"non-contacts", "Non-contacts"}, {"bots", "Bots"}, {"groups", "Groups"}};
                for (String[] t : ts) {
                    LinearLayout b = W.row(c);
                    b.setGravity(Gravity.CENTER);
                    b.setTag(t[0]);
                    b.setOnClickListener(v -> {
                        if (types.contains(t[0])) types.remove(t[0]);
                        else types.add(t[0]);
                        renderTypes();
                        upd();
                    });
                    typeRow.addView(b);
                    b.setContentDescription(t[1]);
                }
                renderTypes();
                chatsLabel = label(c, "Chats");
                body.addView(chatsLabel, W.lpm(W.WC, W.WC, 0, 0, 0, 12));
                list = W.col(c);
                list.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
                int n = 0;
                for (String id : Chats.I.order) if (Chats.I.conv(id) != null) n++;
                int rowH = n == 0 ? T.dp(36) : Math.max(T.dp(21), Math.min(T.dp(36), (T.dp(232) - T.dp(4) * (n - 1)) / n));
                for (String id : Chats.I.order) {
                    Chats.Conv cv = Chats.I.conv(id);
                    if (cv == null) continue;
                    LinearLayout r = W.row(c);
                    r.setPadding(T.dp(20), 0, 0, 0);
                    r.setTag(id);
                    r.setClipChildren(false);
                    r.setBackground(W.pressBg(.08f, 999));
                    r.setOnClickListener(v -> {
                        if (chats.contains(id)) chats.remove(id);
                        else chats.add(id);
                        renderChats();
                        upd();
                    });
                    LinearLayout.LayoutParams rp = W.lp(W.MP, rowH);
                    if (list.getChildCount() > 0) rp.topMargin = T.dp(4);
                    list.addView(r, rp);
                }
                renderChats();
                ScrollView ls = new ScrollView(c);
                ls.setVerticalScrollBarEnabled(false);
                ls.setOverScrollMode(View.OVER_SCROLL_NEVER);
                ls.addView(list, new FrameLayout.LayoutParams(W.MP, W.WC));
                ls.setClipToOutline(true);
                ls.setBackground(W.round(0, 8));
                body.addView(new FrameLayout(c) {
                    {
                        addView(ls, new FrameLayout.LayoutParams(W.MP, W.WC));
                    }

                    protected void onMeasure(int w, int h) {
                        super.onMeasure(w, MeasureSpec.makeMeasureSpec(T.dp(240), MeasureSpec.AT_MOST));
                    }
                }, W.lp(W.MP, W.WC));
                LinearLayout act = W.row(c);
                act.setGravity(Gravity.END);
                act.setPadding(0, T.dp(8), 0, 0);
                act.addView(W.btn(c, "Cancel", false, v -> a.pop()));
                go = W.btn(c, "Create", true, v -> {
                    JSONObject f = new JSONObject();
                    try {
                        f.put("id", UUID.randomUUID().toString()).put("name", name.value().trim()).put("filter", "custom").put("pinnedChatIds", new JSONArray(new ArrayList<>(chats)));
                        if (!types.isEmpty()) f.put("filters", new JSONArray(new ArrayList<>(types)));
                        Sync.arr("chatFolders").put(f);
                    } catch (Exception e) {
                    }
                    Sync.save();
                    a.pop();
                });
                act.addView(go, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
                body.addView(act, W.lpm(W.MP, W.WC, 0, 16, 0, 0));
                upd();
            }

            void renderTypes() {
                Context c = a;
                for (int i = 0; i < typeRow.getChildCount(); i++) {
                    LinearLayout b = (LinearLayout) typeRow.getChildAt(i);
                    String k = (String) b.getTag();
                    boolean on = types.contains(k);
                    b.removeAllViews();
                    b.addView(check(c, on, 16), W.lp(T.dp(16), T.dp(16)));
                    b.addView(W.text(c, (String) b.getContentDescription(), 14, 500, on ? T.text : T.textSecondary), W.lpm(W.WC, W.WC, 6, 0, 0, 0));
                    b.setBackground(on ? W.round(T.surface, 999) : null);
                }
            }

            void renderChats() {
                Context c = a;
                for (int i = 0; i < list.getChildCount(); i++) {
                    LinearLayout r = (LinearLayout) list.getChildAt(i);
                    String id = (String) r.getTag();
                    Chats.Conv cv = Chats.I.conv(id);
                    r.removeAllViews();
                    r.addView(check(c, chats.contains(id), 18), W.lp(T.dp(18), T.dp(18)));
                    TextView t = W.text(c, cv == null ? id : cv.title(), 14, 500, T.textSecondary);
                    t.setSingleLine(true);
                    t.setEllipsize(null);
                    t.setHorizontallyScrolling(true);
                    r.addView(t, W.lpm(W.WC, W.WC, 16, 0, 0, 0));
                }
            }

            void upd() {
                chatsLabel.setText(types.isEmpty() ? "Chats" : "Plus these chats");
                W.enable(go, !name.value().trim().isEmpty() && (!types.isEmpty() || !chats.isEmpty()));
            }
        });
    }
}
