package com.vulpine.at.stories;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Sheets {

    public interface Cb {
        void on(List<String> ids);
    }

    public static final class Sheet {
        public final FrameLayout ov;
        public final LinearLayout card, box;
        public Runnable onClose;
        boolean closed;

        public Sheet(Context c, String title) {
            ov = new FrameLayout(c);
            ov.setBackgroundColor(0x80000000);
            ov.setClickable(true);
            ov.setOnClickListener(v -> close());
            card = W.col(c);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(T.surface);
            float r = T.dpf(16);
            bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            card.setBackground(bg);
            card.setClickable(true);
            card.setElevation(T.dpf(24));
            MainActivity a = MainActivity.cur;
            card.setPadding(0, 0, 0, a == null ? 0 : a.insetBottom);
            LinearLayout h = W.row(c);
            h.setPadding(T.dp(18), T.dp(10), T.dp(6), T.dp(4));
            TextView t = W.text(c, title, 14, 700, T.text);
            T.lh(t, 21);
            W.ellipsize(t);
            h.addView(t, W.lp(0, W.WC, 1));
            ImageView x = new ImageView(c);
            x.setImageDrawable(Ic.get("st.sheets.x", 22, T.textSecondary));
            x.setScaleType(ImageView.ScaleType.CENTER);
            x.setBackground(W.ripple(W.oval(0), 999));
            x.setContentDescription("Close");
            x.setOnClickListener(v -> close());
            h.addView(x, W.lp(T.dp(40), T.dp(40)));
            card.addView(h, W.lp(W.MP, W.WC));
            box = W.col(c);
            box.setPadding(T.dp(12), T.dp(4), T.dp(12), T.dp(12));
            ScrollView sv = new ScrollView(c);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sv.setVerticalScrollBarEnabled(false);
            sv.addView(box, new FrameLayout.LayoutParams(W.MP, W.WC));
            card.addView(sv, W.lp(W.MP, W.WC));
            ov.addView(card, W.flp(W.MP, W.WC, Gravity.BOTTOM));
        }

        public Sheet show() {
            FrameLayout host = Ov.host();
            if (host == null) return this;
            host.addView(ov, new FrameLayout.LayoutParams(W.MP, W.MP));
            int maxH = Math.round(host.getHeight() * .82f);
            card.post(() -> {
                if (maxH > 0 && card.getHeight() > maxH) {
                    FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) card.getLayoutParams();
                    p.height = maxH;
                    card.setLayoutParams(p);
                    View sv = card.getChildAt(1);
                    sv.setLayoutParams(new LinearLayout.LayoutParams(W.MP, 0, 1));
                }
            });
            ov.setAlpha(0);
            ov.animate().alpha(1).setDuration(200).start();
            card.setTranslationY(host.getHeight());
            card.post(() -> {
                card.setTranslationY(card.getHeight());
                card.animate().translationY(0).setDuration(300).setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1f)).start();
            });
            Ov.push(ov, this::close);
            return this;
        }

        public void close() {
            if (closed) return;
            closed = true;
            Ov.remove(ov);
            W.hideKb(ov);
            FrameLayout host = Ov.host();
            ov.animate().alpha(0).setDuration(200).withEndAction(() -> {
                if (host != null) host.removeView(ov);
            }).start();
            if (onClose != null) onClose.run();
        }

        public TextView done(String label, boolean inBody, Runnable on) {
            TextView b = W.btn(card.getContext(), label, true, v -> on.run());
            LinearLayout.LayoutParams p = W.lp(W.MP, T.dp(36));
            if (inBody) {
                p.topMargin = T.dp(4);
                box.addView(b, p);
            } else {
                p.setMargins(T.dp(12), 0, T.dp(12), T.dp(12));
                card.addView(b, p);
            }
            return b;
        }
    }

    static Drawable pressBg() {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, W.round(T.surface2, 12));
        sl.addState(new int[]{}, new ColorDrawable(0));
        return sl;
    }

    static final class Mark extends View {
        final boolean check;
        boolean on;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Drawable tick;

        Mark(Context c, boolean check, boolean on) {
            super(c);
            this.check = check;
            this.on = on;
            tick = check ? Ic.get("st.sheets.check", 16, 0xffffffff) : null;
        }

        void set(boolean v) {
            on = v;
            invalidate();
        }

        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = T.dpf(11);
            p.setStyle(Paint.Style.FILL);
            if (!on) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(T.dpf(2));
                p.setColor(T.mixA(T.text, .30f));
                c.drawCircle(cx, cy, r - T.dpf(1), p);
                return;
            }
            p.setColor(T.accent);
            if (check) {
                c.drawCircle(cx, cy, r, p);
                int s = T.dp(16);
                tick.setBounds(Math.round(cx - s / 2f), Math.round(cy - s / 2f), Math.round(cx + s / 2f), Math.round(cy + s / 2f));
                tick.draw(c);
                return;
            }
            c.drawCircle(cx, cy, r + T.dpf(2), p);
            p.setColor(T.surface);
            c.drawCircle(cx, cy, r, p);
            p.setColor(T.accent);
            c.drawCircle(cx, cy, r - T.dpf(5), p);
        }
    }

    static LinearLayout row(Context c, View lead, String title, String sub, View trail) {
        LinearLayout r = W.row(c);
        r.setPadding(T.dp(6), T.dp(8), T.dp(6), T.dp(8));
        r.setClipToPadding(false);
        r.setBackground(pressBg());
        if (lead != null) {
            r.addView(lead);
            View g = new View(c);
            r.addView(g, W.lp(T.dp(12), 1));
        }
        LinearLayout t = W.col(c);
        TextView b = W.text(c, title, 14, 500, T.text);
        W.ellipsize(b);
        T.lh(b, 18.2f);
        t.addView(b);
        if (sub != null) {
            TextView s = W.text(c, sub, 13, 400, T.textMuted);
            W.ellipsize(s);
            T.lh(s, 16.9f);
            t.addView(s);
        }
        r.addView(t, W.lp(0, W.WC, 1));
        if (trail != null) {
            r.addView(new View(c), W.lp(T.dp(12), 1));
            r.addView(trail);
        }
        LinearLayout.LayoutParams lp = W.lp(W.MP, W.WC);
        lp.bottomMargin = T.dp(2);
        r.setLayoutParams(lp);
        return r;
    }

    static AvatarView av(Context c, JSONObject u, int dp) {
        AvatarView a = new AvatarView(c);
        a.setLayoutParams(W.lp(T.dp(dp), T.dp(dp)));
        a.textSize(dp * .375f);
        a.set(Store.name(u), Store.avatarColor(u), Store.avatarUrl(u));
        return a;
    }

    static TextView empty(Context c, String s) {
        TextView t = W.text(c, s, 14, 400, T.textMuted);
        t.setGravity(Gravity.CENTER);
        t.setPadding(T.dp(8), T.dp(24), T.dp(8), T.dp(24));
        return t;
    }

    public static void intro(MainActivity a) {
        Sheet s = new Sheet(a, "Stories");
        LinearLayout w = W.col(a);
        w.setGravity(Gravity.CENTER_HORIZONTAL);
        w.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(16));
        FrameLayout ic = new FrameLayout(a);
        ic.setBackground(new Ring.GradCircle());
        ImageView pl = new ImageView(a);
        pl.setImageDrawable(Ic.get("st.stories.plus", 24, T.textSecondary));
        ic.addView(pl, W.flp(T.dp(24), T.dp(24), Gravity.CENTER));
        w.addView(ic, W.lpm(T.dp(56), T.dp(56), 0, 0, 0, 12));
        TextView b = W.text(a, "No stories yet", 16, 700, T.text);
        b.setGravity(Gravity.CENTER);
        T.lh(b, 24);
        w.addView(b);
        TextView t = W.text(a, "Share a photo or video with your contacts. It disappears after 24 hours.", 14, 400, T.textSecondary);
        t.setGravity(Gravity.CENTER);
        Lh.set(t, 21);
        w.addView(t, W.lpm(W.WC, W.WC, 0, 6, 0, 0));
        s.box.addView(w);
        s.done("Create a new story", true, () -> {
            s.close();
            Editor.compose(a, null);
        });
        s.show();
    }

    static List<JSONObject> contacts() {
        List<JSONObject> out = new ArrayList<>();
        Http.Resp r = Http.get("/connections");
        if (!r.ok()) return null;
        JSONArray acc = r.obj().optJSONArray("accepted");
        String me = Session.userId();
        if (acc != null) for (int i = 0; i < acc.length(); i++) {
            JSONObject c = acc.optJSONObject(i);
            if (c == null) continue;
            boolean R = me.equals(c.optString("requesterId"));
            String p = R ? "target" : "requester";
            String id = c.optString(R ? "targetId" : "requesterId", "");
            String h = c.optString(p + "Handle", "");
            if ("null".equals(h)) h = "";
            String n = c.optString(p + "Name", "");
            if (n.isEmpty() || "null".equals(n)) n = h;
            if (id.isEmpty() || h.isEmpty() || "_deleted".equals(h)) continue;
            out.add(App.jo("id", id, "handle", h, "displayName", n, "avatarUrl", c.opt(p + "AvatarUrl"), "avatarColor", c.optString(p + "AvatarColor", "")));
        }
        return out;
    }

    public static void pickPeople(MainActivity a, String title, List<String> selected, Cb cb) {
        App.bg(() -> {
            List<JSONObject> list = contacts();
            App.ui(() -> {
                if (list == null) {
                    Ov.toast("Could not load contacts", true);
                    return;
                }
                Set<String> sel = new LinkedHashSet<>(selected == null ? new ArrayList<>() : selected);
                Sheet s = new Sheet(a, title);
                EditText q = new EditText(a);
                q.setHint("Search contacts");
                q.setHintTextColor(0xff757575);
                T.text(q, 14, 400, T.text);
                q.setSingleLine(true);
                q.setBackground(W.round(T.surface2, 999));
                q.setPadding(T.dp(14), 0, T.dp(14), 0);
                s.box.addView(q, W.lpm(W.MP, T.dp(38), 0, 0, 0, 6));
                LinearLayout lst = W.col(a);
                s.box.addView(lst, W.lp(W.MP, W.WC));
                Runnable draw = () -> {
                    String f = q.getText().toString().trim().toLowerCase(Locale.ROOT);
                    lst.removeAllViews();
                    for (JSONObject c : list) {
                        if (!f.isEmpty() && !c.optString("displayName").toLowerCase(Locale.ROOT).contains(f) && !c.optString("handle").toLowerCase(Locale.ROOT).contains(f)) continue;
                        String id = c.optString("id");
                        Mark m = new Mark(a, true, sel.contains(id));
                        m.setLayoutParams(W.lpm(T.dp(26), T.dp(26), -2, -2, -2, -2));
                        LinearLayout r = row(a, av(a, c, 36), c.optString("displayName"), "@" + c.optString("handle"), m);
                        r.setOnClickListener(v -> {
                            if (sel.contains(id)) sel.remove(id);
                            else sel.add(id);
                            m.set(sel.contains(id));
                        });
                        lst.addView(r);
                    }
                    if (lst.getChildCount() == 0) lst.addView(empty(a, "No contacts"));
                };
                q.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence x, int b, int c2, int d) {
                    }

                    public void onTextChanged(CharSequence x, int b, int c2, int d) {
                    }

                    public void afterTextChanged(Editable e) {
                        draw.run();
                    }
                });
                draw.run();
                s.done("Done", false, () -> {
                    s.close();
                    cb.on(new ArrayList<>(sel));
                });
                s.show();
            });
        });
    }

    public static void viewers(MainActivity a, JSONObject st, Runnable onClose) {
        App.bg(() -> {
            Http.Resp r = Http.get("/stories/" + st.optString("id") + "/views");
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast("pro_required".equals(r.error()) ? "Viewers of expired stories are part of Pro Box" : "Could not load viewers", true);
                    if (onClose != null) onClose.run();
                    return;
                }
                JSONObject j = r.obj();
                JSONArray vs = j.optJSONArray("views");
                Sheet s = new Sheet(a, "Viewers · " + j.optInt("total"));
                s.onClose = onClose;
                if (vs == null || vs.length() == 0) s.box.addView(empty(a, "No viewers yet"));
                else for (int i = 0; i < vs.length(); i++) {
                    JSONObject v = vs.optJSONObject(i);
                    TextView re = null;
                    String rx = v.optString("reaction", "");
                    if (!rx.isEmpty() && !"null".equals(rx)) {
                        re = W.text(a, rx, 20, 400, T.text);
                    }
                    s.box.addView(row(a, av(a, v, 36), v.optString("displayName", v.optString("handle")), Store.ago(v.optLong("viewedAt")), re));
                }
                s.show();
            });
        });
    }

    public static void archive(MainActivity a) {
        App.bg(() -> {
            Http.Resp r = Http.get("/stories/archive");
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast("Could not load archive", true);
                    return;
                }
                JSONArray arr = r.obj().optJSONArray("stories");
                List<JSONObject> list = new ArrayList<>();
                if (arr != null) for (int i = 0; i < arr.length(); i++) list.add(arr.optJSONObject(i));
                Sheet s = new Sheet(a, "Archive & profile");
                if (list.isEmpty()) {
                    s.box.addView(empty(a, "Your stories will appear here"));
                    s.show();
                    return;
                }
                s.box.addView(grid(a, list, true, (k, cell) -> {
                    try {
                        JSONObject me = Store.self != null ? new JSONObject(Store.self.toString()) : App.jo("key", Session.userId(), "displayName", "My story");
                        me.put("self", true);
                        me.put("stories", new JSONArray(list));
                        List<JSONObject> l = new ArrayList<>();
                        l.add(me);
                        Viewer.open(a, l, 0, cell, k);
                    } catch (Exception e) {
                    }
                }));
                s.show();
            });
        });
    }

    public interface CellTap {
        void on(int index, View cell);
    }

    public static View grid(Context c, List<JSONObject> list, boolean badges, CellTap tap) {
        LinearLayout g = W.col(c);
        LinearLayout row = null;
        for (int k = 0; k < list.size(); k++) {
            if (k % 3 == 0) {
                row = W.row(c);
                g.addView(row, W.lpm(W.MP, W.WC, 0, k == 0 ? 0 : 4, 0, 0));
            }
            JSONObject st = list.get(k);
            FrameLayout cell = new FrameLayout(c) {
                @Override
                protected void onMeasure(int w, int h) {
                    int ww = MeasureSpec.getSize(w);
                    super.onMeasure(MeasureSpec.makeMeasureSpec(ww, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.round(ww * 16f / 9f), MeasureSpec.EXACTLY));
                }
            };
            cell.setBackground(W.round(T.surface2, 8));
            cell.setClipToOutline(true);
            cell.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            ImageView im = new ImageView(c);
            im.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cell.addView(im, new FrameLayout.LayoutParams(W.MP, W.MP));
            String src = Store.safeMedia(st.optString("url"));
            if (src != null) {
                if ("video".equals(st.optString("kind"))) Media.frameInto(im, src);
                else Img.into(im, src, T.dp(200));
            }
            if (badges) {
                if (st.optBoolean("pinned")) cell.addView(badge(c, "Pinned"), badgeLp(true));
                cell.addView(badge(c, new java.text.SimpleDateFormat("MMM d", Locale.US).format(new java.util.Date(st.optLong("createdAt")))), badgeLp(false));
            }
            final int idx = k;
            cell.setOnClickListener(v -> tap.on(idx, v));
            LinearLayout.LayoutParams p = W.lp(0, W.WC, 1);
            if (k % 3 > 0) p.leftMargin = T.dp(4);
            row.addView(cell, p);
        }
        if (row != null) for (int k = list.size() % 3; k > 0 && k < 3; k++) {
            LinearLayout.LayoutParams p = W.lp(0, 1, 1);
            p.leftMargin = T.dp(4);
            row.addView(new View(c), p);
        }
        return g;
    }

    static TextView badge(Context c, String s) {
        TextView t = W.text(c, s, 10, 600, 0xffffffff);
        T.lh(t, 15);
        t.setPadding(T.dp(6), t.getPaddingTop() + T.dp(1), T.dp(6), t.getPaddingBottom() + T.dp(1));
        t.setBackground(W.round(0x80000000, 999));
        return t;
    }

    static FrameLayout.LayoutParams badgeLp(boolean top) {
        FrameLayout.LayoutParams p = W.flp(W.WC, W.WC, (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.START);
        p.leftMargin = T.dp(4);
        if (top) p.topMargin = T.dp(4);
        else p.bottomMargin = T.dp(4);
        return p;
    }

    public static void audience(MainActivity a, Editor.Opts o, Runnable changed) {
        Sheet s = new Sheet(a, "Who can see this story");
        String[][] opts = {{"everyone", "Everyone", "Anyone who opens your profile"}, {"contacts", "Contacts", "All your contacts"}, {"close", "Close friends", "Only the people on your list"}, {"selected", "Selected contacts", "Pick who can see it"}};
        for (String[] x : opts) {
            Mark m = new Mark(a, false, o.audience.equals(x[0]));
            m.setLayoutParams(W.lpm(T.dp(26), T.dp(26), -2, -2, -2, -2));
            LinearLayout r = row(a, null, x[1], x[2], m);
            r.setOnClickListener(v -> {
                if ("selected".equals(x[0])) {
                    s.close();
                    pickPeople(a, "Selected contacts", o.allow, ids -> {
                        o.allow = ids;
                        if (!ids.isEmpty()) o.audience = "selected";
                        changed.run();
                    });
                    return;
                }
                o.audience = x[0];
                changed.run();
                s.close();
            });
            s.box.addView(r);
        }
        s.box.addView(link(a, "Edit close friends list", () -> {
            s.close();
            App.bg(() -> {
                Http.Resp r = Http.get("/stories/close-friends");
                App.ui(() -> {
                    if (!r.ok()) {
                        Ov.toast("Could not load", true);
                        return;
                    }
                    JSONArray ids = r.obj().optJSONArray("ids");
                    List<String> cur = new ArrayList<>();
                    if (ids != null) for (int i = 0; i < ids.length(); i++) cur.add(ids.optString(i));
                    pickPeople(a, "Close friends", cur, sel -> App.bg(() -> {
                        Http.Resp w = Http.put("/stories/close-friends", App.jo("ids", new JSONArray(sel)));
                        App.ui(() -> {
                            if (w.ok()) {
                                JSONArray got = w.obj().optJSONArray("ids");
                                Ov.toast("Close friends: " + (got == null ? sel.size() : got.length()));
                            } else Ov.toast("Could not save", true);
                        });
                    }));
                });
            });
        }));
        s.box.addView(link(a, "Hide from…" + (o.deny.isEmpty() ? "" : " (" + o.deny.size() + ")"), () -> {
            s.close();
            pickPeople(a, "Hide story from", o.deny, ids -> {
                o.deny = ids;
                changed.run();
            });
        }));
        s.show();
    }

    static TextView link(Context c, String s, Runnable on) {
        TextView t = W.text(c, s, 14, 500, T.accent);
        t.setPadding(0, T.dp(2), 0, T.dp(2));
        t.setOnClickListener(v -> on.run());
        LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
        p.setMargins(T.dp(6), T.dp(6), T.dp(6), 0);
        t.setLayoutParams(p);
        return t;
    }

    public static void duration(MainActivity a, Editor.Opts o, Runnable changed) {
        Sheet s = new Sheet(a, "Story lasts");
        for (int h : new int[]{6, 12, 24, 48}) {
            boolean lock = true;
            for (int x : Store.hours) if (x == h) lock = false;
            boolean lk = lock;
            Mark m = new Mark(a, false, o.hours == h);
            m.setLayoutParams(W.lpm(T.dp(26), T.dp(26), -2, -2, -2, -2));
            LinearLayout r = row(a, null, h + " hours", lock ? "Pro Box" : null, m);
            if (lock) r.setAlpha(.6f);
            r.setOnClickListener(v -> {
                if (lk) {
                    Ov.toast("48-hour stories are part of Pro Box");
                    return;
                }
                o.hours = h;
                changed.run();
                s.close();
            });
            s.box.addView(r);
        }
        s.show();
    }
}
