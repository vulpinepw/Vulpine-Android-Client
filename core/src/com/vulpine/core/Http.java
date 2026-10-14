package com.vulpine.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public final class Http {
    public static final class Resp {
        public final int status;
        public final String body;

        Resp(int s, String b) {
            status = s;
            body = b;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public JSONObject obj() {
            try {
                Object o = new JSONTokener(body).nextValue();
                return o instanceof JSONObject ? (JSONObject) o : new JSONObject();
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public JSONArray arr() {
            try {
                Object o = new JSONTokener(body).nextValue();
                if (o instanceof JSONArray) return (JSONArray) o;
                return new JSONArray();
            } catch (Exception e) {
                return new JSONArray();
            }
        }

        public Object any() {
            try {
                return new JSONTokener(body).nextValue();
            } catch (Exception e) {
                return null;
            }
        }

        public String error() {
            return obj().optString("error", status >= 400 ? "http_" + status : "");
        }
    }

    public interface Progress {
        void on(long sent, long total);
    }

    public static volatile String origin = "https://vulpine.pw";
    public static volatile String userAgent = "Mozilla/5.0 (Linux; Android) Vulpine/3 vulpineapk/30";
    public static volatile String token;
    public static volatile Runnable onUnauthorized;

    public interface Fake {
        Resp on(String method, String path);
    }

    public static volatile Fake fake;

    public static Resp fakeResp(int s, String b) {
        return new Resp(s, b);
    }
    private static CookieManager cookies;

    public static synchronized void init(CookieStore store) {
        if (cookies != null) return;
        cookies = new CookieManager(store, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        CookieHandler.setDefault(cookies);
    }

    public static CookieManager cookies() {
        if (cookies == null) init(null);
        return cookies;
    }

    public static volatile String mediaCookie;
    public static volatile java.util.function.Consumer<String> onMediaCookie;

    static void grabCookie(HttpURLConnection c) {
        try {
            for (java.util.Map.Entry<String, java.util.List<String>> e : c.getHeaderFields().entrySet()) {
                if (e.getKey() == null || !e.getKey().equalsIgnoreCase("Set-Cookie")) continue;
                for (String v : e.getValue()) {
                    if (v == null || !v.startsWith("vulpine_media=")) continue;
                    int i = v.indexOf(';');
                    String mc = i > 0 ? v.substring(0, i) : v;
                    if (!mc.equals(mediaCookie)) {
                        mediaCookie = mc;
                        java.util.function.Consumer<String> l = onMediaCookie;
                        if (l != null) l.accept(mc);
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    public static String cookieFor(String url) {
        String lc = localCookies(url);
        String mc = mediaCookie;
        if (mc == null || url == null || !url.contains("/uploads")) return lc;
        if (lc == null || lc.isEmpty()) return mc;
        if (lc.contains("vulpine_media=")) return lc;
        return lc + "; " + mc;
    }

    public static String localCookies(String url) {
        if (!url.startsWith("http://127.0.0.1") && !url.startsWith("http://localhost")) return null;
        StringBuilder b = new StringBuilder();
        for (java.net.HttpCookie c : cookies().getCookieStore().getCookies()) {
            if (b.length() > 0) b.append("; ");
            b.append(c.getName()).append('=').append(c.getValue());
        }
        return b.length() == 0 ? null : b.toString();
    }

    public static String api(String path) {
        return origin + "/api" + path;
    }

    public static String abs(String url) {
        if (url == null) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/")) return origin + url;
        return origin + "/" + url;
    }

    public static Resp get(String path) {
        return call("GET", path, null);
    }

    public static Resp post(String path, Object body) {
        return call("POST", path, body);
    }

    public static Resp patch(String path, Object body) {
        return call("PATCH", path, body);
    }

    public static Resp put(String path, Object body) {
        return call("PUT", path, body);
    }

    public static Resp delete(String path, Object body) {
        return call("DELETE", path, body);
    }

    public static Resp call(String method, String path, Object body) {
        return raw(method, api(path), body, token, 20000);
    }

    public static java.util.function.BooleanSupplier onMain;
    public static volatile String mainViolation;

    public static volatile boolean strict;
    static final java.util.concurrent.ExecutorService OFF = java.util.concurrent.Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "http-off-main");
        t.setDaemon(true);
        return t;
    });

    public static boolean guard(String what) {
        java.util.function.BooleanSupplier m = onMain;
        if (m == null || !m.getAsBoolean()) return false;
        mainViolation = what;
        if (strict) {
            StringBuilder b = new StringBuilder("MAINNET " + what);
            for (StackTraceElement e : new Throwable().getStackTrace()) if (e.getClassName().startsWith("com.vulpine.at")) b.append(" <- ").append(e.getClassName().replace("com.vulpine.at.", "")).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber());
            System.out.println(b);
        }
        return true;
    }

    public static Resp raw(String method, String url, Object body, String tok, int timeout) {
        if (guard(method + " " + url)) {
            try {
                return OFF.submit(() -> raw0(method, url, body, tok, timeout)).get();
            } catch (Exception e) {
                return new Resp(0, null);
            }
        }
        return raw0(method, url, body, tok, timeout);
    }

    static Resp raw0(String method, String url, Object body, String tok, int timeout) {
        Fake fk = fake;
        if (fk != null && url.startsWith(origin + "/api")) return fk.on(method, url.substring(origin.length()));
        cookies();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(timeout);
            c.setUseCaches(false);
            if ("PATCH".equals(method) && Platform.isAndroid()) c.setRequestMethod("PATCH");
            else if ("PATCH".equals(method)) {
                c.setRequestMethod("POST");
                c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                try {
                    java.lang.reflect.Field f = HttpURLConnection.class.getDeclaredField("method");
                    f.setAccessible(true);
                    f.set(c, "PATCH");
                } catch (Exception e) {
                }
            } else c.setRequestMethod(method);
            c.setRequestProperty("User-Agent", userAgent);
            c.setRequestProperty("Accept", "application/json");
            if (tok != null) c.setRequestProperty("authorization", "Bearer " + tok);
            if (body == null && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) body = "{}";
            if (body != null) {
                byte[] b = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("content-type", "application/json");
                c.setFixedLengthStreamingMode(b.length);
                try (OutputStream o = c.getOutputStream()) {
                    o.write(b);
                }
            }
            int st = c.getResponseCode();
            grabCookie(c);
            String s = read(st >= 400 ? c.getErrorStream() : c.getInputStream());
            if (st == 401 && tok != null && tok.equals(token) && onUnauthorized != null) onUnauthorized.run();
            return new Resp(st, s);
        } catch (IOException e) {
            return new Resp(0, "{\"error\":\"network\",\"message\":" + JSONObject.quote(String.valueOf(e.getMessage())) + "}");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static Resp upload(String path, String field, File f, String name, String mime, Progress p) {
        return upload(path, field, f, name, mime, null, p);
    }

    public static Resp upload(String path, String field, File f, String name, String mime, JSONObject fields, Progress p) {
        if (guard("UPLOAD " + path)) {
            try {
                return OFF.submit(() -> upload(path, field, f, name, mime, fields, p)).get();
            } catch (Exception e) {
                return new Resp(0, null);
            }
        }
        cookies();
        HttpURLConnection c = null;
        String bnd = "----vulpine" + Long.toHexString(new SecureRandom().nextLong());
        try {
            c = (HttpURLConnection) new URL(api(path)).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(180000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("User-Agent", userAgent);
            if (token != null) c.setRequestProperty("authorization", "Bearer " + token);
            c.setRequestProperty("content-type", "multipart/form-data; boundary=" + bnd);
            ByteArrayOutputStream pre = new ByteArrayOutputStream();
            if (fields != null) {
                java.util.Iterator<String> it = fields.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    pre.write(("--" + bnd + "\r\nContent-Disposition: form-data; name=\"" + k + "\"\r\n\r\n" + fields.optString(k) + "\r\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            pre.write(("--" + bnd + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + name.replace("\"", "_") + "\"\r\nContent-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] post = ("\r\n--" + bnd + "--\r\n").getBytes(StandardCharsets.UTF_8);
            long total = pre.size() + f.length() + post.length;
            c.setFixedLengthStreamingMode(total);
            try (OutputStream o = c.getOutputStream(); InputStream in = new FileInputStream(f)) {
                o.write(pre.toByteArray());
                byte[] buf = new byte[64 * 1024];
                long sent = pre.size();
                int n;
                while ((n = in.read(buf)) > 0) {
                    o.write(buf, 0, n);
                    sent += n;
                    if (p != null) try {
                        p.on(sent, total);
                    } catch (RuntimeException ce) {
                        throw new IOException("cancelled");
                    }
                }
                o.write(post);
            }
            int st = c.getResponseCode();
            return new Resp(st, read(st >= 400 ? c.getErrorStream() : c.getInputStream()));
        } catch (IOException e) {
            return new Resp(0, "{\"error\":\"network\"}");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static byte[] bytes(String url, int max) throws IOException {
        if (guard("GET " + url)) {
            try {
                return OFF.submit(() -> bytes(url, max)).get();
            } catch (java.util.concurrent.ExecutionException e) {
                throw e.getCause() instanceof IOException ? (IOException) e.getCause() : new IOException(e.getCause());
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        cookies();
        HttpURLConnection c = (HttpURLConnection) new URL(abs(url)).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", userAgent);
            String lc = cookieFor(abs(url));
            if (lc != null) c.setRequestProperty("Cookie", lc);
            int st = c.getResponseCode();
            if (st >= 400) throw new IOException("http " + st);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    b.write(buf, 0, n);
                    if (max > 0 && b.size() > max) throw new IOException("too large");
                }
                return b.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
