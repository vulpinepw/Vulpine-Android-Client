package com.vulpine.at.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import org.json.JSONArray;
import org.json.JSONObject;

public final class HandlesCard {
    public static View build(Context c) {
        FrameLayout holder = new FrameLayout(c);
        fill(c, holder);
        return holder;
    }

    static void fill(Context c, FrameLayout holder) {
        holder.removeAllViews();
        JSONObject s = Session.s == null ? new JSONObject() : Session.s;
        String primary = s.optString("handle");
        JSONArray hs = s.optJSONArray("handles");
        LinearLayout card = S.card(c);
        card.addView(S.label(c, "Additional handles", 600));
        S.add(card, S.muted(c, "Up to 2 extra @handles that all resolve to your account. Shown on your profile as \"@" + primary + " also …\"."), 12);
        LinearLayout pr = W.row(c);
        TextView pt = S.text(c, "@" + primary, 14, 400, T.text, 21);
        pt.setTypeface(S.mono());
        pr.addView(pt, W.lp(0, W.WC, 1));
        pr.addView(W.text(c, "primary", 13, 400, T.textMuted));
        S.add(card, pr, 12);
        int n = hs == null ? 1 : hs.length();
        for (int i = 1; hs != null && i < hs.length(); i++) {
            String h = hs.optString(i);
            LinearLayout r = W.row(c);
            TextView ht = S.text(c, "@" + h, 14, 400, T.text, 21);
            ht.setTypeface(S.mono());
            r.addView(ht, W.lp(0, W.WC, 1));
            r.addView(S.btn(c, "Remove", S.GHOST, 28, 13, v -> remove(c, holder, h)), W.lp(W.WC, T.dp(28)));
            S.add(card, r, 8);
        }
        if (n < 3) {
            LinearLayout row = W.row(c);
            row.setGravity(Gravity.BOTTOM);
            W.Field f = new W.Field(c, "New handle", "@");
            f.input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(20)});
            row.addView(f, W.lp(0, W.WC, 1));
            TextView add = S.btn(c, "Add", S.PRIMARY, null);
            W.enable(add, false);
            f.input.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence x, int a, int b, int d) {
                }

                public void onTextChanged(CharSequence x, int a, int b, int d) {
                }

                public void afterTextChanged(android.text.Editable e) {
                    W.enable(add, e.toString().trim().length() > 0);
                }
            });
            add.setOnClickListener(v -> {
                String y = f.value().toLowerCase().replaceFirst("^@", "").replaceAll("[^a-z0-9_]", "");
                if (y.isEmpty()) return;
                App.bg(() -> {
                    Http.Resp r = Http.post("/me/handles", App.jo("handle", y));
                    App.ui(() -> {
                        if (r.ok()) {
                            Session.merge(r.obj());
                            Ov.toast("@" + y + " added");
                            fill(c, holder);
                            Sync.patchMe(new JSONObject(), null, null);
                        } else Ov.toast(S.err(r, "Could not add handle"), true);
                    });
                });
            });
            LinearLayout.LayoutParams ap = W.lp(W.WC, T.dp(36));
            ap.leftMargin = T.dp(8);
            row.addView(add, ap);
            S.add(card, row, 8);
        }
        holder.addView(card, new FrameLayout.LayoutParams(W.MP, W.WC));
    }

    static void remove(Context c, FrameLayout holder, String h) {
        App.bg(() -> {
            Http.Resp r = Http.delete("/me/handles/" + h, null);
            App.ui(() -> {
                if (r.ok()) {
                    Session.merge(r.obj());
                    Ov.toast("@" + h + " removed");
                    fill(c, holder);
                } else Ov.toast(S.err(r, "Could not remove handle"), true);
            });
        });
    }
}
