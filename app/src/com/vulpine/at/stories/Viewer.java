package com.vulpine.at.stories;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Viewer {
    static final String[] REACTS = {"❤️", "🔥", "😂", "😮", "😢", "👏", "🎉", "👍"};
    static Viewer cur;
    static boolean muted;

    final MainActivity a;
    final List<JSONObject> list;
    int u, i;
    final FrameLayout root, stage, media, ovl;
    final LinearLayout segs, head, foot;
    final TextView cap;
    JSONObject curS;
    long startT, elapsed;
    boolean paused, closed, held;
    final long dur = 6000;
    MediaPlayer mp;
    TextureView tv;
    final Set<String> seenSent = new HashSet<>();
    int vstTop, vstBot, ime;
    EditText input;
    LinearLayout quick;
    ImageView heartB;
    AudioFocusRequest afr;
    Application.ActivityLifecycleCallbacks life;
    float downX, downY, lastY, vel;
    long downT, lastT;
    boolean moved, tracking;
    final Runnable holdR = () -> {
        held = true;
        pause(true);
    };
    final Runnable tick = this::tick;

    public static void open(MainActivity a, List<JSONObject> list, int ui, View from, int startIdx) {
        if (a == null || list.isEmpty()) return;
        if (cur != null) cur.close(true);
        new Viewer(a, list, ui, from, startIdx);
    }

    Viewer(MainActivity a, List<JSONObject> list, int ui, View from, int startIdx) {
        this.a = a;
        this.list = list;
        this.u = ui;
        cur = this;
        muted = false;
        Context c = a;
        vstTop = Math.max(a.insetTop, T.dp(24)) + T.dp(20);
        vstBot = Math.max(a.insetBottom, T.dp(16)) + T.dp(26);
        root = new FrameLayout(c);
        root.setBackgroundColor(0xeb000000);
        root.setClickable(true);
        root.setContentDescription("Stories");
        stage = new FrameLayout(c) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return touch(e);
            }
        };
        stage.setBackgroundColor(0xff000000);
        stage.setClickable(true);
        root.addView(stage, new FrameLayout.LayoutParams(W.MP, W.MP));
        media = new FrameLayout(c);
        stage.addView(media, new FrameLayout.LayoutParams(W.MP, W.MP));
        ovl = new FrameLayout(c);
        stage.addView(ovl, new FrameLayout.LayoutParams(W.MP, W.MP));
        segs = W.row(c);
        FrameLayout.LayoutParams sp = W.flp(W.MP, Math.round(T.dpf(2.5f)), Gravity.TOP);
        sp.leftMargin = sp.rightMargin = T.dp(10);
        sp.topMargin = vstTop;
        cap = W.text(c, "", 15, 400, 0xffffffff);
        cap.setGravity(Gravity.CENTER);
        T.lh(cap, 21);
        cap.setPadding(T.dp(16), T.dp(10), T.dp(16), T.dp(10));
        cap.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0, 0x80000000}));
        FrameLayout.LayoutParams cp = W.flp(W.MP, W.WC, Gravity.BOTTOM);
        cp.bottomMargin = vstBot + T.dp(66);
        stage.addView(cap, cp);
        head = W.row(c);
        head.setPadding(T.dp(12), vstTop + T.dp(12), T.dp(8), T.dp(26));
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x73000000, 0}));
        stage.addView(head, W.flp(W.MP, W.WC, Gravity.TOP));
        stage.addView(segs, sp);
        foot = W.col(c);
        foot.setPadding(T.dp(10), T.dp(10), T.dp(10), vstBot);
        foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0, 0x8c000000}));
        stage.addView(foot, W.flp(W.MP, W.WC, Gravity.BOTTOM));
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int im = android.os.Build.VERSION.SDK_INT >= 30 ? ins.getInsets(WindowInsets.Type.ime()).bottom : Math.max(0, ins.getSystemWindowInsetBottom() - ins.getStableInsetBottom());
            ime = im;
            foot.setTranslationY(-Math.max(0, im - (vstBot - T.dp(10))));
            return ins;
        });
        FrameLayout host = a.host;
        host.addView(root, new FrameLayout.LayoutParams(W.MP, W.MP));
        root.requestApplyInsets();
        Ov.push(root, () -> close(false));
        if (from != null && !T.lowfx) {
            int[] l = new int[2], h = new int[2];
            from.getLocationInWindow(l);
            host.getLocationInWindow(h);
            int cx = l[0] - h[0] + from.getWidth() / 2, cy = l[1] - h[1] + from.getHeight() / 2;
            float r0 = Math.max(T.dpf(20), from.getWidth() / 2f);
            root.post(() -> {
                try {
                    float rm = (float) Math.hypot(Math.max(cx, root.getWidth() - cx), Math.max(cy, root.getHeight() - cy));
                    android.animation.Animator an = ViewAnimationUtils.createCircularReveal(root, cx, cy, r0, rm);
                    an.setDuration(380);
                    an.setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1f));
                    an.start();
                } catch (Exception e) {
                }
            });
        }
        life = new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity x, Bundle b) {
            }

            public void onActivityStarted(Activity x) {
            }

            public void onActivityResumed(Activity x) {
                if (x == a && !closed && !held && (input == null || !input.hasFocus())) pause(false);
            }

            public void onActivityPaused(Activity x) {
                if (x == a && !closed) pause(true);
            }

            public void onActivityStopped(Activity x) {
            }

            public void onActivitySaveInstanceState(Activity x, Bundle b) {
            }

            public void onActivityDestroyed(Activity x) {
            }
        };
        a.getApplication().registerActivityLifecycleCallbacks(life);
        i = startIdx >= 0 ? startIdx : firstUnseen(entry());
        show();
    }

    JSONObject entry() {
        return list.get(u);
    }

    JSONArray stories(JSONObject e) {
        JSONArray s = e.optJSONArray("stories");
        return s == null ? new JSONArray() : s;
    }

    int firstUnseen(JSONObject e) {
        JSONArray s = stories(e);
        for (int k = 0; k < s.length(); k++) if (!s.optJSONObject(k).optBoolean("seen")) return k;
        return 0;
    }

    static final class Seg extends View {
        float p;
        boolean done;
        final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

        Seg(Context c) {
            super(c);
        }

        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = T.dpf(2);
            pt.setColor(0x52ffffff);
            c.drawRoundRect(0, 0, w, h, r, r, pt);
            float f = done ? 1 : Math.max(0, Math.min(1, p));
            if (f <= 0) return;
            pt.setColor(0xffffffff);
            c.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(0, 0, w, h, r, r, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            c.drawRect(0, 0, w * f, h, pt);
            c.restore();
        }
    }

    void paintSegs() {
        segs.removeAllViews();
        JSONArray s = stories(entry());
        for (int k = 0; k < s.length(); k++) {
            Seg g = new Seg(a);
            g.done = k < i;
            LinearLayout.LayoutParams p = W.lp(0, W.MP, 1);
            if (k > 0) p.leftMargin = T.dp(3);
            segs.addView(g, p);
        }
    }

    void progress(float p) {
        View v = segs.getChildAt(i);
        if (v instanceof Seg) {
            ((Seg) v).p = p;
            v.invalidate();
        }
    }

    void tick() {
        if (closed) return;
        float p;
        if (mp != null) {
            float d = 0, t = 0;
            try {
                if (prepared) {
                    d = mp.getDuration();
                    t = mp.getCurrentPosition();
                }
            } catch (Exception e) {
            }
            p = d > 0 ? t / d : 0;
        } else if (curS != null && "video".equals(curS.optString("kind"))) p = 0;
        else {
            long now = System.currentTimeMillis();
            p = (elapsed + (paused || !imgReady ? 0 : now - startT)) / (float) dur;
        }
        progress(p);
        if (p >= 1 && !paused && mp == null) {
            step(1);
            return;
        }
        root.postDelayed(tick, 16);
    }

    boolean prepared, imgReady;

    void pause(boolean on) {
        if (on == paused) return;
        paused = on;
        if (quick != null) quick.setVisibility(on ? View.VISIBLE : View.GONE);
        if (mp != null && prepared) {
            try {
                if (on) mp.pause();
                else mp.start();
            } catch (Exception e) {
            }
        } else if (on) elapsed += System.currentTimeMillis() - startT;
        else startT = System.currentTimeMillis();
    }

    ImageView ib(String svg, float size, String label, View.OnClickListener on) {
        ImageView b = new ImageView(a);
        b.setImageDrawable(Ic.get(svg, size, 0xffffffff));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackground(W.ripple(W.oval(0), 999));
        b.setContentDescription(label);
        b.setOnClickListener(on);
        b.setLayoutParams(W.lp(T.dp(40), T.dp(40)));
        return b;
    }

    void headFor(JSONObject e, JSONObject s) {
        head.removeAllViews();
        LinearLayout who = W.row(a);
        AvatarView av = Ring.avatar(a, e, 34, 14);
        av.setLayoutParams(W.lp(T.dp(34), T.dp(34)));
        who.addView(av);
        LinearLayout t = W.col(a);
        TextView nm = W.text(a, e.optBoolean("self") ? "My story" : Store.name(e), 14, 700, 0xffffffff);
        W.ellipsize(nm);
        T.lh(nm, 17.5f);
        t.addView(nm);
        TextView sub = W.text(a, Store.ago(s.optLong("createdAt")) + (s.optBoolean("pinned") ? " · Pinned" : "") + (s.optBoolean("expired") ? " · Expired" : ""), 12, 400, 0xbfffffff);
        T.lh(sub, 15);
        t.addView(sub);
        who.addView(t, W.lpm(0, W.WC, 10, 0, 0, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1;
        head.addView(who, W.lp(0, W.WC, 1));
        if ("video".equals(s.optString("kind"))) {
            ImageView[] snd = new ImageView[1];
            snd[0] = ib(muted ? "st.viewer.mute" : "st.viewer.vol", 20, "Sound", v -> {
                muted = !muted;
                applyVolume();
                snd[0].setImageDrawable(Ic.get(muted ? "st.viewer.mute" : "st.viewer.vol", 20, 0xffffffff));
            });
            head.addView(snd[0]);
        }
        head.addView(ib("st.viewer.dots", 22, "More", v -> menu(v, e, s)));
        head.addView(ib("st.sheets.x", 22, "Close", v -> close(false)));
    }

    void footFor(JSONObject e, JSONObject s) {
        foot.removeAllViews();
        quick = null;
        input = null;
        heartB = null;
        if (s.optBoolean("mine")) {
            LinearLayout vb = W.row(a);
            vb.setPadding(T.dp(16), T.dp(8), T.dp(16), T.dp(8));
            vb.setBackground(W.round(0x29ffffff, 999));
            ImageView eye = new ImageView(a);
            eye.setImageDrawable(Ic.get("st.viewer.eye", 18, 0xffffffff));
            vb.addView(eye, W.lp(T.dp(18), T.dp(18)));
            int r = s.optInt("reactions");
            TextView n = W.text(a, s.optInt("views") + (r > 0 ? " · " + r + " ♥" : ""), 14, 500, 0xffffffff);
            vb.addView(n, W.lpm(W.WC, W.WC, 6, 0, 0, 0));
            vb.setContentDescription("Viewers");
            vb.setOnClickListener(v -> {
                pause(true);
                Sheets.viewers(a, s, () -> {
                    if (!closed) pause(false);
                });
            });
            LinearLayout.LayoutParams p = W.lp(W.WC, W.WC);
            p.gravity = Gravity.CENTER_HORIZONTAL;
            foot.addView(vb, p);
            return;
        }
        if (e.optBoolean("group")) return;
        LinearLayout f = W.row(a);
        input = new EditText(a);
        T.text(input, 14, 400, 0xffffffff);
        input.setHint("Reply to " + (Store.name(e).isEmpty() ? "story" : e.optString("displayName", Store.name(e))) + "…");
        input.setHintTextColor(0xb3ffffff);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        input.setBackground(W.round(0x24ffffff, 999));
        input.setPadding(T.dp(16), 0, T.dp(16), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnFocusChangeListener((v, has) -> {
            if (has) pause(true);
            else App.H.postDelayed(() -> {
                if (!closed && input != null && !input.hasFocus()) pause(false);
            }, 50);
        });
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEND || ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                submit(e, s);
                return true;
            }
            return false;
        });
        f.addView(input, W.lp(0, T.dp(42), 1));
        String rx = s.optString("reaction", "");
        boolean on = !rx.isEmpty() && !"null".equals(rx);
        heartB = ib(on ? "st.viewer.heart_on" : "st.viewer.heart", 24, "Like", null);
        if (on) heartB.setImageDrawable(Ic.get("st.viewer.heart_on", 24, 0xffef4444));
        ImageView hb = heartB;
        hb.setOnClickListener(v -> {
            String cr = s.optString("reaction", "");
            boolean has = !cr.isEmpty() && !"null".equals(cr);
            react(s, has ? null : "❤️", hb);
        });
        LinearLayout.LayoutParams hp = W.lp(T.dp(40), T.dp(40));
        hp.leftMargin = T.dp(4);
        f.addView(hb, hp);
        ImageView sb = ib("st.viewer.send", 22, "Send reply", v -> submit(e, s));
        LinearLayout.LayoutParams sbp = W.lp(T.dp(40), T.dp(40));
        sbp.leftMargin = T.dp(4);
        f.addView(sb, sbp);
        foot.addView(f, W.lp(W.MP, W.WC));
        quick = W.row(a);
        quick.setVisibility(paused ? View.VISIBLE : View.GONE);
        for (int k = 0; k < 6; k++) {
            String x = REACTS[k];
            TextView q = W.text(a, x, 26, 400, 0xffffffff);
            q.setGravity(Gravity.CENTER);
            q.setIncludeFontPadding(false);
            q.setContentDescription("React " + x);
            q.setOnTouchListener((vv, ev) -> {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) vv.animate().scaleX(1.25f).scaleY(1.25f).setDuration(150).start();
                else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) vv.animate().scaleX(1).scaleY(1).setDuration(150).start();
                return false;
            });
            q.setOnClickListener(v -> react(s, x, v));
            quick.addView(W.flex(a));
            quick.addView(q, W.lp(T.dp(44), T.dp(44)));
            quick.addView(W.flex(a));
        }
        LinearLayout.LayoutParams qp = W.lp(W.MP, W.WC);
        qp.topMargin = T.dp(8);
        foot.addView(quick, qp);
    }

    void submit(JSONObject e, JSONObject s) {
        if (input == null) return;
        String txt = input.getText().toString().trim();
        if (txt.isEmpty()) return;
        String key = e.optString("key");
        String cid = null;
        for (String id : Chats.I.order) {
            Chats.Conv cv = Chats.I.conv(id);
            if (cv == null || cv.group() || cv.type.contains("secret")) continue;
            if (key.equals(cv.o.optString("peerId")) || key.equals(cv.o.optString("peerUserId"))) {
                cid = id;
                break;
            }
        }
        if (cid == null) {
            Ov.toast("Open a chat with " + (e.optString("displayName", "").isEmpty() ? "them" : e.optString("displayName")) + " to reply", true);
            return;
        }
        W.hideKb(input);
        close(true);
        String fc = cid;
        Home.openChat(fc);
        App.bg(() -> Chats.I.send(fc, "↩ Replied to your story: " + txt, null));
    }

    void show() {
        stopMedia();
        JSONObject e = entry();
        JSONArray st = stories(e);
        JSONObject s = st.optJSONObject(i);
        if (s == null) {
            close(false);
            return;
        }
        curS = s;
        paused = false;
        held = false;
        paintSegs();
        headFor(e, s);
        footFor(e, s);
        media.removeAllViews();
        ovl.removeAllViews();
        String capT = s.optString("caption", "");
        if ("null".equals(capT)) capT = "";
        cap.setText(capT);
        cap.setVisibility(capT.isEmpty() ? View.GONE : View.VISIBLE);
        String src = Store.safeMedia(s.optString("url"));
        if (src == null) {
            root.post(() -> step(1));
            return;
        }
        elapsed = 0;
        startT = System.currentTimeMillis();
        imgReady = false;
        if ("video".equals(s.optString("kind"))) playVideo(src);
        else {
            ImageView im = new ImageView(a);
            im.setScaleType(ImageView.ScaleType.FIT_CENTER);
            media.addView(im, new FrameLayout.LayoutParams(W.MP, W.MP));
            String want = src;
            Img.get(src, 1600, true, (d, b) -> {
                if (closed || curS != s) return;
                if (d != null) {
                    im.setImageDrawable(d);
                    if (android.os.Build.VERSION.SDK_INT >= 28 && d instanceof android.graphics.drawable.AnimatedImageDrawable) ((android.graphics.drawable.AnimatedImageDrawable) d).start();
                } else if (b != null) im.setImageBitmap(b);
                if (!imgReady && want.equals(src)) {
                    imgReady = true;
                    startT = System.currentTimeMillis();
                }
            });
        }
        overlays(s);
        if (!s.optBoolean("mine") && !seenSent.contains(s.optString("id"))) {
            seenSent.add(s.optString("id"));
            if (!s.optBoolean("seen")) {
                try {
                    s.put("seen", true);
                    boolean any = false;
                    for (int k = 0; k < st.length(); k++) if (!st.optJSONObject(k).optBoolean("seen")) any = true;
                    e.put("unseen", any);
                } catch (Exception ex) {
                }
                String id = s.optString("id");
                App.bg(() -> Http.post("/stories/" + id + "/view", null));
                Stories.render();
            }
        }
        preload();
        root.removeCallbacks(tick);
        root.postDelayed(tick, 16);
    }

    void overlays(JSONObject s) {
        JSONArray ov = s.optJSONArray("overlays");
        if (ov == null) return;
        for (int k = 0; k < ov.length(); k++) {
            JSONObject o = ov.optJSONObject(k);
            if (o == null) continue;
            TextView t = txt(a, o);
            ovl.addView(t, W.flp(W.WC, W.WC, Gravity.TOP | Gravity.START));
            place(t, ovl, o);
        }
    }

    static float txtSize(View host) {
        float hdp = (host.getHeight() > 0 ? host.getHeight() : host.getResources().getDisplayMetrics().heightPixels) / T.density;
        return Math.max(18, Math.min(30, hdp * .046f));
    }

    static Typeface face(String f) {
        if ("serif".equals(f)) return Typeface.create(Typeface.SERIF, Typeface.BOLD);
        if ("mono".equals(f)) return Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        return T.b;
    }

    static TextView txt(Context c, JSONObject o) {
        TextView t = new TextView(c);
        t.setText(o.optString("t"));
        t.setTypeface(face(o.optString("f", "sans")));
        t.setTextColor(color(o.optString("c", ""), 0xffffffff));
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.2f / 1.21f);
        t.setPadding(T.dp(10), T.dp(4), T.dp(10), T.dp(4));
        t.setShadowLayer(T.dpf(3), 0, T.dpf(1), 0x8c000000);
        String b = o.optString("b", "");
        if (!b.isEmpty() && !"null".equals(b)) t.setBackground(W.round(color(b, 0), 10));
        return t;
    }

    static int color(String s, int def) {
        if (s == null || !s.matches("^#[0-9a-fA-F]{6}$")) return def;
        return 0xff000000 | Integer.parseInt(s.substring(1), 16);
    }

    static void place(TextView t, View box, JSONObject o) {
        Runnable go = () -> {
            int bw = box.getWidth(), bh = box.getHeight();
            if (bw == 0) return;
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, txtSize(box));
            t.setMaxWidth(Math.round(bw * .86f));
            t.measure(View.MeasureSpec.makeMeasureSpec(Math.round(bw * .86f), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
            int w = t.getMeasuredWidth(), h = t.getMeasuredHeight();
            t.setTranslationX((float) (o.optDouble("x", .5) * bw - w / 2f));
            t.setTranslationY((float) (o.optDouble("y", .5) * bh - h / 2f));
            t.setPivotX(w / 2f);
            t.setPivotY(h / 2f);
            t.setRotation((float) o.optDouble("r", 0));
            float sc = (float) o.optDouble("s", 1);
            t.setScaleX(sc);
            t.setScaleY(sc);
        };
        if (box.getWidth() > 0) go.run();
        box.post(go);
    }

    boolean audioFocus() {
        try {
            AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
            if (afr == null) afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).build();
            am.requestAudioFocus(afr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    void abandonFocus() {
        try {
            if (afr != null) ((AudioManager) a.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocusRequest(afr);
        } catch (Exception e) {
        }
    }

    void applyVolume() {
        if (mp != null) try {
            mp.setVolume(muted ? 0 : 1, muted ? 0 : 1);
        } catch (Exception e) {
        }
    }

    View errV;
    int vidTry;

    void playVideo(String src) {
        vidTry = 0;
        startVideo(src, null);
    }

    void startVideo(String src, java.io.File local) {
        prepared = false;
        if (errV != null) {
            stage.removeView(errV);
            errV = null;
        }
        media.removeAllViews();
        tv = new TextureView(a);
        media.addView(tv, W.flp(W.MP, W.MP, Gravity.CENTER));
        root.removeCallbacks(tick);
        root.postDelayed(tick, 16);
        MediaPlayer p = new MediaPlayer();
        mp = p;
        JSONObject s = curS;
        Runnable timeout = () -> {
            if (mp == p && !prepared && curS == s && !closed) videoFailed(src, local);
        };
        p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
        p.setOnPreparedListener(x -> {
            if (mp != x || closed) return;
            root.removeCallbacks(timeout);
            prepared = true;
            fit(x.getVideoWidth(), x.getVideoHeight());
            applyVolume();
            audioFocus();
            if (!paused) x.start();
        });
        p.setOnVideoSizeChangedListener((x, w, h) -> {
            if (mp == x) fit(w, h);
        });
        p.setOnCompletionListener(x -> {
            if (mp == x && curS == s && prepared) step(1);
        });
        p.setOnErrorListener((x, w, e) -> {
            if (mp == x && curS == s) root.post(() -> {
                root.removeCallbacks(timeout);
                if (mp == x && curS == s && !closed) videoFailed(src, local);
            });
            return true;
        });
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                if (mp != p) return;
                try {
                    p.setSurface(new Surface(st));
                    if (local != null) p.setDataSource(local.getPath());
                    else p.setDataSource(a, Uri.parse(Http.abs(src)), Media.headers(src));
                    p.prepareAsync();
                } catch (Exception e) {
                    root.post(() -> {
                        if (mp == p && curS == s && !closed) videoFailed(src, local);
                    });
                }
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
            }

            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                return true;
            }

            public void onSurfaceTextureUpdated(SurfaceTexture st) {
            }
        });
        root.postDelayed(timeout, local != null ? 10000 : 15000);
    }

    void videoFailed(String src, java.io.File local) {
        JSONObject s = curS;
        stopMedia();
        if (local == null && vidTry++ < 1) {
            App.bg(() -> {
                java.io.File f = Img.file(src);
                App.ui(() -> {
                    if (closed || curS != s) return;
                    if (f != null && f.length() > 0) startVideo(src, f);
                    else showRetry(src);
                });
            });
            return;
        }
        showRetry(src);
    }

    void showRetry(String src) {
        JSONObject s = curS;
        if (errV != null) stage.removeView(errV);
        media.removeAllViews();
        LinearLayout box = W.col(a);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = W.text(a, "Could not play this video", 14, 500, 0xffffffff);
        t.setGravity(Gravity.CENTER);
        box.addView(t, W.lp(W.MP, W.WC));
        TextView b = W.text(a, "Retry", 14, 500, 0xffffffff);
        b.setGravity(Gravity.CENTER);
        T.lh(b, 21);
        b.setPadding(T.dp(16), b.getPaddingTop() + T.dp(8), T.dp(16), b.getPaddingBottom() + T.dp(8));
        b.setBackground(W.ripple(W.round(0x29ffffff, 999), 999));
        b.setContentDescription("Retry");
        b.setOnClickListener(v -> {
            if (closed || curS != s) return;
            stage.removeView(box);
            errV = null;
            vidTry = 0;
            java.io.File c = Img.cached(src);
            startVideo(src, c);
        });
        box.addView(b, W.lpm(W.WC, W.WC, 0, 12, 0, 0));
        errV = box;
        box.setPadding(T.dp(24), 0, T.dp(24), 0);
        stage.addView(box, W.flp(W.MP, W.WC, Gravity.CENTER));
    }

    void fit(int vw, int vh) {
        if (tv == null || vw <= 0 || vh <= 0) return;
        int W0 = media.getWidth(), H0 = media.getHeight();
        if (W0 == 0) return;
        float s = Math.min(W0 / (float) vw, H0 / (float) vh);
        FrameLayout.LayoutParams lp = W.flp(Math.round(vw * s), Math.round(vh * s), Gravity.CENTER);
        tv.setLayoutParams(lp);
    }

    void stopMedia() {
        root.removeCallbacks(tick);
        if (errV != null) {
            stage.removeView(errV);
            errV = null;
        }
        if (mp != null) {
            MediaPlayer p = mp;
            mp = null;
            try {
                p.reset();
                p.release();
            } catch (Exception e) {
            }
            abandonFocus();
        }
        tv = null;
        prepared = false;
    }

    void preload() {
        JSONObject e = entry();
        JSONObject n = stories(e).optJSONObject(i + 1);
        if (n == null && u + 1 < list.size()) n = stories(list.get(u + 1)).optJSONObject(0);
        String src = n == null ? null : Store.safeMedia(n.optString("url"));
        if (src != null && !"video".equals(n.optString("kind"))) Img.get(src, 1600, false, (d, b) -> {
        });
    }

    void step(int d) {
        if (closed) return;
        JSONObject e = entry();
        int n = stories(e).length();
        if (d > 0) {
            if (i < n - 1) {
                i++;
                show();
                return;
            }
            if (u < list.size() - 1) {
                jump(1);
                return;
            }
            close(false);
            return;
        }
        if (i > 0) {
            i--;
            show();
            return;
        }
        if (u > 0) {
            jump(-1);
            return;
        }
        elapsed = 0;
        startT = System.currentTimeMillis();
        if (mp != null && prepared) try {
            mp.seekTo(0);
        } catch (Exception ex) {
        }
    }

    void jump(int d) {
        int nu = u + d;
        if (nu < 0 || nu >= list.size()) {
            if (d > 0) close(false);
            return;
        }
        u = nu;
        i = d > 0 ? firstUnseen(entry()) : 0;
        stage.setTranslationX(T.dp(d > 0 ? 36 : -36));
        stage.setAlpha(.4f);
        stage.animate().translationX(0).alpha(1).setDuration(260).setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1f)).start();
        show();
    }

    void react(JSONObject s, String x, View b) {
        App.bg(() -> {
            Http.Resp r = Http.post("/stories/" + s.optString("id") + "/react", App.jo("reaction", x == null ? JSONObject.NULL : x));
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast("Could not react", true);
                    return;
                }
                try {
                    s.put("reaction", x == null ? JSONObject.NULL : x);
                } catch (Exception e) {
                }
                if (heartB != null && curS == s) heartB.setImageDrawable(x != null ? Ic.get("st.viewer.heart_on", 24, 0xffef4444) : Ic.get("st.viewer.heart", 24, 0xffffffff));
                if (x != null && b != null && b.isAttachedToWindow()) burst(b, x);
            });
        });
    }

    void burst(View anchor, String emoji) {
        int[] l = new int[2], h = new int[2];
        anchor.getLocationInWindow(l);
        root.getLocationInWindow(h);
        float cx = l[0] - h[0] + anchor.getWidth() / 2f, cy = l[1] - h[1] + anchor.getHeight() / 2f;
        for (int k = 0; k < 9; k++) {
            TextView p = W.text(a, emoji, 24, 400, 0xffffffff);
            p.setIncludeFontPadding(false);
            root.addView(p, W.flp(W.WC, W.WC, Gravity.TOP | Gravity.START));
            p.measure(0, 0);
            float pw = p.getMeasuredWidth(), ph = p.getMeasuredHeight();
            p.setTranslationX(cx - pw / 2);
            p.setTranslationY(cy - ph / 2);
            p.setScaleX(.6f);
            p.setScaleY(.6f);
            double ang = -Math.PI / 2 + (Math.random() - .5) * 1.6, dist = T.dpf((float) (80 + Math.random() * 90));
            p.animate().translationX((float) (cx - pw / 2 + Math.cos(ang) * dist)).translationY((float) (cy - ph / 2 + Math.sin(ang) * dist)).scaleX(1.3f).scaleY(1.3f).alpha(0).setDuration(800 + Math.round(Math.random() * 300)).setInterpolator(new PathInterpolator(.2f, .8f, .3f, 1f)).withEndAction(() -> root.removeView(p)).start();
        }
    }

    void menu(View anchor, JSONObject e, JSONObject s) {
        pause(true);
        List<Object[]> m = new ArrayList<>();
        String id = s.optString("id");
        boolean mine = s.optBoolean("mine");
        boolean group = e.optBoolean("group");
        if (mine && !group) {
            m.add(new Object[]{s.optBoolean("pinned") ? "Remove from profile" : "Pin to profile", false, (Runnable) () -> App.bg(() -> {
                Http.Resp r = Http.post("/stories/" + id + "/pin", App.jo("pinned", !s.optBoolean("pinned")));
                App.ui(() -> {
                    if (r.ok()) {
                        boolean pn = r.obj().optBoolean("pinned");
                        try {
                            s.put("pinned", pn);
                        } catch (Exception ex) {
                        }
                        Ov.toast(pn ? "Pinned to your profile" : "Removed from your profile");
                        if (curS == s) headFor(e, s);
                    } else Ov.toast("pin_limit".equals(r.error()) ? "You can pin up to 30 stories" : "Could not update", true);
                });
            })});
            m.add(new Object[]{"Archive", false, (Runnable) () -> {
                close(true);
                Sheets.archive(a);
            }});
        }
        if (mine) m.add(new Object[]{"Delete story", true, (Runnable) () -> confirmDelete(e, s)});
        if (!mine && !e.optBoolean("self")) {
            boolean hid = e.optBoolean("hidden");
            String nm = e.optString("displayName", "");
            m.add(new Object[]{hid ? "Show " + nm + "'s stories" : "Hide " + nm + "'s stories", false, (Runnable) () -> App.bg(() -> {
                Http.Resp r = Http.post("/stories/hide", App.jo("id", e.optString("key"), "hidden", !hid));
                App.ui(() -> {
                    if (r.ok()) {
                        try {
                            e.put("hidden", !hid);
                        } catch (Exception ex) {
                        }
                        Ov.toast(!hid ? "Stories hidden - they move to the end" : "Stories shown");
                        Store.load();
                    } else Ov.toast("Could not update", true);
                });
            })});
        }
        if (!mine) {
            boolean on = Store.stealthUntil > System.currentTimeMillis();
            m.add(new Object[]{on ? "Stealth mode is on" : "Stealth mode" + (Store.pro ? "" : " (Pro)"), false, (Runnable) () -> {
                if (!Store.pro) {
                    Ov.toast("Stealth mode is part of Pro Box");
                    return;
                }
                App.bg(() -> {
                    Http.Resp r = Http.post("/stories/stealth", null);
                    App.ui(() -> {
                        if (r.ok()) {
                            Store.stealthUntil = r.obj().optLong("until");
                            Ov.toast("Stealth mode on for 25 minutes - your views won't show");
                        } else Ov.toast("cooldown".equals(r.error()) ? "Stealth mode is cooling down" : "Could not enable", true);
                    });
                });
            }});
        }
        popMenu(anchor, m);
    }

    boolean dialog;

    void popMenu(View anchor, List<Object[]> items) {
        FrameLayout bd = new FrameLayout(a);
        LinearLayout m = W.col(a);
        m.setPadding(T.dp(4), T.dp(4), T.dp(4), T.dp(4));
        m.setBackground(W.round(T.surface, 12));
        m.setElevation(T.dpf(8));
        m.setMinimumWidth(T.dp(200));
        boolean[] done = {false};
        Runnable fin = () -> {
            if (done[0]) return;
            done[0] = true;
            Ov.remove(bd);
            root.removeView(bd);
            App.H.post(() -> {
                if (!closed && !dialog) pause(false);
            });
        };
        bd.setOnClickListener(v -> fin.run());
        for (Object[] it : items) {
            TextView b = W.text(a, (String) it[0], 14, 400, (Boolean) it[1] ? T.danger : T.text);
            T.lh(b, 21);
            b.setPadding(T.dp(12), b.getPaddingTop() + T.dp(10), T.dp(12), b.getPaddingBottom() + T.dp(10));
            b.setBackground(W.ripple(new android.graphics.drawable.ColorDrawable(0), 8));
            Runnable run = (Runnable) it[2];
            b.setOnClickListener(v -> {
                fin.run();
                run.run();
            });
            m.addView(b, W.lp(W.MP, W.WC));
        }
        bd.addView(m, W.flp(W.WC, W.WC, Gravity.TOP | Gravity.START));
        root.addView(bd, new FrameLayout.LayoutParams(W.MP, W.MP));
        Ov.push(bd, fin);
        int[] l = new int[2], h = new int[2];
        anchor.getLocationInWindow(l);
        root.getLocationInWindow(h);
        int right = l[0] - h[0] + anchor.getWidth(), top = l[1] - h[1] + anchor.getHeight() + T.dp(6);
        m.setVisibility(View.INVISIBLE);
        m.post(() -> {
            int mw = m.getWidth(), mh = m.getHeight();
            m.setTranslationX(Math.max(T.dp(8), Math.min(root.getWidth() - mw - T.dp(8), right - mw)));
            float ty = Math.min(root.getHeight() - mh - T.dp(8), top);
            m.setTranslationY(ty + T.dp(8));
            m.setScaleX(.98f);
            m.setScaleY(.98f);
            m.setAlpha(0);
            m.setVisibility(View.VISIBLE);
            m.animate().translationY(ty).scaleX(1).scaleY(1).alpha(1).setDuration(120).setInterpolator(T.EASE).start();
        });
    }

    void confirmDelete(JSONObject e, JSONObject s) {
        dialog = true;
        pause(true);
        Ov.Sheet d = new Ov.Sheet(a, false);
        d.title("Delete this story?");
        boolean[] go = {false};
        d.onClose = () -> {
            dialog = false;
            if (!go[0] && !closed) pause(false);
        };
        LinearLayout r = W.row(a);
        r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        r.addView(W.btn(a, "Cancel", false, v -> d.close()));
        TextView del = W.btn(a, "Delete", true, v -> {
            go[0] = true;
            d.close();
            doDelete(e, s);
        });
        del.setBackground(W.ripple(W.round(T.danger, 999), 999));
        del.setTextColor(0xffffffff);
        LinearLayout.LayoutParams gp = W.lp(W.WC, W.WC);
        gp.leftMargin = T.dp(8);
        r.addView(del, gp);
        d.body.addView(r);
        d.show();
    }

    void doDelete(JSONObject e, JSONObject s) {
        App.bg(() -> {
            Http.Resp r = Http.delete("/stories/" + s.optString("id"), null);
            App.ui(() -> {
                if (!r.ok()) {
                    Ov.toast("Could not delete", true);
                    if (!closed) pause(false);
                    return;
                }
                Ov.toast("Story deleted");
                JSONArray st = stories(e), n = new JSONArray();
                for (int k = 0; k < st.length(); k++) if (st.optJSONObject(k) != s) n.put(st.optJSONObject(k));
                try {
                    e.put("stories", n);
                } catch (Exception ex) {
                }
                if (n.length() == 0) {
                    close(false);
                    Store.load();
                    return;
                }
                if (i >= n.length()) i = n.length() - 1;
                show();
                Store.load();
            });
        });
    }

    boolean touch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (input != null && input.hasFocus()) {
                    input.clearFocus();
                    W.hideKb(input);
                    return true;
                }
                downX = ev.getRawX();
                downY = ev.getRawY();
                lastY = downY;
                downT = lastT = System.currentTimeMillis();
                moved = false;
                held = false;
                tracking = true;
                vel = 0;
                root.postDelayed(holdR, 220);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!tracking) return true;
                float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                if (!moved && (Math.abs(dx) > T.dp(10) || Math.abs(dy) > T.dp(10))) {
                    moved = true;
                    root.removeCallbacks(holdR);
                }
                long nt = System.currentTimeMillis();
                vel = (ev.getRawY() - lastY) / T.density / Math.max(1, nt - lastT);
                lastY = ev.getRawY();
                lastT = nt;
                if (dy > 0 && Math.abs(dy) > Math.abs(dx)) {
                    float dyd = dy / T.density;
                    stage.setTranslationY(dy);
                    float sc = Math.max(.86f, 1 - dyd / 1200f);
                    stage.setScaleX(sc);
                    stage.setScaleY(sc);
                    root.setBackgroundColor(((int) (Math.max(.2f, .92f - dyd / 600f) * 255) << 24));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!tracking) return true;
                tracking = false;
                root.removeCallbacks(holdR);
                float dx = (ev.getRawX() - downX) / T.density, dy = (ev.getRawY() - downY) / T.density;
                boolean cancel = ev.getActionMasked() == MotionEvent.ACTION_CANCEL;
                stage.animate().translationY(0).scaleX(1).scaleY(1).setDuration(200).start();
                root.setBackgroundColor(0xeb000000);
                if (cancel) {
                    if (held) pause(false);
                    held = false;
                    return true;
                }
                if (held && !moved) {
                    held = false;
                    pause(false);
                    return true;
                }
                if (held) pause(false);
                held = false;
                if (Math.abs(dy) > Math.abs(dx) && (dy > 70 || (dy > 24 && vel > .35f))) {
                    close(false);
                    return true;
                }
                if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy)) {
                    jump(dx < 0 ? 1 : -1);
                    return true;
                }
                if (moved) return true;
                step(ev.getX() < stage.getWidth() / 3f ? -1 : 1);
                return true;
            }
        }
        return true;
    }

    void close(boolean instant) {
        if (closed) return;
        closed = true;
        stopMedia();
        if (cur == this) cur = null;
        Ov.remove(root);
        try {
            a.getApplication().unregisterActivityLifecycleCallbacks(life);
        } catch (Exception e) {
        }
        if (input != null) W.hideKb(input);
        Stories.render();
        if (instant || T.lowfx) {
            a.host.removeView(root);
            return;
        }
        root.animate().alpha(0).scaleX(.94f).scaleY(.94f).setDuration(220).setInterpolator(new PathInterpolator(.4f, 0f, 1f, 1f)).withEndAction(() -> a.host.removeView(root)).start();
    }
}
