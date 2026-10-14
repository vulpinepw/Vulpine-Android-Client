package com.vulpine.at.ui;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Css {
    static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{3,8})\\b");
    static final Pattern RGB = Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*([\\d.]+)\\s*)?\\)");
    static final Pattern DEG = Pattern.compile("(-?[\\d.]+)deg");

    public static Integer color(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.matches("^#?[0-9a-fA-F]{3,8}$")) {
            String h = s.startsWith("#") ? s.substring(1) : s;
            return hex(h);
        }
        Matcher m = RGB.matcher(s);
        if (m.matches()) return rgb(m);
        return null;
    }

    static Integer hex(String h) {
        try {
            if (h.length() == 3 || h.length() == 4) {
                StringBuilder b = new StringBuilder();
                for (char c : h.toCharArray()) b.append(c).append(c);
                h = b.toString();
            }
            if (h.length() == 6) return 0xff000000 | Integer.parseInt(h, 16);
            if (h.length() == 8) {
                long v = Long.parseLong(h, 16);
                return (int) (((v & 0xff) << 24) | (v >> 8));
            }
        } catch (Exception e) {
        }
        return null;
    }

    static int rgb(Matcher m) {
        int a = m.group(4) == null ? 255 : Math.round(Float.parseFloat(m.group(4)) * 255);
        return Color.argb(a, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }

    public static int[] gradientColors(String s) {
        if (s == null || !s.contains("gradient")) return null;
        List<Integer> out = new ArrayList<>();
        Matcher m = Pattern.compile("#[0-9a-fA-F]{3,8}\\b|rgba?\\([^)]*\\)").matcher(s);
        while (m.find()) {
            Integer c = color(m.group());
            if (c != null) out.add(c);
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    public static float gradientAngle(String s) {
        Matcher m = DEG.matcher(s == null ? "" : s);
        return m.find() ? Float.parseFloat(m.group(1)) : 180f;
    }
}
