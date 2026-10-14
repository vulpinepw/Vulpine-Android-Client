package com.vulpine.at.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.ArrayList;
import java.util.List;

public final class AlbumSheet {
    static void open(ChatScreen s, List<Attach.Item> in) {
        Context c = s.a;
        List<Attach.Item> items = new ArrayList<>(in);
        boolean[] car = {false};
        Ov.Sheet sh = new Ov.Sheet(c);
        LinearLayout head = W.row(c);
        TextView title = W.text(c, "Send media", 20, 600, T.text);
        head.addView(title);
        head.addView(W.flex(c));
        TextView count = W.text(c, items.size() + " / 5", 13, 400, T.textSecondary);
        count.setFontFeatureSettings("tnum");
        head.addView(count);
        sh.body.addView(head, W.lpm(W.MP, W.WC, 0, 0, 0, 12));
        int media = 0;
        for (Attach.Item it : items) if (it.mime.startsWith("image/") || it.mime.startsWith("video/")) media++;
        FrameLayout grid = new FrameLayout(c);
        grid.setClipChildren(false);
        Runnable[] render = new Runnable[1];
        if (media >= 2) {
            W.Segmented mode = new W.Segmented(c, new String[]{"Album", "Carousel"}, 0, i -> {
                car[0] = i == 1;
                title.setText(car[0] ? "Send carousel" : "Send media");
                render[0].run();
            });
            sh.body.addView(mode, W.lpm(W.MP, W.WC, 0, 0, 0, 12));
        }
        sh.body.addView(grid);
        TextView tip = W.text(c, "Drag to reorder.", 13, 400, T.textSecondary);
        tip.setGravity(Gravity.CENTER);
        sh.body.addView(tip, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        W.Field cap = new W.Field(c, "Add a caption...", null);
        cap.input.setText(s.composer.input.getText());
        sh.body.addView(cap, W.lpm(W.MP, W.WC, 0, 4, 0, 0));
        LinearLayout acts = W.row(c);
        acts.setGravity(Gravity.END);
        acts.addView(W.btn(c, "Cancel", false, v -> sh.close()));
        TextView send = W.btn(c, "Send " + items.size(), true, v -> {
            sh.close();
            s.composer.input.setText("");
            Attach.sendItems(s, items, cap.value().trim(), car[0]);
        });
        acts.addView(send, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        sh.body.addView(acts, W.lpm(W.MP, W.WC, 0, 12, 0, 0));
        int cell = (c.getResources().getDisplayMetrics().widthPixels - T.dp(16) - sh.card.getPaddingLeft() - sh.card.getPaddingRight() - 2 * T.dp(8)) / 3;
        int gap = T.dp(8);
        List<View> tiles = new ArrayList<>();
        boolean[] first = {true};
        java.util.function.BiConsumer<View, Integer> place = (v, k) -> {
            v.animate().x((k % 3) * (cell + gap)).y((k / 3) * (cell + gap)).setDuration(180).setInterpolator(T.EASE).start();
        };
        render[0] = () -> {
            grid.removeAllViews();
            tiles.clear();
            int slots = items.size() + (items.size() < 5 ? 1 : 0);
            grid.setLayoutParams(new LinearLayout.LayoutParams(3 * cell + 2 * gap, ((slots + 2) / 3) * (cell + gap)));
            count.setText(items.size() + " / 5");
            send.setText(items.size() == 1 ? "Send" : "Send " + items.size());
            tip.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
            for (int i = 0; i < items.size(); i++) {
                Attach.Item it = items.get(i);
                FrameLayout f = new FrameLayout(c);
                f.setBackground(W.round(T.mixA(T.text, .07f), 12));
                f.setClipToOutline(true);
                if (it.mime.startsWith("image/")) {
                    ImageView iv = new ImageView(c);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    App.bg(() -> {
                        Bitmap b = Img.decode(it.file, cell);
                        App.ui(() -> iv.setImageBitmap(b));
                    });
                    f.addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP));
                } else if (it.mime.startsWith("video/")) {
                    ImageView iv = new ImageView(c);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    App.bg(() -> {
                        Bitmap b = null;
                        try {
                            android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                            r.setDataSource(it.file.getPath());
                            b = r.getFrameAtTime(50000);
                            r.release();
                        } catch (Exception e) {
                        }
                        Bitmap fb = b;
                        App.ui(() -> iv.setImageBitmap(fb));
                    });
                    f.addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP));
                    ImageView pl = new ImageView(c);
                    pl.setImageDrawable(Svg.fill(16, 0xffffffff, "M8 5.5v13l11-6.5z"));
                    pl.setScaleType(ImageView.ScaleType.CENTER);
                    pl.setBackground(W.oval(0x80000000));
                    f.addView(pl, new FrameLayout.LayoutParams(T.dp(30), T.dp(30), Gravity.CENTER));
                } else {
                    LinearLayout fl = W.col(c);
                    fl.setGravity(Gravity.CENTER);
                    fl.setPadding(T.dp(8), T.dp(8), T.dp(8), T.dp(8));
                    fl.addView(W.img(c, Svg.stroke(22, T.textSecondary, 2, "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6")), W.lp(T.dp(22), T.dp(22)));
                    TextView n = W.text(c, it.name, 11, 400, T.textSecondary);
                    W.ellipsize(n);
                    fl.addView(n, W.lpm(W.WC, W.WC, 0, 6, 0, 0));
                    f.addView(fl, new FrameLayout.LayoutParams(W.MP, W.MP));
                }
                if (car[0]) {
                    TextView ord = W.text(c, String.valueOf(i + 1), 11, 700, 0xffffffff);
                    ord.setGravity(Gravity.CENTER);
                    ord.setMinWidth(T.dp(20));
                    ord.setPadding(T.dp(6), 0, T.dp(6), 0);
                    ord.setBackground(W.round(T.accent, 99));
                    FrameLayout.LayoutParams op = new FrameLayout.LayoutParams(W.WC, T.dp(20), Gravity.TOP | Gravity.START);
                    op.leftMargin = op.topMargin = T.dp(6);
                    f.addView(ord, op);
                }
                ImageView x = new ImageView(c);
                x.setImageDrawable(Svg.stroke(14, 0xffffffff, 2.2f, "M6 6l12 12", "M18 6L6 18"));
                x.setScaleType(ImageView.ScaleType.CENTER);
                x.setBackground(W.oval(0x8c000000));
                x.setOnClickListener(v -> {
                    items.remove(it);
                    if (items.isEmpty()) sh.close();
                    else render[0].run();
                });
                FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(T.dp(24), T.dp(24), Gravity.TOP | Gravity.END);
                xp.topMargin = xp.rightMargin = T.dp(6);
                f.addView(x, xp);
                float[] st = new float[4];
                boolean[] dragging = {false};
                f.setOnTouchListener((v, e) -> {
                    switch (e.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            st[0] = e.getRawX();
                            st[1] = e.getRawY();
                            st[2] = v.getX();
                            st[3] = v.getY();
                            dragging[0] = false;
                            return items.size() > 1;
                        case android.view.MotionEvent.ACTION_MOVE: {
                            float dx = e.getRawX() - st[0], dy = e.getRawY() - st[1];
                            if (!dragging[0]) {
                                if (Math.hypot(dx, dy) < T.dp(6)) return true;
                                dragging[0] = true;
                                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                                v.bringToFront();
                                v.animate().cancel();
                                v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                                v.setElevation(T.dpf(10));
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                            }
                            float nx = Math.max(0, Math.min(2 * (cell + gap), st[2] + dx)), ny = Math.max(0, Math.min(((items.size() - 1) / 3) * (cell + gap), st[3] + dy));
                            v.setX(nx);
                            v.setY(ny);
                            int col = Math.max(0, Math.min(2, Math.round(nx / (cell + gap)))), row = Math.round(ny / (cell + gap));
                            int to = Math.max(0, Math.min(items.size() - 1, row * 3 + col));
                            int from = tiles.indexOf(v);
                            if (from >= 0 && to != from) {
                                items.add(to, items.remove(from));
                                tiles.add(to, tiles.remove(from));
                                for (int k = 0; k < tiles.size(); k++) if (tiles.get(k) != v) place.accept(tiles.get(k), k);
                            }
                            return true;
                        }
                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                            if (dragging[0]) {
                                dragging[0] = false;
                                v.setElevation(0);
                                int k = tiles.indexOf(v);
                                v.animate().scaleX(1).scaleY(1).x((k % 3) * (cell + gap)).y((k / 3) * (cell + gap)).setDuration(180).setInterpolator(T.EASE).withEndAction(() -> {
                                    first[0] = false;
                                    if (car[0]) render[0].run();
                                }).start();
                            }
                            return true;
                    }
                    return false;
                });
                grid.addView(f, new FrameLayout.LayoutParams(cell, cell));
                tiles.add(f);
                f.setX((i % 3) * (cell + gap));
                f.setY((i / 3) * (cell + gap));
                if (first[0]) {
                    f.setScaleX(.4f);
                    f.setScaleY(.4f);
                    f.setAlpha(0);
                    f.animate().scaleX(1).scaleY(1).alpha(1).setStartDelay(i * 40L).setDuration(260).setInterpolator(T.POP).start();
                }
            }
            if (items.size() < 5) {
                LinearLayout add = W.col(c);
                add.setGravity(Gravity.CENTER);
                add.setBackground(W.round(T.mixA(T.text, .05f), 12));
                add.addView(W.text(c, "+", 26, 400, T.textSecondary));
                add.addView(W.text(c, "Add", 11, 400, T.textSecondary));
                add.setOnClickListener(v -> {
                    android.content.Intent pi = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(android.content.Intent.CATEGORY_OPENABLE).putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
                    App.startForResult(s.a, android.content.Intent.createChooser(pi, null), (res, data) -> {
                        if (res != android.app.Activity.RESULT_OK || data == null) return;
                        List<android.net.Uri> uris = new ArrayList<>();
                        android.content.ClipData cd = data.getClipData();
                        if (cd != null) for (int k = 0; k < cd.getItemCount(); k++) uris.add(cd.getItemAt(k).getUri());
                        else if (data.getData() != null) uris.add(data.getData());
                        int room = 5 - items.size();
                        if (uris.size() > room) {
                            Ov.toast("Up to 5 items per message");
                            uris = uris.subList(0, Math.max(0, room));
                        }
                        List<android.net.Uri> fu = uris;
                        App.bg(() -> {
                            List<Attach.Item> got = new ArrayList<>();
                            for (android.net.Uri u : fu) {
                                Attach.Item n = Attach.copy(s.a, u);
                                if (n != null) got.add(n);
                            }
                            App.ui(() -> {
                                items.addAll(got);
                                render[0].run();
                            });
                        });
                    });
                });
                grid.addView(add, new FrameLayout.LayoutParams(cell, cell));
                add.setX((items.size() % 3) * (cell + gap));
                add.setY((items.size() / 3) * (cell + gap));
            }
        };
        render[0].run();
        first[0] = false;
        sh.show();
    }
}
