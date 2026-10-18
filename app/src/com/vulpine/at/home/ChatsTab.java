package com.vulpine.at.home;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public class ChatsTab extends Home.Tab {
    FrameLayout root;
    ListView list;
    LinearLayout top;
    FrameLayout searchRow;
    SearchField search;
    LinearLayout folderBar;
    FrameLayout folderWrap;
    View folderPill;
    TextView empty;
    final List<String> rows = new ArrayList<>();
    final Map<String, String[]> prev = new ConcurrentHashMap<>();
    final Set<String> prevBusy = ConcurrentHashMap.newKeySet();
    String folderId;
    String query = "";
    boolean hiddenView;
    final Set<String> selected = new HashSet<>();
    boolean selectMode;
    LinearLayout selBar;
    TextView selCount;
    Adapter ad;
    int padTop, padBottom;
    boolean loadingMore;
    View hiddenHeader;
    TextView hiddenTitle;
    public static Set<String> contacts;
    public static Set<String> callConvs = new HashSet<>();
    public static Map<String, String> listening = new ConcurrentHashMap<>();
    View storiesStrip;

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
        buildFolders(c);
        storiesStrip = StoriesPill.strip(c, this);
        bodyPad = new View(c);
        top.addView(bodyPad, W.lp(W.MP, T.dp(8)));
        if (storiesStrip != null) {
            top.addView(storiesStrip);
            storiesStrip.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> syncPad());
        }
        top.addView(new View(c), W.lp(W.MP, T.dp(4)));
        list.addHeaderView(top, null, false);
        ad = new Adapter();
        list.setAdapter(ad);
        list.setOnItemClickListener((p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i < 0 || i >= rows.size()) return;
            String cid = rows.get(i);
            if (Spotlight.active()) Spotlight.close();
            if (cid.startsWith("secret:")) {
                if (!selectMode) com.vulpine.at.secret.Secret.open(cid.substring(7));
                return;
            }
            if (selectMode) {
                toggleSel(cid);
                return;
            }
            open(cid);
        });
        android.widget.AdapterView.OnItemLongClickListener realLong = (p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i < 0 || i >= rows.size()) return false;
            if (rows.get(i).startsWith("secret:")) {
                if (selectMode) return true;
                com.vulpine.at.secret.Sec.Chat sc = com.vulpine.at.secret.Sec.chats.get(rows.get(i).substring(7));
                if (sc == null) return true;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                Ov.Menu sm = new Ov.Menu(c);
                sm.item("Close secret chat", new Svg.Icon(0, 0, 24, 24, T.dp(17), T.danger, 2.4f).add("M4.5 10.5h15v10.5h-15z", false).add("M7.5 10.5V7.5a4.5 4.5 0 0 1 9 0v3", false), true, () -> com.vulpine.at.secret.Secret.confirmClose(sc));
                int[] loc = new int[2];
                v.getLocationInWindow(loc);
                sm.showAt(loc[0] + v.getWidth() / 2, loc[1] + v.getHeight() / 2, false);
                return true;
            }
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            if (hiddenView) {
                String cid = rows.get(i);
                Ov.Menu m = new Ov.Menu(c);
                m.item("Unarchive", Svg.stroke(18, T.text, 2, "M3 4h18v4H3z", "M5 8v12h14V8", "M10 12h4"), false, () -> {
                    Sync.toggle("hiddenChatIds", cid, false);
                    refresh();
                });
                int[] l = new int[2];
                v.getLocationInWindow(l);
                m.showAt(l[0] + v.getWidth() / 2, l[1] + v.getHeight() / 2, false);
                return true;
            }
            enterSelect(rows.get(i));
            return true;
        };
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (!steadyPress(false)) return true;
            long left = 480 - (System.currentTimeMillis() - lpAt);
            if (left <= 0) return realLong.onItemLongClick(p, v, pos, id);
            App.H.postDelayed(() -> {
                if (!lpUp && steadyPress(false)) realLong.onItemLongClick(p, v, pos, id);
            }, left);
            return true;
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int s) {
            }

            public void onScroll(AbsListView v, int first, int vis, int total) {
                if (first + vis >= total - 4 && !loadingMore && Chats.I.total > Chats.I.order.size() && query.isEmpty()) {
                    loadingMore = true;
                    App.bg(() -> {
                        Chats.I.loadList(Chats.I.order.size());
                        App.ui(() -> loadingMore = false);
                    });
                }
                StoriesPill.onScroll(ChatsTab.this);
                fabOnScroll(v, first);
            }
        });
        root.addView(list, new FrameLayout.LayoutParams(W.MP, W.MP));
        empty = W.text(c, "", 14, 400, T.textMuted);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(T.dp(20), T.dp(12), T.dp(20), T.dp(12));
        T.lh(empty, 21);
        empty.setPadding(T.dp(20), empty.getPaddingTop() + T.dp(12), T.dp(20), empty.getPaddingBottom() + T.dp(12));
        FrameLayout ew = new FrameLayout(c);
        ew.addView(empty, new FrameLayout.LayoutParams(W.MP, W.WC));
        list.addFooterView(ew, null, false);
        installPull();
        Chats.I.on(cl);
        com.vulpine.at.secret.Sec.on(secL);
        com.vulpine.at.secret.Sec.wire();
        Chats.I.restoreCache();
        refresh();
        App.bg(() -> {
            Chats.I.loadList(0);
            loadContacts();
        });
        return root;
    }

    final Chats.Listener cl = (ev, cid, d) -> {
        if ("list".equals(ev) || "conv".equals(ev) || "new".equals(ev) || "update".equals(ev)) {
            if (("new".equals(ev) || "update".equals(ev)) && cid != null) prev.remove(cid);
            if ("new".equals(ev) && cid != null) App.ui(() -> onIncoming(cid, (Chats.Msg) d));
            App.ui(this::refresh);
        }
    };

    final com.vulpine.at.secret.Sec.L secL = (ev, id) -> {
        if ("list".equals(ev) || "typing".equals(ev) || "presence".equals(ev) || "closed".equals(ev)) refresh();
    };

    void onIncoming(String cid, Chats.Msg m) {
        if (m == null || m.mine()) return;
        JSONArray hid = Session.settings().optJSONArray("hiddenChatIds");
        if (Sync.contains(hid, cid)) Sync.toggle("hiddenChatIds", cid, false);
        if (cid.equals(Chats.I.activeCid)) return;
        if (Sync.contains(Session.settings().optJSONArray("mutedChatIds"), cid)) return;
        JSONObject u = Sync.unread();
        try {
            u.put(cid, u.optInt(cid) + 1);
        } catch (Exception e) {
        }
        Session.save();
    }

    void loadContacts() {
        Http.Resp r = Http.get("/connections");
        if (!r.ok()) return;
        JSONArray acc = r.obj().optJSONArray("accepted");
        Set<String> s = new HashSet<>();
        if (acc != null) for (int i = 0; i < acc.length(); i++) {
            JSONObject o = acc.optJSONObject(i);
            if (o == null) continue;
            String me = Session.userId();
            String uid = me.equals(o.optString("requesterId")) ? o.optString("targetId") : o.optString("requesterId");
            if (!uid.isEmpty()) s.add(uid);
        }
        contacts = s;
        App.ui(this::refresh);
    }

    View bodyPad;

    int fabY = Integer.MIN_VALUE;
    boolean fabHidden;

    void fabOnScroll(AbsListView v, int first) {
        if (h == null || h.fab == null || v.getChildCount() == 0) return;
        int y = first * 100000 - v.getChildAt(0).getTop();
        if (fabY == Integer.MIN_VALUE) {
            fabY = y;
            return;
        }
        int d = y - fabY;
        boolean atTop = first == 0 && v.getChildAt(0).getTop() >= v.getPaddingTop() - T.dp(8);
        if (atTop) {
            fabY = y;
            showFab(true);
        } else if (d < -T.dp(20)) {
            fabY = y;
            showFab(true);
        } else if (d > T.dp(64)) {
            fabY = y;
            showFab(false);
        } else if ((d > 0 && fabHidden) || (d < 0 && !fabHidden)) fabY = y;
    }

    void showFab(boolean on) {
        if (fabHidden == !on || h == null || h.fab == null) return;
        fabHidden = !on;
        h.fab.animate().cancel();
        h.fab.animate().translationY(on ? 0 : T.dp(120)).alpha(on ? 1 : 0).setDuration(220).setInterpolator(T.EASE).start();
    }

    void syncPad() {
        int sh = storiesStrip == null ? 0 : storiesStrip.getHeight();
        int want = Spotlight.active() ? 0 : Math.max(0, T.dp(8) - sh);
        android.view.ViewGroup.LayoutParams p = bodyPad.getLayoutParams();
        if (p.height != want) {
            p.height = want;
            bodyPad.setLayoutParams(p);
        }
    }

    public void onInsets(int topPad, int bottomPad) {
        padTop = topPad;
        padBottom = bottomPad;
        if (Spotlight.active()) {
            Spotlight.sizeResults();
            return;
        }
        int t = selectMode ? Math.max(h.a.insetTop, T.dp(24)) + T.dp(62) : topPad;
        list.setPadding(T.dp(0), t, T.dp(0), T.dp(120) + h.a.insetBottom);
    }

    public void onShow() {
        showFab(true);
        refresh();
    }

    public void scrollTop() {
        list.smoothScrollToPositionFromTop(0, 0, 300);
    }

    public boolean selecting() {
        return selectMode;
    }

    public boolean onBack() {
        if (selectMode) {
            exitSelect();
            return true;
        }
        if (search.busy()) {
            closeSearch();
            return true;
        }
        if (hiddenView) {
            setHidden(false);
            return true;
        }
        if (folderId != null) {
            selectFolder(null);
            return true;
        }
        return false;
    }

    void open(String cid) {
        if (!query.isEmpty()) {
            search.input.setText("");
            query = "";
        }
        if (hiddenView) {
            Sync.toggle("hiddenChatIds", cid, false);
            setHidden(false);
        }
        JSONObject u = Sync.unread();
        if (u.optInt(cid) > 0) {
            u.remove(cid);
            Sync.save();
        }
        App.bg(() -> Http.post("/conversations/" + cid + "/read", null));
        com.vulpine.at.svc.Notifier.cancelConv(App.ctx, cid);
        Home.openChat(cid);
    }

    static JSONArray pins(JSONObject folder) {
        if (folder != null) {
            JSONArray p = folder.optJSONArray("pins");
            return p == null ? new JSONArray() : p;
        }
        JSONArray p = Session.settings().optJSONArray("pinnedChatIds");
        return p == null ? new JSONArray() : p;
    }

    public static JSONObject folder(String id) {
        if (id == null) return null;
        JSONArray f = Session.settings().optJSONArray("chatFolders");
        if (f != null) for (int i = 0; i < f.length(); i++) if (id.equals(f.optJSONObject(i).optString("id"))) return f.optJSONObject(i);
        return null;
    }

    public static List<String> types(JSONObject f) {
        JSONArray fs = f.optJSONArray("filters");
        if (fs != null) {
            List<String> l = new ArrayList<>();
            for (int i = 0; i < fs.length(); i++) l.add(fs.optString(i));
            return l;
        }
        String legacy = f.optString("filter", "all");
        switch (legacy) {
            case "custom": return new ArrayList<>();
            case "contacts": case "non-contacts": case "groups": {
                List<String> l = new ArrayList<>();
                l.add(legacy);
                return l;
            }
        }
        return null;
    }

    public static boolean typeMatch(JSONObject f, Chats.Conv c) {
        List<String> t = types(f);
        if (t == null) return true;
        if (c.group()) return t.contains("groups");
        String h = c.o.optString("peerHandle");
        if (Badges.isBot(h) || c.o.optBoolean("peerIsBot") || "vulpine-alerts-bot".equals(c.o.optString("peerId"))) return t.contains("bots");
        boolean isContact = contacts == null || contacts.contains(c.o.optString("peerId"));
        return t.contains(isContact ? "contacts" : "non-contacts");
    }

    public static boolean inFolder(JSONObject f, Chats.Conv c) {
        if (Sync.contains(f.optJSONArray("pinnedChatIds"), c.id)) return true;
        if (Sync.contains(f.optJSONArray("excludedChatIds"), c.id)) return false;
        return typeMatch(f, c);
    }

    public void refresh() {
        if (list == null) return;
        JSONObject s = Session.settings();
        JSONArray hid = s.optJSONArray("hiddenChatIds");
        JSONObject f = folder(folderId);
        if (folderId != null && f == null) folderId = null;
        JSONArray pins = pins(f);
        List<String> pinned = new ArrayList<>(), rest = new ArrayList<>();
        String q = query.trim().toLowerCase();
        for (String id : Chats.I.order) {
            Chats.Conv c = Chats.I.conv(id);
            if (c == null) continue;
            boolean hidden = Sync.contains(hid, id);
            if (hiddenView != hidden) continue;
            if (!hiddenView && f != null && !inFolder(f, c)) continue;
            if (!q.isEmpty()) {
                String lab = (c.title() + " " + c.o.optString("peerHandle")).toLowerCase();
                if (!lab.contains(q)) continue;
            }
            if (!hiddenView && Sync.contains(pins, id)) pinned.add(id);
            else rest.add(id);
        }
        List<String> pinOrdered = new ArrayList<>();
        for (int i = 0; i < pins.length(); i++) if (pinned.contains(pins.optString(i))) pinOrdered.add(pins.optString(i));
        rest.sort((x, y) -> Long.compare(Chats.I.conv(y).lastAt(), Chats.I.conv(x).lastAt()));
        rows.clear();
        rows.addAll(pinOrdered);
        rows.addAll(rest);
        if (!hiddenView) {
            List<String> ft = f == null ? null : types(f);
            if (ft == null || ft.contains("contacts") || ft.contains("non-contacts")) {
                for (com.vulpine.at.secret.Sec.Chat sc : com.vulpine.at.secret.Sec.list()) {
                    if (sc.o == null) continue;
                    if (!q.isEmpty() && !(sc.peerName() + " " + sc.o.optString("peerHandle")).toLowerCase().contains(q)) continue;
                    int at = rows.size();
                    for (int k = pinOrdered.size(); k < rows.size(); k++) {
                        String rid = rows.get(k);
                        long la;
                        if (rid.startsWith("secret:")) {
                            com.vulpine.at.secret.Sec.Chat o2 = com.vulpine.at.secret.Sec.chats.get(rid.substring(7));
                            la = o2 == null ? 0 : o2.lastAt;
                        } else la = Chats.I.conv(rid) == null ? 0 : Chats.I.conv(rid).lastAt();
                        if (la < sc.lastAt) {
                            at = k;
                            break;
                        }
                    }
                    rows.add(at, "secret:" + sc.id);
                }
            }
        }
        ad.notifyDataSetChanged();
        rebuildFolders();
        if (hiddenView) hiddenTitle.setText("Hidden Chats (" + rows.size() + ")");
        String e = null;
        if (rows.isEmpty()) {
            if (!q.isEmpty()) e = "No conversations match your search.";
            else if (hiddenView) e = "No hidden chats.";
            else if (f != null) e = "No chats in this folder.";
            else if (Chats.I.total == 0) e = "No conversations yet. Accept a contact request to start chatting.";
        }
        empty.setText(e == null ? "" : e);
        empty.setVisibility(e == null ? View.GONE : View.VISIBLE);
        empty.setTextColor(BgViewColors.mutedOnWallpaper());
        search.hiddenCount(countHidden(hid));
        if (selectMode) selCount.setText(String.valueOf(selected.size()));
    }

    int countHidden(JSONArray hid) {
        int n = 0;
        if (hid != null) for (int i = 0; i < hid.length(); i++) if (Chats.I.conv(hid.optString(i)) != null) n++;
        return n;
    }

    void needPreview(Chats.Conv c) {
        if (prev.containsKey(c.id) || prevBusy.contains(c.id) || c.o.optJSONObject("lastMessage") == null) return;
        if (prevBusy.size() >= 3) return;
        prevBusy.add(c.id);
        App.bg(() -> {
            String raw = Chats.I.preview(c);
            JSONObject lm = c.o.optJSONObject("lastMessage");
            String txt = raw == null ? null : Fmt.preview(raw);
            String who = null;
            if (txt != null && lm != null) {
                String sid = lm.optString("senderId");
                JSONArray hm = Session.settings().optJSONArray("hiddenMsgIds");
                if (Sync.contains(hm, lm.optString("id"))) txt = null;
                else if (sid.equals(Session.userId())) who = "You: ";
                else if (c.group()) {
                    JSONObject mem = c.member(sid);
                    if (mem != null) who = mem.optString("displayName", mem.optString("handle")) + ": ";
                }
            }
            prev.put(c.id, new String[]{txt, who});
            prevBusy.remove(c.id);
            App.ui(() -> ad.notifyDataSetChanged());
        });
    }

    class Adapter extends BaseAdapter {
        public int getCount() {
            return rows.size();
        }

        public Object getItem(int i) {
            return rows.get(i);
        }

        public long getItemId(int i) {
            return rows.get(i).hashCode();
        }

        public boolean hasStableIds() {
            return true;
        }

        public View getView(int i, View cv, ViewGroup p) {
            ChatRow r = cv instanceof ChatRow ? (ChatRow) cv : new ChatRow(p.getContext());
            String cid = rows.get(i);
            if (cid.startsWith("secret:")) {
                com.vulpine.at.secret.Sec.Chat sc = com.vulpine.at.secret.Sec.chats.get(cid.substring(7));
                if (sc != null) {
                    r.bindSecret(sc);
                    r.setAlpha(selectMode ? .45f : 1f);
                }
                return r;
            }
            Chats.Conv c = Chats.I.conv(cid);
            if (c == null) return r;
            JSONArray pins = pins(folder(folderId));
            String[] pv = prev.get(cid);
            if (pv == null && i < 40) needPreview(c);
            r.bind(c, !hiddenView && Sync.contains(pins, cid), selected.contains(cid), selectMode, pv == null ? null : pv[0], pv == null ? null : pv[1], callConvs.contains(cid), c.group() ? null : listening.get(c.o.optString("peerId")));
            return r;
        }
    }

    void buildSearch(Context c) {
        searchRow = new FrameLayout(c);
        search = new SearchField(c, this);
        searchRow.addView(search, W.flp(W.MP, T.dp(52), Gravity.BOTTOM));
        searchRow.setPadding(T.dp(12), 0, T.dp(12), 0);
        hiddenHeader = W.row(c);
        ImageView back = new ImageView(c);
        back.setImageDrawable(Svg.stroke(20, T.text, 2, "M15 18l-6-6 6-6"));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setBackground(W.ripple(W.oval(0), 99));
        back.setContentDescription("Back to chats");
        back.setOnClickListener(v -> setHidden(false));
        ((LinearLayout) hiddenHeader).addView(back, W.lp(T.dp(34), T.dp(34)));
        ((LinearLayout) hiddenHeader).addView(W.flex(c));
        hiddenTitle = W.text(c, "Hidden Chats", 16, 600, T.text);
        T.lh(hiddenTitle, 24);
        ((LinearLayout) hiddenHeader).addView(hiddenTitle, W.lp(W.WC, W.WC));
        ((LinearLayout) hiddenHeader).setGravity(Gravity.CENTER_VERTICAL);
        hiddenHeader.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
        hiddenHeader.setVisibility(View.GONE);
        top.addView(hiddenHeader);
        top.addView(searchRow, W.lp(W.MP, 0));
    }

    public void setHidden(boolean on) {
        hiddenView = on;
        hiddenHeader.setVisibility(on ? View.VISIBLE : View.GONE);
        folderWrap.setVisibility(on || !hasFolders() ? View.GONE : View.VISIBLE);
        if (on) App.bg(() -> Chats.I.loadAll());
        refresh();
    }

    void openSearch(boolean anim) {
        ViewGroup.LayoutParams p = searchRow.getLayoutParams();
        int h0 = p.height, h1 = T.dp(52);
        if (!anim) {
            p.height = h1;
            searchRow.setLayoutParams(p);
            search.setVsp(1);
            return;
        }
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
        ViewGroup.LayoutParams p = searchRow.getLayoutParams();
        int h0 = p.height;
        if (h0 == 0) return;
        Ov.anim(searchRow, h0, 0, 360, x -> {
            float v = (float) x.getAnimatedValue();
            ViewGroup.LayoutParams q = searchRow.getLayoutParams();
            q.height = Math.round(v);
            searchRow.setLayoutParams(q);
            search.setVsp(v / T.dp(52));
        });
    }

    final float[] lpDown = new float[4];
    long lpAt;

    boolean lpUp;

    boolean steadyPress(boolean timed) {
        float dx = lpDown[2] - lpDown[0], dy = lpDown[3] - lpDown[1];
        return Math.hypot(dx, dy) <= T.dp(6) && (!timed || System.currentTimeMillis() - lpAt >= 480);
    }

    void installPull() {
        final float[] st = new float[4];
        final boolean[] drag = {false};
        final long[] hd = new long[1];
        final float[] hy = new float[1];
        list.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lpDown[0] = e.getRawX();
                lpDown[1] = e.getRawY();
                lpDown[2] = e.getRawX();
                lpDown[3] = e.getRawY();
                lpAt = System.currentTimeMillis();
                lpUp = false;
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                lpUp = true;
            } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                lpDown[2] = e.getRawX();
                lpDown[3] = e.getRawY();
            }
            if (hiddenView && !selectMode) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    hd[0] = System.currentTimeMillis();
                    hy[0] = e.getRawY();
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP && hy[0] - e.getRawY() >= T.dp(55) && System.currentTimeMillis() - hd[0] < 600 && !list.canScrollVertically(1)) setHidden(false);
                return false;
            }
            if (selectMode) return false;
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
                    if (!drag[0]) {
                        if (Math.abs(dy) < T.dp(6) || Math.abs(dx) > Math.abs(dy)) return false;
                        if (!atTop) return false;
                        boolean closed = st[2] == 0;
                        if (closed && dy > 0 && StoriesPill.pullFirst(ChatsTab.this)) {
                            drag[0] = true;
                            st[3] = 2;
                        } else if (closed && dy > 0) {
                            drag[0] = true;
                            st[3] = 1;
                        } else if (!closed && dy < 0 && !search.busy()) {
                            drag[0] = true;
                            st[3] = 1;
                        } else return false;
                        MotionEvent ce = MotionEvent.obtain(e);
                        ce.setAction(MotionEvent.ACTION_CANCEL);
                        list.onTouchEvent(ce);
                        ce.recycle();
                    }
                    if (st[3] == 2) {
                        StoriesPill.dragStrip(ChatsTab.this, dy);
                        return true;
                    }
                    int H = T.dp(52);
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
                        StoriesPill.releaseStrip(ChatsTab.this);
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

    boolean hasFolders() {
        JSONArray f = Session.settings().optJSONArray("chatFolders");
        return f != null && f.length() > 0;
    }

    void buildFolders(Context c) {
        folderWrap = new FrameLayout(c);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setFillViewport(true);
        FrameLayout inner = new FrameLayout(c);
        folderPill = new View(c);
        folderPill.setBackground(new W.Pill(T.mixA(T.text, .10f), T.mixA(T.text, .12f), 1f, T.mixA(T.text, .08f)));
        inner.addView(folderPill, new FrameLayout.LayoutParams(0, T.dp(42)));
        folderBar = W.row(c);
        inner.addView(folderBar, new FrameLayout.LayoutParams(W.MP, T.dp(42)));
        hs.addView(inner, new FrameLayout.LayoutParams(W.MP, T.dp(42)));
        folderWrap.setBackground(W.round(T.mixA(T.text, .06f), 999));
        folderWrap.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        folderWrap.addView(hs, new FrameLayout.LayoutParams(W.MP, T.dp(42)));
        LinearLayout.LayoutParams p = W.lpm(W.MP, W.WC, 12, 0, 12, 8);
        top.addView(folderWrap, p);
        folderBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (pillAnim == null || !pillAnim.isRunning()) placeFolderPill(false);
        });
    }

    String folderSig;

    void rebuildFolders() {
        JSONArray f = Session.settings().optJSONArray("chatFolders");
        String sig = f == null ? "" : f.toString();
        folderWrap.setVisibility(hiddenView || Spotlight.active() || f == null || f.length() == 0 ? View.GONE : View.VISIBLE);
        if (sig.equals(folderSig)) {
            tintFolders();
            return;
        }
        folderSig = sig;
        folderBar.removeAllViews();
        if (f == null || f.length() == 0) return;
        Context c = folderBar.getContext();
        List<String[]> items = new ArrayList<>();
        items.add(new String[]{null, "All"});
        for (int i = 0; i < f.length(); i++) items.add(new String[]{f.optJSONObject(i).optString("id"), f.optJSONObject(i).optString("name")});
        for (String[] it : items) {
            boolean on = it[0] == null ? folderId == null : it[0].equals(folderId);
            TextView chip = W.text(c, it[1], 14, 500, on ? T.accent : T.textSecondary);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(T.dp(8), 0, T.dp(8), 0);
            chip.setSingleLine(true);
            chip.setEllipsize(null);
            chip.setTag(it[0] == null ? "" : it[0]);
            chip.setOnClickListener(v -> selectFolder(it[0]));
            if (it[0] != null) chip.setOnLongClickListener(v -> {
                Ov.Menu m = new Ov.Menu(c).ctx();
                m.item("Delete folder", Svg.stroke(15, T.danger, 1.8f, "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13"), true, () -> Folders.delete(it[0], () -> {
                    if (it[0].equals(folderId)) folderId = null;
                    refresh();
                }));
                int[] l = new int[2];
                v.getLocationInWindow(l);
                int W0 = h.root.getWidth();
                m.showAt(Math.min(l[0] + v.getWidth() / 2, W0 - T.dp(200)), l[1] + v.getHeight() / 2, false);
                return true;
            });
            LinearLayout.LayoutParams lp = W.lp(it[0] == null ? T.dp(16) : 0, T.dp(42), 1);
            if (folderBar.getChildCount() > 0) lp.leftMargin = T.dp(4);
            folderBar.addView(chip, lp);
        }
        folderBar.post(() -> placeFolderPill(false));
    }

    void tintFolders() {
        for (int i = 0; i < folderBar.getChildCount(); i++) {
            View ch = folderBar.getChildAt(i);
            String tag = (String) ch.getTag();
            boolean on = folderId == null ? "".equals(tag) : folderId.equals(tag);
            if (ch instanceof TextView) ((TextView) ch).setTextColor(on ? T.accent : T.textSecondary);
        }
    }

    android.animation.ValueAnimator pillAnim;

    void placeFolderPill(boolean anim) {
        for (int i = 0; i < folderBar.getChildCount(); i++) {
            View ch = folderBar.getChildAt(i);
            String tag = (String) ch.getTag();
            boolean on = folderId == null ? "".equals(tag) : folderId.equals(tag);
            if (!on || ch.getWidth() == 0) continue;
            ViewGroup.LayoutParams p = folderPill.getLayoutParams();
            if (pillAnim != null) pillAnim.cancel();
            if (!anim || p.width == 0) {
                if (p.width != ch.getWidth()) {
                    p.width = ch.getWidth();
                    folderPill.setLayoutParams(p);
                }
                folderPill.setTranslationX(ch.getLeft());
                return;
            }
            float x0 = folderPill.getTranslationX(), x1 = ch.getLeft();
            int w0 = p.width, w1 = ch.getWidth();
            pillAnim = android.animation.ValueAnimator.ofFloat(0, 1);
            pillAnim.setDuration(280);
            pillAnim.setInterpolator(T.EASE);
            pillAnim.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                ViewGroup.LayoutParams q = folderPill.getLayoutParams();
                q.width = Math.round(w0 + (w1 - w0) * t);
                folderPill.setLayoutParams(q);
                folderPill.setTranslationX(x0 + (x1 - x0) * t);
            });
            pillAnim.start();
            return;
        }
    }

    void selectFolder(String id) {
        folderId = id;
        refresh();
        placeFolderPill(true);
    }

    void enterSelect(String cid) {
        selectMode = true;
        selected.clear();
        selected.add(cid);
        Context c = root.getContext();
        if (selBar == null) {
            selBar = W.row(c);
            ImageView x = new ImageView(c);
            x.setImageDrawable(Svg.stroke(18, T.text, 2.2f, "M6 6l12 12", "M18 6L6 18"));
            x.setScaleType(ImageView.ScaleType.CENTER);
            x.setBackground(W.oval(T.mixA(T.text, .09f)));
            x.setContentDescription("Close selection");
            x.setOnClickListener(v -> exitSelect());
            W.pressNow(x, .88f);
            selBar.addView(x, W.lp(T.dp(44), T.dp(44)));
            selCount = W.text(c, "1", 15, 700, T.text);
            selCount.setGravity(Gravity.CENTER);
            T.lh(selCount, 22.5f);
            selCount.setPadding(T.dp(20), selCount.getPaddingTop() + T.dp(10), T.dp(20), selCount.getPaddingBottom() + T.dp(10));
            selCount.setBackground(W.round(T.mixA(T.text, .09f), 999));
            selBar.addView(selCount, W.lpm(W.WC, W.WC, 10, 0, 0, 0));
            selBar.addView(W.flex(c));
            ImageView dots = new ImageView(c);
            dots.setImageDrawable(Ic.get("more", 22, T.text));
            dots.setScaleType(ImageView.ScaleType.CENTER);
            dots.setBackground(W.oval(T.mixA(T.text, .09f)));
            W.pressNow(dots, .88f);
            dots.setContentDescription("More options");
            dots.setOnClickListener(v -> SelMenu.show(this, v));
            selBar.addView(dots, W.lp(T.dp(44), T.dp(44)));
        }
        if (selBar.getParent() == null) h.root.addView(selBar, W.flp(W.MP, W.WC, Gravity.TOP));
        selBar.setPadding(T.dp(20), Math.max(h.a.insetTop, T.dp(24)) + T.dp(8), T.dp(20), T.dp(10));
        h.header.setVisibility(View.GONE);
        onInsets(padTop, padBottom);
        refresh();
    }

    void toggleSel(String cid) {
        if (selected.contains(cid)) selected.remove(cid);
        else selected.add(cid);
        if (selected.isEmpty()) exitSelect();
        else refresh();
    }

    public void exitSelect() {
        selectMode = false;
        selected.clear();
        if (selBar != null && selBar.getParent() != null) ((ViewGroup) selBar.getParent()).removeView(selBar);
        h.header.setVisibility(View.VISIBLE);
        onInsets(padTop, padBottom);
        refresh();
    }

    public List<String> selectedIds() {
        return new ArrayList<>(selected);
    }

    static final class BgViewColors {
        static int mutedOnWallpaper() {
            String wp = com.vulpine.at.ui.BgView.wallpaper();
            if (wp == null || wp.isEmpty()) return T.textMuted;
            return com.vulpine.at.ui.BgView.wallpaperLum(wp) > .45 ? 0x99000000 : 0x99ffffff;
        }
    }
}
