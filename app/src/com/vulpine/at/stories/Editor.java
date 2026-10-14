package com.vulpine.at.stories;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Editor {
    static final String[] PALETTE = {"#ffffff", "#111111", "#f97316", "#ef4444", "#facc15", "#22c55e", "#3b82f6", "#a855f7", "#ec4899"};
    static Editor cur;

    public static final class Opts {
        public String audience = "contacts";
        public List<String> allow = new ArrayList<>(), deny = new ArrayList<>();
        public int hours = 24;
        public String groupId;
    }

    static final class Ovl {
        JSONObject o;
        EditText el;
    }

    final MainActivity a;
    final File file;
    final String mime, name;
    final boolean isVid;
    final Opts opts = new Opts();
    final FrameLayout root, stage, ovl, prog;
    final View progFill;
    final LinearLayout bottom, tools, pal;
    final EditText capIn;
    final TextView audB, durB, postB;
    ImageView penB, mediaIv;
    DrawView draw;
    TextureView tv;
    MediaPlayer mp;
    Bitmap preview;
    int mw, mh;
    double vdur;
    String mode = "";
    int penColor = 0xffffffff;
    final List<Media.Stroke> strokes = new ArrayList<>();
    final List<Ovl> overlays = new ArrayList<>();
    Ovl active;
    boolean busy, done;
    int vstTop, vstBot;

    public static void compose(MainActivity a, String groupId) {
        if (a == null) return;
        Media.pick(a, new String[]{"image/*", "video/mp4", "video/webm", "video/quicktime"}, true, (f, mime, name) -> {
            boolean vid = mime != null && mime.startsWith("video/"), img = mime != null && mime.startsWith("image/");
            if (!vid && !img) {
                Ov.toast("Pick a photo or a video", true);
                return;
            }
            if (cur != null) cur.finish();
            new Editor(a, f, mime, name, groupId);
        });
    }

    Editor(MainActivity a, File f, String mime, String name, String groupId) {
        this.a = a;
        this.file = f;
        this.mime = mime;
        this.name = name;
        cur = this;
        isVid = mime.startsWith("video/");
        opts.groupId = groupId;
        if (groupId != null) opts.audience = "members";
        Context c = a;
        vstTop = Math.max(a.insetTop, T.dp(24)) + T.dp(20);
        vstBot = Math.max(a.insetBottom, T.dp(16)) + T.dp(26);
        root = new FrameLayout(c);
        root.setBackgroundColor(0xff000000);
        root.setClickable(true);
        LinearLayout col = W.col(c);
        root.addView(col, new FrameLayout.LayoutParams(W.MP, W.MP));
        stage = new FrameLayout(c);
        stage.setClipChildren(true);
        col.addView(stage, W.lp(W.MP, 0, 1));
        if (isVid) {
            tv = new TextureView(c);
            stage.addView(tv, W.flp(1, 1, Gravity.CENTER));
        } else {
            mediaIv = new ImageView(c);
            mediaIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            stage.addView(mediaIv, new FrameLayout.LayoutParams(W.MP, W.MP));
            draw = new DrawView(c);
            stage.addView(draw, W.flp(0, 0, Gravity.TOP | Gravity.START));
        }
        ovl = new FrameLayout(c);
        ovl.setClipChildren(false);
        stage.addView(ovl, W.flp(0, 0, Gravity.TOP | Gravity.START));
        stage.setOnClickListener(v -> {
            if (active != null && active.el.hasFocus()) active.el.clearFocus();
        });
        LinearLayout top = W.row(c);
        top.setPadding(T.dp(8), vstTop, T.dp(8), T.dp(8));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x80000000, 0}));
        top.setGravity(Gravity.TOP);
        top.addView(ib("st.sheets.x", "Cancel", v -> finish()));
        top.addView(W.flex(c));
        tools = W.row(c);
        tools.addView(ib("st.editor.text", "Add text", v -> addText()));
        if (!isVid) {
            penB = ib("st.editor.pen", "Draw", v -> setMode("draw".equals(mode) ? "" : "draw"));
            LinearLayout.LayoutParams pp = (LinearLayout.LayoutParams) penB.getLayoutParams();
            pp.leftMargin = T.dp(2);
            tools.addView(penB);
        }
        ImageView undo = ib("st.editor.undo", "Undo", v -> undo());
        ((LinearLayout.LayoutParams) undo.getLayoutParams()).leftMargin = T.dp(2);
        tools.addView(undo);
        top.addView(tools);
        root.addView(top, W.flp(W.MP, W.WC, Gravity.TOP));
        pal = W.col(c);
        for (String hex : PALETTE) {
            int col0 = 0xff000000 | Integer.parseInt(hex.substring(1), 16);
            float[] sc = {penColor == col0 ? 1.25f : 1f};
            View sw = new View(c) {
                final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

                @Override
                protected void onDraw(Canvas cv) {
                    float cx = getWidth() / 2f, r = T.dpf(13) * sc[0];
                    boolean on = penColor == col0;
                    p.setColor(on ? 0xffffffff : 0x80ffffff);
                    cv.drawCircle(cx, cx, r + T.dpf(on ? 2.5f : 2f), p);
                    p.setColor(col0);
                    cv.drawCircle(cx, cx, r, p);
                }
            };
            sw.setTag(sc);
            sw.setContentDescription("Color");
            sw.setOnClickListener(v -> {
                penColor = col0;
                for (int k = 0; k < pal.getChildCount(); k++) {
                    View x = pal.getChildAt(k);
                    float[] xs = (float[]) x.getTag();
                    float to = x == v ? 1.25f : 1f;
                    if (xs[0] == to) {
                        x.invalidate();
                        continue;
                    }
                    android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(xs[0], to);
                    va.setDuration(150);
                    va.addUpdateListener(an -> {
                        xs[0] = (float) an.getAnimatedValue();
                        x.invalidate();
                    });
                    va.start();
                }
                if ("text".equals(mode) && active != null) {
                    active.el.setTextColor(col0);
                    put(active.o, "c", hex);
                }
            });
            LinearLayout.LayoutParams sp = W.lp(T.dp(40), T.dp(40));
            if (pal.getChildCount() > 0) sp.topMargin = -T.dp(6);
            pal.addView(sw, sp);
        }
        pal.setClipChildren(false);
        pal.setClipToPadding(false);
        pal.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = W.flp(W.WC, W.WC, Gravity.END | Gravity.CENTER_VERTICAL);
        plp.rightMargin = T.dp(3);
        root.addView(pal, plp);
        bottom = W.col(c);
        bottom.setBackgroundColor(0xff000000);
        bottom.setPadding(T.dp(12), T.dp(10), T.dp(12), vstBot);
        capIn = new EditText(c);
        T.text(capIn, 14, 400, 0xffffffff);
        capIn.setHint("Add a caption…");
        capIn.setHintTextColor(0xff757575);
        capIn.setSingleLine(true);
        capIn.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1024)});
        capIn.setBackground(W.round(0x1fffffff, 999));
        capIn.setPadding(T.dp(16), 0, T.dp(16), 0);
        bottom.addView(capIn, W.lp(W.MP, T.dp(42)));
        LinearLayout row = W.row(c);
        audB = chip("Audience", v -> Sheets.audience(a, opts, this::labels));
        durB = chip("Duration", v -> Sheets.duration(a, opts, this::labels));
        if (groupId == null) row.addView(audB);
        LinearLayout.LayoutParams dp = (LinearLayout.LayoutParams) durB.getLayoutParams();
        if (groupId == null) dp.leftMargin = T.dp(8);
        row.addView(durB);
        row.addView(W.flex(c));
        postB = W.btn(c, "Share story", true, v -> post());
        postB.setMinHeight(T.dp(36));
        row.addView(postB, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        bottom.addView(row, W.lpm(W.MP, W.WC, 0, 10, 0, 0));
        col.addView(bottom, W.lp(W.MP, W.WC));
        prog = new FrameLayout(c);
        progFill = new View(c);
        progFill.setBackgroundColor(T.accent);
        progFill.setPivotX(0);
        progFill.setScaleX(0);
        prog.addView(progFill, new FrameLayout.LayoutParams(W.MP, W.MP));
        root.addView(prog, W.flp(W.MP, T.dp(3), Gravity.TOP));
        labels();
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int im = android.os.Build.VERSION.SDK_INT >= 30 ? ins.getInsets(WindowInsets.Type.ime()).bottom : Math.max(0, ins.getSystemWindowInsetBottom() - ins.getStableInsetBottom());
            bottom.setPadding(T.dp(12), T.dp(10), T.dp(12), im > 0 ? T.dp(10) + im : vstBot);
            return ins;
        });
        a.host.addView(root, new FrameLayout.LayoutParams(W.MP, W.MP));
        root.requestApplyInsets();
        root.setTranslationY(a.host.getHeight());
        root.animate().translationY(0).setDuration(T.lowfx ? 160 : 320).setInterpolator(new PathInterpolator(.2f, .9f, .25f, 1f)).start();
        Ov.push(root, () -> {
            if (busy) return;
            finish();
        });
        stage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) stage.post(this::placeBox);
        });
        if (isVid) loadVideo();
        else loadPhoto();
    }

    TextView chip(String label, View.OnClickListener on) {
        TextView t = W.text(a, label, 13, 500, 0xffffffff);
        t.setGravity(Gravity.CENTER);
        t.setPadding(T.dp(14), 0, T.dp(14), 0);
        t.setSingleLine(true);
        t.setBackground(W.ripple(W.round(0x24ffffff, 999), 999));
        t.setOnClickListener(on);
        t.setLayoutParams(W.lp(W.WC, T.dp(36)));
        return t;
    }

    ImageView ib(String svg, String label, View.OnClickListener on) {
        ImageView b = new ImageView(a);
        b.setImageDrawable(Ic.get(svg, 22, 0xffffffff));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackground(W.ripple(W.oval(0), 999));
        b.setContentDescription(label);
        b.setOnClickListener(on);
        b.setLayoutParams(W.lp(T.dp(40), T.dp(40)));
        return b;
    }

    void labels() {
        String aud;
        switch (opts.audience) {
            case "everyone": aud = "Everyone"; break;
            case "close": aud = "Close friends"; break;
            case "selected": aud = "Selected · " + opts.allow.size(); break;
            default: aud = "Contacts";
        }
        audB.setText(aud + (opts.deny.isEmpty() ? "" : " · hiding " + opts.deny.size()));
        durB.setText(opts.hours + "h");
    }

    void loadPhoto() {
        App.bg(() -> {
            Bitmap b = Media.decode(file, 1600);
            App.ui(() -> {
                if (done) return;
                if (b == null) {
                    Ov.toast("Could not open the file", true);
                    finish();
                    return;
                }
                preview = b;
                mw = b.getWidth();
                mh = b.getHeight();
                mediaIv.setImageBitmap(b);
                stage.post(this::placeBox);
            });
        });
    }

    void loadVideo() {
        App.bg(() -> {
            Media.Probe p = Media.probe(file);
            App.ui(() -> {
                if (done) return;
                vdur = p.dur;
                if (p.dur > 60.5) {
                    Ov.toast("Videos can be up to 60 seconds", true);
                    finish();
                    return;
                }
                mw = p.w;
                mh = p.h;
                stage.post(this::placeBox);
                TextureView.SurfaceTextureListener l = new TextureView.SurfaceTextureListener() {
                    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                        if (done || mp != null) return;
                        try {
                            MediaPlayer m = new MediaPlayer();
                            mp = m;
                            m.setSurface(new Surface(st));
                            m.setDataSource(file.getPath());
                            m.setLooping(true);
                            m.setVolume(0, 0);
                            m.setOnPreparedListener(x -> {
                                if (mp == x && !done) x.start();
                            });
                            m.setOnVideoSizeChangedListener((x, vw, vh) -> {
                                if (vw > 0 && vh > 0 && (mw == 0 || mh == 0)) {
                                    mw = vw;
                                    mh = vh;
                                    placeBox();
                                }
                            });
                            m.prepareAsync();
                        } catch (Exception e) {
                        }
                    }

                    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                    }

                    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                        return true;
                    }

                    public void onSurfaceTextureUpdated(SurfaceTexture st) {
                    }
                };
                tv.setSurfaceTextureListener(l);
                if (tv.isAvailable() && tv.getSurfaceTexture() != null) l.onSurfaceTextureAvailable(tv.getSurfaceTexture(), tv.getWidth(), tv.getHeight());
            });
        });
    }

    int[] box() {
        int sw = stage.getWidth(), sh = stage.getHeight();
        if (mw <= 0 || mh <= 0 || sw == 0) return new int[]{0, 0, sw, sh};
        float s = Math.min(sw / (float) mw, sh / (float) mh);
        int w = Math.round(mw * s), h = Math.round(mh * s);
        return new int[]{(sw - w) / 2, (sh - h) / 2, w, h};
    }

    void placeBox() {
        int[] b = box();
        if (b[2] == 0) return;
        if (tv != null) setBox(tv, b);
        if (draw != null) setBox(draw, b);
        setBox(ovl, b);
        for (Ovl o : overlays) positionText(o);
    }

    static void setBox(View v, int[] b) {
        FrameLayout.LayoutParams p = W.flp(b[2], b[3], Gravity.TOP | Gravity.START);
        p.leftMargin = b[0];
        p.topMargin = b[1];
        v.setLayoutParams(p);
    }

    void setMode(String m) {
        mode = m;
        if (penB != null) penB.setBackground("draw".equals(m) ? W.oval(0x38ffffff) : W.ripple(W.oval(0), 999));
        pal.setVisibility("draw".equals(m) || "text".equals(m) ? View.VISIBLE : View.GONE);
        for (Ovl o : overlays) o.el.setEnabled(!"draw".equals(m));
    }

    void undo() {
        if ("draw".equals(mode) && !strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            draw.invalidate();
        } else if (!overlays.isEmpty()) {
            Ovl o = overlays.remove(overlays.size() - 1);
            ovl.removeView(o.el);
            if (active == o) active = null;
        }
    }

    static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (Exception e) {
        }
    }

    void addText() {
        setMode("text");
        String hex = String.format(Locale.ROOT, "#%06x", penColor & 0xffffff);
        Ovl o = new Ovl();
        o.o = App.jo("t", "", "x", .5, "y", .4, "s", 1, "r", 0, "c", hex, "b", "", "f", "sans");
        EditText t = new EditText(a) {
            @Override
            public boolean onKeyPreIme(int code, android.view.KeyEvent ev) {
                if (code == android.view.KeyEvent.KEYCODE_BACK && ev.getAction() == android.view.KeyEvent.ACTION_UP) clearFocus();
                return super.onKeyPreIme(code, ev);
            }
        };
        t.setBackground(null);
        t.setTypeface(Viewer.face("sans"));
        t.setTextColor(penColor);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setPadding(T.dp(10), T.dp(4), T.dp(10), T.dp(4));
        t.setShadowLayer(T.dpf(3), 0, T.dpf(1), 0x8c000000);
        t.setMinWidth(T.dp(30));
        t.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        t.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        t.setSingleLine(false);
        if (android.os.Build.VERSION.SDK_INT >= 29) t.setTextCursorDrawable(com.vulpine.at.ui.W.cursor(0xffffffff));
        o.el = t;
        overlays.add(o);
        active = o;
        ovl.addView(t, W.flp(W.WC, W.WC, Gravity.TOP | Gravity.START));
        t.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int b, int c, int d) {
            }

            public void onTextChanged(CharSequence s, int b, int c, int d) {
            }

            public void afterTextChanged(Editable e) {
                put(o.o, "t", e.toString());
                t.post(() -> positionText(o));
            }
        });
        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(has ? W.round(0, 10, 0xb3ffffff, 1.5f) : null);
            if (has) {
                active = o;
                setMode("text");
            } else {
                if (t.getText().toString().trim().isEmpty()) {
                    ovl.removeView(t);
                    overlays.remove(o);
                    if (active == o) active = null;
                }
                setMode("");
                W.hideKb(t);
            }
        });
        GestureDetector gd = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
            public boolean onDoubleTap(MotionEvent e) {
                String[] fs = {"sans", "serif", "mono"};
                String curF = o.o.optString("f", "sans");
                int k = 0;
                for (int q = 0; q < 3; q++) if (fs[q].equals(curF)) k = q;
                String n = fs[(k + 1) % 3];
                put(o.o, "f", n);
                t.setTypeface(Viewer.face(n));
                t.post(() -> positionText(o));
                return true;
            }
        });
        final float[] ds = new float[5];
        t.setOnTouchListener((v, e) -> {
            gd.onTouchEvent(e);
            if (t.hasFocus()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    ds[0] = e.getRawX();
                    ds[1] = e.getRawY();
                    ds[2] = (float) o.o.optDouble("x", .5);
                    ds[3] = (float) o.o.optDouble("y", .5);
                    ds[4] = 0;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = (e.getRawX() - ds[0]) / Math.max(1, ovl.getWidth()), dy = (e.getRawY() - ds[1]) / Math.max(1, ovl.getHeight());
                    if (Math.abs(dx) + Math.abs(dy) > .01f) ds[4] = 1;
                    put(o.o, "x", Math.max(0, Math.min(1, ds[2] + dx)));
                    put(o.o, "y", Math.max(0, Math.min(1, ds[3] + dy)));
                    positionText(o);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (ds[4] == 0) {
                        t.requestFocus();
                        W.showKb(t);
                    }
                    return true;
            }
            return true;
        });
        t.post(() -> {
            positionText(o);
            t.requestFocus();
            W.showKb(t);
        });
    }

    void positionText(Ovl o) {
        TextView t = o.el;
        int bw = ovl.getWidth(), bh = ovl.getHeight();
        if (bw == 0) return;
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, Viewer.txtSize(a.host));
        t.setMaxWidth(Math.round(bw * .86f));
        t.measure(View.MeasureSpec.makeMeasureSpec(Math.round(bw * .86f), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
        int w = t.getMeasuredWidth(), h = t.getMeasuredHeight();
        t.setTranslationX((float) (o.o.optDouble("x", .5) * bw - w / 2f));
        t.setTranslationY((float) (o.o.optDouble("y", .5) * bh - h / 2f));
    }

    final class DrawView extends View {
        DrawView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas c) {
            Media.drawStrokes(c, strokes, getWidth(), getHeight());
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!"draw".equals(mode)) return false;
            float x = e.getX() / Math.max(1, getWidth()), y = e.getY() / Math.max(1, getHeight());
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    Media.Stroke s = new Media.Stroke();
                    s.color = penColor;
                    s.p.add(new float[]{x, y});
                    strokes.add(s);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (!strokes.isEmpty()) {
                        Media.Stroke s = strokes.get(strokes.size() - 1);
                        for (int h = 0; h < e.getHistorySize(); h++) s.p.add(new float[]{e.getHistoricalX(h) / Math.max(1, getWidth()), e.getHistoricalY(h) / Math.max(1, getHeight())});
                        s.p.add(new float[]{x, y});
                        invalidate();
                    }
                    return true;
            }
            return true;
        }
    }

    void setBusy(boolean b) {
        busy = b;
        W.enable(postB, !b);
        stage.setAlpha(b ? .7f : 1f);
        if (!b) progFill.setScaleX(0);
    }

    void progress(float f) {
        App.ui(() -> progFill.animate().scaleX(Math.max(0, Math.min(1, f))).setDuration(200).setInterpolator(new android.view.animation.LinearInterpolator()).start());
    }

    void post() {
        if (busy) return;
        if (active != null && active.el.hasFocus()) active.el.clearFocus();
        setBusy(true);
        String caption = capIn.getText().toString().trim();
        JSONArray ovs = new JSONArray();
        for (Ovl o : overlays) {
            String t = o.o.optString("t").trim();
            if (t.isEmpty()) continue;
            ovs.put(App.jo("t", t, "x", o.o.optDouble("x"), "y", o.o.optDouble("y"), "s", o.o.optDouble("s", 1), "r", o.o.optDouble("r", 0), "c", o.o.optString("c"), "b", o.o.optString("b"), "f", o.o.optString("f")));
        }
        List<Media.Stroke> st = new ArrayList<>(strokes);
        App.bg(() -> {
            File up;
            String upName, upMime;
            int w, h;
            double d = 0;
            if (isVid) {
                long limit = (Store.pro ? 100L : 30L) * 1024 * 1024;
                up = Media.maybeTranscode(file, limit, f -> progress(f * .3f));
                boolean tr = up != file;
                upName = tr ? "story.mp4" : (name == null || name.isEmpty() ? "story.mp4" : name);
                upMime = tr ? "video/mp4" : mime;
                Media.Probe p = Media.probe(file);
                w = p.w > 0 ? p.w : mw;
                h = p.h > 0 ? p.h : mh;
                d = p.dur > 0 ? p.dur : vdur;
            } else {
                int[] dims = new int[2];
                up = Media.flatten(file, st, new File(file.getParentFile(), file.getName() + ".webp"), dims);
                upName = "story.webp";
                upMime = "image/webp";
                w = dims[0];
                h = dims[1];
            }
            if (up == null) {
                fail("encode_failed", null);
                return;
            }
            float base = isVid && up != file ? .3f : 0f;
            Http.Resp r = Http.upload("/stories/media", "file", up, upName, upMime, (sent, total) -> progress(base + (1 - base) * sent / (float) Math.max(1, total)));
            if (!r.ok()) {
                fail(r.error(), r.obj());
                return;
            }
            JSONObject body = App.jo("media", r.obj().optString("media"), "caption", caption, "audience", opts.audience, "allow", new JSONArray(opts.allow), "deny", new JSONArray(opts.deny), "hours", opts.hours, "width", w, "height", h, "duration", Math.max(0, Math.min(60, d)), "overlays", ovs);
            if (opts.groupId != null) put(body, "groupId", opts.groupId);
            Http.Resp p = Http.post("/stories", body);
            if (!p.ok()) {
                fail(p.error(), p.obj());
                return;
            }
            App.ui(() -> {
                Ov.toast(opts.groupId != null ? "Group story shared" : "Story shared");
                busy = false;
                finish();
                Store.load();
            });
        });
    }

    void fail(String m, JSONObject data) {
        App.ui(() -> {
            String msg;
            if ("daily_limit".equals(m)) msg = "Daily story limit reached (" + (data == null ? "" : data.opt("max")) + ")" + (Store.pro ? "" : " - Pro Box allows more");
            else if ("file_too_large".equals(m)) msg = "File is too large (" + (data == null ? "" : data.optString("limit", Store.pro ? "100MB" : "30MB")) + ")";
            else if ("invalid_file_type".equals(m)) msg = "This file type isn't supported";
            else if ("storage_full".equals(m)) msg = "Stories are paused for a moment, try later";
            else if ("boost_level_required".equals(m)) msg = "Group stories need boost level 5";
            else msg = "Could not share the story";
            Ov.toast(msg, true);
            setBusy(false);
        });
    }

    void finish() {
        if (done) return;
        done = true;
        if (cur == this) cur = null;
        Ov.remove(root);
        W.hideKb(root);
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception e) {
            }
            mp = null;
        }
        root.animate().alpha(0).setDuration(220).withEndAction(() -> {
            a.host.removeView(root);
            if (preview != null) preview = null;
        }).start();
    }
}
