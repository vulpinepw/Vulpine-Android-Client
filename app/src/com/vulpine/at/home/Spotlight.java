package com.vulpine.at.home;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class Spotlight {
    static FrameLayout scrim, field, results;
    static ChatsTab owner;
    static EditText input;
    static ImageView clear;
    static TextView label;
    static ViewGroup listParent;
    static int listIndex;
    static ViewGroup.LayoutParams listLp;
    static int[] listPad;
    static boolean busy;

    static int top(FrameLayout host) {
        int H = host.getHeight() > 0 ? host.getHeight() : host.getResources().getDisplayMetrics().heightPixels;
        int safe = owner == null ? 0 : owner.h.a.insetTop;
        return Math.max(Math.round(H * .14f), safe + T.dp(28));
    }

    static void open(ChatsTab tab, SearchField f) {
        if (scrim != null || busy) return;
        owner = tab;
        FrameLayout host = tab.h.root;
        Context c = host.getContext();
        scrim = new FrameLayout(c);
        scrim.setBackgroundColor(T.dark ? 0x57000000 : 0x24000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> close());
        host.addView(scrim, new FrameLayout.LayoutParams(W.MP, W.MP));
        scrim.setAlpha(0);
        scrim.animate().alpha(1).setDuration(240).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
        int panelW = Math.min(T.dp(640), host.getWidth() > 0 ? host.getWidth() - T.dp(32) : c.getResources().getDisplayMetrics().widthPixels - T.dp(32));
        int t = top(host);
        field = new FrameLayout(c);
        field.setBackground(W.round(T.mixA(T.surface, .9f), 18));
        field.setElevation(T.dpf(16));
        field.setClickable(true);
        input = new EditText(c);
        input.setBackground(null);
        T.text(input, 20, 400, T.text);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(T.dp(54), 0, T.dp(48), 0);
        if (android.os.Build.VERSION.SDK_INT >= 29) input.setTextCursorDrawable(com.vulpine.at.ui.W.cursor(T.accent));
        field.addView(input, new FrameLayout.LayoutParams(W.MP, W.MP));
        LinearLayout lab = W.row(c);
        lab.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mag = new ImageView(c);
        mag.setImageDrawable(Svg.stroke(22, T.textMuted, 2.4f, "M15.5 15.5L21 21", "M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13z"));
        lab.addView(mag, W.lp(T.dp(22), T.dp(22)));
        label = W.text(c, "Search conversations…", 20, 400, T.textMuted);
        W.ellipsize(label);
        lab.addView(label, W.lpm(W.WC, W.WC, 13, 0, 0, 0));
        FrameLayout.LayoutParams lp = W.flp(W.MP, W.WC, Gravity.START | Gravity.CENTER_VERTICAL);
        lp.leftMargin = T.dp(18);
        lp.rightMargin = T.dp(48);
        lab.setClickable(false);
        field.addView(lab, lp);
        clear = new ImageView(c);
        clear.setImageDrawable(Svg.stroke(11, T.surface, 2f, "M6 6l12 12", "M18 6L6 18"));
        clear.setScaleType(ImageView.ScaleType.CENTER);
        clear.setBackground(W.oval(T.textMuted));
        clear.setContentDescription("Clear search");
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(v -> {
            input.setText("");
            W.showKb(input);
        });
        FrameLayout.LayoutParams cp = W.flp(T.dp(20), T.dp(20), Gravity.END | Gravity.CENTER_VERTICAL);
        cp.rightMargin = T.dp(18);
        field.addView(clear, cp);
        FrameLayout.LayoutParams fp = W.flp(panelW, T.dp(60), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        fp.topMargin = t;
        host.addView(field, fp);
        results = new FrameLayout(c);
        results.setBackground(W.round(T.mixA(T.surface, .9f), 18));
        results.setElevation(T.dpf(16));
        results.setPadding(0, T.dp(6), 0, T.dp(6));
        results.setClipToOutline(true);
        results.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        results.setClickable(true);
        FrameLayout.LayoutParams rp = W.flp(panelW, W.WC, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rp.topMargin = t + T.dp(70);
        host.addView(results, rp);
        results.setVisibility(View.GONE);
        ListView l = tab.list;
        listParent = (ViewGroup) l.getParent();
        listIndex = listParent.indexOfChild(l);
        listLp = l.getLayoutParams();
        listPad = new int[]{l.getPaddingLeft(), l.getPaddingTop(), l.getPaddingRight(), l.getPaddingBottom()};
        busy = true;
        listParent.removeView(l);
        busy = false;
        l.setPadding(0, 0, 0, 0);
        l.setTranslationX(-T.dp(8));
        results.addView(l, new FrameLayout.LayoutParams(W.MP, W.WC));
        l.setSelectionFromTop(0, 0);
        tab.searchRow.setVisibility(View.GONE);
        tab.folderWrap.setVisibility(View.GONE);
        tab.syncPad();
        sizeResults();
        field.setAlpha(0);
        field.setTranslationY(-T.dp(10));
        field.setScaleX(.95f);
        field.setScaleY(.95f);
        field.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(280).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(Editable e) {
                if (owner == null) return;
                String q = e.toString();
                label.setVisibility(q.isEmpty() ? View.VISIBLE : View.INVISIBLE);
                clear.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (!q.equals(owner.search.input.getText().toString())) owner.search.input.setText(q);
                boolean show = !q.isEmpty();
                if (show && results.getVisibility() != View.VISIBLE) {
                    results.setVisibility(View.VISIBLE);
                    owner.list.setSelectionFromTop(0, 0);
                    results.setAlpha(0);
                    results.setTranslationY(-T.dp(8));
                    results.animate().alpha(1).translationY(0).setDuration(260).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
                } else if (!show) results.setVisibility(View.GONE);
                sizeResults();
            }
        });
        input.setOnEditorActionListener((v, a, e) -> {
            W.hideKb(input);
            return true;
        });
        Ov.push(scrim, Spotlight::close);
        tab.search.input.clearFocus();
        input.requestFocus();
        W.showKb(input);
    }

    static void sizeResults() {
        if (results == null || owner == null) return;
        FrameLayout host = owner.h.root;
        int H = host.getHeight() > 0 ? host.getHeight() : host.getResources().getDisplayMetrics().heightPixels;
        int ime = owner.h.a.insetIme;
        int t = top(host);
        int max = Math.min(Math.round((H - ime) * .56f), H - ime - t - T.dp(90));
        ListView l = owner.list;
        int want = 0;
        android.widget.ListAdapter ad = l.getAdapter();
        int w = results.getWidth() > 0 ? results.getWidth() : ((FrameLayout.LayoutParams) results.getLayoutParams()).width;
        if (ad != null) for (int i = 0; i < ad.getCount() && want < max; i++) {
            View v = ad.getView(i, null, l);
            v.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1, w), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
            want += v.getMeasuredHeight();
        }
        int h = Math.max(T.dp(40), Math.min(max, want));
        ViewGroup.LayoutParams p = l.getLayoutParams();
        if (p.height != h) {
            p.height = h;
            l.setLayoutParams(p);
        }
    }

    static void query(ChatsTab tab, String q) {
        if (input == null || owner == null) return;
        if (!q.equals(input.getText().toString())) input.setText(q);
        results.post(Spotlight::sizeResults);
    }

    static boolean active() {
        return scrim != null;
    }

    static void close() {
        if (scrim == null) return;
        FrameLayout s = scrim, fl = field, rs = results;
        ChatsTab t = owner;
        scrim = null;
        field = null;
        results = null;
        owner = null;
        Ov.remove(s);
        if (input != null) {
            W.hideKb(input);
            input.clearFocus();
        }
        input = null;
        android.view.animation.PathInterpolator out = new android.view.animation.PathInterpolator(.4f, 0f, 1f, 1f);
        s.animate().alpha(0).setDuration(200).withEndAction(() -> {
            if (s.getParent() != null) ((FrameLayout) s.getParent()).removeView(s);
        }).start();
        fl.animate().alpha(0).translationY(-T.dp(8)).scaleX(.97f).scaleY(.97f).setDuration(200).setInterpolator(out).withEndAction(() -> {
            if (fl.getParent() != null) ((FrameLayout) fl.getParent()).removeView(fl);
        }).start();
        if (t != null) {
            ListView l = t.list;
            busy = true;
            rs.removeView(l);
            l.setPadding(listPad[0], listPad[1], listPad[2], listPad[3]);
            l.setTranslationX(0);
            listParent.addView(l, Math.min(listIndex, listParent.getChildCount()), listLp);
            busy = false;
            t.onInsets(t.padTop, t.padBottom);
            if (rs.getParent() != null) ((FrameLayout) rs.getParent()).removeView(rs);
            t.searchRow.setVisibility(View.VISIBLE);
            t.syncPad();
            t.search.input.setText("");
            t.search.input.clearFocus();
            t.rebuildFolders();
            t.closeSearch();
            t.refresh();
        } else if (rs.getParent() != null) ((FrameLayout) rs.getParent()).removeView(rs);
    }
}
