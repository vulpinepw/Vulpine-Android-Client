package com.vulpine.at.notes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Rte {
    interface Mark {
    }

    public static class Ed extends EditText {
        Runnable onSel;

        public Ed(Context c) {
            super(c);
        }

        @Override
        protected void onSelectionChanged(int a, int b) {
            super.onSelectionChanged(a, b);
            if (onSel != null) onSel.run();
        }

        @Override
        protected void onFocusChanged(boolean f, int d, android.graphics.Rect r) {
            super.onFocusChanged(f, d, r);
            if (onSel != null) onSel.run();
        }
    }

    static final class Hide extends ReplacementSpan implements Mark {
        public int getSize(Paint p, CharSequence t, int a, int b, Paint.FontMetricsInt fm) {
            return 0;
        }

        public void draw(Canvas c, CharSequence t, int a, int b, float x, int top, int y, int bot, Paint p) {
        }
    }

    static final class Face extends MetricAffectingSpan implements Mark {
        final Typeface tf;
        final float skew, size;

        Face(Typeface tf, float skew, float size) {
            this.tf = tf;
            this.skew = skew;
            this.size = size;
        }

        void ap(TextPaint p) {
            if (tf != null) p.setTypeface(tf);
            if (skew != 0) p.setTextSkewX(skew);
            if (size > 0) p.setTextSize(size);
        }

        public void updateDrawState(TextPaint p) {
            ap(p);
        }

        public void updateMeasureState(TextPaint p) {
            ap(p);
        }
    }

    static final class U extends UnderlineSpan implements Mark {
    }

    static final class S extends StrikethroughSpan implements Mark {
    }

    static final class Col extends ForegroundColorSpan implements Mark {
        Col(int c) {
            super(c);
        }
    }

    static final class Box extends ReplacementSpan implements Mark {
        final int bg;
        final boolean mono;

        Box(int bg, boolean mono) {
            this.bg = bg;
            this.mono = mono;
        }

        TextPaint tp(Paint p) {
            TextPaint t = new TextPaint(p);
            if (mono) {
                t.setTypeface(Typeface.MONOSPACE);
                t.setTextSize(p.getTextSize() * .92f);
            }
            return t;
        }

        public int getSize(Paint p, CharSequence t, int a, int b, Paint.FontMetricsInt fm) {
            if (fm != null) p.getFontMetricsInt(fm);
            return Math.round(tp(p).measureText(t, a, b) + (mono ? T.dpf(10) : 0));
        }

        public void draw(Canvas c, CharSequence t, int a, int b, float x, int top, int y, int bot, Paint p) {
            TextPaint q = tp(p);
            float w = q.measureText(t, a, b) + (mono ? T.dpf(10) : 0);
            Paint.FontMetrics fm = q.getFontMetrics();
            int col = p.getColor();
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setColor(bg);
            float r = T.dpf(4), pad = mono ? T.dpf(1) : 0;
            c.drawRoundRect(x, y + fm.ascent - pad, x + w, y + fm.descent + pad, r, r, g);
            q.setColor(col);
            c.drawText(t, a, b, x + (mono ? T.dpf(5) : 0), y, q);
        }
    }

    static final class Rail implements LeadingMarginSpan, Mark {
        public int getLeadingMargin(boolean first) {
            return T.dp(13);
        }

        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int base, int bottom, CharSequence t, int start, int end, boolean first, android.text.Layout l) {
            int col = p.getColor();
            Paint.Style st = p.getStyle();
            p.setColor(T.accent);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(x, top, x + T.dp(3), bottom, p);
            p.setColor(col);
            p.setStyle(st);
        }
    }

    static final Pattern[] INLINE = {
            Pattern.compile("(\\[)([^\\]\\n]{1,80})(\\]\\((https?://[^\\s)]{2,200})\\))"),
            Pattern.compile("(?<![\\p{L}\\p{N}*])(\\*\\*)(?=\\S)(.+?)(?<=\\S)(\\*\\*)(?![\\p{L}\\p{N}])"),
            Pattern.compile("(?<![\\p{L}\\p{N}_])(__)(?=\\S)(.+?)(?<=\\S)(__)(?![\\p{L}\\p{N}])"),
            Pattern.compile("(?<![\\p{L}\\p{N}*])(\\*)(?=[^\\s*])(.+?)(?<=[^\\s*])(\\*)(?![\\p{L}\\p{N}*])"),
            Pattern.compile("(?<![\\p{L}\\p{N}_])(_)(?=[^\\s_])(.+?)(?<=[^\\s_])(_)(?![\\p{L}\\p{N}_])"),
            Pattern.compile("(?<![\\p{L}\\p{N}~])(~~)(?=\\S)(.+?)(?<=\\S)(~~)(?![\\p{L}\\p{N}])"),
            Pattern.compile("(`)([^`\\n]+)(`)"),
            Pattern.compile("(\\|\\|)(?=\\S)(.+?)(?<=\\S)(\\|\\|)")
    };
    static final Pattern URL = Pattern.compile("https?://\\S+");
    static final Pattern QUOTE = Pattern.compile("(?m)^(> ?)(.*)$");

    public static void attach(EditText e) {
        if (e instanceof Ed) ((Ed) e).onSel = () -> {
            if (BaseInputConnection.getComposingSpanStart(e.getText()) < 0) restyle(e);
            selBar(e);
        };
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                if (BaseInputConnection.getComposingSpanStart(s) >= 0) {
                    e.removeCallbacks(rest(e));
                    e.postDelayed(rest(e), 150);
                    return;
                }
                restyle(e);
            }
        });
    }

    static final java.util.Map<EditText, Runnable> restyle = new java.util.WeakHashMap<>();
    static final java.util.Map<EditText, String> lastSig = new java.util.WeakHashMap<>();

    static Runnable rest(EditText e) {
        Runnable r = restyle.get(e);
        if (r == null) {
            r = () -> {
                Editable s = e.getText();
                if (BaseInputConnection.getComposingSpanStart(s) < 0) restyle(e);
            };
            restyle.put(e, r);
        }
        return r;
    }

    static void restyle(EditText e) {
        Editable s = e.getText();
        int a = e.getSelectionStart(), b = e.getSelectionEnd();
        boolean f = e.hasFocus();
        String sig = s.toString() + "\u0000" + (a == b ? a : -1) + f + T.dark + T.accent;
        if (sig.equals(lastSig.get(e))) return;
        lastSig.put(e, sig);
        style(s, a == b ? a : -1, f);
    }

    public static void style(Editable s) {
        style(s, -1, false);
    }

    static boolean overlaps(boolean[] used, int a, int b) {
        for (int i = a; i < b; i++) if (used[i]) return true;
        return false;
    }

    public static void style(Editable s, int caret, boolean focused) {
        for (Mark m : s.getSpans(0, s.length(), Mark.class)) s.removeSpan(m);
        String t = s.toString();
        int n = t.length();
        int dim = T.mixA(T.text, .5f);
        boolean[] used = new boolean[n + 1];
        Matcher um = URL.matcher(t);
        while (um.find()) {
            boolean inLink = um.start() >= 2 && t.charAt(um.start() - 1) == '(' && t.charAt(um.start() - 2) == ']';
            if (!inLink) for (int i = um.start(); i < um.end(); i++) used[i] = true;
        }
        for (int k : new int[]{0, 6, 1, 2, 3, 4, 5, 7}) {
            Matcher m = INLINE[k].matcher(t);
            while (m.find()) {
                int a = m.start(1), b = m.end(3);
                if (overlaps(used, a, b) && k != 0) continue;
                boolean raw = caret > a && caret < b;
                int c0 = m.start(2), c1 = m.end(2);
                if (k == 0 || k == 6) for (int i = a; i < b; i++) used[i] = true;
                else {
                    for (int i = a; i < m.end(1); i++) used[i] = true;
                    for (int i = m.start(3); i < b; i++) used[i] = true;
                }
                if (raw) {
                    s.setSpan(new Col(dim), a, m.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    s.setSpan(new Col(dim), m.start(3), b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    s.setSpan(new Hide(), a, m.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    s.setSpan(new Hide(), m.start(3), b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                switch (k) {
                    case 0:
                        s.setSpan(new Col(T.accent), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        s.setSpan(new U(), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case 1:
                        s.setSpan(new Face(T.b, 0, 0), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case 2:
                        s.setSpan(new U(), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case 3:
                    case 4:
                        s.setSpan(new Face(null, -.2f, 0), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case 5:
                        s.setSpan(new S(), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case 6:
                        if (raw) s.setSpan(new Face(Typeface.MONOSPACE, 0, 0), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        else s.setSpan(new Box(T.dark ? 0x1fffffff : 0x14000000, true), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    default:
                        if (raw || c1 - c0 > 40 || t.substring(c0, c1).indexOf('\n') >= 0) s.setSpan(new SpBg(T.mixA(T.text, .18f)), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        else s.setSpan(new Box(T.mixA(T.text, .18f), false), c0, c1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        Matcher q = QUOTE.matcher(t);
        if (q.find() && q.start() == 0) {
            s.setSpan(new Col(dim), q.start(1), q.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new Rail(), 0, n, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            s.setSpan(new Col(T.textSecondary), q.end(1), n, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        }
    }

    static final class SpBg extends android.text.style.BackgroundColorSpan implements Mark {
        SpBg(int c) {
            super(c);
        }
    }

    static final java.util.Map<EditText, LinearLayout> bars = new java.util.WeakHashMap<>();

    static void hideBar(EditText e) {
        LinearLayout p = bars.remove(e);
        if (p != null && p.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) p.getParent()).removeView(p);
    }

    static void selBar(EditText e) {
        Runnable r = selR.get(e);
        if (r == null) {
            r = () -> showBar(e);
            selR.put(e, r);
        }
        e.removeCallbacks(r);
        e.postDelayed(r, 120);
    }

    static final java.util.Map<EditText, Runnable> selR = new java.util.WeakHashMap<>();

    static void showBar(EditText e) {
        int a = e.getSelectionStart(), b = e.getSelectionEnd();
        if (!e.hasFocus() || a == b || a < 0 || e.getLayout() == null || BaseInputConnection.getComposingSpanStart(e.getText()) >= 0 || inCode(e.getText().toString(), Math.min(a, b))) {
            hideBar(e);
            return;
        }
        Context c = e.getContext();
        FrameLayout host = com.vulpine.at.ui.Ov.host();
        if (host == null) return;
        LinearLayout bar = bars.get(e);
        boolean fresh = bar == null || bar.getParent() == null;
        if (bar == null) {
            bar = W.row(c);
            bar.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
            bar.setBackground(W.round(T.glassBgStrong, 12, T.glassStroke, .5f));
            bar.setElevation(T.dpf(6));
            bar.setTranslationZ(T.dpf(16));
            bar.setClickable(true);
            bar.setContentDescription("Format selection");
            String[][] it = {{"B", "Bold", "**"}, {"I", "Italic", "*"}, {"U", "Underline", "__"}, {"S", "Strikethrough", "~~"}, {"••", "Spoiler", "||"}, {"</>", "Code", "`"}, {"🔗", "Link", ""}};
            for (String[] x : it) {
                TextView btn = W.text(c, x[0], 13, 700, T.text);
                btn.setGravity(Gravity.CENTER);
                btn.setIncludeFontPadding(false);
                btn.setMinWidth(T.dp(40));
                btn.setPadding(T.dp(8), 0, T.dp(8), 0);
                if ("Italic".equals(x[1])) btn.setTypeface(T.b, Typeface.ITALIC);
                if ("Underline".equals(x[1])) btn.setPaintFlags(btn.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                if ("Strikethrough".equals(x[1])) btn.setPaintFlags(btn.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                if ("Code".equals(x[1])) btn.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                btn.setContentDescription(x[1]);
                btn.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 8));
                btn.setOnClickListener(v -> {
                    hideBar(e);
                    if (x[2].isEmpty()) link(e);
                    else wrap(e, x[2]);
                });
                LinearLayout.LayoutParams lp = W.lp(W.WC, T.dp(40));
                if (bar.getChildCount() > 0) lp.leftMargin = T.dp(2);
                bar.addView(btn, lp);
            }
            bars.put(e, bar);
        }
        android.text.Layout L = e.getLayout();
        int s0 = Math.min(a, b), s1 = Math.max(a, b);
        int l0 = L.getLineForOffset(s0), l1 = L.getLineForOffset(s1);
        float x0 = l0 == l1 ? L.getPrimaryHorizontal(s0) : L.getLineLeft(l1), x1 = l0 == l1 ? L.getPrimaryHorizontal(s1) : L.getLineRight(l1);
        int bottom = L.getLineBottom(l1) - e.getScrollY() + e.getTotalPaddingTop();
        int[] loc = new int[2], hl = new int[2];
        e.getLocationInWindow(loc);
        host.getLocationInWindow(hl);
        bar.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int bw = bar.getMeasuredWidth(), bh = bar.getMeasuredHeight();
        int sw = host.getWidth(), sh = host.getHeight();
        float cx = loc[0] - hl[0] + e.getTotalPaddingLeft() + (x0 + x1) / 2f;
        int x = Math.round(Math.max(T.dp(8), Math.min(sw - bw - T.dp(8), cx - bw / 2f)));
        int y = Math.min(loc[1] - hl[1] + bottom + T.dp(10), sh - bh - T.dp(8));
        if (fresh) host.addView(bar, W.flp(W.WC, W.WC, Gravity.TOP | Gravity.START));
        bar.setTranslationX(x);
        bar.setTranslationY(y);
        if (fresh && !T.lowfx) {
            bar.setAlpha(0);
            bar.setScaleX(.96f);
            bar.setScaleY(.96f);
            bar.animate().alpha(1).scaleX(1).scaleY(1).setDuration(160).setInterpolator(T.POP).start();
        }
    }

    static boolean inCode(String t, int i) {
        int n = 0;
        for (int k = 0; k < i && k < t.length(); k++) if (t.startsWith("```", k)) {
            n++;
            k += 2;
        }
        return n % 2 == 1;
    }

    public static void wrap(EditText e, String mk) {
        Editable s = e.getText();
        int a = Math.max(0, Math.min(e.getSelectionStart(), e.getSelectionEnd())), b = Math.max(e.getSelectionStart(), e.getSelectionEnd());
        if (a < 0) a = b = s.length();
        int n = mk.length();
        if (a >= n && b + n <= s.length() && s.subSequence(a - n, a).toString().equals(mk) && s.subSequence(b, b + n).toString().equals(mk)) {
            s.delete(b, b + n);
            s.delete(a - n, a);
            e.setSelection(a - n, b - n);
            return;
        }
        String sel = s.subSequence(a, b).toString();
        if (sel.length() >= 2 * n && sel.startsWith(mk) && sel.endsWith(mk)) {
            s.replace(a, b, sel.substring(n, sel.length() - n));
            e.setSelection(a, b - 2 * n);
            return;
        }
        s.replace(a, b, mk + sel + mk);
        if (a == b) e.setSelection(a + n);
        else e.setSelection(a + n, b + n);
    }

    static boolean safeUrl(String u) {
        return u != null && u.matches("(?i)^https?://\\S{1,200}$") && u.length() >= 2 && u.length() <= 200;
    }

    public static void link(EditText e) {
        Editable s = e.getText();
        int a = Math.max(0, Math.min(e.getSelectionStart(), e.getSelectionEnd())), b = Math.max(0, Math.max(e.getSelectionStart(), e.getSelectionEnd()));
        String sel = s.subSequence(a, b).toString().trim();
        if (safeUrl(sel)) {
            s.replace(a, b, "[" + sel.substring(0, Math.min(80, sel.length())) + "](" + sel + ")");
            return;
        }
        Ov.Sheet sh = new Ov.Sheet(e.getContext(), false);
        LinearLayout r = W.row(e.getContext());
        EditText in = new EditText(e.getContext());
        in.setHint("https://example.com");
        in.setHintTextColor(T.textMuted);
        T.text(in, 14, 400, T.text);
        in.setSingleLine(true);
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        in.setBackground(W.round(T.surface2, 8));
        in.setPadding(T.dp(10), 0, T.dp(10), 0);
        in.setContentDescription("Link URL");
        r.addView(in, W.lp(0, T.dp(36), 1));
        Runnable ok = () -> {
            String u = in.getText().toString().trim();
            if (!safeUrl(u)) {
                Ov.toast("Invalid link", true);
                return;
            }
            sh.close();
            String label = sel.isEmpty() ? "label" : sel.replace("]", "").replace("[", "");
            if (label.length() > 80) label = label.substring(0, 80);
            s.replace(a, b, "[" + label + "](" + u + ")");
        };
        TextView go = W.btn(e.getContext(), "OK", true, v -> ok.run());
        go.setMinHeight(T.dp(30));
        r.addView(go, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        in.setOnEditorActionListener((v, id, ev) -> {
            ok.run();
            return true;
        });
        sh.body.addView(r, W.lp(W.MP, W.WC));
        sh.show();
        in.post(() -> W.showKb(in));
    }

    static int lineStart(CharSequence s, int i) {
        while (i > 0 && s.charAt(i - 1) != '\n') i--;
        return i;
    }

    static int lineEnd(CharSequence s, int i) {
        while (i < s.length() && s.charAt(i) != '\n') i++;
        return i;
    }

    public static void prefix(EditText e, String p) {
        Editable s = e.getText();
        int a = Math.max(0, Math.min(e.getSelectionStart(), e.getSelectionEnd())), b = Math.max(0, Math.max(e.getSelectionStart(), e.getSelectionEnd()));
        int ls = lineStart(s, a), le = lineEnd(s, b);
        String[] lines = s.subSequence(ls, le).toString().split("\n", -1);
        boolean all = true;
        for (String l : lines) if (!l.startsWith(p)) all = false;
        StringBuilder o = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].replaceFirst("^(#{1,3} |> |- \\[[ xX]\\] |- |1\\. )", "");
            if (i > 0) o.append('\n');
            o.append(all ? l : p + l);
        }
        s.replace(ls, le, o);
        e.setSelection(Math.min(s.length(), ls + o.length()));
    }

    public static void block(EditText e, String open, String close) {
        Editable s = e.getText();
        int a = Math.max(0, Math.min(e.getSelectionStart(), e.getSelectionEnd())), b = Math.max(0, Math.max(e.getSelectionStart(), e.getSelectionEnd()));
        int ls = lineStart(s, a), le = lineEnd(s, b);
        String body = s.subSequence(ls, le).toString();
        String pre = ls > 0 && s.charAt(ls - 1) != '\n' ? "\n" : "";
        String ins = pre + open + "\n" + body + "\n" + close;
        s.replace(ls, le, ins);
        e.setSelection(Math.min(s.length(), ls + pre.length() + open.length() + 1 + body.length()));
    }

    public static void insert(EditText e, String text) {
        Editable s = e.getText();
        int a = Math.max(0, Math.min(e.getSelectionStart(), e.getSelectionEnd())), b = Math.max(0, Math.max(e.getSelectionStart(), e.getSelectionEnd()));
        String pre = a > 0 && s.charAt(a - 1) != '\n' ? "\n" : "";
        s.replace(a, b, pre + text);
        e.setSelection(Math.min(s.length(), a + pre.length() + text.length()));
    }

    static void unalign(EditText e) {
        Editable s = e.getText();
        int a = Math.max(0, e.getSelectionStart());
        String t = s.toString();
        int open = -1;
        for (int i = lineStart(t, a); ; ) {
            String line = t.substring(i, lineEnd(t, i));
            if (line.equals(":::center") || line.equals(":::right")) {
                open = i;
                break;
            }
            if (i == 0) break;
            i = lineStart(t, i - 1);
        }
        if (open < 0) return;
        int close = t.indexOf("\n:::", lineEnd(t, open));
        while (close >= 0 && lineEnd(t, close + 1) != close + 4) close = t.indexOf("\n:::", close + 1);
        if (close < 0) return;
        s.delete(close, close + 4);
        s.delete(open, Math.min(s.length(), lineEnd(t, open) + 1));
    }

    public static View toolbar(Context c, EditText e) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout r = W.row(c);
        r.setPadding(T.dp(8), T.dp(6), T.dp(8), T.dp(6));
        Object[][] items = {
                {"B", "Bold", (Runnable) () -> wrap(e, "**")},
                {"I", "Italic", (Runnable) () -> wrap(e, "*")},
                {"U", "Underline", (Runnable) () -> wrap(e, "__")},
                {"S", "Strikethrough", (Runnable) () -> wrap(e, "~~")},
                {"</>", "Inline code", (Runnable) () -> wrap(e, "`")},
                {"🔗", "Link", (Runnable) () -> link(e)},
                {"H1", "Heading 1", (Runnable) () -> prefix(e, "# ")},
                {"H2", "Heading 2", (Runnable) () -> prefix(e, "## ")},
                {"H3", "Heading 3", (Runnable) () -> prefix(e, "### ")},
                {"•", "Bullet list", (Runnable) () -> prefix(e, "- ")},
                {"1.", "Numbered list", (Runnable) () -> prefix(e, "1. ")},
                {"✓", "Checklist", (Runnable) () -> prefix(e, "- [ ] ")},
                {"›", "Quote", (Runnable) () -> prefix(e, "> ")},
                {"{ }", "Code block", (Runnable) () -> block(e, "```", "```")},
                {"⇤", "Align left", (Runnable) () -> unalign(e)},
                {"≡", "Align center", (Runnable) () -> block(e, ":::center", ":::")},
                {"⇥", "Align right", (Runnable) () -> block(e, ":::right", ":::")},
                {"▸", "Drop-down quote", (Runnable) () -> block(e, ":::drop Title", ":::")},
                {"▦", "Table", (Runnable) () -> insert(e, "| Header 1 | Header 2 |\n| --- | --- |\n|  |  |")},
                {"—", "Divider", (Runnable) () -> insert(e, "---\n")}
        };
        for (Object[] it : items) {
            TextView b = W.text(c, (String) it[0], 12, 600, T.textSecondary);
            b.setGravity(Gravity.CENTER);
            b.setMinWidth(T.dp(28));
            b.setPadding(T.dp(7), 0, T.dp(7), 0);
            String lab = (String) it[0];
            if ("I".equals(lab)) b.setTypeface(Typeface.create(T.sb, Typeface.ITALIC));
            if ("U".equals(lab)) b.setPaintFlags(b.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            if ("S".equals(lab)) b.setPaintFlags(b.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            if ("B".equals(lab)) b.setTypeface(T.b);
            b.setContentDescription((String) it[1]);
            b.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 8));
            Runnable run = (Runnable) it[2];
            b.setOnClickListener(v -> run.run());
            LinearLayout.LayoutParams p = W.lp(W.WC, T.dp(28));
            if (r.getChildCount() > 0) p.leftMargin = T.dp(4);
            r.addView(b, p);
        }
        hs.addView(r);
        hs.setBackground(W.round(T.surface2, 12));
        hs.setElevation(T.dpf(2));
        return hs;
    }
}
