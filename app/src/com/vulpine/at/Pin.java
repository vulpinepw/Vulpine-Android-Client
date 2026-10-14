package com.vulpine.at;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.BgView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import org.json.JSONObject;

public final class Pin {
    public static volatile boolean unlocked;
    public static volatile boolean fake;
    public static JSONObject fakeProfile;
    static long lastActivity = System.currentTimeMillis();
    static long pausedAt;
    static FrameLayout lockView;

    static boolean cachedHasPin() {
        try {
            String s = App.kv.get("vulpine.pinState");
            if (s == null) return false;
            JSONObject o = new JSONObject(s);
            return o.optString("u").equals(Session.userId()) && o.optInt("p") == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasPin() {
        return cachedHasPin();
    }

    static void setPinState(boolean p) {
        App.kv.put("vulpine.pinState", App.jo("u", Session.userId(), "p", p ? 1 : 0).toString());
    }

    public static void gate(MainActivity a, Runnable then) {
        if (unlocked) {
            then.run();
            return;
        }
        if (cachedHasPin()) {
            show(a, then);
            return;
        }
        unlocked = true;
        then.run();
        App.bg(() -> {
            Http.Resp r = Http.get("/me");
            if (!r.ok()) return;
            boolean hp = r.obj().optBoolean("hasPin");
            setPinState(hp);
            if (hp) App.ui(() -> {
                unlocked = false;
                show(a, null);
            });
        });
    }

    public static void lockNow() {
        MainActivity a = MainActivity.cur;
        if (a == null) return;
        if (fake) {
            fake = false;
            fakeProfile = null;
            Http.fake = null;
        }
        unlocked = false;
        if (!cachedHasPin()) {
            App.boot(a);
            return;
        }
        com.vulpine.core.Rt.I.stop();
        Sync.stop();
        a.setRoot(new com.vulpine.at.auth.Splash());
        App.boot(a);
    }

    public static void onPause() {
        pausedAt = System.currentTimeMillis();
    }

    public static void onResume() {
        checkAuto(pausedAt > 0 ? pausedAt : lastActivity);
    }

    public static void touch() {
        lastActivity = System.currentTimeMillis();
    }

    static long autoMs() {
        if (fake) return 60000;
        String v = Session.settings().optString("autoLock", "");
        switch (v) {
            case "1m": return 60000;
            case "5m": return 300000;
            case "30m": return 1800000;
            case "1h": return 3600000;
            case "24h": return 86400000;
        }
        return 0;
    }

    static void checkAuto(long since) {
        long ms = autoMs();
        if (ms <= 0 || !unlocked || !cachedHasPin()) return;
        if (Calls.active()) {
            touch();
            return;
        }
        if (System.currentTimeMillis() - since >= ms) lockNow();
    }

    static {
        App.H.postDelayed(new Runnable() {
            public void run() {
                if (App.fg) checkAuto(lastActivity);
                App.H.postDelayed(this, 10000);
            }
        }, 10000);
    }

    static void show(MainActivity a, Runnable then) {
        if (lockView != null) return;
        Context c = a;
        FrameLayout root = new FrameLayout(c) {
            final Paint p = new Paint();

            protected void dispatchDraw(Canvas cv) {
                int w = getWidth(), h = getHeight();
                p.setShader(new RadialGradient(w / 2f, h * .3f, (float) Math.hypot(w, h) * .7f, T.dark ? 0xff18181b : 0xffffffff, T.dark ? 0xff0a0a0b : 0xffe9e7e3, Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, w, h, p);
                super.dispatchDraw(cv);
            }
        };
        root.setWillNotDraw(false);
        root.setClickable(true);
        BgView pat = new BgView(c, true);
        pat.patternOnly(.15f);
        root.addView(pat, new FrameLayout.LayoutParams(W.MP, W.MP));
        LinearLayout card = W.col(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int fg = T.dark ? 0xffffffff : T.text;
        TextView title = W.text(c, "Enter Passcode", 15, 500, T.dark ? 0x99ffffff : T.textSecondary);
        title.setLetterSpacing(.02f);
        T.lh(title, 22.5f);
        card.addView(title, W.lpm(W.WC, W.WC, 0, 0, 0, 24));
        LinearLayout dots = W.row(c);
        dots.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dots.setMinimumHeight(T.dp(18));
        card.addView(dots, W.lpm(W.WC, W.WC, 0, 0, 0, 14));
        TextView err = W.text(c, "", 13, 500, 0xffef4444);
        err.setGravity(Gravity.CENTER);
        err.setAlpha(0);
        card.addView(err, W.lpm(W.MP, T.dp(20), 0, 0, 0, 6));
        GridLayout pad = new GridLayout(c);
        pad.setColumnCount(3);
        card.addView(pad, W.lpm(W.WC, W.WC, 0, 8, 0, 0));
        StringBuilder pin = new StringBuilder();
        boolean[] busy = {false};
        long[] lockedUntil = {0};
        Runnable[] render = new Runnable[1];
        java.util.function.Function<Boolean, View> mk = on -> {
            View d = new View(c);
            d.setBackground(on ? W.round(T.accent, 99) : W.round(0, 99, T.dark ? 0x4dffffff : 0x4d000000, 2));
            d.setTag(on);
            if (on) {
                d.setScaleX(1.15f);
                d.setScaleY(1.15f);
            }
            LinearLayout.LayoutParams lp = W.lp(T.dp(13), T.dp(13));
            lp.setMargins(T.dp(7), 0, T.dp(7), 0);
            d.setLayoutParams(lp);
            return d;
        };
        Runnable ensurePh = () -> {
            int filled = 0;
            java.util.List<View> phs = new java.util.ArrayList<>();
            for (int i = 0; i < dots.getChildCount(); i++) {
                View d = dots.getChildAt(i);
                if (Boolean.TRUE.equals(d.getTag()) || "out".equals(d.getTag())) filled++;
                else phs.add(d);
            }
            int want = Math.max(0, 4 - filled);
            for (int i = phs.size(); i < want; i++) dots.addView(mk.apply(false));
            for (int j = phs.size() - 1; j >= want; j--) dots.removeView(phs.get(j));
        };
        render[0] = () -> {
            dots.removeAllViews();
            for (int i = 0; i < pin.length(); i++) dots.addView(mk.apply(true));
            ensurePh.run();
        };
        Runnable addDot = () -> {
            for (int i = 0; i < dots.getChildCount(); i++) if (Boolean.FALSE.equals(dots.getChildAt(i).getTag())) {
                dots.removeViewAt(i);
                break;
            }
            View d = mk.apply(true);
            dots.addView(d);
            d.setScaleX(0);
            d.setScaleY(0);
            d.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).setInterpolator(new android.view.animation.PathInterpolator(.3f, 1.6f, .36f, 1f)).start();
            ensurePh.run();
        };
        Runnable dropDot = () -> {
            View last = null;
            for (int i = 0; i < dots.getChildCount(); i++) {
                Object t = dots.getChildAt(i).getTag();
                if (Boolean.TRUE.equals(t) || "out".equals(t)) last = dots.getChildAt(i);
            }
            if (last != null && Boolean.TRUE.equals(last.getTag())) {
                View l = last;
                l.setTag("out");
                l.animate().cancel();
                l.animate().scaleX(0).scaleY(0).alpha(0).setDuration(110).withEndAction(() -> {
                    dots.removeView(l);
                    ensurePh.run();
                }).start();
            } else if (pin.length() == 0) ensurePh.run();
        };
        Runnable[] submit = new Runnable[1];
        Runnable[] countdown = new Runnable[1];
        java.util.function.Consumer<String> showErr = (msg) -> {
            err.setText(msg);
            err.animate().alpha(1).setDuration(200).start();
            card.animate().cancel();
            android.animation.ObjectAnimator sh = android.animation.ObjectAnimator.ofFloat(card, "translationX", 0, -T.dp(10), T.dp(10), -T.dp(7), T.dp(7), 0);
            sh.setDuration(400);
            sh.start();
        };
        countdown[0] = () -> {
            long left = lockedUntil[0] - System.currentTimeMillis();
            if (left <= 0) {
                err.animate().alpha(0).setDuration(200).start();
                pad.setAlpha(1);
                return;
            }
            long s = (left + 999) / 1000;
            err.setText("Too many attempts. Try again in " + (s / 60) + ":" + String.format("%02d", s % 60));
            err.setAlpha(1);
            pad.setAlpha(.4f);
            App.H.postDelayed(countdown[0], 500);
        };
        View.OnClickListener digit = v -> {
            if (busy[0] || System.currentTimeMillis() < lockedUntil[0]) return;
            String d = (String) v.getTag();
            if ("del".equals(d)) {
                err.animate().alpha(0).setDuration(200).start();
                v.animate().scaleX(.88f).scaleY(.88f).setDuration(70).withEndAction(() -> v.animate().scaleX(1).scaleY(1).setDuration(120).start()).start();
                if (pin.length() > 0) pin.setLength(pin.length() - 1);
                dropDot.run();
                return;
            } else if ("ok".equals(d)) {
                if (pin.length() >= 4) submit[0].run();
                return;
            } else if (pin.length() < 8) {
                pin.append(d);
                err.animate().alpha(0).setDuration(200).start();
                v.animate().scaleX(.88f).scaleY(.88f).setDuration(70).withEndAction(() -> v.animate().scaleX(1).scaleY(1).setDuration(120).start()).start();
                addDot.run();
                if (pin.length() == 8) App.H.postDelayed(submit[0], 140);
            }
        };
        submit[0] = () -> {
            if (busy[0] || pin.length() < 4) return;
            busy[0] = true;
            pad.setAlpha(.4f);
            String tried = pin.toString();
            new Thread(() -> {
                Http.Resp r = Http.post("/me/pin-unlock", App.jo("pin", tried));
                App.ui(() -> {
                    busy[0] = false;
                    pad.setAlpha(1);
                    JSONObject o = r.obj();
                    if (r.ok() && "real".equals(o.optString("mode"))) {
                        unlocked = true;
                        touch();
                        close(a);
                        if (then != null) then.run();
                    } else if (r.ok() && "fake".equals(o.optString("mode"))) {
                        unlocked = true;
                        enterFake(a, o.optJSONObject("fakeProfile"));
                    } else {
                        if (pin.toString().startsWith(tried)) pin.delete(0, tried.length());
                        else pin.setLength(0);
                        render[0].run();
                        int ra = o.optInt("retryAfter");
                        if (r.status == 429 || ra > 0) {
                            lockedUntil[0] = System.currentTimeMillis() + Math.max(1, ra) * 1000L;
                            showErr.accept("Too many attempts.");
                            countdown[0].run();
                        } else if (r.status == 401) showErr.accept("Wrong passcode");
                        else showErr.accept("Something went wrong");
                    }
                });
            }, "pin-unlock").start();
        };
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "del", "0", "ok"};
        for (String k : keys) {
            FrameLayout b = new FrameLayout(c);
            b.setTag(k);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, T.dark ? new int[]{0x1affffff, 0x08ffffff} : new int[]{0x0d000000, 0x05000000});
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setStroke(Math.max(1, T.dp(1)), T.dark ? 0x0fffffff : 0x14000000);
            b.setBackground(W.ripple(g, 99));
            View content;
            if ("del".equals(k)) content = W.img(c, Svg.stroke(24, fg, 2, "M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z", "M18 9l-6 6", "M12 9l6 6"));
            else if ("ok".equals(k)) content = W.img(c, Svg.stroke(26, T.accent, 2.8f, "M20 6 9 17l-5-5"));
            else {
                TextView t = W.text(c, k, 26, 500, fg);
                t.setGravity(Gravity.CENTER);
                content = t;
            }
            b.addView(content, W.flp(W.MP, W.MP, Gravity.CENTER));
            b.setOnClickListener(digit);
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = T.dp(68);
            gp.height = T.dp(68);
            gp.setMargins(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
            pad.addView(b, gp);
        }
        render[0].run();
        root.addView(card, W.flp(W.WC, W.WC, Gravity.CENTER));
        lockView = root;
        a.host.addView(root, new FrameLayout.LayoutParams(W.MP, W.MP));
        root.setElevation(T.dpf(100));
        Ov.push(root, () -> a.moveTaskToBack(true));
    }

    static void close(MainActivity a) {
        if (lockView == null) return;
        FrameLayout v = lockView;
        lockView = null;
        Ov.remove(v);
        v.animate().alpha(0).setDuration(T.DUR).withEndAction(() -> a.host.removeView(v)).start();
    }

    static void enterFake(MainActivity a, JSONObject fp) {
        fake = true;
        fakeProfile = fp == null ? new JSONObject() : fp;
        Http.fake = Pin::fakeResp;
        com.vulpine.core.Rt.I.stop();
        Sync.stop();
        com.vulpine.core.Chats.I.reset();
        close(a);
        a.setRoot(new com.vulpine.at.home.Home());
    }

    static Http.Resp fakeResp(String method, String path) {
        String p = path.replaceFirst("^/api", "");
        if (!"GET".equals(method)) return Http.fakeResp(200, "{\"ok\":true}");
        if (p.startsWith("/me") && (p.equals("/me") || p.startsWith("/me?"))) {
            JSONObject o = App.jo("id", "_fake", "handle", "user", "displayName", fakeProfile.optString("displayName", "User"), "bio", fakeProfile.optString("bio", ""), "avatarColor", fakeProfile.optString("avatarColor", "#808080"), "status", fakeProfile.optString("status", "online"), "hasPin", false, "settings", new JSONObject());
            return Http.fakeResp(200, o.toString());
        }
        if (p.matches("^/(conversations|connections|messages|users).*")) {
            if (p.startsWith("/conversations")) return Http.fakeResp(200, "{\"conversations\":[],\"total\":0}");
            if (p.startsWith("/messages")) return Http.fakeResp(200, "{\"messages\":[],\"hasMore\":false}");
            if (p.startsWith("/connections")) return Http.fakeResp(200, "{\"accepted\":[],\"incoming\":[],\"outgoing\":[]}");
            return Http.fakeResp(200, "[]");
        }
        if (p.startsWith("/shop/wallet")) return Http.fakeResp(200, "{\"wallet\":[],\"used\":0,\"capacity\":30,\"extraSlots\":0,\"attached\":[],\"pro\":false,\"listedCount\":0,\"pricePerSlot\":1,\"maxListed\":15}");
        if (p.startsWith("/shop/market")) return Http.fakeResp(200, "{\"items\":[],\"q\":\"\",\"sort\":\"newest\",\"limit\":24,\"offset\":0,\"total\":0}");
        if (p.startsWith("/unread")) return Http.fakeResp(200, "{\"counts\":{}}");
        return Http.fakeResp(200, "{}");
    }
}
