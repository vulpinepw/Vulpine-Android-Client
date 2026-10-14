package com.vulpine.at.notes;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Screen;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class NotebookScreen extends Screen implements com.vulpine.at.chat.Panels.Host {
    com.vulpine.at.chat.Panels panels;
    ImageView emoB, gifB;
    int navH, imeH;

    public android.content.Context ctx() {
        return a;
    }

    public FrameLayout root() {
        return frame;
    }

    public void scrollBottom() {
        sv.post(() -> sv.scrollTo(0, list.getHeight()));
    }

    public String cid() {
        return "note:" + nbId;
    }

    public int navH() {
        return navH;
    }

    public int imeH() {
        return imeH;
    }

    public EditText input() {
        return input;
    }

    public ImageView emojiBtn() {
        return emoB;
    }

    public ImageView gifBtn() {
        return gifB;
    }

    public void onInsets(int bottom, int ime) {
        onInsets(a.insetTop, bottom, ime);
    }

    public void sendRaw(String body) {
        Editable e = input.getText();
        e.insert(Math.max(0, input.getSelectionStart()), body);
    }


    final String nbId;
    FrameLayout frame;
    ScrollView sv;
    LinearLayout list, header, comp, form, editBar, pinBar;
    View fade, tools;
    EditText input;
    ImageView sendB, rtB;
    TextView pinText, pinSub;
    String editing;
    int pinIdx;
    int lastCount = -1;
    boolean toolsOn;
    String hintFull = "";

    TextView hintV;

    void fitHint() {
        if (hintV != null) {
            hintV.setText(hintFull);
            hintV.setVisibility(input.length() == 0 ? View.VISIBLE : View.GONE);
        }
    }
    final Sync.L syncL = w -> {
        if ("me".equals(w) || "settings".equals(w)) render(false);
    };

    public NotebookScreen(String id) {
        this.nbId = id;
    }

    JSONObject nb() {
        JSONObject n = Notes.find(nbId);
        return n == null ? Notes.list().get(0) : n;
    }

    long openedAt;

    public View build() {
        openedAt = android.os.SystemClock.uptimeMillis();
        Context c = a;
        frame = new com.vulpine.at.ui.Page.EdgeFrame(c, this);
        frame.setBackgroundColor(T.bg);
        frame.setElevation(T.dpf(8));
        sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setClipToPadding(false);
        list = W.col(c);
        list.setPadding(T.dp(16), 0, T.dp(16), T.dp(16));
        sv.addView(list, new FrameLayout.LayoutParams(W.MP, W.WC));
        frame.addView(sv, new FrameLayout.LayoutParams(W.MP, W.MP));
        fade = new View(c);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{T.mixA(T.bg, .92f), T.mixA(T.bg, .55f), 0}));
        frame.addView(fade, W.flp(W.MP, T.dp(120), Gravity.TOP));
        buildHeader(c);
        buildPinBar(c);
        buildComposer(c);
        Sync.on(syncL);
        render(true);
        return frame;
    }

    void buildHeader(Context c) {
        header = W.row(c);
        header.setBackground(W.round(T.dark ? T.mixA(T.surface, .94f) : 0xf0ffffff, 999, T.glassStroke, .5f));
        header.setElevation(T.dpf(6));
        header.setPadding(T.dp(10), T.dp(5), T.dp(10), T.dp(5));
        ImageView back = new ImageView(c);
        back.setImageDrawable(Ic.get("nt.notebookscreen.back", 22, T.text));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setBackground(W.ripple(W.oval(0), 999));
        back.setContentDescription("Back to notebooks");
        back.setOnClickListener(v -> a.onBackPressed());
        header.addView(back, W.lp(T.dp(36), T.dp(36)));
        LinearLayout row = W.row(c);
        row.setPadding(T.dp(8), T.dp(3), T.dp(12), T.dp(3));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(W.oval(T.mixA(T.accent, .18f)));
        ImageView ii = new ImageView(c);
        ii.setImageDrawable(Ic.get("nt.notebookscreen.nb22", 18, T.accent));
        ic.addView(ii, W.flp(T.dp(18), T.dp(18), Gravity.CENTER));
        row.addView(ic, W.lp(T.dp(32), T.dp(32)));
        LinearLayout t = W.col(c);
        TextView nm = W.text(c, "", 14, 600, T.text);
        W.ellipsize(nm);
        T.lh(nm, 21);
        nm.setTag("name");
        t.addView(nm);
        TextView sub = W.text(c, "Your personal notepad", 13, 400, T.textMuted);
        W.ellipsize(sub);
        T.lh(sub, 19.5f);
        t.addView(sub);
        row.addView(t, W.lpm(0, W.WC, 8, 0, 0, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1;
        header.addView(row, W.lpm(0, W.WC, 0, 0, 0, 0));
        ((LinearLayout.LayoutParams) row.getLayoutParams()).weight = 1;
        FrameLayout.LayoutParams hp = W.flp(W.MP, W.WC, Gravity.TOP);
        hp.leftMargin = hp.rightMargin = T.dp(10);
        frame.addView(header, hp);
    }

    void buildPinBar(Context c) {
        pinBar = W.row(c);
        pinBar.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{new android.graphics.drawable.ColorDrawable(T.border), inset(new android.graphics.drawable.ColorDrawable(T.surface2))}));
        pinBar.setPadding(T.dp(12), T.dp(6), T.dp(8), T.dp(6));
        ImageView pi = new ImageView(c);
        pi.setImageDrawable(Ic.get("nt.notebookscreen.pin16", 16, T.accent));
        pinBar.addView(pi, W.lp(T.dp(16), T.dp(16)));
        LinearLayout tx = W.col(c);
        pinText = W.text(c, "", 13, 500, T.text);
        W.ellipsize(pinText);
        pinSub = W.text(c, "", 12, 400, T.textMuted);
        pinSub.setFontFeatureSettings("tnum");
        tx.addView(pinText);
        tx.addView(pinSub);
        pinBar.addView(tx, W.lpm(0, W.WC, 10, 0, 0, 0));
        ((LinearLayout.LayoutParams) tx.getLayoutParams()).weight = 1;
        pinBar.setOnClickListener(v -> cyclePin(1));
        frame.addView(pinBar, W.flp(W.MP, W.WC, Gravity.TOP));
        pinBar.setVisibility(View.GONE);
    }

    static android.graphics.drawable.Drawable inset(android.graphics.drawable.Drawable d) {
        return new android.graphics.drawable.InsetDrawable(d, 0, Math.max(1, T.dp(1)), 0, Math.max(1, T.dp(1)));
    }

    ImageView ib(Context c, String svg, float size, int sizeBox, String label, View.OnClickListener on) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(Ic.get(svg, size, T.textSecondary));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackground(W.ripple(W.oval(0), 999));
        b.setContentDescription(label);
        b.setOnClickListener(on);
        b.setLayoutParams(W.lp(T.dp(sizeBox), T.dp(sizeBox)));
        W.press(b, .88f);
        return b;
    }

    void buildComposer(Context c) {
        comp = W.col(c);
        comp.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(40));
        editBar = W.row(c);
        editBar.setPadding(T.dp(12), T.dp(8), T.dp(8), T.dp(8));
        View rail = new View(c);
        rail.setBackground(W.round(T.accent, 2));
        editBar.addView(rail, W.lp(T.dp(3), T.dp(28)));
        TextView el = W.text(c, "Editing note — Esc to cancel", 13, 600, T.accent);
        editBar.addView(el, W.lpm(0, W.WC, 10, 0, 0, 0));
        ((LinearLayout.LayoutParams) el.getLayoutParams()).weight = 1;
        ImageView ex = new ImageView(c);
        ex.setImageDrawable(Ic.get("nt.notebookscreen.x", 14, T.textSecondary));
        ex.setScaleType(ImageView.ScaleType.CENTER);
        ex.setContentDescription("Cancel edit");
        ex.setOnClickListener(v -> cancelEdit());
        editBar.addView(ex, W.lp(T.dp(28), T.dp(28)));
        editBar.setVisibility(View.GONE);
        comp.addView(editBar, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        form = W.row(c);
        form.setGravity(Gravity.BOTTOM);
        form.setBackground(W.round(T.dark ? T.mixA(T.surface, .94f) : 0xf0ffffff, 22, T.glassStroke, .5f));
        form.setElevation(T.dpf(10));
        form.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
        form.addView(ib(c, "nt.notebookscreen.attach", 22, 40, "Attach media to note", v -> attach()));
        input = new Rte.Ed(c);
        input.setBackground(null);
        T.text(input, 14, 400, T.text);
        input.setHintTextColor(T.textMuted);
        com.vulpine.at.stories.Lh.set(input, 21);
        input.setPadding(T.dp(8), T.dp(9) + input.getPaddingTop(), T.dp(8), T.dp(10) + input.getPaddingBottom());
        input.setMinHeight(T.dp(40));
        input.setMaxHeight(T.dp(120));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        input.setHorizontallyScrolling(false);
        input.setMaxLines(6);
        if (android.os.Build.VERSION.SDK_INT >= 29) input.setTextCursorDrawable(com.vulpine.at.ui.W.cursor(T.accent));
        input.setOnKeyListener((v, k, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (k == KeyEvent.KEYCODE_ENTER && !e.isShiftPressed()) {
                submit();
                return true;
            }
            if (k == KeyEvent.KEYCODE_ESCAPE && editing != null) {
                cancelEdit();
                return true;
            }
            return false;
        });
        input.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEND) {
                submit();
                return true;
            }
            return false;
        });
        Rte.attach(input);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a0, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a0, int b, int d) {
            }

            public void afterTextChanged(Editable e) {
                updateSend();
                fitHint();
            }
        });
        LinearLayout.LayoutParams ip = W.lp(0, W.WC, 1);
        ip.leftMargin = ip.rightMargin = T.dp(8);
        FrameLayout iw = new FrameLayout(c);
        iw.addView(input, new FrameLayout.LayoutParams(W.MP, W.WC));
        hintV = W.text(c, hintFull, 14, 400, T.textMuted);
        hintV.setSingleLine(true);
        hintV.setEllipsize(TextUtils.TruncateAt.END);
        com.vulpine.at.stories.Lh.set(hintV, 21);
        hintV.setPadding(T.dp(8), T.dp(9) + hintV.getPaddingTop(), T.dp(8), T.dp(10) + hintV.getPaddingBottom());
        hintV.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        iw.addView(hintV, W.flp(W.MP, W.WC, Gravity.TOP));
        form.addView(iw, ip);
        ImageView gif = ib(c, "nt.notebookscreen.gif", 21, 36, "GIF", v -> gifSheet());
        ((LinearLayout.LayoutParams) gif.getLayoutParams()).bottomMargin = T.dp(2);
        form.addView(gif);
        gifB = gif;
        ImageView emo = ib(c, "nt.notebookscreen.emoji", 21, 36, "Emoji", v -> panels.toggle(com.vulpine.at.chat.Panels.EMOJI));
        emoB = emo;
        LinearLayout.LayoutParams ep = (LinearLayout.LayoutParams) emo.getLayoutParams();
        ep.leftMargin = T.dp(8);
        ep.bottomMargin = T.dp(2);
        form.addView(emo);
        rtB = ib(c, "nt.notebookscreen.rt", 21, 36, "Rich text", v -> toggleTools());
        LinearLayout.LayoutParams rp = (LinearLayout.LayoutParams) rtB.getLayoutParams();
        rp.leftMargin = T.dp(8);
        rp.bottomMargin = T.dp(2);
        rtB.setVisibility(View.GONE);
        form.addView(rtB);
        sendB = new ImageView(c);
        sendB.setImageDrawable(Ic.get("nt.notebookscreen.send", 18, T.accentFg));
        sendB.setScaleType(ImageView.ScaleType.CENTER);
        sendB.setBackground(W.accentBg(999));
        sendB.setContentDescription("Save note");
        sendB.setOnClickListener(v -> submit());
        W.press(sendB, .9f);
        LinearLayout.LayoutParams sp = W.lp(T.dp(40), T.dp(40));
        sp.leftMargin = T.dp(8);
        form.addView(sendB, sp);
        comp.addView(form, W.lp(W.MP, W.WC));
        panels = new com.vulpine.at.chat.Panels(this);
        input.setOnFocusChangeListener((v, f) -> {
            if (f && panels.open()) panels.closeKeepKb();
        });
        frame.addView(comp, W.flp(W.MP, W.WC, Gravity.BOTTOM));
        comp.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) padScroll();
        });
        updateSend();
    }

    void toggleTools() {
        toolsOn = !toolsOn;
        if (toolsOn) {
            tools = Rte.toolbar(a, input);
            comp.addView(tools, comp.indexOfChild(form), W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        } else if (tools != null) {
            comp.removeView(tools);
            tools = null;
        }
        rtB.setImageDrawable(Ic.get("nt.notebookscreen.rt", 21, toolsOn ? T.accent : T.textSecondary));
        updateSend();
    }

    void updateSend() {
        boolean has = input.getText().toString().trim().length() > 0;
        sendB.setAlpha(has ? 1f : .4f);
        rtB.setVisibility(toolsOn ? View.VISIBLE : View.GONE);
        input.setMaxLines(input.length() == 0 ? 1 : 6);
    }

    void padScroll() {
        int top = Math.max(a.insetTop, T.dp(24)) + T.dp(84) + (pinBar.getVisibility() == View.VISIBLE ? pinBar.getHeight() + T.dp(8) : 0);
        sv.setPadding(0, top, 0, 0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sv.getLayoutParams();
        if (lp != null && lp.bottomMargin != comp.getHeight()) {
            lp.bottomMargin = comp.getHeight();
            sv.setLayoutParams(lp);
        }
    }

    public void onInsets(int top, int bottom, int ime) {
        int t = Math.max(top, T.dp(24));
        FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) header.getLayoutParams();
        hp.topMargin = t + T.dp(14);
        header.setLayoutParams(hp);
        FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) fade.getLayoutParams();
        fp.height = t + T.dp(96);
        fade.setLayoutParams(fp);
        FrameLayout.LayoutParams pp = (FrameLayout.LayoutParams) pinBar.getLayoutParams();
        pp.topMargin = t + T.dp(14) + T.dp(57) + T.dp(8);
        pinBar.setLayoutParams(pp);
        navH = bottom;
        imeH = ime;
        comp.setPadding(T.dp(12), T.dp(12), T.dp(12), ime > 0 ? ime + T.dp(6) : panels != null && panels.open() ? panels.height() + bottom + T.dp(6) : T.dp(40) + bottom);
        boolean stick = android.os.SystemClock.uptimeMillis() - openedAt > 800 && atBottom();
        padScroll();
        if (stick) sv.post(() -> sv.scrollTo(0, list.getHeight()));
    }

    public boolean atBottom() {
        return sv.getScrollY() + sv.getHeight() >= list.getHeight() + sv.getPaddingBottom() - T.dp(40);
    }

    public void onDestroy() {
        Rte.hideBar(input);
        Sync.off(syncL);
    }

    public boolean onBack() {
        if (panels != null && panels.open()) {
            panels.close();
            return true;
        }
        if (editing != null) {
            cancelEdit();
            return true;
        }
        return false;
    }

    void render(boolean first) {
        JSONObject nb = nb();
        ((TextView) header.findViewWithTag("name")).setText(nb.optString("name"));
        hintFull = "Write a note in \"" + nb.optString("name") + "\"…";
        fitHint();
        list.removeAllViews();
        Context c = a;
        list.addView(intro(c, nb));
        JSONArray m = Notes.msgs(nb);
        for (int i = 0; i < m.length(); i++) {
            JSONObject n = m.optJSONObject(i);
            if (n == null) continue;
            View v = note(c, nb, n);
            LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
            p.gravity = Gravity.END;
            p.topMargin = T.dp(8);
            list.addView(v, p);
        }
        renderPins(nb);
        int cnt = m.length();
        if (!first && cnt != lastCount) sv.post(() -> sv.smoothScrollTo(0, list.getHeight()));
        lastCount = cnt;
    }

    View intro(Context c, JSONObject nb) {
        LinearLayout w = W.col(c);
        w.setGravity(Gravity.CENTER_HORIZONTAL);
        w.setPadding(T.dp(16), T.dp(16), T.dp(16), T.dp(12));
        w.setBackground(W.round(0x1a7f7f7f, 16));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Ic.get("nt.notebookscreen.nb22", 22, T.accent));
        w.addView(ic, W.lp(T.dp(22), T.dp(22)));
        TextView t = W.text(c, nb.optString("name"), 14, 600, T.text);
        t.setGravity(Gravity.CENTER);
        T.lh(t, 21);
        w.addView(t, W.lpm(W.WC, W.WC, 0, 6, 0, 0));
        TextView d = W.text(c, "Your private notepad. Only you can see these notes, and they sync across your devices. Save messages, links and files here.", 13, 400, T.textSecondary);
        d.setGravity(Gravity.CENTER);
        com.vulpine.at.stories.Lh.set(d, 18.85f);
        w.addView(d, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
        LinearLayout.LayoutParams p = W.lp(Math.min(T.dp(320), a.getResources().getDisplayMetrics().widthPixels - T.dp(32)), W.WC);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = T.dp(8);
        p.bottomMargin = T.dp(4);
        w.setLayoutParams(p);
        return w;
    }

    int maxW() {
        return Math.round((a.getResources().getDisplayMetrics().widthPixels - T.dp(32)) * .75f);
    }

    View note(Context c, JSONObject nb, JSONObject n) {
        String text = n.optString("text");
        boolean media = text.startsWith(Fmt.CLIP);
        FrameLayout wrap = new FrameLayout(c);
        wrap.setClipChildren(false);
        wrap.setTag("note-" + n.optString("id"));
        LinearLayout box = W.col(c);
        Fmt.Parsed p = Fmt.parse(text);
        if (!media) {
            box.setBackground(W.round(T.surface2, 16));
            box.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
        }
        if (p.pollId != null) {
            View pv = com.vulpine.at.polls.Polls.view(c, p.pollId, null, Math.min(T.dp(400), maxW() - T.dp(24)));
            box.addView(pv, W.lpm(W.WC, W.WC, 0, 2, 0, 2));
        } else {
            for (Fmt.Att att : p.atts) box.addView(attView(c, att), W.lpm(W.WC, W.WC, 0, box.getChildCount() > 0 ? 4 : 0, 0, 0));
            String body = media ? p.body : text;
            if (!body.trim().isEmpty()) {
                TextView tv = body(c, body);
                if (media) {
                    tv.setBackground(W.round(T.surface2, 16));
                    tv.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
                }
                box.addView(tv, W.lpm(W.WC, W.WC, 0, media && box.getChildCount() > 0 ? 4 : 0, 0, 0));
                tv.setOnLongClickListener(v -> {
                    noteMenu(v, nb, n);
                    return true;
                });
            }
        }
        wrap.addView(box, new FrameLayout.LayoutParams(W.WC, W.WC));
        if (n.optBoolean("pinned")) {
            FrameLayout b = new FrameLayout(c);
            b.setBackground(W.oval(T.accent));
            b.setElevation(T.dpf(2));
            ImageView pi = new ImageView(c);
            pi.setImageDrawable(Ic.get("nt.notebookscreen.pin16", 10, T.accentFg));
            b.addView(pi, W.flp(T.dp(10), T.dp(10), Gravity.CENTER));
            b.setContentDescription("Pinned");
            FrameLayout.LayoutParams bp = W.flp(T.dp(18), T.dp(18), Gravity.TOP | Gravity.END);
            bp.topMargin = -T.dp(6);
            bp.rightMargin = -T.dp(6);
            wrap.addView(b, bp);
        }
        box.setOnLongClickListener(v -> {
            noteMenu(v, nb, n);
            return true;
        });
        return wrap;
    }

    TextView body(Context c, String text) {
        TextView t = new TextView(c);
        T.text(t, 14, 400, T.text);
        t.setMaxWidth(maxW() - T.dp(24));
        com.vulpine.at.stories.Lh.set(t, 21);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setHighlightColor(0);
        t.setLinksClickable(true);
        CharSequence cs;
        try {
            com.vulpine.at.chat.Md.Out o = com.vulpine.at.chat.Md.render(text, t, new com.vulpine.at.chat.Md.Links() {
                public void url(String url, boolean ext) {
                    com.vulpine.at.home.Home.openLink(url);
                }

                public void mention(String handle) {
                    com.vulpine.at.people.Profile.open(a, handle);
                }
            });
            cs = o.sb;
        } catch (Throwable e) {
            cs = new SpannableStringBuilder(text);
        }
        t.setText(cs);
        return t;
    }

    static void lineHeight(TextView t, float dp) {
        int target = T.dp(dp);
        android.graphics.Paint.FontMetricsInt fm = t.getPaint().getFontMetricsInt();
        int natural = fm.descent - fm.ascent;
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setLineHeight(target);
        else t.setLineSpacing(Math.max(0, target - natural), 1f);
    }

    View attView(Context c, Fmt.Att att) {
        int mw = maxW();
        if (att.image() || att.gif()) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(W.round(T.surface2, 12));
            iv.setClipToOutline(true);
            iv.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            int w = att.w > 0 ? att.w : 4, h = att.h > 0 ? att.h : 3;
            int vw = Math.min(mw, T.dp(260));
            int vh = Math.max(T.dp(80), Math.min(T.dp(320), Math.round(vw * h / (float) w)));
            iv.setLayoutParams(W.lp(vw, vh));
            if (att.gif()) Img.anim(iv, att.url, T.dp(260), !T.power);
            else Img.into(iv, att.url, T.dp(520));
            iv.setOnClickListener(v -> NoteMedia.open(a, att));
            return iv;
        }
        if (att.video()) {
            FrameLayout f = new FrameLayout(c);
            f.setBackground(W.round(0xff000000, 12));
            f.setClipToOutline(true);
            f.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP));
            com.vulpine.at.stories.Media.frameInto(iv, att.url);
            FrameLayout play = new FrameLayout(c);
            play.setBackground(W.oval(0x8c000000));
            ImageView pi = new ImageView(c);
            pi.setImageDrawable(com.vulpine.at.ui.Svg.fill(20, 0xffffffff, "M8 5v14l11-7z"));
            play.addView(pi, W.flp(T.dp(20), T.dp(20), Gravity.CENTER));
            f.addView(play, W.flp(T.dp(44), T.dp(44), Gravity.CENTER));
            int vw = Math.min(mw, T.dp(260));
            int w = att.w > 0 ? att.w : 16, h = att.h > 0 ? att.h : 9;
            f.setLayoutParams(W.lp(vw, Math.max(T.dp(100), Math.min(T.dp(320), Math.round(vw * h / (float) w)))));
            f.setOnClickListener(v -> NoteMedia.open(a, att));
            return f;
        }
        LinearLayout chip = W.row(c);
        chip.setPadding(T.dp(10), T.dp(8), T.dp(14), T.dp(8));
        chip.setBackground(W.round(T.surface2, 12));
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(W.round(T.mixA(T.accent, .18f), 8));
        ImageView ii = new ImageView(c);
        ii.setImageDrawable(com.vulpine.at.ui.Svg.stroke(18, T.accent, 2, "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6"));
        ic.addView(ii, W.flp(T.dp(18), T.dp(18), Gravity.CENTER));
        chip.addView(ic, W.lp(T.dp(36), T.dp(36)));
        TextView nm = W.text(c, att.name, 14, 500, T.text);
        nm.setMaxWidth(mw - T.dp(80));
        W.ellipsize(nm);
        chip.addView(nm, W.lpm(W.WC, W.WC, 10, 0, 0, 0));
        chip.setOnClickListener(v -> NoteMedia.open(a, att));
        return chip;
    }

    void noteMenu(View anchor, JSONObject nb, JSONObject n) {
        String text = n.optString("text");
        boolean media = text.startsWith(Fmt.CLIP);
        Ov.Menu m = new Ov.Menu(a);
        if (!media) m.item("Edit", com.vulpine.at.ui.Svg.stroke(15, T.text, 1.8f, "M4 20h4L19 9l-4-4L4 16v4z"), false, () -> startEdit(n));
        m.item("Copy", com.vulpine.at.ui.Svg.stroke(15, T.text, 1.8f, "M8 8h11v12H8z", "M5 16V4h11"), false, () -> {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("note", text));
            Ov.toast("Copied");
        });
        boolean pinned = n.optBoolean("pinned");
        m.item(pinned ? "Unpin" : "Pin", pinned ? com.vulpine.at.ui.Svg.stroke(15, T.text, 1.8f, "M12 17v5", "M9 4h6l-1 7 3 3H7l3-3z", "M4 4l16 16") : com.vulpine.at.ui.Svg.stroke(15, T.text, 1.8f, "M12 17v5", "M9 4h6l-1 7 3 3H7l3-3z"), false, () -> {
            Notes.pinNote(n, !pinned);
            render(false);
        });
        m.item("Delete", com.vulpine.at.ui.Svg.stroke(15, T.danger, 1.8f, "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13"), true, () -> {
            if (n.optString("id").equals(editing)) cancelEdit();
            Notes.remove(nb, n.optString("id"));
            render(false);
        });
        int[] l = new int[2], h = new int[2];
        anchor.getLocationInWindow(l);
        a.host.getLocationInWindow(h);
        m.showAt(l[0] - h[0] + anchor.getWidth(), l[1] - h[1] + anchor.getHeight() / 2, true);
    }

    void startEdit(JSONObject n) {
        editing = n.optString("id");
        editBar.setVisibility(View.VISIBLE);
        input.setText(n.optString("text"));
        input.requestFocus();
        input.setSelection(input.length());
        W.showKb(input);
    }

    void cancelEdit() {
        editing = null;
        editBar.setVisibility(View.GONE);
        input.setText("");
    }

    void submit() {
        String t = input.getText().toString();
        if (t.trim().isEmpty()) return;
        JSONObject nb = nb();
        if (editing != null) {
            JSONArray m = Notes.msgs(nb);
            for (int i = 0; i < m.length(); i++) if (editing.equals(m.optJSONObject(i).optString("id"))) Notes.edit(m.optJSONObject(i), t);
            editing = null;
            editBar.setVisibility(View.GONE);
        } else Notes.add(nb, t);
        input.setText("");
        render(false);
    }

    List<JSONObject> pinnedNotes(JSONObject nb) {
        List<JSONObject> l = new ArrayList<>();
        JSONArray m = Notes.msgs(nb);
        for (int i = 0; i < m.length(); i++) if (m.optJSONObject(i) != null && m.optJSONObject(i).optBoolean("pinned")) l.add(m.optJSONObject(i));
        return l;
    }

    void renderPins(JSONObject nb) {
        List<JSONObject> l = pinnedNotes(nb);
        boolean was = pinBar.getVisibility() == View.VISIBLE;
        if (l.isEmpty()) {
            pinBar.setVisibility(View.GONE);
            if (was) padScroll();
            return;
        }
        if (pinIdx >= l.size()) pinIdx = 0;
        while (pinBar.getChildCount() > 2) pinBar.removeViewAt(2);
        if (l.size() > 1) {
            pinBar.addView(ib(a, "nt.notebookscreen.chev_l", 16, 32, "Previous pinned", v -> cyclePin(-1)));
            pinBar.addView(ib(a, "nt.notebookscreen.chev_r", 16, 32, "Next pinned", v -> cyclePin(1)));
        }
        ImageView un = ib(a, "nt.notebookscreen.x", 14, 32, "Unpin", v -> {
            List<JSONObject> cur = pinnedNotes(nb());
            if (pinIdx < cur.size()) {
                Notes.pinNote(cur.get(pinIdx), false);
                render(false);
            }
        });
        pinBar.addView(un);
        pinText.setText(Notes.preview(l.get(pinIdx).optString("text")));
        pinSub.setText(l.size() > 1 ? (pinIdx + 1) + "/" + l.size() + " - tap for next" : "");
        pinSub.setVisibility(l.size() > 1 ? View.VISIBLE : View.GONE);
        pinBar.setVisibility(View.VISIBLE);
        if (!was) pinBar.post(this::padScroll);
    }

    void cyclePin(int d) {
        List<JSONObject> l = pinnedNotes(nb());
        if (l.isEmpty()) return;
        pinIdx = ((pinIdx + d) % l.size() + l.size()) % l.size();
        renderPins(nb());
        View target = list.findViewWithTag("note-" + l.get(pinIdx).optString("id"));
        if (target == null) return;
        sv.smoothScrollTo(0, Math.max(0, target.getTop() - sv.getHeight() / 2 + target.getHeight() / 2));
        View box = ((ViewGroup) target).getChildAt(0);
        android.graphics.drawable.Drawable old = box.getBackground();
        box.setBackground(W.round(T.accentSoft, 16));
        box.postDelayed(() -> box.setBackground(old), 1200);
    }

    void attach() {
        com.vulpine.at.stories.Media.pick(a, new String[]{"*/*"}, false, (f, mime, name) -> {
            boolean pro = Session.s != null && (Session.s.optBoolean("pro") || Session.s.optBoolean("isPro"));
            long max = (pro ? 100L : 30L) * 1024 * 1024;
            if (f.length() > max) {
                Ov.toast("File too large (max " + (pro ? 100 : 30) + "MB)", true);
                return;
            }
            App.bg(() -> {
                Http.Resp r = Http.upload("/me/attachment", "file", f, name, mime == null ? "application/octet-stream" : mime, null);
                App.ui(() -> {
                    if (!r.ok()) {
                        Ov.toast("file_too_large".equals(r.error()) ? "File too large (max " + (pro ? 100 : 30) + "MB)" : "invalid_file_type".equals(r.error()) ? "This file type isn't supported" : "Upload failed", true);
                        return;
                    }
                    JSONObject j = r.obj();
                    String url = j.optString("url");
                    if (!url.startsWith("/uploads/") && !url.startsWith("https://") && !url.startsWith("http://")) return;
                    String line = "📎|" + j.optString("name", name).replace("|", "_") + "|" + j.optString("type", mime) + "|" + url;
                    String cur = input.getText().toString();
                    input.setText(cur.isEmpty() ? line : cur.replaceAll("\\s+$", "") + "\n" + line);
                    input.setSelection(input.length());
                    Ov.toast("Attached to note");
                });
            });
        });
    }

    void gifSheet() {
        NoteMedia.gifPicker(a, url -> {
            Editable e = input.getText();
            int at = Math.max(0, input.getSelectionStart());
            e.insert(at, "\n📎|GIF|image/gif|" + url + "\n");
            updateSend();
        });
    }
}
