package com.vulpine.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Cap {
    static int fnv(int h, String s) {
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h += (h << 1) + (h << 4) + (h << 7) + (h << 8) + (h << 24);
        }
        return h;
    }

    static String prng(int st, int len) {
        StringBuilder b = new StringBuilder();
        while (b.length() < len) {
            st ^= st << 13;
            st ^= st >>> 17;
            st ^= st << 5;
            String hx = Long.toHexString(st & 0xffffffffL);
            for (int i = hx.length(); i < 8; i++) b.append('0');
            b.append(hx);
        }
        return b.substring(0, len);
    }

    static long solveOne(String salt, String target) throws Exception {
        MessageDigest base = MessageDigest.getInstance("SHA-256");
        base.update(salt.getBytes(StandardCharsets.UTF_8));
        boolean clone = true;
        try {
            base.clone();
        } catch (CloneNotSupportedException e) {
            clone = false;
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] sb = salt.getBytes(StandardCharsets.UTF_8);
        int full = target.length() / 2;
        byte[] tb = new byte[full];
        for (int i = 0; i < full; i++) tb[i] = (byte) Integer.parseInt(target.substring(i * 2, i * 2 + 2), 16);
        int half = target.length() % 2 == 1 ? Integer.parseInt(target.substring(target.length() - 1), 16) : -1;
        byte[] num = new byte[20];
        for (long n = 0; ; n++) {
            int len = 0;
            long v = n;
            if (v == 0) num[len++] = '0';
            else {
                int st = 20;
                while (v > 0) {
                    num[--st] = (byte) ('0' + (v % 10));
                    v /= 10;
                }
                len = 20 - st;
                System.arraycopy(num, st, num, 0, len);
            }
            byte[] d;
            if (clone) {
                MessageDigest m = (MessageDigest) base.clone();
                m.update(num, 0, len);
                d = m.digest();
            } else {
                md.reset();
                md.update(sb);
                md.update(num, 0, len);
                d = md.digest();
            }
            boolean ok = true;
            for (int i = 0; i < full; i++) if (d[i] != tb[i]) {
                ok = false;
                break;
            }
            if (ok && half >= 0 && ((d[full] >> 4) & 0xf) != half) ok = false;
            if (ok) return n;
        }
    }

    public static JSONObject solve(JSONObject ch) throws Exception {
        String token = ch.getString("token");
        JSONObject c = ch.getJSONObject("challenge");
        int count = c.getInt("c"), s = c.getInt("s"), d = c.getInt("d");
        int h0 = fnv(0x811c9dc5, token);
        long[] out = new long[count];
        int threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            java.util.List<java.util.concurrent.Future<?>> fs = new java.util.ArrayList<>();
            for (int i = 1; i <= count; i++) {
                final int k = i;
                fs.add(ex.submit(() -> {
                    int saltSeed = fnv(h0, String.valueOf(k));
                    int targetSeed = fnv(saltSeed, "d");
                    out[k - 1] = solveOne(prng(saltSeed, s), prng(targetSeed, d));
                    return null;
                }));
            }
            for (java.util.concurrent.Future<?> f : fs) f.get();
        } finally {
            ex.shutdownNow();
        }
        JSONArray sol = new JSONArray();
        for (long v : out) sol.put(v);
        return new JSONObject().put("token", token).put("solutions", sol);
    }

    public static String obtain() throws Exception {
        Http.Resp r = Http.post("/cap/challenge", null);
        if (!r.ok()) throw new Exception("cap_error");
        Http.Resp v = Http.post("/cap/redeem", solve(r.obj()));
        JSONObject o = v.obj();
        if (!o.optBoolean("success")) throw new Exception("captcha_failed:" + o.optString("reason"));
        return o.getString("token");
    }
}
