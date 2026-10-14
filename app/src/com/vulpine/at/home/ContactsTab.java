package com.vulpine.at.home;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.people.Conns;
import com.vulpine.at.people.Pres;
import com.vulpine.at.people.Profile;
import com.vulpine.at.people.U;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Rt;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class ContactsTab extends Home.Tab {
    static final String[][] FILTERS = {{"online", "Online"}, {"all", "All"}, {"pending", "Pending"}};
    FrameLayout root;
    ListView list;
    LinearLayout top, filterRow, addWrap;
    FrameLayout searchRow;
    Search search;
    TextView addBtn, pendBadge;
    final TextView[] filterBtns = new TextView[3];
    View emptyBlock;
    JSONObject lists;
    String filter = "all", query = "", error;
    boolean addOpen, busy;
    final List<Object[]> items = new ArrayList<>();
    Adapter ad;
    int padTop, padBottom;
    FrameLayout scrim;

    public View build(Context c) {
        root = new FrameLayout(c);
        list = new ClipList(c);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        top = W.col(c);
        buildSearch(c);
        buildHeader(c);
        addWrap = W.col(c);
        addWrap.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        addWrap.setVisibility(View.GONE);
        top.addView(addWrap, W.lp(W.MP, W.WC));
        buildFilters(c);
        View storiesStrip = com.vulpine.at.stories.Stories.strip(c, this);
        if (storiesStrip != null) top.addView(storiesStrip);
        top.addView(new View(c), W.lp(W.MP, T.dp(12)));
        list.addHeaderView(top, null, false);
        ad = new Adapter();
        list.setAdapter(ad);
        list.setOnItemClickListener((p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i < 0 || i >= items.size()) return;
            Object[] it = items.get(i);
            if ((int) it[0] == 0) openProfile((Conns.P) it[1]);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i < 0 || i >= items.size()) return false;
            Object[] it = items.get(i);
            if ((int) it[0] != 0) return false;
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            menu((Conns.P) it[1], v);
            return true;
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int s) {
            }

            public void onScroll(AbsListView v, int first, int vis, int total) {
                if (first + vis >= total - 3 && total > 0) loadMore(false);
                com.vulpine.at.stories.Stories.onListScroll(ContactsTab.this);
            }
        });
        root.addView(list, new FrameLayout.LayoutParams(W.MP, W.MP));
        buildEmpty(c);
        installPull();
        if (cur != null) {
            Rt.I.off(cur.ws);
            Pres.off(cur.pl);
        }
        Rt.I.on(ws);
        Pres.start();
        Pres.on(pl);
        cur = this;
        rebuild();
        App.bg(this::load);
        return root;
    }

    final Rt.Listener ws = m -> {
        if ("connection.update".equals(m.optString("type"))) App.bg(this::load);
    };

    final Pres.L pl = id -> {
        if ("online".equals(filter)) rebuild();
    };

    public void onShow() {
        if (lists != null) App.bg(this::load);
        App.bg(Pres::loadPresence);
    }

    public void onInsets(int topPad, int bottomPad) {
        padTop = topPad;
        padBottom = bottomPad;
        list.setPadding(0, topPad, 0, T.dp(120) + h.a.insetBottom);
        layoutEmpty();
    }

    public void scrollTop() {
        list.smoothScrollToPositionFromTop(0, 0, 300);
    }

    public boolean onBack() {
        if (scrim != null) {
            closeSpot();
            return true;
        }
        if (search.busy() || searchRow.getLayoutParams().height > 0) {
            closeSearch();
            return true;
        }
        return false;
    }

    void buildHeader(Context c) {
        FrameLayout hd = new FrameLayout(c);
        hd.setPadding(T.dp(12), 0, T.dp(12), 0);
        TextView t = W.text(c, "Contacts", 16, 600, T.text);
        hd.addView(t, W.flp(W.WC, W.WC, Gravity.START | Gravity.CENTER_VERTICAL));
        addBtn = U.btn(c, "Add", U.PRIMARY, v -> toggleAdd());
        hd.addView(addBtn, W.flp(W.WC, T.dp(36), Gravity.END | Gravity.CENTER_VERTICAL));
        top.addView(hd, W.lp(W.MP, T.dp(52)));
    }

    void buildFilters(Context c) {
        filterRow = W.row(c);
        filterRow.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
        filterRow.setClipChildren(false);
        filterRow.setClipToPadding(false);
        for (int i = 0; i < 3; i++) {
            final String k = FILTERS[i][0];
            FrameLayout cell = new FrameLayout(c);
            cell.setClipChildren(false);
            TextView b = U.btn(c, FILTERS[i][1], k.equals(filter) ? U.PRIMARY : U.GHOST, 30, 13, v -> setFilter(k));
            b.setPadding(T.dp(8), 0, T.dp(8), 0);
            filterBtns[i] = b;
            cell.addView(b, new FrameLayout.LayoutParams(W.MP, T.dp(30)));
            if ("pending".equals(k)) {
                pendBadge = W.text(c, "", 9, 700, 0xffffffff);
                pendBadge.setGravity(Gravity.CENTER);
                pendBadge.setMinWidth(T.dp(14));
                pendBadge.setPadding(T.dp(3), 0, T.dp(3), 0);
                pendBadge.setBackground(W.round(T.danger, 999));
                pendBadge.setVisibility(View.GONE);
                FrameLayout.LayoutParams bp = W.flp(W.WC, T.dp(14), Gravity.TOP | Gravity.END);
                bp.topMargin = -T.dp(2);
                bp.rightMargin = T.dp(4);
                cell.addView(pendBadge, bp);
            }
            LinearLayout.LayoutParams lp = W.lp(0, T.dp(30), 1);
            if (i > 0) lp.leftMargin = T.dp(4);
            filterRow.addView(cell, lp);
        }
        top.addView(filterRow, W.lp(W.MP, W.WC));
    }

    void setFilter(String k) {
        filter = k;
        for (int i = 0; i < 3; i++) U.variant(filterBtns[i], FILTERS[i][0].equals(k) ? U.PRIMARY : U.GHOST);
        rebuild();
    }

    void buildEmpty(Context c) {
        LinearLayout e = W.col(c);
        e.setGravity(Gravity.CENTER);
        e.setPadding(T.dp(32), T.dp(32), T.dp(32), T.dp(32));
        TextView t = W.text(c, "No contacts yet", 16, 500, T.textSecondary);
        t.setGravity(Gravity.CENTER);
        e.addView(t);
        TextView hint = W.text(c, "Add someone by their @handle to start chatting.", 14, 400, T.textMuted);
        hint.setGravity(Gravity.CENTER);
        hint.setMaxWidth(T.dp(320));
        e.addView(hint, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        ((LinearLayout.LayoutParams) hint.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        emptyBlock = e;
        root.addView(e, W.flp(W.MP, W.WC, Gravity.BOTTOM));
        root.addOnLayoutChangeListener((v, l, t1, r, b, ol, ot, or, ob) -> {
            if (b - t1 != ob - ot) layoutEmpty();
        });
    }

    void layoutEmpty() {
        if (emptyBlock == null || root.getHeight() == 0) return;
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) emptyBlock.getLayoutParams();
        int hh = root.getHeight() / 2;
        if (p.height != hh) {
            p.height = hh;
            emptyBlock.setLayoutParams(p);
        }
    }

    void load() {
        Http.Resp r = Http.get("/connections?limit=30&offset=0");
        App.ui(() -> {
            if (r.ok()) {
                JSONObject o = r.obj();
                if (lists != null && lists.optJSONArray("accepted") != null && query.length() > 0) {
                    JSONArray old = lists.optJSONArray("accepted"), fresh = o.optJSONArray("accepted");
                    if (old.length() > fresh.length()) {
                        java.util.Set<String> ids = new java.util.HashSet<>();
                        for (int i = 0; i < fresh.length(); i++) ids.add(fresh.optJSONObject(i).optString("id"));
                        for (int i = 0; i < old.length(); i++) if (!ids.contains(old.optJSONObject(i).optString("id"))) fresh.put(old.optJSONObject(i));
                    }
                }
                lists = o;
                error = null;
            } else error = r.status == 0 ? "Could not load contacts." : U.err(r, "Could not load contacts.");
            rebuild();
            if (h != null) {
                int n = lists == null || lists.optJSONArray("incoming") == null ? 0 : lists.optJSONArray("incoming").length();
                h.contactsBadge = n;
                h.updateBadges();
            }
            if (lists != null) App.bg(() -> {
                List<String> ids = new ArrayList<>();
                for (Conns.P p : Conns.list(lists, "accepted")) ids.add(p.id);
                Pres.loadActivity(ids);
            });
            if (!query.isEmpty()) loadMore(true);
        });
    }

    void loadMore(boolean all) {
        if (busy || lists == null) return;
        JSONArray acc = lists.optJSONArray("accepted");
        int cur = acc == null ? 0 : acc.length();
        int tot = lists.has("acceptedTotal") ? lists.optInt("acceptedTotal") : cur;
        if (cur == 0 || cur >= tot) return;
        busy = true;
        App.bg(() -> {
            Http.Resp r = Http.get("/connections?limit=30&offset=" + cur);
            App.ui(() -> {
                busy = false;
                if (!r.ok() || lists == null) return;
                JSONObject j = r.obj();
                JSONArray more = j.optJSONArray("accepted");
                if (more == null || more.length() == 0) return;
                JSONArray a = lists.optJSONArray("accepted");
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (int i = 0; i < a.length(); i++) ids.add(a.optJSONObject(i).optString("id"));
                for (int i = 0; i < more.length(); i++) if (!ids.contains(more.optJSONObject(i).optString("id"))) a.put(more.optJSONObject(i));
                try {
                    if (j.has("acceptedTotal")) lists.put("acceptedTotal", j.optInt("acceptedTotal"));
                } catch (Exception e) {
                }
                rebuild();
                if (all && !query.isEmpty()) App.H.postDelayed(() -> loadMore(true), 80);
            });
        });
    }

    void rebuild() {
        items.clear();
        String q = query.trim();
        if (error != null) items.add(new Object[]{4, error, T.danger});
        int inc = lists == null || lists.optJSONArray("incoming") == null ? 0 : lists.optJSONArray("incoming").length();
        pendBadge.setText(String.valueOf(inc));
        pendBadge.setVisibility(inc > 0 ? View.VISIBLE : View.GONE);
        if (lists == null) {
            if (error == null) items.add(new Object[]{4, "Loading…", T.textMuted});
        } else if ("pending".equals(filter)) {
            List<Conns.P> in = new ArrayList<>(), out = new ArrayList<>();
            for (Conns.P p : Conns.list(lists, "incoming")) if (Conns.match(p, q)) in.add(p);
            for (Conns.P p : Conns.list(lists, "outgoing")) if (Conns.match(p, q)) out.add(p);
            for (Conns.P p : in) items.add(new Object[]{1, p});
            if (!out.isEmpty()) {
                items.add(new Object[]{2, "SENT REQUESTS"});
                for (Conns.P p : out) items.add(new Object[]{3, p});
            }
            if (in.isEmpty() && out.isEmpty()) items.add(new Object[]{4, q.isEmpty() ? "No pending requests." : "No contacts match your search.", T.textMuted});
        } else {
            List<Conns.P> acc = new ArrayList<>();
            for (Conns.P p : Conns.list(lists, "accepted")) {
                if (!Conns.match(p, q)) continue;
                if ("online".equals(filter) && !Pres.online(p.id)) continue;
                acc.add(p);
            }
            for (Conns.P p : acc) items.add(new Object[]{0, p});
            if (acc.isEmpty()) items.add(new Object[]{4, q.isEmpty() ? "No contacts yet." : "No contacts match your search.", T.textMuted});
        }
        JSONArray a = lists == null ? null : lists.optJSONArray("accepted");
        emptyBlock.setVisibility(a == null || a.length() == 0 ? View.VISIBLE : View.GONE);
        ad.notifyDataSetChanged();
    }

    void toggleAdd() {
        addOpen = !addOpen;
        addBtn.setText(addOpen ? "Close" : "Add");
        addWrap.removeAllViews();
        addWrap.setVisibility(addOpen ? View.VISIBLE : View.GONE);
        if (addOpen) buildAdd(addWrap.getContext());
        else W.hideKb(addWrap);
    }

    void buildAdd(Context c) {
        LinearLayout row = W.row(c);
        row.setGravity(Gravity.TOP);
        U.Field f = new U.Field(c, "Find by @handle", "@", 0, false);
        f.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        f.input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        row.addView(f, W.lp(T.dp(230), W.WC));
        row.addView(W.flex(c));
        TextView go = U.btn(c, "Search", U.PRIMARY, 40, 14, null);
        go.setPadding(T.dp(16), 0, T.dp(16), 0);
        LinearLayout.LayoutParams gp = W.lp(W.WC, T.dp(40));
        gp.leftMargin = T.dp(8);
        row.addView(go, gp);
        addWrap.addView(row, W.lp(W.MP, W.WC));
        LinearLayout res = W.col(c);
        addWrap.addView(res, W.lp(W.MP, W.WC));
        final boolean[] busyA = {false};
        Runnable sync = () -> U.enable(go, f.value().length() > 0 && !busyA[0]);
        f.input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(Editable e) {
                String raw = e.toString(), w = raw.toLowerCase().replaceFirst("^@", "").replaceAll("[^a-z0-9_]", "");
                if (!w.equals(raw)) {
                    e.replace(0, e.length(), w);
                    return;
                }
                res.removeAllViews();
                sync.run();
            }
        });
        Runnable doSearch = () -> {
            String w = f.value();
            if (w.isEmpty() || busyA[0]) return;
            busyA[0] = true;
            sync.run();
            res.removeAllViews();
            App.bg(() -> {
                Http.Resp r = Http.get("/users/" + w);
                App.ui(() -> {
                    busyA[0] = false;
                    sync.run();
                    res.removeAllViews();
                    if (r.ok()) res.addView(resultRow(c, r.obj(), res, busyA, sync), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
                    else if (r.status == 404) res.addView(U.muted(c, "No user with that handle.", 13), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
                    else res.addView(W.text(c, "Search failed.", 13, 400, T.accent), W.lpm(W.MP, W.WC, 0, 12, 0, 0));
                });
            });
        };
        go.setOnClickListener(v -> doSearch.run());
        f.input.setOnEditorActionListener((v, a, e) -> {
            doSearch.run();
            return true;
        });
        sync.run();
    }

    View resultRow(Context c, JSONObject u, LinearLayout res, boolean[] busyA, Runnable sync) {
        LinearLayout r = W.row(c);
        r.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        String handle = u.optString("handle"), dn = u.optString("displayName");
        r.addView(U.avatar(c, dn.isEmpty() ? handle : dn, u.isNull("avatarColor") ? null : u.optString("avatarColor", null), u.isNull("avatarUrl") ? null : u.optString("avatarUrl", null), 48, 18));
        LinearLayout m = W.col(c);
        TextView t = W.text(c, dn, 14, 500, T.text);
        W.ellipsize(t);
        T.lh(t, 21);
        m.addView(t);
        TextView s = W.text(c, "@" + handle, 13, 400, T.textMuted);
        W.ellipsize(s);
        T.lh(s, 20);
        m.addView(s);
        r.addView(m, W.lpm(0, W.WC, 12, 0, 12, 0));
        ((LinearLayout.LayoutParams) m.getLayoutParams()).weight = 1;
        TextView send = U.btn(c, "Send", U.PRIMARY, 30, 13, null);
        send.setOnClickListener(v -> {
            if (busyA[0]) return;
            busyA[0] = true;
            U.enable(send, false);
            App.bg(() -> {
                Http.Resp x = Http.post("/connections/request", App.jo("handle", handle));
                App.ui(() -> {
                    busyA[0] = false;
                    U.enable(send, true);
                    if (x.ok()) {
                        Ov.toast("Request sent");
                        if (addOpen) toggleAdd();
                        App.bg(this::load);
                    } else {
                        String msg = U.err(x, "Could not send request.");
                        TextView et = W.text(c, msg, 13, 400, T.accent);
                        res.addView(et, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
                        Ov.toast(msg, true);
                    }
                });
            });
        });
        r.addView(send, W.lp(W.WC, T.dp(30)));
        return r;
    }

    void openProfile(Conns.P p) {
        if (scrim != null) closeSpot();
        Profile.open(h.a, p.handle);
    }

    void menu(Conns.P p, View anchor) {
        Ov.Menu m = new Ov.Menu(anchor.getContext());
        m.item("Open chat", Svg.stroke(15, T.text, 1.8f, "M4 5h16v11H8l-4 4z"), false, () -> openChat(p));
        m.item("View profile", Svg.stroke(15, T.text, 1.8f, "M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M5 21c0-4 3-6 7-6s7 2 7 6"), false, () -> openProfile(p));
        m.item("Remove contact", Svg.stroke(15, T.danger, 1.8f, "M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M5 21c0-4 3-6 7-6", "M16 15l5 5", "M21 15l-5 5"), true, () -> Ov.confirm("Remove contact?", "This will remove the contact and delete your conversation. This cannot be undone.", "Remove", true, () -> App.bg(() -> {
            Http.Resp r = Http.call("DELETE", "/connections/" + p.cid, null);
            App.ui(() -> {
                if (r.ok()) {
                    Ov.toast("Contact removed");
                    App.bg(this::load);
                    App.bg(() -> com.vulpine.core.Chats.I.loadList(0));
                } else U.toastErr(r, "Could not remove");
            });
        })));
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        m.showAt(l[0] + anchor.getWidth() / 2, l[1] + anchor.getHeight() / 2, false);
    }

    public static void openChat(Conns.P p) {
        if (ContactsTab.cur != null) {
            ContactsTab t = ContactsTab.cur;
            if (!t.query.isEmpty()) t.search.input.setText("");
        }
        App.bg(() -> {
            Http.Resp r = Http.get("/connections/" + p.cid + "/conversation");
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast("Could not open chat.", true);
                    return;
                }
                String cid = r.obj().optString("id");
                App.bg(() -> {
                    if (com.vulpine.core.Chats.I.conv(cid) == null) {
                        com.vulpine.core.Chats.I.refresh(cid);
                        if (!com.vulpine.core.Chats.I.order.contains(cid)) com.vulpine.core.Chats.I.order.add(0, cid);
                    }
                    App.ui(() -> Home.openChat(cid));
                });
            });
        });
    }

    static ContactsTab cur;

    void act(Conns.P p, String action) {
        App.bg(() -> {
            Http.Resp r = Http.post("/connections/" + p.cid + "/action", App.jo("action", action));
            if (r.ok()) {
                load();
                App.ui(() -> Ov.toast("accept".equals(action) ? "Contact accepted" : "block".equals(action) ? "Contact blocked" : "unblock".equals(action) ? "Contact unblocked" : "Request declined"));
                if ("accept".equals(action)) com.vulpine.core.Chats.I.loadList(0);
            } else App.ui(() -> {
                String msg = U.err(r, "Action failed.");
                error = msg;
                rebuild();
                Ov.toast(msg, true);
            });
        });
    }

    void cancel(Conns.P p) {
        App.bg(() -> {
            Http.Resp r = Http.call("DELETE", "/connections/" + p.cid, null);
            App.ui(() -> {
                if (r.ok()) {
                    Ov.toast("Request cancelled");
                    App.bg(this::load);
                } else U.toastErr(r, "Could not cancel request");
            });
        });
    }

    View contactRow(Context c, View cv, Conns.P p, int kind) {
        Row r = cv instanceof Row && ((Row) cv).kind == kind ? (Row) cv : new Row(c, kind);
        r.bind(p);
        return r;
    }

    class Row extends LinearLayout {
        final int kind;
        final AvatarView av;
        final TextView title, sub;
        final LinearLayout titleRow, badges;
        Conns.P p;

        Row(Context c, int kind) {
            super(c);
            this.kind = kind;
            setOrientation(VERTICAL);
            setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
            if (kind == 0) U.pressBg(this, 0);
            LinearLayout top = W.row(c);
            av = U.avatar(c, "", null, null, 48, 18);
            top.addView(av);
            LinearLayout m = W.col(c);
            titleRow = W.row(c);
            title = W.text(c, "", 14, 500, T.text);
            W.ellipsize(title);
            T.lh(title, 21);
            titleRow.addView(title, W.lp(W.WC, W.WC));
            badges = W.row(c);
            titleRow.addView(badges);
            m.addView(titleRow);
            sub = W.text(c, "", 13, 400, T.textMuted);
            W.ellipsize(sub);
            T.lh(sub, 20);
            m.addView(sub);
            top.addView(m, W.lpm(0, W.WC, 12, 0, 0, 0));
            ((LinearLayout.LayoutParams) m.getLayoutParams()).weight = 1;
            addView(top, W.lp(W.MP, T.dp(48)));
            if (kind == 1 || kind == 3) {
                LinearLayout b = W.row(c);
                if (kind == 1) {
                    b.addView(small(c, "Accept", U.PRIMARY, T.accentFg, v -> act(p, "accept")));
                    b.addView(small(c, "Decline", U.GHOST, T.textSecondary, v -> act(p, "decline")), lm());
                    b.addView(small(c, "Block", U.GHOST, T.danger, v -> act(p, "block")), lm());
                } else b.addView(small(c, "Cancel", U.GHOST, T.danger, v -> cancel(p)));
                addView(b, W.lpm(W.WC, T.dp(28), 60, 12, 0, 0));
            }
        }

        LinearLayout.LayoutParams lm() {
            LinearLayout.LayoutParams l = W.lp(W.WC, T.dp(28));
            l.leftMargin = T.dp(8);
            return l;
        }

        TextView small(Context c, String l, int v, int color, View.OnClickListener on) {
            TextView t = U.btn(c, l, v, 28, 13, on);
            t.setTextColor(color);
            return t;
        }

        void bind(Conns.P p) {
            this.p = p;
            av.set(p.name, p.avatarColor, p.avatarUrl);
            title.setText(p.name);
            sub.setText("@" + p.handle);
            badges.removeAllViews();
            U.badges(badges, kind == 3 ? p.targetId : null, p.handle, p.pro, 14, 4, true);
            for (int i = 0; i < badges.getChildCount(); i++) ((LinearLayout.LayoutParams) badges.getChildAt(i).getLayoutParams()).leftMargin = T.dp(12);
            title.setMaxWidth(Math.max(T.dp(40), getResources().getDisplayMetrics().widthPixels - T.dp(96) - badges.getChildCount() * T.dp(18)));
        }
    }

    class Adapter extends BaseAdapter {
        public int getCount() {
            return items.size();
        }

        public Object getItem(int i) {
            return items.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public int getViewTypeCount() {
            return 5;
        }

        public int getItemViewType(int i) {
            return (int) items.get(i)[0];
        }

        public boolean isEnabled(int i) {
            return (int) items.get(i)[0] == 0;
        }

        public View getView(int i, View cv, ViewGroup parent) {
            Object[] it = items.get(i);
            int k = (int) it[0];
            Context c = parent.getContext();
            if (k == 0 || k == 1 || k == 3) return contactRow(c, cv, (Conns.P) it[1], k);
            if (k == 2) {
                TextView t = cv instanceof TextView ? (TextView) cv : W.text(c, "", 13, 400, T.textMuted);
                t.setText((String) it[1]);
                t.setLetterSpacing(.05f);
                t.setPadding(0, T.dp(8), 0, T.dp(8));
                return t;
            }
            TextView t = cv instanceof TextView ? (TextView) cv : W.text(c, "", 14, 400, T.textMuted);
            t.setText((String) it[1]);
            t.setTextColor((int) it[2]);
            T.text(t, error != null && it[1].equals(error) ? 13 : 14, 400, (int) it[2]);
            t.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
            return t;
        }
    }

    void buildSearch(Context c) {
        searchRow = new FrameLayout(c);
        search = new Search(c);
        searchRow.addView(search, W.flp(W.MP, T.dp(52), Gravity.BOTTOM));
        searchRow.setPadding(T.dp(12), 0, T.dp(12), 0);
        searchRow.setClipChildren(true);
        top.addView(searchRow, W.lp(W.MP, 0));
    }

    class Search extends FrameLayout {
        final EditText input;
        final ImageView clear;
        final LinearLayout lab;
        final TextView label;

        Search(Context c) {
            super(c);
            FrameLayout box = new FrameLayout(c);
            box.setBackground(W.round(T.dark ? 0x3d767680 : 0x1f767680, 10));
            input = new EditText(c);
            input.setBackground(null);
            T.text(input, 14, 400, T.text);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            input.setPadding(T.dp(34), 0, T.dp(34), 0);
            box.addView(input, new FrameLayout.LayoutParams(W.MP, W.MP));
            lab = W.row(c);
            ImageView mag = new ImageView(c);
            mag.setImageDrawable(Svg.stroke(16, T.textMuted, 2.4f, "M15.5 15.5L21 21", "M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13z"));
            lab.addView(mag, W.lp(T.dp(16), T.dp(16)));
            label = W.text(c, "Search contacts…", 14, 400, T.textMuted);
            lab.addView(label, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
            box.addView(lab, W.flp(W.WC, W.WC, Gravity.CENTER));
            clear = new ImageView(c);
            clear.setImageDrawable(Svg.stroke(10, T.surface, 1.8f, "M6 6l12 12", "M18 6L6 18"));
            clear.setScaleType(ImageView.ScaleType.CENTER);
            clear.setBackground(W.oval(T.textMuted));
            clear.setContentDescription("Clear search");
            clear.setScaleX(.6f);
            clear.setScaleY(.6f);
            clear.setAlpha(0f);
            clear.setOnClickListener(v -> {
                input.setText("");
                W.showKb(input);
            });
            FrameLayout.LayoutParams cp = W.flp(T.dp(18), T.dp(18), Gravity.END | Gravity.CENTER_VERTICAL);
            cp.rightMargin = T.dp(9);
            box.addView(clear, cp);
            addView(box, W.flp(W.MP, T.dp(36), Gravity.CENTER_VERTICAL));
            input.setOnFocusChangeListener((v, f) -> {
                update();
                if (f) openSpot();
            });
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int d) {
                }

                public void onTextChanged(CharSequence s, int a, int b, int d) {
                }

                public void afterTextChanged(Editable e) {
                    query = e.toString();
                    update();
                    rebuild();
                    if (!query.isEmpty()) loadMore(true);
                }
            });
        }

        void update() {
            boolean f = input.hasFocus() || input.length() > 0;
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) lab.getLayoutParams();
            int g = f ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER;
            if (p.gravity != g) {
                p.gravity = g;
                p.leftMargin = f ? T.dp(11) : 0;
                lab.setLayoutParams(p);
            }
            label.setAlpha(input.length() > 0 ? 0 : 1);
            boolean show = input.length() > 0;
            clear.animate().alpha(show ? 1 : 0).scaleX(show ? 1 : .6f).scaleY(show ? 1 : .6f).setDuration(180).start();
        }

        void setVsp(float v) {
            float op = Math.max(0, Math.min(1, (v - .35f) / .55f));
            setAlpha(op);
            setScaleX(.9f + .1f * Math.min(1, v));
            setScaleY(.9f + .1f * Math.min(1, v));
        }

        boolean busy() {
            return input.hasFocus() || input.length() > 0;
        }
    }

    void openSpot() {
        if (scrim != null) return;
        FrameLayout host = h.root;
        scrim = new FrameLayout(host.getContext());
        scrim.setBackgroundColor(T.dark ? 0x57000000 : 0x24000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> closeSpot());
        host.addView(scrim, new FrameLayout.LayoutParams(W.MP, W.MP));
        scrim.setAlpha(0);
        scrim.animate().alpha(1).setDuration(240).start();
        Ov.push(scrim, this::closeSpot);
        host.bringChildToFront(h.content);
    }

    void closeSpot() {
        if (scrim == null) return;
        FrameLayout s = scrim;
        scrim = null;
        Ov.remove(s);
        s.animate().alpha(0).setDuration(200).withEndAction(() -> {
            if (s.getParent() != null) ((FrameLayout) s.getParent()).removeView(s);
        }).start();
        search.input.clearFocus();
        W.hideKb(search.input);
        h.root.bringChildToFront(h.header);
        h.root.bringChildToFront(h.fade);
        h.root.bringChildToFront(h.nav);
        h.root.bringChildToFront(h.fab);
        if (query.isEmpty()) closeSearch();
    }

    void openSearch(boolean anim) {
        int h0 = searchRow.getLayoutParams().height, h1 = T.dp(52);
        Ov.anim(searchRow, h0, h1, 360, x -> {
            float v = (float) x.getAnimatedValue();
            ViewGroup.LayoutParams q = searchRow.getLayoutParams();
            q.height = Math.round(v);
            searchRow.setLayoutParams(q);
            search.setVsp(v / h1);
        });
    }

    void closeSearch() {
        search.input.setText("");
        search.input.clearFocus();
        W.hideKb(search.input);
        int h0 = searchRow.getLayoutParams().height;
        if (h0 == 0) return;
        Ov.anim(searchRow, h0, 0, 360, x -> {
            float v = (float) x.getAnimatedValue();
            ViewGroup.LayoutParams q = searchRow.getLayoutParams();
            q.height = Math.round(v);
            searchRow.setLayoutParams(q);
            search.setVsp(v / T.dp(52));
        });
    }

    void installPull() {
        final float[] st = new float[4];
        final boolean[] drag = {false};
        list.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    st[0] = e.getRawX();
                    st[1] = e.getRawY();
                    st[2] = searchRow.getLayoutParams().height;
                    st[3] = 0;
                    drag[0] = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - st[0], dy = e.getRawY() - st[1];
                    boolean atTop = list.getFirstVisiblePosition() == 0 && (list.getChildCount() == 0 || list.getChildAt(0).getTop() >= list.getPaddingTop() - 1);
                    int H = T.dp(52);
                    if (!drag[0]) {
                        if (Math.abs(dy) < T.dp(6) || Math.abs(dx) > Math.abs(dy)) return false;
                        if (!atTop) return false;
                        boolean closed = st[2] == 0;
                        if (closed && dy > 0 && com.vulpine.at.stories.Stories.pullFirst(ContactsTab.this)) {
                            drag[0] = true;
                            st[3] = 2;
                        } else if (closed && dy > 0) drag[0] = true;
                        else if (!closed && dy < 0 && !search.busy()) drag[0] = true;
                        else return false;
                    }
                    if (st[3] == 2) {
                        com.vulpine.at.stories.Stories.dragStrip(ContactsTab.this, dy);
                        return true;
                    }
                    float hh;
                    if (st[2] == 0) hh = dy <= H ? dy : H + (dy - H) * .3f;
                    else hh = Math.min(H, H + dy);
                    hh = Math.max(0, hh);
                    ViewGroup.LayoutParams q = searchRow.getLayoutParams();
                    q.height = Math.round(hh);
                    searchRow.setLayoutParams(q);
                    search.setVsp(Math.min(1, hh / H));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (!drag[0]) return false;
                    drag[0] = false;
                    if (st[3] == 2) {
                        com.vulpine.at.stories.Stories.releaseStrip(ContactsTab.this);
                        return true;
                    }
                    int H = T.dp(52);
                    float hh = searchRow.getLayoutParams().height;
                    if (st[2] == 0) {
                        if (hh > .45f * H && e.getActionMasked() == MotionEvent.ACTION_UP) openSearch(true);
                        else closeSearch();
                    } else {
                        if (hh < .55f * H) closeSearch();
                        else openSearch(true);
                    }
                    return true;
                }
            }
            return false;
        });
    }

    public void onHide() {
        if (scrim != null) closeSpot();
    }
}
