package com.vulpine.at.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public final class Media {
    static final Map<String, int[]> dims = new ConcurrentHashMap<>();
    static final Map<String, JSONObject> vmeta = new ConcurrentHashMap<>();
    static final Map<String, Bitmap> recent = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, Bitmap>(16, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
            return size() > 16;
        }
    });

    public static void alias(String localUrl, String serverUrl) {
        Bitmap b = recent.get(base(localUrl));
        if (b != null) recent.put(base(serverUrl), b);
        JSONObject vm = vmeta.get(base(localUrl) + ".vp.json");
        if (vm != null) vmeta.put(base(serverUrl) + ".vp.json", vm);
        int[] d = dims.get(base(localUrl));
        if (d != null) dims.putIfAbsent(base(serverUrl), d);
    }

    public static class RoundImage extends ImageView {
        final float[] radii = new float[8];
        final Path clip = new Path();

        public RoundImage(Context c) {
            super(c);
            setScaleType(ScaleType.CENTER_CROP);
        }

        public void corners(float tl, float tr, float br, float bl) {
            radii[0] = radii[1] = T.dpf(tl);
            radii[2] = radii[3] = T.dpf(tr);
            radii[4] = radii[5] = T.dpf(br);
            radii[6] = radii[7] = T.dpf(bl);
            invalidate();
        }

        protected void onDraw(Canvas c) {
            clip.reset();
            clip.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), radii, Path.Direction.CW);
            int s = c.save();
            c.clipPath(clip);
            super.onDraw(c);
            c.restoreToCount(s);
        }
    }

    static String base(String url) {
        return url.replaceAll("[#?].*$", "");
    }

    static int[] box(Fmt.Att a, int mw, int mh, int fw, float fallbackRatio) {
        int w = a.w, h = a.h;
        if (w <= 0 || h <= 0) {
            int[] d = dims.get(base(a.url));
            if (d != null) {
                w = d[0];
                h = d[1];
            }
        }
        if (w > 0 && h > 0) {
            float s = Math.min(1f, Math.min(mw / (float) w, mh / (float) h));
            int bw = Math.max(1, Math.round(w * s)), bh = Math.max(1, Math.round(h * s));
            return new int[]{T.dp(bw), T.dp(bh)};
        }
        return new int[]{T.dp(fw), Math.round(T.dp(fw) / fallbackRatio)};
    }

    public static String gifLite(String u, String sz) {
        if (u.matches("(?i)^/uploads/[^#?]+\\.gif([#?].*)?$")) return base(u) + ".vp.webp";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^(https://media\\d*\\.giphy\\.com/media/)(?:v1\\.[^/]+/)?([^/]+)/[^/]+\\.gif").matcher(u);
        if (m.find()) return m.group(1) + m.group(2) + "/" + sz + ".webp";
        return u;
    }

    public static boolean isSticker(Fmt.Att a) {
        return a != null && "sticker.webp".equals(a.name) && a.image();
    }

    static void fitWidth(View v, int maxW) {
        android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.width > maxW) {
            lp.height = Math.round(lp.height * (maxW / (float) lp.width));
            lp.width = maxW;
        }
    }

    public static void attachments(ChatScreen s, LinearLayout col, Chats.Msg m, Fmt.Parsed p, int timeW) {
        Context c = s.a;
        int maxW = Math.round((c.getResources().getDisplayMetrics().widthPixels - T.dp(32)) * .95f);
        String cap = p.body == null ? "" : p.body.trim();
        if (p.atts.size() == 1) {
            Fmt.Att a = p.atts.get(0);
            boolean hasCap = !cap.isEmpty();
            if (a.image()) {
                boolean gif = a.gif();
                boolean stk = isSticker(a) && !hasCap;
                int[] bx = stk ? new int[]{T.dp(160), T.dp(160)} : gif ? box(a, 260, 200, 200, 4f / 3f) : box(a, 420, 360, 260, 4f / 3f);
                RoundImage iv = new RoundImage(c);
                iv.corners(12, 12, hasCap ? 0 : 12, hasCap ? 0 : 12);
                iv.setBackgroundColor(T.mixA(T.text, .08f));
                if (stk) {
                    iv.corners(0, 0, 0, 0);
                    iv.setBackgroundColor(0);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setTag(0x7f0f0002, Boolean.TRUE);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(bx[0], bx[1]);
                iv.setLayoutParams(lp);
                fitWidth(iv, maxW);
                if (hasCap && lp.width < Math.min(T.dp(220), Math.round(maxW * .6f))) {
                    int nw = Math.min(T.dp(220), Math.round(maxW * .6f));
                    lp.height = Math.min(T.dp(440), Math.round(lp.height * (nw / (float) lp.width)));
                    lp.width = nw;
                }
                List<Fmt.Att> one = new ArrayList<>();
                one.add(a);
                if (stk) {
                    Img.anim(iv, a.url, T.dp(320), true);
                    s.trackAnim(iv);
                    iv.setOnClickListener(v -> Panels.stickerInfo(s.root, a.url));
                } else {
                    loadImage(s, iv, a.url, gif, lp.width);
                    iv.setOnClickListener(v -> Viewer.open(s, one, 0, m));
                }
                iv.setOnLongClickListener(v -> {
                    s.menuFor(m, v, gif ? a.url : null);
                    return true;
                });
                col.addView(iv);
                if (hasCap) addCaption(s, col, cap, timeW, false, iv.getLayoutParams().width);
                return;
            }
            if (a.video()) {
                View v = videoThumb(s, a, hasCap, maxW, m, null, 0);
                col.addView(v);
                if (hasCap) addCaption(s, col, cap, timeW, false, v.getLayoutParams().width);
                return;
            }
            if (a.audio()) {
                col.addView(Voice.player(s, a, m));
                if (hasCap) addCaption(s, col, cap, timeW, false, 0);
                else col.addView(new View(c), W.lp(1, T.dp(12)));
                return;
            }
            col.addView(fileChip(s, a));
            if (hasCap) addCaption(s, col, cap, timeW, false, 0);
            else col.addView(new View(c), W.lp(1, T.dp(12)));
            return;
        }
        List<Fmt.Att> med = new ArrayList<>(), files = new ArrayList<>();
        for (Fmt.Att a : p.atts) (a.media() ? med : files).add(a);
        boolean hasCap = !cap.isEmpty();
        if (!med.isEmpty()) {
            if (p.carousel && med.size() >= 2) col.addView(carousel(s, med, hasCap || !files.isEmpty(), m));
            else col.addView(albumGrid(s, med, hasCap || !files.isEmpty(), m));
        }
        if (!files.isEmpty()) {
            LinearLayout fl = W.col(c);
            if (!med.isEmpty()) fl.setPadding(T.dp(12), T.dp(8), T.dp(12), hasCap ? 0 : T.dp(8));
            for (Fmt.Att f : files) {
                View chip = f.audio() ? Voice.player(s, f, m) : fileChip(s, f);
                fl.addView(chip, W.lpm(W.WC, W.WC, 0, 0, 0, 6));
            }
            col.addView(fl);
        }
        if (hasCap) {
            TextView t = MsgView.newBody(c);
            Md.Out out = Md.render(cap, t, s.links);
            MsgView.finishBody(t, out.sb, timeW);
            LinearLayout.LayoutParams lp = W.lp(med.isEmpty() ? W.WC : 0, W.WC);
            if (!med.isEmpty()) {
                t.setPadding(T.dp(12), t.getPaddingTop() + T.dp(4), T.dp(12), t.getPaddingBottom() + T.dp(8));
                lp.width = W.MP;
            } else lp.topMargin = T.dp(6);
            col.addView(t, lp);
        } else if (med.isEmpty()) col.addView(new View(c), W.lp(1, T.dp(12)));
    }

    static void addCaption(ChatScreen s, LinearLayout col, String cap, int timeW, boolean md, int width) {
        TextView t = MsgView.newBody(s.a);
        if (md) {
            Md.Out out = Md.render(cap, t, s.links);
            MsgView.finishBody(t, out.sb, timeW);
        } else MsgView.finishBody(t, new SpannableStringBuilder(cap), timeW);
        int pt = t.getPaddingTop(), pb = t.getPaddingBottom();
        if (width > 0) t.setPadding(T.dp(8), pt + T.dp(2), T.dp(8), pb + T.dp(8));
        else t.setPadding(0, pt + T.dp(4), 0, pb);
        col.addView(t, new LinearLayout.LayoutParams(width > 0 ? width : W.WC, W.WC));
    }

    static void loadImage(ChatScreen s, ImageView iv, String url, boolean gif, int px) {
        String src = gif ? gifLite(url, "200w") : url;
        iv.setTag(src);
        if (gif) {
            Img.get(src, Math.max(px, T.dp(200)), true, (d, b) -> {
                if (!src.equals(iv.getTag())) return;
                if (d == null && b == null && !src.equals(url)) {
                    loadImage(s, iv, url, false, px);
                    return;
                }
                fitNatural(iv, url, d != null ? d.getIntrinsicWidth() : b != null ? b.getWidth() : 0, d != null ? d.getIntrinsicHeight() : b != null ? b.getHeight() : 0, true);
                setDrawable(iv, d, b, true);
                s.trackAnim(iv);
            });
            return;
        }
        Bitmap pre = recent.get(base(url));
        if (pre != null) iv.setImageBitmap(pre);
        Img.get(src, Math.max(px, T.dp(120)), false, (d, b) -> {
            if (!src.equals(iv.getTag()) || b == null) return;
            fitNatural(iv, url, b.getWidth(), b.getHeight(), false);
            iv.setImageBitmap(b);
            if (url.startsWith("file:")) recent.put(base(url), b);
            if (pre == null) {
                iv.setAlpha(0f);
                iv.animate().alpha(1).setDuration(160).start();
            }
            dims.putIfAbsent(base(url), new int[]{b.getWidth(), b.getHeight()});
        });
    }

    static void fitNatural(ImageView iv, String url, int w, int h, boolean gif) {
        if (w <= 0 || h <= 0 || iv.getTag(0x7f0f0002) != null) return;
        String k = base(url);
        if (url.matches("(?s).*#(?:[^#]*&)?vw=\\d+&vh=\\d+.*")) return;
        if (gif && !url.toLowerCase().contains(".gif") && dims.containsKey(k)) return;
        int[] known = dims.get(k);
        if (known != null && known[0] * h == known[1] * w && iv.getTag(0x7f0f0001) != null) return;
        dims.put(k, new int[]{w, h});
        int mw = gif ? 260 : 420, mh = gif ? 200 : 360;
        float sc = Math.min(1f, Math.min(mw / (float) w, mh / (float) h));
        android.view.ViewGroup.LayoutParams lp = iv.getLayoutParams();
        if (lp == null) return;
        int nw = T.dp(Math.max(1, Math.round(w * sc))), nh = T.dp(Math.max(1, Math.round(h * sc)));
        int maxW = Math.round((iv.getContext().getResources().getDisplayMetrics().widthPixels - T.dp(32)) * .95f);
        if (nw > maxW) {
            nh = Math.round(nh * (maxW / (float) nw));
            nw = maxW;
        }
        if (lp.width != nw || lp.height != nh) {
            lp.width = nw;
            lp.height = nh;
            iv.setLayoutParams(lp);
        }
        iv.setTag(0x7f0f0001, Boolean.TRUE);
    }

    public static void setDrawable(ImageView iv, Drawable d, Bitmap b, boolean play) {
        if (d != null) {
            iv.setImageDrawable(d);
            if (play && Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).start();
        } else if (b != null) iv.setImageBitmap(b);
    }

    public static void inlineGif(ChatScreen s, LinearLayout col, String url) {
        Context c = s.a;
        RoundImage iv = new RoundImage(c);
        iv.corners(12, 12, 12, 12);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setMaxHeight(T.dp(220));
        LinearLayout.LayoutParams lp = W.lpm(T.dp(200), T.dp(150), 0, 4, 0, 4);
        iv.setLayoutParams(lp);
        String src = gifLite(url, "200w");
        iv.setTag(src);
        Img.get(Http.abs(src), T.dp(220), true, (d, b) -> {
            if (!src.equals(iv.getTag())) return;
            int w = d != null ? d.getIntrinsicWidth() : b != null ? b.getWidth() : 0;
            int h = d != null ? d.getIntrinsicHeight() : b != null ? b.getHeight() : 0;
            if (w > 0 && h > 0) {
                float sc = Math.min(T.dp(200) / (float) w, T.dp(220) / (float) h);
                sc = Math.min(sc, Math.max(1f, T.density * .9f));
                lp.width = Math.round(w * sc);
                lp.height = Math.round(h * sc);
                iv.setLayoutParams(lp);
            }
            setDrawable(iv, d, b, true);
            s.trackAnim(iv);
        });
        List<Fmt.Att> one = new ArrayList<>();
        Fmt.Att a = new Fmt.Att();
        a.name = "GIF";
        a.mime = "image/gif";
        a.url = url;
        one.add(a);
        iv.setOnClickListener(v -> Viewer.open(s, one, 0, null));
        col.addView(iv);
    }

    static View videoThumb(ChatScreen s, Fmt.Att a, boolean hasCap, int maxW, Chats.Msg m, List<Fmt.Att> album, int index) {
        Context c = s.a;
        int[] bx = album != null ? new int[]{0, 0} : box(a, 420, 360, 320, 16f / 9f);
        FrameLayout f = new FrameLayout(c);
        if (album == null) {
            f.setLayoutParams(new LinearLayout.LayoutParams(bx[0], Math.min(bx[1], T.dp(360))));
            fitWidth(f, maxW);
        }
        RoundImage poster = new RoundImage(c);
        if (album == null) poster.corners(12, 12, hasCap ? 0 : 12, hasCap ? 0 : 12);
        poster.setBackgroundColor(0x59000000);
        f.addView(poster, new FrameLayout.LayoutParams(W.MP, W.MP));
        TextView dur = W.text(c, "", album != null ? 11 : 12, 600, 0xffffffff);
        dur.setFontFeatureSettings("tnum");
        dur.setPadding(T.dp(album != null ? 6 : 7), T.dp(2), T.dp(album != null ? 6 : 7), T.dp(2));
        dur.setBackground(W.round(0x73000000, 99));
        dur.setVisibility(View.GONE);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.START);
        dp.leftMargin = dp.topMargin = T.dp(album != null ? 6 : 8);
        f.addView(dur, dp);
        int pc = album != null ? 40 : 50;
        ImageView play = new ImageView(c);
        play.setImageDrawable(Svg.fill(album != null ? 20 : 24, 0xffffffff, "M8 5.5v13l11-6.5z"));
        play.setScaleType(ImageView.ScaleType.CENTER);
        play.setPadding(T.dp(3), 0, 0, 0);
        play.setBackground(W.oval(0x73000000));
        f.addView(play, new FrameLayout.LayoutParams(T.dp(pc), T.dp(pc), Gravity.CENTER));
        String meta = base(a.url) + ".vp.json";
        if (a.url.startsWith("file:")) {
            JSONObject cached = vmeta.get(meta);
            if (cached != null) applyMeta(s, f, poster, dur, cached, album == null, maxW);
            else App.bg(() -> {
                try {
                    android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                    r.setDataSource(base(a.url).substring(5));
                    Bitmap fr = r.getFrameAtTime(0);
                    String ds = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    r.release();
                    if (fr == null) return;
                    float sc = Math.min(1f, 480f / Math.max(fr.getWidth(), fr.getHeight()));
                    Bitmap sm = Bitmap.createScaledBitmap(fr, Math.max(1, Math.round(fr.getWidth() * sc)), Math.max(1, Math.round(fr.getHeight() * sc)), true);
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    sm.compress(Bitmap.CompressFormat.JPEG, 80, bo);
                    JSONObject o = new JSONObject().put("p", "data:image/jpeg;base64," + android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP)).put("d", ds == null ? 0 : Long.parseLong(ds) / 1000.0).put("w", a.w > 0 ? a.w : fr.getWidth()).put("h", a.h > 0 ? a.h : fr.getHeight());
                    vmeta.put(meta, o);
                    App.ui(() -> applyMeta(s, f, poster, dur, o, album == null, maxW));
                } catch (Throwable e) {
                }
            });
        } else if (a.url.startsWith("/uploads/") && a.url.matches("(?i)^[^#?]+\\.(mp4|webm|mov|mkv|m4v)([#?].*)?$")) {
            JSONObject cached = vmeta.get(meta);
            if (cached != null) applyMeta(s, f, poster, dur, cached, album == null, maxW);
            else App.bg(() -> {
                try {
                    byte[] b = Http.bytes(meta, 2 * 1024 * 1024);
                    JSONObject o = new JSONObject(new String(b, "UTF-8"));
                    if (o.optString("p").startsWith("data:image/jpeg;base64,")) {
                        vmeta.put(meta, o);
                        App.ui(() -> applyMeta(s, f, poster, dur, o, album == null, maxW));
                    }
                } catch (Exception e) {
                }
            });
        }
        f.setOnClickListener(v -> {
            if (album != null) Viewer.open(s, album, index, m);
            else {
                List<Fmt.Att> one = new ArrayList<>();
                one.add(a);
                Viewer.open(s, one, 0, m);
            }
        });
        if (m != null) f.setOnLongClickListener(v -> {
            s.menuFor(m, v, null);
            return true;
        });
        return f;
    }

    static void applyMeta(ChatScreen s, FrameLayout f, ImageView poster, TextView dur, JSONObject o, boolean resize, int maxW) {
        try {
            byte[] jpg = android.util.Base64.decode(o.optString("p").substring("data:image/jpeg;base64,".length()), android.util.Base64.DEFAULT);
            Bitmap b = BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
            if (b != null) poster.setImageBitmap(b);
        } catch (Exception e) {
        }
        double d = o.optDouble("d", 0);
        if (d > 0) {
            dur.setText(fmtT(d));
            dur.setVisibility(View.VISIBLE);
        }
        int w = o.optInt("w"), h = o.optInt("h");
        if (resize && w > 0 && h > 0) {
            float sc = Math.min(1f, Math.min(420f / w, 360f / h));
            android.view.ViewGroup.LayoutParams lp = f.getLayoutParams();
            lp.width = T.dp(Math.max(1, Math.round(w * sc)));
            lp.height = T.dp(Math.max(1, Math.round(h * sc)));
            if (lp.width > maxW) {
                lp.height = Math.round(lp.height * (maxW / (float) lp.width));
                lp.width = maxW;
            }
            f.setLayoutParams(lp);
        }
    }

    public static String fmtT(double s) {
        int t = (int) Math.max(0, Math.floor(s));
        if (t >= 3600) return (t / 3600) + ":" + String.format("%02d:%02d", (t % 3600) / 60, t % 60);
        return (t / 60) + ":" + String.format("%02d", t % 60);
    }

    static View albumGrid(ChatScreen s, List<Fmt.Att> med, boolean capBelow, Chats.Msg m) {
        Context c = s.a;
        int n = Math.min(med.size(), 5);
        int width = Math.min(T.dp(400), Math.round(c.getResources().getDisplayMetrics().widthPixels * .72f));
        int gap = T.dp(2);
        FrameLayout grid = new FrameLayout(c) {
            final Path clip = new Path();

            protected void dispatchDraw(Canvas cv) {
                clip.reset();
                float r = T.dpf(12);
                clip.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), new float[]{r, r, r, r, capBelow ? 0 : r, capBelow ? 0 : r, capBelow ? 0 : r, capBelow ? 0 : r}, Path.Direction.CW);
                int sv = cv.save();
                cv.clipPath(clip);
                super.dispatchDraw(cv);
                cv.restoreToCount(sv);
            }
        };
        grid.setBackgroundColor(T.mixA(T.text, .08f));
        int[][] cells;
        int H;
        int cw = width;
        switch (n) {
            case 1:
                cells = new int[][]{{0, 0, cw, T.dp(260)}};
                H = T.dp(260);
                break;
            case 2: {
                int w2 = (cw - gap) / 2;
                cells = new int[][]{{0, 0, w2, T.dp(200)}, {w2 + gap, 0, cw - w2 - gap, T.dp(200)}};
                H = T.dp(200);
                break;
            }
            case 3: {
                int wl = Math.round((cw - gap) * .6f);
                cells = new int[][]{{0, 0, wl, T.dp(282)}, {wl + gap, 0, cw - wl - gap, T.dp(140)}, {wl + gap, T.dp(142), cw - wl - gap, T.dp(140)}};
                H = T.dp(282);
                break;
            }
            case 4: {
                int w2 = (cw - gap) / 2;
                cells = new int[][]{{0, 0, w2, T.dp(150)}, {w2 + gap, 0, cw - w2 - gap, T.dp(150)}, {0, T.dp(152), w2, T.dp(150)}, {w2 + gap, T.dp(152), cw - w2 - gap, T.dp(150)}};
                H = T.dp(302);
                break;
            }
            default: {
                int w2 = (cw - gap) / 2, w3 = (cw - 2 * gap) / 3;
                cells = new int[][]{{0, 0, w2, T.dp(170)}, {w2 + gap, 0, cw - w2 - gap, T.dp(170)}, {0, T.dp(172), w3, T.dp(120)}, {w3 + gap, T.dp(172), w3, T.dp(120)}, {2 * (w3 + gap), T.dp(172), cw - 2 * (w3 + gap), T.dp(120)}};
                H = T.dp(292);
            }
        }
        for (int i = 0; i < n; i++) {
            Fmt.Att a = med.get(i);
            int[] cl = cells[i];
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cl[2], cl[3]);
            lp.leftMargin = cl[0];
            lp.topMargin = cl[1];
            final int idx = i;
            if (a.video()) {
                View v = videoThumb(s, a, false, width, m, med, idx);
                grid.addView(v, lp);
            } else {
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setTag(a.url);
                Bitmap pre = recent.get(base(a.url));
                if (pre != null) iv.setImageBitmap(pre);
                Img.get(a.url, Math.max(cl[2], cl[3]), false, (d, b) -> {
                    if (b != null && a.url.equals(iv.getTag())) {
                        iv.setImageBitmap(b);
                        if (a.url.startsWith("file:")) recent.put(base(a.url), b);
                    }
                });
                iv.setOnClickListener(v -> Viewer.open(s, med, idx, m));
                iv.setOnLongClickListener(v -> {
                    s.menuFor(m, v, null);
                    return true;
                });
                grid.addView(iv, lp);
            }
        }
        grid.setLayoutParams(new LinearLayout.LayoutParams(width, H));
        return grid;
    }

    static View carousel(ChatScreen s, List<Fmt.Att> med, boolean capBelow, Chats.Msg m) {
        Context c = s.a;
        int width = Math.min(T.dp(360), Math.round(c.getResources().getDisplayMetrics().widthPixels * .72f));
        Fmt.Att f0 = med.get(0);
        float ar = f0.w > 0 && f0.h > 0 ? Math.max(.8f, Math.min(1.8f, f0.w / (float) f0.h)) : 4f / 3f;
        int height = Math.round(width / ar);
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(T.mixA(T.text, .08f));
        root.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) {
                float r = T.dpf(12);
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight() + (capBelow ? Math.round(r) : 0), r);
            }
        });
        root.setClipToOutline(true);
        HorizontalScrollView hs = new HorizontalScrollView(c) {
            float downX, downY;
            int startPage;
            boolean horiz;
            android.view.VelocityTracker vt;

            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent e) {
                int act = e.getActionMasked();
                if (act == android.view.MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX();
                    downY = e.getRawY();
                    horiz = false;
                    startPage = Math.round(getScrollX() / (float) Math.max(1, getWidth()));
                    if (vt != null) vt.recycle();
                    vt = android.view.VelocityTracker.obtain();
                }
                if (vt != null) vt.addMovement(e);
                if (act == android.view.MotionEvent.ACTION_MOVE && !horiz) {
                    float dx = Math.abs(e.getRawX() - downX), dy = Math.abs(e.getRawY() - downY);
                    if (dx > T.dp(6) && dx > dy) {
                        horiz = true;
                        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if ((act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) && horiz) {
                    float dx = e.getRawX() - downX;
                    float v = 0;
                    if (vt != null) {
                        vt.computeCurrentVelocity(1);
                        v = vt.getXVelocity() / T.density;
                    }
                    int pg = startPage;
                    if (dx < -getWidth() * .12f || v < -.3f) pg++;
                    else if (dx > getWidth() * .12f || v > .3f) pg--;
                    pg = Math.max(0, Math.min(med.size() - 1, pg));
                    android.view.MotionEvent ce = android.view.MotionEvent.obtain(e);
                    ce.setAction(android.view.MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(ce);
                    ce.recycle();
                    smoothScrollTo(pg * getWidth(), 0);
                    horiz = false;
                    return true;
                }
                return super.dispatchTouchEvent(e);
            }
        };
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout track = W.row(c);
        for (int i = 0; i < med.size(); i++) {
            Fmt.Att a = med.get(i);
            final int idx = i;
            if (a.video()) {
                View v = videoThumb(s, a, false, width, m, med, idx);
                track.addView(v, new LinearLayout.LayoutParams(width, height));
            } else {
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Img.into(iv, a.url, Math.max(width, height));
                iv.setOnClickListener(v -> Viewer.open(s, med, idx, m));
                track.addView(iv, new LinearLayout.LayoutParams(width, height));
            }
        }
        hs.addView(track);
        root.addView(hs, new FrameLayout.LayoutParams(width, height));
        TextView cnt = W.text(c, "1 / " + med.size(), 11, 600, 0xffffffff);
        cnt.setFontFeatureSettings("tnum");
        cnt.setPadding(T.dp(8), T.dp(2), T.dp(8), T.dp(2));
        cnt.setBackground(W.round(0x73000000, 99));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.END);
        cp.topMargin = cp.rightMargin = T.dp(8);
        root.addView(cnt, cp);
        LinearLayout dots = W.row(c);
        for (int i = 0; i < med.size(); i++) {
            View d = new View(c);
            d.setBackground(W.round(i == 0 ? 0xffffffff : 0x80ffffff, 99));
            d.setElevation(T.dpf(1));
            LinearLayout.LayoutParams dl = W.lp(T.dp(i == 0 ? 16 : 6), T.dp(6));
            dl.leftMargin = i > 0 ? T.dp(5) : 0;
            dots.addView(d, dl);
        }
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dp.bottomMargin = T.dp(8);
        root.addView(dots, dp);
        hs.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            int ci = Math.round(x / (float) Math.max(1, width));
            cnt.setText((ci + 1) + " / " + med.size());
            for (int i = 0; i < dots.getChildCount(); i++) {
                View d = dots.getChildAt(i);
                LinearLayout.LayoutParams dl = (LinearLayout.LayoutParams) d.getLayoutParams();
                int want = T.dp(i == ci ? 16 : 6);
                if (dl.width != want) {
                    dl.width = want;
                    d.setLayoutParams(dl);
                    d.setBackground(W.round(i == ci ? 0xffffffff : 0x80ffffff, 99));
                }
            }
        });
        root.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return root;
    }

    static View fileChip(ChatScreen s, Fmt.Att a) {
        Context c = s.a;
        LinearLayout r = W.row(c);
        r.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
        r.setBackground(W.round(0x33000000, 12));
        ImageView ic = new ImageView(c);
        ic.setImageDrawable(Svg.stroke(20, T.text, 2, "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6"));
        r.addView(ic, W.lp(T.dp(20), T.dp(20)));
        TextView n = W.text(c, a.name, 14, 400, T.text);
        n.setPaintFlags(n.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        W.ellipsize(n);
        r.addView(n, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        r.setOnClickListener(v -> Download.open(s.a, a));
        return r;
    }
}
