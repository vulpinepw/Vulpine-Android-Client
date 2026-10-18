package com.vulpine.at.people;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.MainActivity;
import com.vulpine.at.home.Badges;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class BadgeInfo extends Page {
    final String key;

    BadgeInfo(String key) {
        super("Badge");
        this.key = key;
    }

    public static void open(String key) {
        MainActivity a = MainActivity.cur;
        if (a != null && key != null) a.push(new BadgeInfo(key));
    }

    public static void tappable(View v, String key) {
        if (v == null || key == null) return;
        v.setClickable(true);
        v.setOnClickListener(x -> open(key));
        W.press(v, .88f);
    }

    static String[] info(String k) {
        if (k.startsWith("lv:")) {
            String n = k.substring(3);
            return new String[]{"Level " + n + " group", "Members boosted this group to level " + n + ". Higher levels unlock more perks for everyone in the group."};
        }
        if ("pro".equals(k)) return new String[]{"Vulpine Pro", "This user has a Pro subscription."};
        if ("bot".equals(k)) return new String[]{"Bot", "Automated bot account."};
        if (k.contains("badge-butterfly") || k.contains("badge-bughunter")) return new String[]{"Bug Hunter", "This user found and reported bugs."};
        if (k.contains("badge-spider")) return new String[]{"Critical Bug Finder", "This user found and reported critical bugs."};
        if (k.contains("badge-staff")) return new String[]{"Vulpine Staff", "This user is part of the Vulpine team."};
        if (k.contains("badge-tiara")) return new String[]{"Owner", "This user is the owner of Vulpine."};
        if (k.contains("badge-promoter")) return new String[]{"Promoter", "This user helps advertise Vulpine."};
        if (k.contains("badge-certified")) return new String[]{"Certified", "This is a certified group."};
        return new String[]{"Custom Badge", "This user has a custom badge."};
    }

    protected void content(LinearLayout body) {
        Context c = a;
        String[] in = info(key);
        LinearLayout card = W.col(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(T.dp(20), T.dp(28), T.dp(20), T.dp(24));
        card.setBackground(W.round(T.surface, 20));
        FrameLayout halo = new FrameLayout(c);
        halo.setBackground(W.oval(T.dark ? 0x14ffffff : 0x0f000000));
        View art;
        if (key.startsWith("lv:")) {
            TextView p = W.text(c, "Lv " + key.substring(3), 26, 800, 0xffffffff);
            p.setGravity(Gravity.CENTER);
            p.setPadding(T.dp(18), T.dp(6), T.dp(18), T.dp(6));
            int lv = 0;
            try {
                lv = Integer.parseInt(key.substring(3));
            } catch (Exception e) {
            }
            p.setBackground(lv >= 10 ? new U.Grad(new int[]{0xfff97316, 0xffec4899, 0xff8b5cf6, 0xff3b82f6, 0xff22c55e, 0xfff97316}, 90, T.dpf(999)) : new U.Grad(new int[]{0xffa78bfa, 0xff7c3aed}, 160, T.dpf(999)));
            art = p;
            halo.addView(p, new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.CENTER));
        } else {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if ("bot".equals(key)) iv.setImageDrawable(Ic.svg(Badges.BOT_SVG, 72, T.text));
            else Img.anim(iv, "pro".equals(key) ? "asset:pro-badge.gif" : key, T.dp(160), true);
            art = iv;
            halo.addView(iv, new FrameLayout.LayoutParams(T.dp(80), T.dp(80), Gravity.CENTER));
        }
        card.addView(halo, W.lp(T.dp(128), T.dp(128)));
        TextView t = W.text(c, in[0], 22, 700, T.text);
        t.setGravity(Gravity.CENTER);
        card.addView(t, W.lpm(W.WC, W.WC, 0, 18, 0, 0));
        TextView d = W.text(c, in[1], 14, 400, T.textSecondary);
        d.setGravity(Gravity.CENTER);
        card.addView(d, W.lpm(W.WC, W.WC, 0, 6, 0, 0));
        body.addView(card, W.lpm(W.MP, W.WC, 0, 8, 0, 16));
        art.setScaleX(.6f);
        art.setScaleY(.6f);
        art.setAlpha(0f);
        art.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
    }
}
