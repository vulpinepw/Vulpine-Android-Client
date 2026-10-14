package com.vulpine.at.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class ImageEditor {
    static final int[] COLORS = {0xffe11d48, 0xfff59e0b, 0xff22c55e, 0xff3b82f6, 0xffffffff, 0xff111111};

    static class Stage extends View {
        Bitmap bmp;
        final List<Bitmap> hist = new ArrayList<>();
        boolean draw;
        int color = COLORS[0];
        RectF sel;
        float sx, sy;
        Path cur;
        final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG), dim = new Paint(), img = new Paint(Paint.FILTER_BITMAP_FLAG);
        RectF dst = new RectF();
        Runnable changed;

        Stage(Context c, Bitmap b) {
            super(c);
            bmp = b.copy(Bitmap.Config.ARGB_8888, true);
            pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeCap(Paint.Cap.ROUND);
            pen.setStrokeJoin(Paint.Join.ROUND);
            dim.setColor(0x8c000000);
        }

        void layoutDst() {
            float s = Math.min(getWidth() / (float) bmp.getWidth(), getHeight() / (float) bmp.getHeight());
            float w = bmp.getWidth() * s, h = bmp.getHeight() * s;
            dst.set((getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
        }

        protected void onDraw(Canvas c) {
            layoutDst();
            c.drawBitmap(bmp, null, dst, img);
            if (cur != null) {
                pen.setColor(color);
                pen.setStrokeWidth(Math.max(2, bmp.getWidth() / 160f) * dst.width() / bmp.getWidth());
                c.drawPath(cur, pen);
            }
            if (!draw && sel != null) {
                RectF r = norm(sel);
                c.drawRect(dst.left, dst.top, dst.right, r.top, dim);
                c.drawRect(dst.left, r.bottom, dst.right, dst.bottom, dim);
                c.drawRect(dst.left, r.top, r.left, r.bottom, dim);
                c.drawRect(r.right, r.top, dst.right, r.bottom, dim);
                Paint b = new Paint();
                b.setStyle(Paint.Style.STROKE);
                b.setColor(0xffffffff);
                b.setStrokeWidth(T.dpf(1.5f));
                c.drawRect(r, b);
            }
        }

        static RectF norm(RectF r) {
            return new RectF(Math.min(r.left, r.right), Math.min(r.top, r.bottom), Math.max(r.left, r.right), Math.max(r.top, r.bottom));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = Math.max(dst.left, Math.min(dst.right, e.getX())), y = Math.max(dst.top, Math.min(dst.bottom, e.getY()));
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    if (draw) {
                        cur = new Path();
                        cur.moveTo(x, y);
                    } else sel = new RectF(x, y, x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (draw && cur != null) cur.lineTo(x, y);
                    else if (sel != null) {
                        sel.right = x;
                        sel.bottom = y;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (draw && cur != null) commit();
                    break;
            }
            invalidate();
            if (changed != null) changed.run();
            return true;
        }

        void commit() {
            push();
            Canvas cv = new Canvas(bmp);
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setRectToRect(dst, new RectF(0, 0, bmp.getWidth(), bmp.getHeight()), android.graphics.Matrix.ScaleToFit.FILL);
            Path p = new Path(cur);
            p.transform(m);
            pen.setColor(color);
            pen.setStrokeWidth(Math.max(2, bmp.getWidth() / 160f));
            cv.drawPath(p, pen);
            cur = null;
        }

        void push() {
            hist.add(bmp.copy(Bitmap.Config.ARGB_8888, true));
            if (hist.size() > 12) hist.remove(0);
        }

        void undo() {
            if (hist.isEmpty()) return;
            bmp = hist.remove(hist.size() - 1);
            sel = null;
            invalidate();
        }

        boolean canCrop() {
            if (sel == null) return false;
            RectF r = norm(sel);
            return r.width() >= T.dp(5) && r.height() >= T.dp(5);
        }

        void crop() {
            if (!canCrop()) return;
            RectF r = norm(sel);
            float s = bmp.getWidth() / dst.width();
            int x = Math.round((r.left - dst.left) * s), y = Math.round((r.top - dst.top) * s), w = Math.round(r.width() * s), h = Math.round(r.height() * s);
            x = Math.max(0, Math.min(bmp.getWidth() - 1, x));
            y = Math.max(0, Math.min(bmp.getHeight() - 1, y));
            w = Math.max(1, Math.min(bmp.getWidth() - x, w));
            h = Math.max(1, Math.min(bmp.getHeight() - y, h));
            push();
            bmp = Bitmap.createBitmap(bmp, x, y, w, h).copy(Bitmap.Config.ARGB_8888, true);
            sel = null;
            invalidate();
        }
    }

    static void open(ChatScreen s, Attach.Item it) {
        Context c = s.a;
        Bitmap b = Img.decode(it.file, 900);
        if (b == null) {
            Attach.sendItems(s, java.util.Collections.singletonList(it), "", false);
            return;
        }
        try {
            android.media.ExifInterface ex = new android.media.ExifInterface(it.file.getPath());
            int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            int rot = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0;
            if (rot != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(rot);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
        } catch (Exception e) {
        }
        FrameLayout ov = new FrameLayout(c);
        ov.setBackgroundColor(0xcc000000);
        ov.setClickable(true);
        ov.setElevation(T.dpf(60));
        ov.setPadding(T.dp(12), s.a.insetTop + T.dp(12), T.dp(12), s.a.insetBottom + T.dp(12));
        LinearLayout card = W.col(c);
        card.setBackground(W.round(T.surface, 16));
        card.setPadding(T.dp(12), T.dp(12), T.dp(12), T.dp(12));
        card.setElevation(T.dpf(20));
        LinearLayout head = W.row(c);
        head.addView(W.text(c, "Send media", 14, 600, T.text));
        head.addView(W.flex(c));
        TextView fn = W.text(c, it.name, 13, 400, T.textMuted);
        W.ellipsize(fn);
        fn.setMaxWidth(Math.round(c.getResources().getDisplayMetrics().widthPixels * .45f));
        head.addView(fn);
        card.addView(head, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        FrameLayout box = new FrameLayout(c);
        box.setBackground(W.round(0xff000000, 12));
        box.setClipToOutline(true);
        Stage st = new Stage(c, b);
        box.addView(st, new FrameLayout.LayoutParams(W.MP, W.MP));
        card.addView(box, W.lp(W.MP, Math.round(c.getResources().getDisplayMetrics().heightPixels * .42f)));
        LinearLayout tools = W.row(c);
        W.Segmented seg = new W.Segmented(c, new String[]{"Crop", "Draw"}, 0, null);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams segp = W.lp(0, W.WC, 1);
        tools.addView(seg, segp);
        seg.setMinimumWidth(T.dp(120));
        LinearLayout colors = W.row(c);
        for (int col : COLORS) {
            View d = new View(c);
            d.setTag(col);
            d.setOnClickListener(v -> {
                st.color = col;
                for (int k = 0; k < colors.getChildCount(); k++) {
                    View x = colors.getChildAt(k);
                    x.setBackground(W.round((int) x.getTag(), 99, (int) x.getTag() == st.color ? T.text : 0x40ffffff, 2));
                }
            });
            d.setBackground(W.round(col, 99, col == st.color ? T.text : 0x40ffffff, 2));
            colors.addView(d, W.lpm(T.dp(22), T.dp(22), 4, 0, 0, 0));
        }
        colors.setVisibility(View.GONE);
        TextView undo = W.btn(c, "Undo", false, v -> st.undo());
        undo.setSingleLine(true);
        tools.addView(undo, W.lpm(W.WC, T.dp(30), 6, 0, 0, 0));
        TextView apply = W.btn(c, "Apply crop", false, v -> st.crop());
        apply.setBackground(W.ripple(W.round(T.surface2, 999), 999));
        apply.setTextColor(T.text);
        apply.setSingleLine(true);
        tools.addView(apply, W.lpm(W.WC, T.dp(30), 6, 0, 0, 0));
        card.addView(tools, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        android.widget.HorizontalScrollView csv = new android.widget.HorizontalScrollView(c);
        csv.setHorizontalScrollBarEnabled(false);
        csv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        colors.setPadding(0, T.dp(2), T.dp(4), T.dp(2));
        csv.addView(colors);
        card.addView(csv, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        TextView hint = W.text(c, "Drag on the image to select a region, then Apply crop.", 13, 400, T.textMuted);
        card.addView(hint, W.lpm(W.MP, W.WC, 0, 8, 0, 8));
        seg.on = i -> {
            st.draw = i == 1;
            st.sel = null;
            st.invalidate();
            colors.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
            apply.setVisibility(i == 1 ? View.GONE : View.VISIBLE);
            hint.setText(i == 1 ? "Draw with your mouse or finger. Use Undo to remove strokes." : "Drag on the image to select a region, then Apply crop.");
        };
        st.changed = () -> W.enable(apply, st.canCrop());
        W.enable(apply, false);
        EditText cap = new EditText(c);
        cap.setHint("Add a caption…");
        cap.setHintTextColor(T.textMuted);
        T.text(cap, 14, 400, T.text);
        cap.setBackground(W.round(T.surface2, 12));
        cap.setPadding(T.dp(12), T.dp(10), T.dp(12), T.dp(10));
        cap.setSingleLine(true);
        cap.setImeOptions(EditorInfo.IME_ACTION_SEND);
        cap.setText(s.composer.input.getText());
        card.addView(cap, W.lp(W.MP, W.WC));
        LinearLayout acts = W.row(c);
        acts.setGravity(Gravity.END);
        Runnable[] close = new Runnable[1];
        close[0] = () -> {
            com.vulpine.at.ui.Ov.remove(ov);
            W.hideKb(cap);
            ov.animate().alpha(0).setDuration(200).withEndAction(() -> s.a.host.removeView(ov)).start();
        };
        acts.addView(W.btn(c, "Cancel", false, v -> close[0].run()));
        Runnable send = () -> {
            String caption = cap.getText().toString().trim();
            Bitmap out = st.bmp;
            close[0].run();
            s.composer.input.setText("");
            App.bg(() -> {
                try {
                    File f = new File(it.file.getParentFile(), it.file.getName().replaceAll("\\.[^.]*$", "") + "-ed.png");
                    try (FileOutputStream fo = new FileOutputStream(f)) {
                        out.compress(Bitmap.CompressFormat.PNG, 100, fo);
                    }
                    Attach.Item n = new Attach.Item();
                    n.file = f;
                    n.name = it.name.replaceAll("\\.[^.]*$", "") + ".png";
                    n.mime = "image/png";
                    n.w = out.getWidth();
                    n.h = out.getHeight();
                    App.ui(() -> Attach.sendItems(s, java.util.Collections.singletonList(n), caption, false));
                } catch (Exception e) {
                    App.ui(() -> Ov.toast("Upload failed", true));
                }
            });
        };
        cap.setOnEditorActionListener((v, a, e) -> {
            send.run();
            return true;
        });
        acts.addView(W.btn(c, "Send", true, v -> send.run()), W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        card.addView(acts, W.lpm(W.MP, W.WC, 0, 10, 0, 0));
        ov.addView(card, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.CENTER));
        s.a.host.addView(ov, new FrameLayout.LayoutParams(W.MP, W.MP));
        com.vulpine.at.ui.Ov.push(ov, close[0]);
        ov.setAlpha(0);
        ov.animate().alpha(1).setDuration(220).start();
        card.setTranslationY(T.dp(14));
        card.setScaleX(.96f);
        card.setScaleY(.96f);
        card.animate().translationY(0).scaleX(1).scaleY(1).setDuration(300).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
    }
}
