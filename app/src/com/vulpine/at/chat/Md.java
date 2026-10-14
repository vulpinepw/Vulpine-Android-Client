package com.vulpine.at.chat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import com.vulpine.at.ui.T;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Md {
    public interface Links {
        void url(String url, boolean ext);

        void mention(String handle);
    }

    public static final class Out {
        public SpannableStringBuilder sb = new SpannableStringBuilder();
        public List<String> urls = new ArrayList<>();
        public List<String> gifs = new ArrayList<>();
        public List<String[]> embeds = new ArrayList<>();
        public List<Object[]> codes = new ArrayList<>();
    }

    static final Pattern GIF = Pattern.compile("(?i)^(https?://media\\d*\\.giphy\\.com/\\S+?\\.gif|/uploads/\\S+?\\.gif)$");
    static final Pattern EMBED = Pattern.compile("(?i)^https?://(?:www\\.)?vulpine\\.(?:pw|at)/(join|call)/([A-Za-z0-9_-]{2,64})/?$");

    public static class TypeSpan extends TypefaceSpan {
        final Typeface tf;

        public TypeSpan(Typeface tf) {
            super("sans-serif");
            this.tf = tf;
        }

        public void updateDrawState(TextPaint p) {
            p.setTypeface(tf);
        }

        public void updateMeasureState(TextPaint p) {
            p.setTypeface(tf);
        }
    }

    public static class CodeSpan extends ReplacementSpan {
        final int bg;

        CodeSpan(int bg) {
            this.bg = bg;
        }

        public int getSize(Paint p, CharSequence t, int s, int e, Paint.FontMetricsInt fm) {
            Paint q = new Paint(p);
            q.setTypeface(Typeface.MONOSPACE);
            q.setTextSize(p.getTextSize() * .92f);
            return Math.round(q.measureText(t, s, e) + T.dpf(10));
        }

        public void draw(Canvas c, CharSequence t, int s, int e, float x, int top, int y, int bottom, Paint p) {
            Paint q = new Paint(p);
            q.setTypeface(Typeface.MONOSPACE);
            q.setTextSize(p.getTextSize() * .92f);
            float w = q.measureText(t, s, e) + T.dpf(10);
            Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
            b.setColor(bg);
            Paint.FontMetrics fm = q.getFontMetrics();
            c.drawRoundRect(new RectF(x, y + fm.ascent - T.dpf(1), x + w, y + fm.descent + T.dpf(1)), T.dpf(4), T.dpf(4), b);
            c.drawText(t, s, e, x + T.dpf(5), y, q);
        }
    }

    public static class SpoilerSpan extends ClickableSpan {
        public boolean shown;
        final View host;

        SpoilerSpan(View host) {
            this.host = host;
        }

        public void onClick(View v) {
            shown = true;
            v.invalidate();
            if (v instanceof android.widget.TextView) ((android.widget.TextView) v).setText(((android.widget.TextView) v).getText());
        }

        public void updateDrawState(TextPaint p) {
            if (!shown) {
                p.setColor(0);
                p.bgColor = T.mixA(T.text, .7f);
            } else p.bgColor = T.mixA(T.text, .08f);
        }
    }

    static class QuoteSpan implements LeadingMarginSpan, LineBackgroundSpan {
        final int color;

        QuoteSpan(int color) {
            this.color = color;
        }

        public int getLeadingMargin(boolean first) {
            return T.dp(15);
        }

        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int base, int bottom, CharSequence t, int s, int e, boolean first, android.text.Layout l) {
            Paint.Style st = p.getStyle();
            int col = p.getColor();
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRect(x, top, x + dir * T.dp(3), bottom, p);
            p.setStyle(st);
            p.setColor(col);
        }

        public void drawBackground(Canvas c, Paint p, int l, int r, int top, int base, int bottom, CharSequence t, int s, int e, int n) {
        }
    }

    static class BulletSpan implements LeadingMarginSpan {
        final String mark;
        final int color;

        BulletSpan(String mark, int color) {
            this.mark = mark;
            this.color = color;
        }

        public int getLeadingMargin(boolean first) {
            return T.dp(20);
        }

        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int base, int bottom, CharSequence t, int s, int e, boolean first, android.text.Layout l) {
            if (!first) return;
            int col = p.getColor();
            p.setColor(color);
            c.drawText(mark, x, base, p);
            p.setColor(col);
        }
    }

    static class RuleSpan extends ReplacementSpan {
        public int getSize(Paint p, CharSequence t, int s, int e, Paint.FontMetricsInt fm) {
            return T.dp(2000);
        }

        public void draw(Canvas c, CharSequence t, int s, int e, float x, int top, int y, int bottom, Paint p) {
            Paint q = new Paint();
            q.setColor(T.border);
            float cy = (top + bottom) / 2f;
            c.drawRect(x, cy, c.getWidth(), cy + Math.max(1, T.dp(1)), q);
        }
    }

    public static void bio(android.widget.TextView t, String text) {
        Out o = render(text, t, new Links() {
            public void url(String url, boolean ext) {
                com.vulpine.at.MainActivity a = com.vulpine.at.MainActivity.cur;
                if (a != null) com.vulpine.at.chat.Links.open(a, url);
            }

            public void mention(String handle) {
                com.vulpine.at.MainActivity a = com.vulpine.at.MainActivity.cur;
                if (a != null) com.vulpine.at.people.Profile.open(a, handle);
            }
        });
        t.setText(o.sb);
        t.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        t.setHighlightColor(0);
    }

    public static Out render(String text, View host, Links links) {
        Out o = new Out();
        if (text == null) return o;
        String[] lines = text.split("\n", -1);
        boolean inCode = false;
        StringBuilder code = new StringBuilder();
        String codeLang = "";
        int num = 0;
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            if (inCode) {
                if (line.trim().equals("```")) {
                    inCode = false;
                    appendCode(o, code.toString(), codeLang);
                    code.setLength(0);
                    continue;
                }
                if (code.length() > 0) code.append('\n');
                code.append(line);
                continue;
            }
            Matcher cf = Pattern.compile("^```([A-Za-z0-9_+#.-]{0,20})\\s*$").matcher(line);
            if (cf.matches()) {
                inCode = true;
                codeLang = cf.group(1);
                continue;
            }
            if (o.sb.length() > 0) o.sb.append('\n');
            int start = o.sb.length();
            String trimmed = line.trim();
            if (GIF.matcher(trimmed).matches() && lines.length <= 3) {
                o.gifs.add(trimmed);
                continue;
            }
            Matcher em = EMBED.matcher(trimmed);
            if (em.matches()) o.embeds.add(new String[]{em.group(1), em.group(2), trimmed});
            if (trimmed.matches("^(-{3,}|\\*{3,}|_{3,}|\\* \\* \\*)$")) {
                o.sb.append(" ");
                o.sb.setSpan(new RuleSpan(), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            Matcher h = Pattern.compile("^(#{1,3}) (.*)$").matcher(line);
            if (h.matches()) {
                inline(o, h.group(2), host, links);
                int lv = h.group(1).length();
                o.sb.setSpan(new RelativeSizeSpan(lv == 1 ? 20f / 14f : lv == 2 ? 16f / 14f : 1f), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                o.sb.setSpan(new TypeSpan(lv == 3 ? T.sb : T.b), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            Matcher q = Pattern.compile("^(>+)\\s?(.*)$").matcher(line);
            if (q.matches()) {
                inline(o, q.group(2), host, links);
                int depth = Math.min(6, q.group(1).length());
                for (int d = 0; d < depth; d++) o.sb.setSpan(new QuoteSpan(T.accent), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                o.sb.setSpan(new ForegroundColorSpan(T.textSecondary), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            Matcher task = Pattern.compile("^\\s*[-*+] \\[( |x|X)\\] (.*)$").matcher(line);
            if (task.matches()) {
                boolean done = !task.group(1).equals(" ");
                inline(o, task.group(2), host, links);
                o.sb.setSpan(new BulletSpan(done ? "☑" : "☐", done ? T.accent : T.textSecondary), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (done) {
                    o.sb.setSpan(new StrikethroughSpan(), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    o.sb.setSpan(new ForegroundColorSpan(T.mixA(T.text, .65f)), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                continue;
            }
            Matcher bl = Pattern.compile("^\\s*[-*+] (.*)$").matcher(line);
            if (bl.matches()) {
                inline(o, bl.group(1), host, links);
                o.sb.setSpan(new BulletSpan("•", T.accent), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                num = 0;
                continue;
            }
            Matcher nl = Pattern.compile("^\\s*\\d+\\. (.*)$").matcher(line);
            if (nl.matches()) {
                num++;
                inline(o, nl.group(1), host, links);
                o.sb.setSpan(new BulletSpan(num + ".", T.accent), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            num = 0;
            if (line.matches("^:::(drop|center|right|left)( .*)?$") || line.equals(":::")) {
                Matcher dm = Pattern.compile("^:::drop (.*)$").matcher(line);
                if (dm.matches()) {
                    o.sb.append("▸ ");
                    o.sb.setSpan(new ForegroundColorSpan(T.accent), start, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int s2 = o.sb.length();
                    o.sb.append(dm.group(1));
                    o.sb.setSpan(new TypeSpan(T.sb), s2, o.sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (o.sb.length() > 0 && o.sb.charAt(o.sb.length() - 1) == '\n') o.sb.delete(o.sb.length() - 1, o.sb.length());
                continue;
            }
            if (line.startsWith("|") && line.endsWith("|") && line.length() > 1) {
                if (line.matches("^\\|[\\s:|-]+\\|$")) {
                    if (o.sb.length() > 0 && o.sb.charAt(o.sb.length() - 1) == '\n') o.sb.delete(o.sb.length() - 1, o.sb.length());
                    continue;
                }
                String[] cells = line.substring(1, line.length() - 1).split("\\|");
                for (int k = 0; k < cells.length; k++) {
                    if (k > 0) o.sb.append("  │  ");
                    inline(o, cells[k].trim(), host, links);
                }
                continue;
            }
            inline(o, line, host, links);
        }
        if (inCode) appendCode(o, code.toString(), codeLang);
        return o;
    }

    static void appendCode(Out o, String code, String lang) {
        if (o.sb.length() > 0 && o.sb.charAt(o.sb.length() - 1) != '\n') o.sb.append('\n');
        int s = o.sb.length();
        appendCode(o.sb, code, lang);
        o.codes.add(new Object[]{s, o.sb.length(), code, lang});
    }

    static void appendCode(SpannableStringBuilder sb, String code, String lang) {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
        int s = sb.length();
        if (!lang.isEmpty()) {
            sb.append(lang.toLowerCase()).append('\n');
            sb.setSpan(new ForegroundColorSpan(T.textMuted), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new RelativeSizeSpan(11f / 14f), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int cs = sb.length();
        sb.append(code);
        sb.setSpan(new TypeSpan(Typeface.MONOSPACE), cs, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(.86f), cs, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.BackgroundColorSpan(T.mixA(T.text, .06f)), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    static boolean openOk(String s, int i, int len) {
        if (i + len >= s.length()) return false;
        if (Character.isWhitespace(s.charAt(i + len))) return false;
        return i == 0 || !wordChar(s.charAt(i - 1));
    }

    static boolean closeOk(String s, int i, int len) {
        if (i == 0 || Character.isWhitespace(s.charAt(i - 1))) return false;
        int a = i + len;
        return a >= s.length() || !wordChar(s.charAt(a));
    }

    static final String[] DELIMS = {"**", "__", "~~", "||", "*", "_"};
    static final Pattern URL = Pattern.compile("^https?://[^\\s<>\"'`]+");
    static final Pattern LINK = Pattern.compile("^\\[([^\\]]{1,80})\\]\\((https?://[^)\\s]{2,200})\\)");
    static final Pattern MENTION = Pattern.compile("^@([a-z0-9_]{3,20})");
    static final Pattern EMO = Pattern.compile("^:([a-z0-9_]{2,32}):");

    public static void inline(Out o, String s, View host, Links links) {
        SpannableStringBuilder sb = o.sb;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            String rest = s.substring(i);
            if (c == '`') {
                int e = s.indexOf('`', i + 1);
                if (e > i + 1) {
                    int st = sb.length();
                    sb.append(s, i + 1, e);
                    sb.setSpan(new CodeSpan(T.dark ? 0x1fffffff : 0x14000000), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = e + 1;
                    continue;
                }
            }
            if (c == '[') {
                Matcher m = LINK.matcher(rest);
                if (m.find()) {
                    int st = sb.length();
                    sb.append(m.group(1));
                    final String url = m.group(2);
                    sb.setSpan(new ClickableSpan() {
                        public void onClick(View v) {
                            if (links != null) links.url(url, true);
                        }

                        public void updateDrawState(TextPaint p) {
                            p.setColor(T.accent);
                            p.setUnderlineText(true);
                        }
                    }, st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    o.urls.add(url);
                    i += m.end();
                    continue;
                }
            }
            if ((c == 'h' || c == 'H') && (i == 0 || !wordChar(s.charAt(i - 1)))) {
                Matcher m = URL.matcher(rest);
                if (m.find()) {
                    String u = m.group().replaceAll("[.,;:!?\"')\\]}>]+$", "");
                    int st = sb.length();
                    sb.append(u);
                    final String url = u;
                    sb.setSpan(new ClickableSpan() {
                        public void onClick(View v) {
                            if (links != null) links.url(url, true);
                        }

                        public void updateDrawState(TextPaint p) {
                            p.setColor(T.text);
                            p.setUnderlineText(true);
                            p.setTypeface(T.b);
                        }
                    }, st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    o.urls.add(u);
                    i += u.length();
                    continue;
                }
            }
            if (c == '@' && (i == 0 || !wordChar(s.charAt(i - 1)))) {
                Matcher m = MENTION.matcher(rest);
                if (m.find()) {
                    int st = sb.length();
                    sb.append(m.group());
                    final String hd = m.group(1);
                    sb.setSpan(new ClickableSpan() {
                        public void onClick(View v) {
                            if (links != null) links.mention(hd);
                        }

                        public void updateDrawState(TextPaint p) {
                            p.setUnderlineText(true);
                            p.setTypeface(T.b);
                        }
                    }, st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i += m.end();
                    continue;
                }
            }
            if (c == ':') {
                Matcher m = EMO.matcher(rest);
                if (m.find() && Emoji.custom(m.group(1)) != null) {
                    int st = sb.length();
                    sb.append(m.group());
                    sb.setSpan(new Emoji.ImgSpan(host, Emoji.custom(m.group(1)), 1.45f), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i += m.end();
                    continue;
                }
            }
            boolean matched = false;
            for (String d : DELIMS) {
                if (!rest.startsWith(d) || !openOk(s, i, d.length())) continue;
                int from = i + d.length();
                int e = from;
                int found = -1;
                while (true) {
                    e = s.indexOf(d, e);
                    if (e < 0) break;
                    if (e > from && closeOk(s, e, d.length())) {
                        found = e;
                        break;
                    }
                    e++;
                }
                if (found < 0) continue;
                int st = sb.length();
                Out sub = new Out();
                sub.sb = sb;
                inline(sub, s.substring(from, found), host, links);
                o.urls.addAll(sub.urls);
                Object span;
                switch (d) {
                    case "**": span = new TypeSpan(T.b); break;
                    case "__": span = new UnderlineSpan(); break;
                    case "~~": span = new StrikethroughSpan(); break;
                    case "||": span = new SpoilerSpan(host); break;
                    default: span = new StyleSpan(Typeface.ITALIC);
                }
                sb.setSpan(span, st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = found + d.length();
                matched = true;
                break;
            }
            if (matched) continue;
            sb.append(c);
            i++;
        }
    }

    static final Pattern JUMBO_STRIP = Pattern.compile("[\\s\\uFE0F\\u200D]");

    public static int jumboCount(String t) {
        if (t == null) return 0;
        String s = t.trim();
        if (s.isEmpty() || s.length() > 60) return 0;
        int n = 0;
        java.text.BreakIterator bi = java.text.BreakIterator.getCharacterInstance();
        bi.setText(s);
        int st = bi.first();
        for (int e = bi.next(); e != java.text.BreakIterator.DONE; st = e, e = bi.next()) {
            String g = s.substring(st, e);
            if (g.trim().isEmpty()) continue;
            if (!isPictographic(g)) return 0;
            n++;
            if (n > 6) return 0;
        }
        return n;
    }

    static boolean isPictographic(String g) {
        int cp = g.codePointAt(0);
        if (cp == 0x2764 || cp == 0x263A || cp == 0x2639 || cp == 0x2B50 || cp == 0x2728) return true;
        if (cp >= 0x1F000 && cp <= 0x1FAFF) return true;
        if (cp >= 0x2600 && cp <= 0x27BF) return true;
        if (cp >= 0x2190 && cp <= 0x21FF) return g.length() > 1;
        if (cp == 0x00A9 || cp == 0x00AE || cp == 0x203C || cp == 0x2049 || cp == 0x2122) return g.length() > 1;
        if ((cp >= '0' && cp <= '9') || cp == '#' || cp == '*') return g.contains("⃣");
        return false;
    }
}
