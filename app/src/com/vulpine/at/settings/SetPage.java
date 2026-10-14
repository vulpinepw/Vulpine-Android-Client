package com.vulpine.at.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Pin;
import com.vulpine.at.home.Discover;
import com.vulpine.at.home.Folders;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Page;
import com.vulpine.at.ui.Screen;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Session;

public abstract class SetPage extends Screen {
    public final String key, title;
    protected FrameLayout frame, header;
    protected ScrollView sv;
    protected LinearLayout body;
    protected LinearLayout titleRow;
    protected boolean alive;

    public SetPage(String key, String title) {
        this.key = key;
        this.title = title;
    }

    protected abstract void content(LinearLayout body);

    public View build() {
        Context c = a;
        alive = true;
        frame = new Page.EdgeFrame(c, this);
        frame.setBackgroundColor(T.bg);
        sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        body = W.col(c);
        body.setPadding(T.dp(24), 0, T.dp(24), 0);
        sv.addView(body, new FrameLayout.LayoutParams(W.MP, W.WC));
        frame.addView(sv, new FrameLayout.LayoutParams(W.MP, W.MP));
        titleRow = W.row(c);
        ImageView back = new ImageView(c);
        back.setImageDrawable(Svg.stroke(22, T.text, 2, "M15 18l-6-6 6-6"));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("Back to settings");
        back.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 12));
        back.setOnClickListener(v -> a.onBackPressed());
        W.press(back, .88f);
        titleRow.addView(back, W.lp(T.dp(36), T.dp(36)));
        TextView h2 = W.text(c, title, 20, 600, T.text);
        h2.setMaxLines(1);
        h2.setEllipsize(android.text.TextUtils.TruncateAt.END);
        h2.setAutoSizeTextTypeUniformWithConfiguration(Math.round(15 * T.scale), Math.round(20 * T.scale), 1, android.util.TypedValue.COMPLEX_UNIT_DIP);
        LinearLayout.LayoutParams hp = W.lp(0, T.dp(36), 1);
        hp.leftMargin = T.dp(8);
        h2.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(h2, hp);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        content(body);
        header = buildHeader(a, frame);
        FrameLayout.LayoutParams tp = W.flp(W.MP, T.dp(40), Gravity.START | Gravity.CENTER_VERTICAL);
        tp.topMargin = T.dp(8);
        tp.leftMargin = T.dp(12);
        tp.rightMargin = T.dp(3 * 42 + 4);
        header.addView(titleRow, tp);
        return frame;
    }

    public static FrameLayout buildHeader(MainActivity a, FrameLayout parent) {
        Context c = a;
        FrameLayout header = new FrameLayout(c);
        LinearLayout right = W.row(c);
        right.addView(hbtn(c, "lock", "Lock app", 22, v -> Pin.lockNow()));
        right.addView(hbtn(c, "discover", "Discover groups", 24, v -> Discover.open(a)));
        right.addView(hbtn(c, "more", "More options", 22, v -> moreMenu(a, parent)));
        FrameLayout.LayoutParams rp = W.flp(W.WC, W.WC, Gravity.END | Gravity.CENTER_VERTICAL);
        rp.topMargin = T.dp(8);
        header.addView(right, rp);
        header.setPadding(T.dp(12), 0, T.dp(12), 0);
        View scrim = new View(c);
        scrim.setBackgroundColor(T.bg);
        parent.addView(scrim, W.flp(W.MP, 0, Gravity.TOP));
        parent.addView(header, W.flp(W.MP, T.dp(44), Gravity.TOP));
        header.setTag(scrim);
        return header;
    }

    static View hbtn(Context c, String icon, String label, float size, View.OnClickListener on) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(Ic.get(icon, size, T.text));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setContentDescription(label);
        b.setBackground(W.ripple(W.oval(0), 999));
        b.setOnClickListener(on);
        W.press(b, .88f);
        LinearLayout.LayoutParams p = W.lp(T.dp(40), T.dp(40));
        p.leftMargin = T.dp(2);
        b.setLayoutParams(p);
        return b;
    }

    static void moreMenu(MainActivity a, FrameLayout root) {
        Ov.Menu m = new Ov.Menu(a);
        String h = Session.s == null ? "" : Session.s.optString("handle");
        if (h.equals("tor") || h.equals("tortue974") || h.equals("doudou")) m.item("Admin", null, false, () -> Ov.toast("Admin is available on the web app"));
        m.item("Notes", Ic.get("menu.notes", 18, T.text), false, () -> toHome(a, "notes", null));
        m.item("Shop", Ic.get("menu.shop", 18, T.text), false, () -> toHome(a, "shop", null));
        m.item("Create folder", Ic.get("menu.folder", 18, T.text), false, () -> toHome(a, "chats", () -> Folders.create(a)));
        m.item("Log out", null, true, Settings::logoutConfirm);
        m.showAt(root.getWidth() - T.dp(12), T.dp(56) + Math.max(a.insetTop, T.dp(24)), true);
    }

    static void toHome(MainActivity a, String tab, Runnable then) {
        Home h = Home.I;
        if (h == null) return;
        a.popTo(h);
        h.select(tab, false);
        if (then != null) App.H.postDelayed(then, 80);
    }

    public static int top(MainActivity a) {
        return Math.max(a.insetTop, T.dp(24)) + T.dp(8);
    }

    public void onInsets(int top, int bottom, int ime) {
        int ht = Math.max(top, T.dp(24)) + T.dp(8);
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams();
        hp.topMargin = ht;
        header.setLayoutParams(hp);
        View scrim = (View) header.getTag();
        FrameLayout.LayoutParams sp = (FrameLayout.LayoutParams) scrim.getLayoutParams();
        sp.height = ht + T.dp(44) + T.dp(8);
        scrim.setLayoutParams(sp);
        FrameLayout.LayoutParams vp = (FrameLayout.LayoutParams) sv.getLayoutParams();
        vp.topMargin = ht + T.dp(44) + T.dp(8);
        sv.setLayoutParams(vp);
        sv.setPadding(0, T.dp(4), 0, Math.max(T.dp(113), T.dp(97) + bottom) + ime);
    }

    public void onDestroy() {
        alive = false;
    }

    public void scrollToDp(int y) {
        sv.scrollTo(0, T.dp(y));
    }

    protected void rebuild() {
        int y = sv.getScrollY();
        body.removeAllViews();
        content(body);
        sv.post(() -> sv.scrollTo(0, y));
    }

    protected LinearLayout.LayoutParams lp(int top) {
        return S.gap(top);
    }

    protected View add(View v) {
        return S.add(body, v, 12);
    }

    protected View add(View v, int topDp) {
        return S.add(body, v, topDp);
    }
}
