package com.vulpine.at.groups;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.people.U;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import org.json.JSONObject;

final class RoleEdit extends Page {
    static final String[][] PERMS = {{"editProfile", "Edit profile"}, {"addMembers", "Add members"}, {"removeMembers", "Kick members"}, {"banMembers", "Ban members"}, {"manageRoles", "Manage roles"}, {"manageInvite", "Invite links"}, {"pinMessages", "Pin messages"}, {"deleteMessages", "Delete messages"}, {"muteMembers", "Mute members"}, {"sendAttachments", "Send images & files"}, {"sendMessages", "Can send messages"}, {"startCalls", "Start calls"}, {"createCallRooms", "Create call rooms"}, {"addBots", "Add bots"}, {"manageAutoDelete", "Manage auto-delete"}};
    final GroupInfo gi;
    final String id;
    final JSONObject perms;
    U.Field name, color;
    TextView save;
    LinearLayout list;

    RoleEdit(GroupInfo gi, JSONObject r) {
        super(r == null ? "New role" : "Edit role");
        this.gi = gi;
        id = r == null ? null : r.optString("id");
        JSONObject p = r == null ? null : r.optJSONObject("permissions");
        JSONObject cp = new JSONObject();
        try {
            if (p != null) cp = new JSONObject(p.toString());
        } catch (Exception e) {
        }
        perms = cp;
        nameInit = r == null ? "" : r.optString("name");
        colorInit = r == null ? "" : r.optString("color", "");
    }

    final String nameInit, colorInit;

    static void open(GroupInfo gi, JSONObject r) {
        gi.a.push(new RoleEdit(gi, r));
    }

    boolean checked(String k) {
        if (k.equals("sendMessages") || k.equals("sendAttachments")) return !(perms.has(k) && !perms.optBoolean(k, true));
        if (k.equals("createCallRooms") && !perms.has(k)) return perms.optBoolean("startCalls");
        return perms.optBoolean(k);
    }

    protected void content(LinearLayout body) {
        Context c = a;
        name = new U.Field(c, "Role name", null, 32, false);
        name.set(nameInit);
        body.addView(name, W.lp(W.MP, W.WC));
        color = new U.Field(c, "Color (hex)", null, 9, false);
        color.set(colorInit);
        body.addView(color, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        body.addView(U.muted(c, "Permissions", 13), W.lpm(W.WC, W.WC, 0, 12, 0, 0));
        list = W.col(c);
        body.addView(list, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        for (String[] pk : PERMS) {
            LinearLayout r = W.row(c);
            r.setPadding(T.dp(20), 0, T.dp(20), 0);
            U.pressBg(r, 999);
            TextView ck = U.check(c, 16, 4, checked(pk[0]), 10);
            r.addView(ck);
            r.addView(U.txt(c, pk[1], 14, 500, T.textSecondary), W.lpm(W.WC, W.WC, 8, 0, 0, 0));
            r.setOnClickListener(v -> {
                boolean nv = !checked(pk[0]);
                try {
                    perms.put(pk[0], nv);
                } catch (Exception e) {
                }
                U.setCheck(ck, nv, 4);
            });
            LinearLayout.LayoutParams lp = W.lp(W.MP, T.dp(34));
            if (list.getChildCount() > 0) lp.topMargin = T.dp(12);
            list.addView(r, lp);
        }
        LinearLayout act = W.row(c);
        act.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (id != null && !id.equals("member") && !id.equals("admin")) act.addView(U.btn(c, "Delete", U.GHOST, v -> del()));
        act.addView(U.btn(c, "Cancel", U.GHOST, v -> a.onBackPressed()), act.getChildCount() > 0 ? gi.lpBtn(8) : W.lp(W.WC, T.dp(36)));
        save = U.btn(c, "Save", U.PRIMARY, v -> save());
        act.addView(save, gi.lpBtn(8));
        body.addView(act, W.lpm(W.MP, W.WC, 0, 28, 0, 0));
        name.onText(() -> U.enable(save, !name.value().trim().isEmpty()));
        U.enable(save, !name.value().trim().isEmpty());
    }

    void save() {
        JSONObject b = new JSONObject();
        try {
            b.put("name", name.value().isEmpty() ? "Role" : name.value());
            b.put("color", color.value());
            b.put("permissions", perms);
        } catch (Exception e) {
        }
        App.bg(() -> {
            Http.Resp r = id != null ? Http.patch("/conversations/" + gi.cid + "/roles/" + id, b) : Http.post("/conversations/" + gi.cid + "/roles", b);
            App.ui(() -> {
                if (r.ok()) {
                    gi.update(r.obj());
                    Ov.toast("Role saved");
                    a.onBackPressed();
                } else Ov.toast(U.err(r, "Could not save role"), true);
            });
        });
    }

    void del() {
        App.bg(() -> {
            Http.Resp r = Http.call("DELETE", "/conversations/" + gi.cid + "/roles/" + id, null);
            App.ui(() -> {
                if (r.ok()) {
                    gi.update(r.obj());
                    Ov.toast("Role deleted");
                    a.onBackPressed();
                } else Ov.toast(U.err(r, "Could not delete"), true);
            });
        });
    }
}
