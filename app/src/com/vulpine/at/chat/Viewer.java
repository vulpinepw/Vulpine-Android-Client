package com.vulpine.at.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimatedImageDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import java.net.HttpCookie;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Viewer {
    static FrameLayout root;
    static View cur;
    static MediaPlayer player;
    static int index;
    static List<Fmt.Att> items;
    static TextView pos;
    static ImageView prev, next;

    public static void open(ChatScreen s, List<Fmt.Att> list, int i, Chats.Msg m) {
        open(s.a, list, i, m);
    }

    public static void open(MainActivity a, List<Fmt.Att> list, int i, Chats.Msg m) {
        close(false);
        items = list;
        index = i;
        Context c = a;
        root = new FrameLayout(c);
        root.setBackgroundColor(0xeb000000);
        root.setClickable(true);
        root.setOnClickListener(v -> close(true));
        root.setElevation(T.dpf(60));
        a.host.addView(root, new FrameLayout.LayoutParams(W.MP, W.MP));
        root.setAlpha(0);
        root.animate().alpha(1).setDuration(200).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
        int top = Math.max(a.insetTop, T.dp(40)) + T.dp(12);
        ImageView x = circleBtn(c, Svg.stroke(22, 0xffffffff, 2, "M6 6l12 12", "M18 6L6 18"));
        x.setContentDescription("Close");
        x.setOnClickListener(v -> close(true));
        FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(T.dp(40), T.dp(40), Gravity.TOP | Gravity.END);
        xp.topMargin = top;
        xp.rightMargin = T.dp(16);
        root.addView(x, xp);
        if (list.size() > 1) {
            pos = W.text(c, "", 14, 600, 0xffffffff);
            pos.setFontFeatureSettings("tnum");
            pos.setPadding(T.dp(14), T.dp(6), T.dp(14), T.dp(6));
            pos.setBackground(W.round(0x24ffffff, 99));
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(W.WC, W.WC, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            pp.topMargin = top + T.dp(4);
            root.addView(pos, pp);
            prev = circleBtn(c, Svg.stroke(22, 0xffffffff, 2.4f, "M15 18l-6-6 6-6"));
            prev.setContentDescription("Previous");
            prev.setOnClickListener(v -> go(a, m, -1));
            next = circleBtn(c, Svg.stroke(22, 0xffffffff, 2.4f, "M9 18l6-6-6-6"));
            next.setContentDescription("Next");
            next.setOnClickListener(v -> go(a, m, 1));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(T.dp(40), T.dp(40), Gravity.CENTER_VERTICAL | Gravity.START);
            lp.leftMargin = T.dp(10);
            root.addView(prev, lp);
            FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(T.dp(40), T.dp(40), Gravity.CENTER_VERTICAL | Gravity.END);
            rp.rightMargin = T.dp(10);
            root.addView(next, rp);
        }
        show(a, m, 0);
        Ov.push(root, () -> close(true));
    }

    static ImageView circleBtn(Context c, android.graphics.drawable.Drawable d) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(d);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackground(W.oval(0x26ffffff));
        b.setElevation(T.dpf(2));
        W.press(b, .92f);
        return b;
    }

    static void go(MainActivity s, Chats.Msg m, int d) {
        int ni = index + d;
        if (ni < 0 || ni >= items.size()) return;
        index = ni;
        show(s, m, d);
    }

    static void show(MainActivity s, Chats.Msg m, int dir) {
        Context c = root.getContext();
        if (cur != null) root.removeView(cur);
        stopPlayer();
        Fmt.Att a = items.get(index);
        View v = a.video() ? video(c, a) : image(s, c, a, m);
        root.addView(v, 0, new FrameLayout.LayoutParams(W.MP, W.MP));
        cur = v;
        View media = v instanceof ZoomView ? ((ZoomView) v).iv : v;
        if (dir == 0) {
            media.setAlpha(0);
            media.setScaleX(.92f);
            media.setScaleY(.92f);
            media.animate().alpha(1).scaleX(1).scaleY(1).setDuration(280).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
        } else {
            media.setAlpha(0);
            media.setTranslationX(T.dp(36) * dir);
            media.animate().alpha(1).translationX(0).setDuration(220).setInterpolator(new android.view.animation.PathInterpolator(.2f, .8f, .2f, 1f)).start();
        }
        if (pos != null) pos.setText((index + 1) + " / " + items.size());
        if (prev != null) prev.setVisibility(index > 0 ? View.VISIBLE : View.GONE);
        if (next != null) next.setVisibility(index < items.size() - 1 ? View.VISIBLE : View.GONE);
    }

    public static boolean close(boolean anim) {
        if (root == null) return false;
        FrameLayout r = root;
        root = null;
        cur = null;
        pos = null;
        prev = next = null;
        stopPlayer();
        Ov.remove(r);
        if (anim) r.animate().alpha(0).setDuration(180).setInterpolator(T.EASE_IN).withEndAction(() -> {
            if (r.getParent() != null) ((FrameLayout) r.getParent()).removeView(r);
        }).start();
        else if (r.getParent() != null) ((FrameLayout) r.getParent()).removeView(r);
        return true;
    }

    static void stopPlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception e) {
            }
            player = null;
        }
    }

    static class ZoomView extends FrameLayout {
        final ImageView iv;
        float scale = 1, tx, ty;
        final ScaleGestureDetector sg;
        final GestureDetector gd;
        float lastX, lastY;
        float downX, downY;
        long downAt;
        Runnable onSwipeLeft, onSwipeRight, onTap;

        ZoomView(Context c) {
            super(c);
            iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP, Gravity.CENTER));
            sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector d) {
                    float ns = Math.max(1f, Math.min(5f, scale * d.getScaleFactor()));
                    float f = ns / scale;
                    float px = d.getFocusX() - getWidth() / 2f, py = d.getFocusY() - getHeight() / 2f;
                    tx = px - (px - tx) * f;
                    ty = py - (py - ty) * f;
                    scale = ns;
                    apply(false);
                    return true;
                }
            });
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                public boolean onDoubleTap(MotionEvent e) {
                    if (scale > 1.01f) {
                        scale = 1;
                        tx = ty = 0;
                    } else {
                        float ns = 2.5f;
                        float px = e.getX() - getWidth() / 2f, py = e.getY() - getHeight() / 2f;
                        tx = -px * (ns - 1);
                        ty = -py * (ns - 1);
                        scale = ns;
                    }
                    apply(true);
                    return true;
                }

                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (onTap != null) onTap.run();
                    return true;
                }

                public void onLongPress(MotionEvent e) {
                    performLongClick();
                }
            });
        }

        void apply(boolean anim) {
            float maxX = Math.max(0, (getWidth() * scale - getWidth()) / 2f), maxY = Math.max(0, (getHeight() * scale - getHeight()) / 2f);
            tx = Math.max(-maxX, Math.min(maxX, tx));
            ty = Math.max(-maxY, Math.min(maxY, ty));
            if (anim) iv.animate().scaleX(scale).scaleY(scale).translationX(tx).translationY(ty).setDuration(150).start();
            else {
                iv.setScaleX(scale);
                iv.setScaleY(scale);
                iv.setTranslationX(tx);
                iv.setTranslationY(ty);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            sg.onTouchEvent(e);
            gd.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = downX = e.getX();
                    lastY = downY = e.getY();
                    downAt = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (scale > 1.01f && !sg.isInProgress() && e.getPointerCount() == 1) {
                        tx += e.getX() - lastX;
                        ty += e.getY() - lastY;
                        apply(false);
                    }
                    lastX = e.getX();
                    lastY = e.getY();
                    break;
                case MotionEvent.ACTION_UP:
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (scale <= 1.01f && Math.abs(dx) > T.dp(60) && Math.abs(dx) > 1.5f * Math.abs(dy)) {
                        if (dx < 0 && onSwipeLeft != null) onSwipeLeft.run();
                        if (dx > 0 && onSwipeRight != null) onSwipeRight.run();
                    }
                    break;
            }
            return true;
        }
    }

    static View image(MainActivity s, Context c, Fmt.Att a, Chats.Msg m) {
        ZoomView z = new ZoomView(c);
        z.onTap = () -> close(true);
        z.onSwipeLeft = () -> go(s, m, 1);
        z.onSwipeRight = () -> go(s, m, -1);
        int maxPx = Math.max(c.getResources().getDisplayMetrics().widthPixels, c.getResources().getDisplayMetrics().heightPixels);
        Img.get(a.url, maxPx, a.gif(), (d, b) -> {
            if (d != null) {
                z.iv.setImageDrawable(d);
                if (Build.VERSION.SDK_INT >= 28 && d instanceof AnimatedImageDrawable) ((AnimatedImageDrawable) d).start();
            } else if (b != null) z.iv.setImageBitmap(b);
        });
        int top = Math.max(s.insetTop, T.dp(40)) + T.dp(12);
        ImageView save = circleBtn(c, com.vulpine.at.ui.Ic.svg("<svg viewBox=\"0 -960 960 960\" fill=\"#e3e3e3\"><path d=\"M480-320 280-520l56-58 104 104v-326h80v326l104-104 56 58-200 200ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Z\"/></svg>", 24, 0xffe3e3e3));
        save.setContentDescription("Save image");
        save.setOnClickListener(v -> Download.save(s, a.url, safeName(a), a.mime));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(T.dp(40), T.dp(40), Gravity.TOP | Gravity.END);
        sp.topMargin = top;
        sp.rightMargin = T.dp(64);
        z.addView(save, sp);
        z.setLongClickable(true);
        z.setOnLongClickListener(v -> {
            Ov.Menu menu = new Ov.Menu(c);
            menu.item("Copy image", Svg.stroke(16, T.text, 1.8f, "M8 8h11v11H8z", "M16 8V5H5v11h3"), false, () -> copyImage(c, a));
            if (!a.url.startsWith("file:")) menu.item("Copy link", Svg.stroke(16, T.text, 1.8f, "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"), false, () -> {
                ((ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("link", Http.abs(a.url)));
                Ov.toast("Link copied");
            });
            menu.item("Save image", Svg.stroke(16, T.text, 1.8f, "M12 3v12", "M7 10l5 5 5-5", "M5 21h14"), false, () -> Download.save(s, a.url, safeName(a), a.mime));
            menu.item("Share", Svg.stroke(16, T.text, 1.8f, "M4 12v8h16v-8", "M16 6l-4-4-4 4", "M12 2v13"), false, () -> share(s, a));
            if (!a.url.startsWith("file:")) menu.item("Open in browser", Svg.stroke(16, T.text, 1.8f, "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6", "M15 3h6v6", "M10 14L21 3"), false, () -> Download.openExternal(s, Http.abs(a.url)));
            menu.showAt((int) z.lastX, (int) z.lastY, false);
            return true;
        });
        return z;
    }

    static String safeName(Fmt.Att a) {
        String n = a.name == null || a.name.isEmpty() ? "image" : a.name.replaceAll("[^\\w.\\- ]", "_");
        if (!n.contains(".")) {
            String e = a.url.replaceAll("[#?].*$", "");
            n += e.contains(".") ? e.substring(e.lastIndexOf('.')) : ".png";
        }
        return n;
    }

    static void copyImage(Context c, Fmt.Att a) {
        App.bg(() -> {
            java.io.File f = Img.file(a.url);
            App.ui(() -> {
                if (f == null) {
                    Ov.toast("Couldn't copy the image", true);
                    return;
                }
                try {
                    Uri u = com.vulpine.at.Share.uriFor(c, f, safeName(a));
                    ((ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newUri(c.getContentResolver(), "image", u));
                    Ov.toast("Image copied");
                } catch (Exception e) {
                    Ov.toast("Couldn't copy the image", true);
                }
            });
        });
    }

    static void share(MainActivity a, Fmt.Att att) {
        App.bg(() -> {
            java.io.File f = Img.file(att.url);
            App.ui(() -> {
                if (f == null) return;
                try {
                    Uri u = com.vulpine.at.Share.uriFor(a, f, safeName(att));
                    Intent i = new Intent(Intent.ACTION_SEND).setType(att.mime).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    a.startActivity(Intent.createChooser(i, null));
                } catch (Exception e) {
                    Ov.toast("Could not share", true);
                }
            });
        });
    }

    static Map<String, String> cookieHeaders(String url) {
        Map<String, String> h = new HashMap<>();
        try {
            String ck = Http.cookieFor(Http.abs(url));
            if (ck != null && !ck.isEmpty()) h.put("Cookie", ck);
            h.put("User-Agent", Http.userAgent);
        } catch (Exception e) {
        }
        return h;
    }

    static View video(Context c, Fmt.Att a) {
        FrameLayout f = new FrameLayout(c);
        f.setClickable(true);
        f.setOnClickListener(v -> close(true));
        FrameLayout box = new FrameLayout(c);
        box.setBackgroundColor(0xff000000);
        box.setClickable(true);
        TextureView tv = new TextureView(c);
        box.addView(tv, new FrameLayout.LayoutParams(W.MP, W.MP, Gravity.CENTER));
        int sw = c.getResources().getDisplayMetrics().widthPixels, sh = c.getResources().getDisplayMetrics().heightPixels;
        f.addView(box, new FrameLayout.LayoutParams(sw, Math.round(sh * .5f), Gravity.CENTER));
        W.Spin spin = new W.Spin(c, 0xffffffff, 3);
        box.addView(spin, new FrameLayout.LayoutParams(T.dp(46), T.dp(46), Gravity.CENTER));
        ImageView big = new ImageView(c);
        big.setImageDrawable(Svg.fill(30, 0xffffffff, "M8 5.5v13l11-6.5z"));
        big.setScaleType(ImageView.ScaleType.CENTER);
        big.setPadding(T.dp(4), 0, 0, 0);
        big.setBackground(W.oval(0x80000000));
        big.setVisibility(View.GONE);
        box.addView(big, new FrameLayout.LayoutParams(T.dp(64), T.dp(64), Gravity.CENTER));
        LinearLayout bar = W.col(c);
        bar.setPadding(T.dp(8), T.dp(26), T.dp(8), T.dp(6));
        bar.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xb8000000, 0x59000000, 0}));
        SeekBar sb = new SeekBar(c);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffffffff));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffffffff));
        sb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x40ffffff));
        sb.setSecondaryProgressTintList(android.content.res.ColorStateList.valueOf(0x59ffffff));
        bar.addView(sb, W.lp(W.MP, T.dp(16)));
        LinearLayout row = W.row(c);
        ImageView pp = new ImageView(c);
        pp.setScaleType(ImageView.ScaleType.CENTER);
        row.addView(pp, W.lp(T.dp(34), T.dp(34)));
        ImageView mute = new ImageView(c);
        mute.setScaleType(ImageView.ScaleType.CENTER);
        row.addView(mute, W.lp(T.dp(34), T.dp(34)));
        TextView time = W.text(c, "0:00 / 0:00", 12, 500, 0xffffffff);
        time.setFontFeatureSettings("tnum");
        row.addView(time, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
        row.addView(W.flex(c));
        TextView rate = W.text(c, "1x", 13, 700, 0xffffffff);
        rate.setGravity(Gravity.CENTER);
        rate.setMinWidth(T.dp(42));
        row.addView(rate, W.lp(W.WC, T.dp(34)));
        bar.addView(row);
        box.addView(bar, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.BOTTOM));
        float[] rates = {.5f, 1f, 1.25f, 1.5f, 2f};
        float[] cr = {1f};
        boolean[] muted = {"1".equals(App.prefs.getString("vpl-mute", "0"))};
        Runnable[] ui = new Runnable[1];
        Runnable hide = () -> {
            if (player != null && player.isPlaying()) bar.animate().alpha(0).translationY(T.dp(6)).setDuration(220).start();
        };
        Runnable show = () -> {
            bar.animate().alpha(1).translationY(0).setDuration(220).start();
            bar.removeCallbacks(hide);
            bar.postDelayed(hide, 2600);
        };
        box.setOnClickListener(v -> {
            if (bar.getAlpha() > .5f && player != null && player.isPlaying()) hide.run();
            else show.run();
        });
        ui[0] = () -> {
            boolean pl = player != null && player.isPlaying();
            pp.setImageDrawable(pl ? Svg.fill(20, 0xffffffff, "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z") : Svg.fill(20, 0xffffffff, "M8 5.5v13l11-6.5z"));
            mute.setImageDrawable(muted[0] ? Svg.stroke(20, 0xffffffff, 2, "M11 5L6 9H2v6h4l5 4V5z", "M23 9l-6 6", "M17 9l6 6") : Svg.stroke(20, 0xffffffff, 2, "M11 5L6 9H2v6h4l5 4V5z", "M15.54 8.46a5 5 0 0 1 0 7.07", "M19.07 4.93a10 10 0 0 1 0 14.14"));
            big.setVisibility(!pl && player != null && spin.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
            if (player != null) {
                try {
                    int d = player.getDuration(), p = player.getCurrentPosition();
                    if (d > 0) {
                        sb.setMax(d);
                        sb.setProgress(p);
                        time.setText(Media.fmtT(p / 1000.0) + " / " + Media.fmtT(d / 1000.0));
                    }
                } catch (Exception e) {
                }
            }
            rate.setText((cr[0] == (int) cr[0] ? String.valueOf((int) cr[0]) : String.valueOf(cr[0])) + "x");
        };
        Runnable tick = new Runnable() {
            public void run() {
                if (!f.isAttachedToWindow()) return;
                ui[0].run();
                f.postDelayed(this, 250);
            }
        };
        View.OnClickListener toggle = v -> {
            if (player == null) return;
            if (player.isPlaying()) player.pause();
            else player.start();
            ui[0].run();
            show.run();
        };
        pp.setOnClickListener(toggle);
        big.setOnClickListener(toggle);
        mute.setOnClickListener(v -> {
            muted[0] = !muted[0];
            App.prefs.edit().putString("vpl-mute", muted[0] ? "1" : "0").apply();
            if (player != null) player.setVolume(muted[0] ? 0 : 1, muted[0] ? 0 : 1);
            ui[0].run();
        });
        rate.setOnClickListener(v -> {
            Ov.Menu m = new Ov.Menu(c);
            for (float r : rates) m.item((r == (int) r ? String.valueOf((int) r) : String.valueOf(r)) + "x", null, false, () -> {
                cr[0] = r;
                if (player != null && Build.VERSION.SDK_INT >= 23) try {
                    boolean was = player.isPlaying();
                    player.setPlaybackParams(player.getPlaybackParams().setSpeed(r));
                    if (!was) player.pause();
                } catch (Exception e) {
                }
                ui[0].run();
            });
            int[] l = new int[2];
            v.getLocationInWindow(l);
            m.showAt(l[0] + v.getWidth(), l[1] - T.dp(220), true);
        });
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && player != null) player.seekTo(p);
            }

            public void onStartTrackingTouch(SeekBar s) {
                bar.removeCallbacks(hide);
            }

            public void onStopTrackingTouch(SeekBar s) {
                show.run();
            }
        });
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                try {
                    MediaPlayer mp = new MediaPlayer();
                    player = mp;
                    mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
                    String url = Http.abs(a.url.replaceAll("#.*$", ""));
                    java.io.File cached = Img.cached(a.url);
                    if (cached != null) mp.setDataSource(cached.getPath());
                    else mp.setDataSource(c, Uri.parse(url), cookieHeaders(url));
                    mp.setSurface(new Surface(st));
                    mp.setOnPreparedListener(x -> {
                        spin.setVisibility(View.GONE);
                        int vw = x.getVideoWidth(), vh = x.getVideoHeight();
                        if (vw > 0 && vh > 0) {
                            int bw = sw, bh = Math.min(Math.round(sh * .8f), Math.round(sw * vh / (float) vw));
                            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
                            lp.width = bw;
                            lp.height = bh;
                            box.setLayoutParams(lp);
                            float s = Math.min(bw / (float) vw, bh / (float) vh);
                            FrameLayout.LayoutParams tl = (FrameLayout.LayoutParams) tv.getLayoutParams();
                            tl.width = Math.round(vw * s);
                            tl.height = Math.round(vh * s);
                            tv.setLayoutParams(tl);
                        }
                        x.setVolume(muted[0] ? 0 : 1, muted[0] ? 0 : 1);
                        x.start();
                        ui[0].run();
                        show.run();
                    });
                    mp.setOnBufferingUpdateListener((x, pct) -> {
                        try {
                            sb.setSecondaryProgress(Math.round(x.getDuration() * pct / 100f));
                        } catch (Exception e) {
                        }
                    });
                    mp.setOnInfoListener((x, what, extra) -> {
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) spin.setVisibility(View.VISIBLE);
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) spin.setVisibility(View.GONE);
                        return false;
                    });
                    mp.setOnCompletionListener(x -> {
                        ui[0].run();
                        show.run();
                    });
                    mp.prepareAsync();
                    f.post(tick);
                } catch (Exception e) {
                    Ov.toast("Could not play the video", true);
                }
            }

            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                stopPlayer();
                return true;
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
            }

            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        return f;
    }
}
