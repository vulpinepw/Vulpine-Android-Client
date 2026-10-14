package com.vulpine.at.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import org.json.JSONArray;
import org.json.JSONObject;

public class BugHunterPage extends SetPage {
    boolean busy;

    public BugHunterPage() {
        super("bughunter", "Bug Hunter");
    }

    ImageView badge(Context c) {
        ImageView iv = new ImageView(c);
        try {
            Bitmap b = BitmapFactory.decodeStream(c.getAssets().open("set-badge-butterfly.png"));
            iv.setImageBitmap(b);
        } catch (Exception e) {
        }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    protected void content(LinearLayout body) {
        Context c = a;
        LinearLayout c1 = W.row(c);
        c1.setBackground(W.round(T.surface, 16));
        c1.setPadding(T.dp(20), T.dp(16), T.dp(20), T.dp(16));
        c1.addView(badge(c), W.lp(T.dp(56), T.dp(56)));
        LinearLayout t1 = W.col(c);
        t1.addView(S.label(c, "Find a bug, get a badge", 600));
        S.add(t1, S.text(c, "Report a bug that turns out to be real and you get the Bug Hunter badge on your profile. It is rare and it stays for good.", 14, 400, T.textMuted, 21), 4);
        LinearLayout.LayoutParams tp = W.lp(0, W.WC, 1);
        tp.leftMargin = T.dp(16);
        c1.addView(t1, tp);
        add(c1);
        LinearLayout c2 = S.card(c, 20, 16);
        TextView h = W.text(c, "HOW IT WORKS", 13, 600, T.textMuted);
        h.setLetterSpacing(.04f);
        T.lh(h, 19.5f);
        c2.addView(h);
        for (String s : new String[]{"1. Apply below. It opens a private chat with @tor.", "2. Describe the bug: what you did, what happened, what you expected.", "3. If it is a real bug, @tor adds the badge to your account by hand."})
            S.add(c2, S.text(c, s, 14, 400, T.textMuted, 22.4f), 8);
        add(c2);
        LinearLayout c3 = W.row(c);
        c3.setBackground(W.round(T.surface, 16));
        c3.setPadding(T.dp(20), T.dp(16), T.dp(20), T.dp(16));
        LinearLayout t3 = W.col(c);
        t3.addView(S.label(c, "The badge", 500));
        t3.addView(S.muted(c, "Shown next to your name, like the staff badge."));
        c3.addView(t3, W.lp(0, W.WC, 1));
        LinearLayout.LayoutParams bp = W.lp(T.dp(36), T.dp(36));
        bp.leftMargin = T.dp(16);
        c3.addView(badge(c), bp);
        add(c3);
        TextView go = S.btn(c, "Apply to the program", S.PRIMARY, 38, 14, null);
        go.setOnClickListener(v -> apply(go));
        add(go);
    }

    void apply(TextView b) {
        if (busy) return;
        busy = true;
        S.busy(b, true, "Apply to the program", "Opening chat…");
        App.bg(() -> {
            String cid = null, err = null;
            Http.Resp u = Http.get("/users/tor");
            if (!u.ok()) err = "Could not open the chat. Try again.";
            else {
                String pid = u.obj().optString("id");
                Http.Resp cs = Http.get("/conversations");
                if (!cs.ok()) err = "Could not open the chat. Try again.";
                else {
                    Object any = cs.any();
                    JSONArray arr = any instanceof JSONArray ? (JSONArray) any : any instanceof JSONObject ? ((JSONObject) any).optJSONArray("conversations") : null;
                    for (int i = 0; arr != null && i < arr.length(); i++) {
                        JSONObject x = arr.optJSONObject(i);
                        if (x != null && "dm".equals(x.optString("type")) && pid.equals(x.optString("peerId"))) cid = x.optString("id");
                    }
                    if (cid == null) err = "You are not connected with @tor yet. Add them as a contact first.";
                }
            }
            String fc = cid, fe = err;
            App.ui(() -> {
                busy = false;
                if (!alive) return;
                S.busy(b, false, "Apply to the program", "Opening chat…");
                if (fe != null) Ov.toast(fe, true);
                else Home.openChat(fc);
            });
        });
    }
}
