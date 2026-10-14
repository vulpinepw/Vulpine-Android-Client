package com.vulpine.at.chat;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieResult;
import com.vulpine.at.App;
import com.vulpine.at.ui.Img;
import com.vulpine.core.Http;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Lottie {
    static final int MAX = 12;
    static Map<String, String> map;
    static final Map<String, LottieComposition> comps = new HashMap<>();
    static final List<LottieDrawable> live = new ArrayList<>();
    static final ExecutorService EX = Executors.newSingleThreadExecutor();

    static synchronized Map<String, String> map() {
        if (map != null) return map;
        map = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(App.ctx.getAssets().open("anemoji.txt")))) {
            String l;
            while ((l = r.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty()) continue;
                StringBuilder k = new StringBuilder();
                for (String p : l.split("_")) {
                    if (p.equals("fe0f")) continue;
                    if (k.length() > 0) k.append('_');
                    k.append(p);
                }
                map.put(k.toString(), l);
            }
        } catch (Exception e) {
        }
        return map;
    }

    public static String file(String glyph) {
        StringBuilder k = new StringBuilder();
        int i = 0;
        while (i < glyph.length()) {
            int cp = glyph.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xfe0f || cp == 0xfe0e) continue;
            if (k.length() > 0) k.append('_');
            k.append(Integer.toHexString(cp));
        }
        return map().get(k.toString());
    }

    public static void attach(FrameLayout host, TextView fallback, String glyph, int sizeDp, ChatScreen s) {
        String f = file(glyph);
        if (f == null) return;
        ImageView iv = new ImageView(host.getContext());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setVisibility(View.INVISIBLE);
        host.addView(iv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LottieDrawable d = new LottieDrawable();
        d.setRepeatCount(LottieDrawable.INFINITE);
        iv.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {
                if (d.getComposition() != null) play(d);
            }

            public void onViewDetachedFromWindow(View v) {
                pause(d);
            }
        });
        load(f, comp -> {
            if (comp == null) return;
            d.setComposition(comp);
            iv.setImageDrawable(d);
            iv.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.INVISIBLE);
            if (iv.isAttachedToWindow()) play(d);
        });
    }

    interface Cb {
        void on(LottieComposition c);
    }

    static void load(String f, Cb cb) {
        LottieComposition hit;
        synchronized (comps) {
            hit = comps.get(f);
        }
        if (hit != null) {
            cb.on(hit);
            return;
        }
        EX.execute(() -> {
            LottieComposition c = null;
            try {
                File file = Img.file(Http.abs("/assets/anemoji/" + f + ".json"));
                if (file != null) {
                    try (FileInputStream in = new FileInputStream(file)) {
                        LottieResult<LottieComposition> r = LottieCompositionFactory.fromJsonInputStreamSync(in, "anemoji/" + f);
                        c = r.getValue();
                    }
                }
            } catch (Throwable e) {
            }
            LottieComposition fc = c;
            if (fc != null) synchronized (comps) {
                comps.put(f, fc);
            }
            App.ui(() -> cb.on(fc));
        });
    }

    static void play(LottieDrawable d) {
        live.remove(d);
        int n = 0;
        for (LottieDrawable x : live) if (x.isRunning()) n++;
        if (n >= MAX) for (LottieDrawable x : live) if (x.isRunning()) {
            x.pauseAnimation();
            break;
        }
        live.add(d);
        if (d.isRunning()) return;
        if (d.getProgress() <= 0f) d.playAnimation();
        else d.resumeAnimation();
    }

    static void pause(LottieDrawable d) {
        d.pauseAnimation();
        live.remove(d);
    }

    public static void pauseAll() {
        for (LottieDrawable x : new ArrayList<>(live)) x.pauseAnimation();
    }
}
