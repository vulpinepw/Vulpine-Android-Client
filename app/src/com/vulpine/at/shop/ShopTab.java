package com.vulpine.at.shop;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public class ShopTab extends Home.Tab {
    static String pendingTab;
    static ShopTab I;
    static final String[] KEYS = {"market", "wallet", "official"};
    LinearLayout col;
    FrameLayout bodyHost;
    Seg seg;
    String tab = "market";
    Part part;
    int top, bottom;
    Context c;

    public abstract static class Part {
        public ShopTab shop;
        public ScrollView sv;
        public LinearLayout body;

        public abstract void build(Context c, LinearLayout body);

        public void onShow() {
        }

        public void onHide() {
        }

        public void onScroll() {
        }
    }

    public static void openOfficial() {
        Home h = Home.I;
        if (h == null) return;
        pendingTab = "official";
        ShopTab cur = I;
        if (cur != null && cur.h == h) {
            pendingTab = null;
            cur.show("official");
        }
        if (h.a != null) h.a.popTo(h);
        h.select("shop", false);
    }

    public View build(Context c) {
        this.c = c;
        I = this;
        FrameLayout root = new FrameLayout(c);
        col = W.col(c);
        seg = new Seg(c, new String[]{"Market", "Wallet", "Official"}, 0, K.XS, i -> show(KEYS[i]));
        LinearLayout segWrap = W.col(c);
        segWrap.setPadding(T.dp(16), T.dp(16), T.dp(16), 0);
        segWrap.addView(seg, W.lp(W.MP, T.dp(42)));
        col.addView(segWrap, W.lp(W.MP, W.WC));
        bodyHost = new FrameLayout(c);
        col.addView(bodyHost, W.lp(W.MP, 0, 1));
        root.addView(col, new FrameLayout.LayoutParams(W.MP, W.MP));
        String t0 = pendingTab != null ? pendingTab : "market";
        pendingTab = null;
        show(t0);
        return root;
    }

    public void show(String key) {
        int i = java.util.Arrays.asList(KEYS).indexOf(key);
        if (i < 0) i = 0;
        if (part != null && key.equals(tab) && bodyHost.getChildCount() > 0) return;
        tab = KEYS[i];
        seg.select(i, false);
        if (part != null) part.onHide();
        bodyHost.removeAllViews();
        part = "wallet".equals(tab) ? new Wallet() : "official".equals(tab) ? new Official() : new Market();
        part.shop = this;
        ScrollView sv = new ScrollView(c) {
            @Override
            protected void onScrollChanged(int l, int t, int ol, int ot) {
                super.onScrollChanged(l, t, ol, ot);
                if (part != null) part.onScroll();
            }
        };
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        LinearLayout body = W.col(c);
        body.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(16));
        sv.addView(body, new FrameLayout.LayoutParams(W.MP, W.WC));
        part.sv = sv;
        part.body = body;
        part.build(c, body);
        bodyHost.addView(sv, new FrameLayout.LayoutParams(W.MP, W.MP));
        sv.setPadding(0, 0, 0, bottom);
        K.fade(sv, 250);
        part.onShow();
    }

    @Override
    public void onInsets(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
        if (col != null) col.setPadding(0, top, 0, 0);
        if (part != null && part.sv != null) part.sv.setPadding(0, 0, 0, bottom);
    }

    @Override
    public void onShow() {
        if (pendingTab != null) {
            String p = pendingTab;
            pendingTab = null;
            show(p);
        } else if (part != null) part.onShow();
    }

    @Override
    public void onHide() {
        if (part != null) part.onHide();
    }

    @Override
    public void scrollTop() {
        if (part != null && part.sv != null) part.sv.smoothScrollTo(0, 0);
    }
}
