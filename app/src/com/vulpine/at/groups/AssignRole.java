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
import org.json.JSONArray;
import org.json.JSONObject;

final class AssignRole extends Page {
    final GroupInfo gi;
    final String uid;

    AssignRole(GroupInfo gi, String uid) {
        super("Assign role");
        this.gi = gi;
        this.uid = uid;
    }

    static void open(GroupInfo gi, String uid) {
        gi.a.push(new AssignRole(gi, uid));
    }

    protected void content(LinearLayout body) {
        Context c = a;
        JSONArray rs = gi.g.optJSONArray("roles");
        LinearLayout box = W.col(c);
        if (rs != null) for (int i = 0; i < rs.length(); i++) {
            JSONObject rr = rs.optJSONObject(i);
            TextView b = U.btn(c, rr.optString("name"), U.GHOST, v -> assign(rr.optString("id")));
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = W.lp(W.MP, T.dp(36));
            if (box.getChildCount() > 0) lp.topMargin = T.dp(4);
            box.addView(b, lp);
        }
        body.addView(box, W.lp(W.MP, W.WC));
        LinearLayout act = W.row(c);
        act.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        act.addView(U.btn(c, "Cancel", U.GHOST, v -> a.onBackPressed()));
        body.addView(act, W.lpm(W.MP, W.WC, 0, 28, 0, 0));
    }

    void assign(String role) {
        App.bg(() -> {
            Http.Resp r = Http.post("/conversations/" + gi.cid + "/members/" + uid + "/role", App.jo("role", role));
            App.ui(() -> {
                if (r.ok()) {
                    gi.update(r.obj());
                    Ov.toast("Role updated");
                    a.onBackPressed();
                } else Ov.toast(U.err(r, "Could not assign"), true);
            });
        });
    }
}
