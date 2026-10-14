package com.vulpine.at.ui;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Fmt {
    public static final String CLIP = "📎|";

    public static String listTime(long at) {
        if (at <= 0) return "";
        Calendar now = Calendar.getInstance(), t = Calendar.getInstance();
        t.setTimeInMillis(at);
        Date d = new Date(at);
        if (now.get(Calendar.YEAR) == t.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR))
            return android.text.format.DateFormat.getTimeFormat(com.vulpine.at.App.ctx).format(d);
        long diff = System.currentTimeMillis() - at;
        if (diff >= 0 && diff < 6.5 * 86400000L) return new SimpleDateFormat("EEE", Locale.getDefault()).format(d);
        if (now.get(Calendar.YEAR) == t.get(Calendar.YEAR)) return android.text.format.DateUtils.formatDateTime(com.vulpine.at.App.ctx, at, android.text.format.DateUtils.FORMAT_SHOW_DATE | android.text.format.DateUtils.FORMAT_NO_YEAR | android.text.format.DateUtils.FORMAT_ABBREV_MONTH);
        return DateFormat.getDateInstance(DateFormat.SHORT).format(d);
    }

    public static String time(long at) {
        boolean h24 = android.text.format.DateFormat.is24HourFormat(com.vulpine.at.App.ctx);
        return new SimpleDateFormat(h24 ? "HH:mm" : "hh:mm a", Locale.getDefault()).format(new Date(at));
    }

    public static String fullDate(long at) {
        return android.text.format.DateUtils.formatDateTime(com.vulpine.at.App.ctx, at, android.text.format.DateUtils.FORMAT_SHOW_DATE | android.text.format.DateUtils.FORMAT_SHOW_YEAR);
    }

    public static String mimeFromName(String n) {
        String e = n.toLowerCase();
        int q = e.indexOf('?');
        if (q >= 0) e = e.substring(0, q);
        int h = e.indexOf('#');
        if (h >= 0) e = e.substring(0, h);
        e = e.substring(e.lastIndexOf('.') + 1);
        switch (e) {
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            case "avif": return "image/avif";
            case "mp4": return "video/mp4";
            case "webm": return "video/webm";
            case "mov": return "video/quicktime";
            case "mkv": return "video/x-matroska";
            case "mp3": return "audio/mpeg";
            case "m4a": return "audio/mp4";
            case "aac": return "audio/aac";
            case "ogg": case "oga": case "opus": return "audio/ogg";
            case "wav": return "audio/wav";
            case "flac": return "audio/flac";
            case "pdf": return "application/pdf";
            case "txt": return "text/plain";
        }
        return "application/octet-stream";
    }

    public static final class Att {
        public String name, mime, url, caption;
        public int w, h;
        public boolean car;

        public boolean image() {
            return mime.startsWith("image/");
        }

        public boolean video() {
            return mime.startsWith("video/");
        }

        public boolean audio() {
            return mime.startsWith("audio/");
        }

        public boolean gif() {
            return "image/gif".equals(mime);
        }

        public boolean voice() {
            return audio() && (name.toLowerCase().startsWith("voice") || mime.contains("webm") || mime.contains("ogg") || mime.contains("opus"));
        }

        public boolean media() {
            return image() || video();
        }
    }

    static final Pattern DIMS = Pattern.compile("#(?:[^#]*&)?vw=(\\d{1,5})&vh=(\\d{1,5})");

    public static Att att(String line) {
        if (line == null || !line.startsWith(CLIP)) return null;
        String[] p = line.substring(3).split("\\|", -1);
        if (p.length < 3) return null;
        StringBuilder u = new StringBuilder(p[2]);
        for (int i = 3; i < p.length; i++) u.append('|').append(p[i]);
        String url = u.toString();
        if (!url.matches("(?is)^(https?://|/|file:/).*")) return null;
        Att a = new Att();
        a.name = p[0];
        a.mime = p[1].trim();
        a.url = url;
        if (a.mime.isEmpty() || "application/octet-stream".equals(a.mime)) a.mime = mimeFromName(url);
        if ("video/webm".equals(a.mime) && a.name.toLowerCase().startsWith("voice")) a.mime = "audio/webm";
        Matcher m = DIMS.matcher(url);
        if (m.find()) {
            a.w = Integer.parseInt(m.group(1));
            a.h = Integer.parseInt(m.group(2));
        }
        a.car = url.matches("(?s).*[#&]vcar=1.*");
        return a;
    }

    public static final class Parsed {
        public String replyAuthor, replySnippet, replyId;
        public String fwdFrom;
        public boolean forwarded;
        public java.util.List<Att> atts = new java.util.ArrayList<>();
        public String body = "";
        public String pollId;
        public boolean carousel;
    }

    static final Pattern FWD = Pattern.compile("^» Forwarded(?: from ([^\\n]{1,90}))?\\n([\\s\\S]*)$");
    static final Pattern RID = Pattern.compile("^#([A-Za-z0-9-]{6,64})#(.*)$", Pattern.DOTALL);
    static final Pattern POLL = Pattern.compile("^\\[poll:([a-z0-9-]{6,64})\\]$");

    public static Parsed parse(String t) {
        Parsed p = new Parsed();
        if (t == null) return p;
        Matcher fm = FWD.matcher(t);
        if (fm.matches()) {
            p.forwarded = true;
            p.fwdFrom = fm.group(1);
            t = fm.group(2);
        }
        if (t.startsWith("»|")) {
            String[] ls = t.split("\n", 3);
            if (ls.length >= 2 && ls[1].startsWith("»|")) {
                p.replyAuthor = ls[0].substring(2);
                String sn = ls[1].substring(2);
                Matcher rm = RID.matcher(sn);
                if (rm.matches()) {
                    p.replyId = rm.group(1);
                    sn = rm.group(2);
                }
                p.replySnippet = sn;
                t = ls.length > 2 ? ls[2] : "";
            }
        }
        Matcher pm = POLL.matcher(t.trim());
        if (pm.matches()) {
            p.pollId = pm.group(1);
            return p;
        }
        String[] lines = t.split("\n", -1);
        int i = 0;
        while (i < lines.length && i < 5) {
            Att a = att(lines[i]);
            if (a == null) break;
            p.atts.add(a);
            i++;
        }
        if (p.atts.size() > 1 && p.atts.get(0).car) p.carousel = true;
        StringBuilder b = new StringBuilder();
        for (int k = i; k < lines.length; k++) {
            if (b.length() > 0) b.append('\n');
            b.append(lines[k]);
        }
        p.body = b.toString();
        return p;
    }

    public static String label(Att a) {
        if (a.gif()) return "GIF";
        if (a.image()) return "Photo";
        if (a.video()) return "Video";
        if (a.audio()) return a.voice() ? "Voice message" : "Audio";
        return "File";
    }

    public static String preview(String raw) {
        if (raw == null) return null;
        String t = raw;
        Matcher fm = FWD.matcher(t);
        if (fm.matches()) t = fm.group(2);
        if (t.startsWith("»|")) {
            String[] ls = t.split("\n", 3);
            if (ls.length >= 2 && ls[1].startsWith("»|")) t = ls.length > 2 ? ls[2] : "";
        }
        if (POLL.matcher(t.trim()).matches()) return "Poll";
        if (t.startsWith("[[joinreq:")) return "Join request";
        if (t.startsWith("[[botjoin:")) return "Bot added to the group";
        Parsed p = parse(t);
        if (!p.atts.isEmpty()) {
            String cap = clean(p.body);
            if (p.atts.size() > 1) return (p.carousel ? "Carousel (" : "Album (") + p.atts.size() + ")" + (cap.isEmpty() ? "" : ", " + cap);
            Att a = p.atts.get(0);
            String l = label(a);
            if (!cap.isEmpty()) return l + ", " + cap;
            if ("File".equals(l)) return "File: " + a.name;
            return l;
        }
        String c = clean(t);
        return c.isEmpty() ? "Message" : c;
    }

    public static String clean(String t) {
        String s = t;
        s = s.replaceAll("\\[(?:btn|btnlayout|kbd|kbdopt|kbdph):[^\\]]*\\]", "").replace("[btnrow]", "").replace("[kbdremove]", "");
        s = s.replaceAll("\\|\\|[\\s\\S]*?\\|\\|", "▒▒▒▒");
        s = s.replaceAll("\\[\\[[a-z]+:[^\\]]*\\]\\]", "");
        s = s.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "Photo");
        s = s.replaceAll("\\[([^\\]]+)\\]\\((https?://[^)]+)\\)", "$1");
        s = s.replaceAll("```[A-Za-z0-9_+#.-]*\\n?", "");
        s = s.replace("**", "").replace("__", "").replace("~~", "").replace("`", "");
        s = s.replaceAll("(^|\\s)[*_]([^*_\\n]+)[*_](?=\\s|$)", "$1$2");
        s = s.replaceAll("(?m)^>\\s?", "");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    public static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / 1048576.0);
    }

    public static String dur(double s) {
        int t = (int) Math.max(0, Math.floor(s));
        return (t / 60) + ":" + String.format(Locale.US, "%02d", t % 60);
    }
}
