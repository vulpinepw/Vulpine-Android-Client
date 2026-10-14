package com.vulpine.at.groups;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.people.Conns;
import com.vulpine.at.people.U;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

final class AddMembers extends Page {
    final GroupInfo gi;
    final List<Conns.P> people = new ArrayList<>();
    final Set<String> sel = new LinkedHashSet<>();
    LinearLayout box;
    TextView add;
    boolean busy;

    AddMembers(GroupInfo gi) {
        super("Add members");
        this.gi = gi;
    }

    static void open(GroupInfo gi) {
        gi.a.push(new AddMembers(gi));
    }

    protected void content(LinearLayout body) {
        Context c = a;
        box = W.col(c);
        body.addView(box, W.lp(W.MP, W.WC));
        LinearLayout act = W.row(c);
        act.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        act.addView(U.btn(c, "Cancel", U.GHOST, v -> a.onBackPressed()));
        add = U.btn(c, "Add", U.PRIMARY, v -> doAdd());
        act.addView(add, gi.lpBtn(8));
        body.addView(act, W.lpm(W.MP, W.WC, 0, 28, 0, 0));
        sync();
        App.bg(() -> {
            JSONObject l = Conns.loadAll();
            List<Conns.P> acc = l == null ? new ArrayList<>() : Conns.list(l, "accepted");
            App.ui(() -> {
                for (Conns.P p : acc) if (!p.handle.isEmpty() && gi.member(p.id) == null) people.add(p);
                render();
            });
        });
    }

    void sync() {
        add.setText(busy ? "Adding…" : "Add");
        U.enable(add, !busy && !sel.isEmpty());
    }

    void render() {
        Context c = a;
        box.removeAllViews();
        if (people.isEmpty()) {
            TextView e = U.muted(c, "No contacts to add", 14);
            e.setGravity(Gravity.CENTER);
            e.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
            box.addView(e, W.lp(W.MP, W.WC));
            return;
        }
        for (Conns.P p : people) {
            boolean on = sel.contains(p.handle);
            LinearLayout r = W.row(c);
            r.setPadding(T.dp(20), 0, T.dp(20), 0);
            U.pressBg(r, 999);
            r.addView(U.avatar(c, p.name, p.avatarColor, p.avatarUrl, 32, 13));
            TextView ck = U.check(c, 18, 6, on, 11);
            LinearLayout.LayoutParams cl = W.lp(T.dp(18), T.dp(18));
            cl.leftMargin = T.dp(8);
            r.addView(ck, cl);
            TextView n = U.txt(c, p.raw.optString(p.raw.optString("requesterId").equals(com.vulpine.core.Session.userId()) ? "targetName" : "requesterName", "").isEmpty() ? "@" + p.handle : p.name, 14, 500, T.textSecondary);
            W.ellipsize(n);
            r.addView(n, W.lpm(0, W.WC, 16, 0, 0, 0));
            ((LinearLayout.LayoutParams) n.getLayoutParams()).weight = 1;
            r.setOnClickListener(v -> {
                if (sel.contains(p.handle)) sel.remove(p.handle);
                else sel.add(p.handle);
                U.setCheck(ck, sel.contains(p.handle), 6);
                sync();
            });
            LinearLayout.LayoutParams lp = W.lp(W.MP, T.dp(36));
            if (box.getChildCount() > 0) lp.topMargin = T.dp(4);
            box.addView(r, lp);
        }
    }

    void doAdd() {
        if (busy || sel.isEmpty()) return;
        busy = true;
        sync();
        App.bg(() -> {
            JSONObject b = new JSONObject();
            try {
                b.put("memberHandles", new JSONArray(new ArrayList<>(sel)));
                b.put("keyEnvelopes", new JSONArray());
            } catch (Exception e) {
            }
            Http.Resp r = Http.post("/conversations/" + gi.cid + "/members", b);
            App.ui(() -> {
                busy = false;
                if (r.ok()) {
                    gi.update(r.obj());
                    sel.clear();
                    Ov.toast("Members added");
                    a.onBackPressed();
                } else {
                    Ov.toast(U.err(r, "Could not add"), true);
                    sync();
                }
            });
        });
    }
}
