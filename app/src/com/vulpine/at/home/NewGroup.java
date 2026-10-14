package com.vulpine.at.home;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.groups.GroupKeys;
import com.vulpine.at.people.Conns;
import com.vulpine.at.people.U;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class NewGroup extends Page {
    U.Field name;
    TextView count, create;
    LinearLayout listBox;
    final List<Conns.P> people = new ArrayList<>();
    final Set<String> sel = new LinkedHashSet<>();
    boolean busy;

    NewGroup() {
        super("New group");
    }

    public static void open(MainActivity a) {
        a.push(new NewGroup());
    }

    protected void content(LinearLayout body) {
        Context c = a;
        name = new U.Field(c, "Group name", null, 64, false);
        body.addView(name, W.lp(W.MP, W.WC));
        count = U.muted(c, "Select members (0 selected)", 13);
        body.addView(count, W.lpm(W.WC, W.WC, 0, 12, 0, 0));
        listBox = W.col(c);
        body.addView(listBox, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        LinearLayout act = W.row(c);
        act.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        act.addView(U.btn(c, "Cancel", U.GHOST, v -> a.onBackPressed()));
        create = U.btn(c, "Create group", U.PRIMARY, v -> create());
        LinearLayout.LayoutParams cp = W.lp(W.WC, T.dp(36));
        cp.leftMargin = T.dp(8);
        act.addView(create, cp);
        body.addView(act, W.lpm(W.MP, W.WC, 0, 24, 0, 0));
        name.onText(this::sync);
        sync();
        renderList();
        App.bg(() -> {
            org.json.JSONObject l = Conns.loadAll();
            List<Conns.P> acc = l == null ? new ArrayList<>() : Conns.list(l, "accepted");
            App.ui(() -> {
                people.clear();
                people.addAll(acc);
                renderList();
            });
        });
        App.H.postDelayed(() -> W.showKb(name.input), 350);
    }

    void sync() {
        U.enable(create, !busy && !name.value().trim().isEmpty());
        create.setText(busy ? "Creating…" : "Create group");
        count.setText("Select members (" + sel.size() + " selected)");
    }

    void renderList() {
        Context c = a;
        listBox.removeAllViews();
        if (people.isEmpty()) {
            TextView e = U.muted(c, "No contacts yet. Add some first.", 14);
            e.setGravity(Gravity.CENTER);
            e.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
            listBox.addView(e, W.lp(W.MP, W.WC));
            return;
        }
        for (Conns.P p : people) {
            boolean on = sel.contains(p.id);
            LinearLayout r = W.row(c);
            r.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
            r.setBackground(W.round(on ? T.accentSoft : T.surface2, 12));
            r.addView(U.avatar(c, p.name.isEmpty() ? p.handle : p.name, null, null, 32, 13));
            LinearLayout m = W.col(c);
            TextView t = U.txt(c, p.name, 14, 500, T.text);
            W.ellipsize(t);
            T.lh(t, 21);
            m.addView(t);
            TextView s = U.muted(c, "@" + p.handle, 13);
            W.ellipsize(s);
            T.lh(s, 20);
            m.addView(s);
            r.addView(m, W.lpm(0, W.WC, 8, 0, 8, 0));
            ((LinearLayout.LayoutParams) m.getLayoutParams()).weight = 1;
            FrameLayout ck = new FrameLayout(c);
            if (on) {
                ck.setBackground(W.oval(T.accent));
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(Ic.get("ppl.check", 12, T.accentFg));
                ck.addView(iv, W.flp(T.dp(12), T.dp(12), Gravity.CENTER));
            }
            r.addView(ck, W.lp(T.dp(20), T.dp(20)));
            r.setOnClickListener(v -> {
                if (sel.contains(p.id)) sel.remove(p.id);
                else sel.add(p.id);
                sync();
                renderList();
            });
            W.press(r, .98f);
            LinearLayout.LayoutParams lp = W.lp(W.MP, W.WC);
            if (listBox.getChildCount() > 0) lp.topMargin = T.dp(4);
            listBox.addView(r, lp);
        }
    }

    void create() {
        String n = name.value().trim();
        if (busy || n.isEmpty()) return;
        busy = true;
        sync();
        List<String> handles = new ArrayList<>();
        for (Conns.P p : people) if (sel.contains(p.id)) handles.add(p.handle);
        App.bg(() -> {
            GroupKeys.Result r = null;
            String err = null;
            try {
                r = GroupKeys.create(n, handles);
                if (!r.resp.ok()) err = U.err(r.resp, "Could not create group");
            } catch (Exception e) {
                err = e.getMessage() == null ? "Could not create group" : e.getMessage();
            }
            if (err != null) {
                String m = err;
                App.ui(() -> {
                    Ov.toast(m, true);
                    busy = false;
                    sync();
                });
                return;
            }
            String cid = r.cid;
            Chats.I.refresh(cid);
            if (!Chats.I.order.contains(cid)) Chats.I.order.add(0, cid);
            Chats.I.loadList(0);
            App.ui(() -> {
                Ov.toast("Group created");
                Home.openChat(cid);
            });
        });
    }
}
