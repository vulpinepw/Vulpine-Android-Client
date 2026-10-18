package com.vulpine.at.chat;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Share;
import com.vulpine.at.ui.Blur;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AttachSheet {
    static final class M {
        long id;
        boolean video;
        long dur;
        Uri uri;
    }

    static AttachSheet cur;
    static final LruCache<Long, Bitmap> thumbs = new LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        protected int sizeOf(Long k, Bitmap b) {
            return b.getByteCount();
        }
    };
    static final ExecutorService pool = Executors.newFixedThreadPool(3);

    final ChatScreen s;
    FrameLayout scrim;
    LinearLayout sheet, tabs, sendBar;
    FrameLayout area;
    GridView grid;
    TextView title, sendBtn;
    final List<M> media = new ArrayList<>();
    final List<M> picked = new ArrayList<>();
    Adapter ad;
    boolean closing;

    static void open(ChatScreen s) {
        if (cur != null) return;
        W.hideKb(s.composer.input);
        if (s.composer.panels.open()) s.composer.panels.close();
        cur = new AttachSheet(s);
        cur.build();
    }

    static boolean close() {
        if (cur == null) return false;
        cur.dismiss();
        return true;
    }

    AttachSheet(ChatScreen s) {
        this.s = s;
    }

    void build() {
        Context c = s.a;
        scrim = new FrameLayout(c);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(v -> dismiss());
        int H = s.root.getHeight() > 0 ? s.root.getHeight() : c.getResources().getDisplayMetrics().heightPixels;
        int nav = s.composer.navH;
        sheet = W.col(c);
        sheet.setClickable(true);
        sheet.setBackground(W.round(T.glassBgStrong, 22, T.glassStroke, .5f));
        Blur.apply(sheet, new float[]{22, 22, 0, 0}, .88f, s.root.getChildAt(0), s.root.getChildAt(1));
        sheet.setElevation(T.dpf(24));
        sheet.setPadding(T.dp(8), 0, T.dp(8), nav + T.dp(8));
        LinearLayout top = W.col(c);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        View handle = new View(c);
        handle.setBackground(W.round(T.dark ? 0x40ffffff : 0x33000000, 99));
        top.addView(handle, W.lpm(T.dp(36), T.dp(4), 0, 8, 0, 0));
        title = W.text(c, "Gallery", 16, 700, T.text);
        LinearLayout.LayoutParams tp = W.lpm(W.MP, W.WC, 8, 8, 8, 8);
        top.addView(title, tp);
        drag(top);
        sheet.addView(top, W.lp(W.MP, W.WC));
        area = new FrameLayout(c);
        sheet.addView(area, W.lp(W.MP, 0, 1));
        tabs = W.row(c);
        tabs.setGravity(Gravity.CENTER);
        tabs.addView(tab(c, "Gallery", true, Svg.stroke(22, T.accentFg, 2, "M3 5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z", "M21 15l-5-5L5 21"), v -> {
            if (!granted()) ask();
        }), W.lp(0, W.WC, 1));
        tabs.addView(tab(c, "Camera", false, Svg.stroke(22, T.text, 2, "M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z", "M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"), v -> camera()), W.lp(0, W.WC, 1));
        tabs.addView(tab(c, "File", false, Svg.stroke(22, T.text, 2, "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", "M14 2v6h6", "M9 13h6", "M9 17h6"), v -> {
            dismiss();
            Attach.files(s);
        }), W.lp(0, W.WC, 1));
        if (s.conv != null && s.conv.group() && s.composer.canPoll()) tabs.addView(tab(c, "Poll", false, Svg.stroke(22, T.text, 2, "M4 20V10", "M10 20V4", "M16 20v-6", "M22 20H2"), v -> {
            dismiss();
            com.vulpine.at.polls.Polls.create(s.a, s.cid);
        }), W.lp(0, W.WC, 1));
        sheet.addView(tabs, W.lpm(W.MP, W.WC, 0, 8, 0, 0));
        sendBar = W.row(c);
        sendBar.setGravity(Gravity.CENTER);
        sendBtn = W.text(c, "Send", 15, 700, T.accentFg);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setBackground(W.ripple(W.accentBg(999), 999));
        sendBtn.setOnClickListener(v -> sendPicked());
        sendBar.addView(sendBtn, W.lp(W.MP, T.dp(48)));
        sendBar.setVisibility(View.GONE);
        sheet.addView(sendBar, W.lpm(W.MP, W.WC, 8, 10, 8, 6));
        s.root.addView(scrim, new FrameLayout.LayoutParams(W.MP, W.MP));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W.MP, Math.round(H * .66f), Gravity.BOTTOM);
        s.root.addView(sheet, lp);
        scrim.setAlpha(0);
        scrim.animate().alpha(1).setDuration(180).start();
        sheet.setTranslationY(H * .66f);
        sheet.animate().translationY(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        if (granted()) load();
        else {
            placeholder(false);
            ask();
        }
    }

    void drag(View v) {
        float[] y0 = new float[1];
        v.setOnTouchListener((x, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    y0[0] = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    sheet.setTranslationY(Math.max(0, e.getRawY() - y0[0]));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (sheet.getTranslationY() > sheet.getHeight() / 4f) dismiss();
                    else sheet.animate().translationY(0).setDuration(160).start();
                    return true;
            }
            return false;
        });
    }

    View tab(Context c, String label, boolean active, android.graphics.drawable.Drawable icon, View.OnClickListener on) {
        LinearLayout t = W.col(c);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, T.dp(4), 0, T.dp(2));
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setBackground(active ? W.accentBg(999) : W.round(T.dark ? 0x1fffffff : 0x14000000, 999));
        t.addView(iv, W.lp(T.dp(50), T.dp(50)));
        TextView l = W.text(c, label, 12, active ? 600 : 500, active ? T.accent : T.textSecondary);
        t.addView(l, W.lpm(W.WC, W.WC, 0, 6, 0, 0));
        t.setOnClickListener(on);
        W.press(t, .94f);
        return t;
    }

    static String[] perms() {
        if (Build.VERSION.SDK_INT >= 34) return new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"};
        if (Build.VERSION.SDK_INT >= 33) return new String[]{"android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"};
        return new String[]{"android.permission.READ_EXTERNAL_STORAGE"};
    }

    boolean granted() {
        for (String p : perms()) if (s.a.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED) return true;
        return false;
    }

    void ask() {
        App.requestPerms(s.a, perms(), ok -> {
            if (cur != this) return;
            if (granted()) load();
            else placeholder(true);
        });
    }

    void placeholder(boolean denied) {
        area.removeAllViews();
        Context c = s.a;
        LinearLayout col = W.col(c);
        col.setGravity(Gravity.CENTER);
        col.setPadding(T.dp(24), 0, T.dp(24), 0);
        TextView t = W.text(c, denied ? "Vulpine needs access to your photos and videos to show them here." : "Loading…", 14, 400, T.textSecondary);
        t.setGravity(Gravity.CENTER);
        col.addView(t, W.lp(W.WC, W.WC));
        if (denied) {
            TextView b = W.text(c, "Allow access", 14, 600, T.accentFg);
            b.setGravity(Gravity.CENTER);
            b.setPadding(T.dp(20), 0, T.dp(20), 0);
            b.setBackground(W.ripple(W.accentBg(999), 999));
            b.setOnClickListener(v -> {
                Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + s.a.getPackageName()));
                App.startForResult(s.a, i, (r, d) -> {
                    if (cur == this && granted()) load();
                });
            });
            col.addView(b, W.lpm(W.WC, T.dp(42), 0, 14, 0, 0));
            TextView o = W.text(c, "Choose from gallery", 14, 600, T.accent);
            o.setGravity(Gravity.CENTER);
            o.setPadding(T.dp(20), T.dp(10), T.dp(20), T.dp(10));
            o.setOnClickListener(v -> systemPicker());
            col.addView(o, W.lpm(W.WC, W.WC, 0, 6, 0, 0));
        }
        area.addView(col, new FrameLayout.LayoutParams(W.MP, W.MP));
    }

    void systemPicker() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 33) i = new Intent(MediaStore.ACTION_PICK_IMAGES).putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 5);
        else i = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"}).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        dismiss();
        App.startForResult(s.a, i, (res, data) -> {
            if (res != Activity.RESULT_OK || data == null) return;
            List<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) for (int k = 0; k < cd.getItemCount(); k++) uris.add(cd.getItemAt(k).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            Attach.pickedUris(s, uris);
        });
    }

    void load() {
        placeholder(false);
        App.bg(() -> {
            List<M> out = new ArrayList<>();
            Uri col = MediaStore.Files.getContentUri("external");
            String[] proj = Build.VERSION.SDK_INT >= 29 ? new String[]{MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DURATION} : new String[]{MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE};
            String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
            try (Cursor cu = s.a.getContentResolver().query(col, proj, sel, null, MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
                while (cu != null && cu.moveToNext() && out.size() < 600) {
                    M m = new M();
                    m.id = cu.getLong(0);
                    m.video = cu.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                    m.dur = proj.length > 2 ? cu.getLong(2) : 0;
                    m.uri = ContentUris.withAppendedId(m.video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, m.id);
                    out.add(m);
                }
            } catch (Exception e) {
            }
            App.ui(() -> {
                if (cur != this) return;
                media.clear();
                media.addAll(out);
                showGrid();
            });
        });
    }

    void showGrid() {
        area.removeAllViews();
        Context c = s.a;
        if (media.isEmpty()) {
            TextView t = W.text(c, "No photos or videos yet", 14, 400, T.textSecondary);
            t.setGravity(Gravity.CENTER);
            area.addView(t, new FrameLayout.LayoutParams(W.MP, W.MP));
            return;
        }
        grid = new GridView(c);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(T.dp(3));
        grid.setVerticalSpacing(T.dp(3));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= 21) grid.setNestedScrollingEnabled(true);
        ad = new Adapter();
        grid.setAdapter(ad);
        grid.setClipToOutline(true);
        grid.setOutlineProvider(new android.view.ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), T.dpf(14));
            }
        });
        area.addView(grid, new FrameLayout.LayoutParams(W.MP, W.MP));
    }

    void toggle(M m) {
        if (picked.contains(m)) picked.remove(m);
        else {
            if (picked.size() >= 5) {
                Ov.toast("Up to 5 items per message");
                return;
            }
            picked.add(m);
        }
        refreshPicked();
    }

    void refreshPicked() {
        int n = picked.size();
        title.setText(n == 0 ? "Gallery" : n + " selected");
        sendBtn.setText(n == 1 ? "Send 1 item" : "Send " + n + " items");
        boolean show = n > 0;
        if (show != (sendBar.getVisibility() == View.VISIBLE)) {
            sendBar.setVisibility(show ? View.VISIBLE : View.GONE);
            tabs.setVisibility(show ? View.GONE : View.VISIBLE);
            View in = show ? sendBar : tabs;
            in.setAlpha(0);
            in.setTranslationY(T.dp(10));
            in.animate().alpha(1).translationY(0).setDuration(160).start();
        }
        if (ad != null) ad.notifyDataSetChanged();
    }

    void sendPicked() {
        List<Uri> uris = new ArrayList<>();
        for (M m : picked) uris.add(m.uri);
        dismiss();
        Attach.pickedUris(s, uris);
    }

    void camera() {
        App.requestPerms(s.a, new String[]{android.Manifest.permission.CAMERA}, ok -> {
            if (!ok) {
                Ov.toast("Camera access denied", true);
                return;
            }
            File dir = new File(s.a.getCacheDir(), "share");
            dir.mkdirs();
            File f = new File(dir, "cam-" + System.currentTimeMillis() + ".jpg");
            try {
                f.createNewFile();
            } catch (Exception e) {
                Ov.toast("Could not open the camera", true);
                return;
            }
            Uri u = Uri.parse("content://" + Share.AUTH + "/" + Uri.encode(f.getName()));
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, u);
            i.setClipData(ClipData.newRawUri("", u));
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            dismiss();
            App.startForResult(s.a, i, (res, data) -> {
                if (res != Activity.RESULT_OK || f.length() == 0) {
                    f.delete();
                    if (res == Activity.RESULT_OK) Ov.toast("Could not take the photo", true);
                    return;
                }
                App.bg(() -> {
                    File up = new File(s.a.getCacheDir(), "up");
                    up.mkdirs();
                    File dst = new File(up, f.getName());
                    if (!f.renameTo(dst)) dst = f;
                    Attach.Item it = new Attach.Item();
                    it.file = dst;
                    it.name = "photo-" + System.currentTimeMillis() + ".jpg";
                    it.mime = "image/jpeg";
                    Attach.dims(it);
                    List<Attach.Item> items = new ArrayList<>();
                    items.add(it);
                    App.ui(() -> Attach.route(s, items));
                });
            });
        });
    }

    void dismiss() {
        if (closing) return;
        closing = true;
        if (cur == this) cur = null;
        float h = sheet.getHeight() > 0 ? sheet.getHeight() : T.dp(400);
        scrim.animate().alpha(0).setDuration(160).start();
        sheet.animate().translationY(h).setDuration(200).setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f)).withEndAction(() -> {
            s.root.removeView(sheet);
            s.root.removeView(scrim);
        }).start();
    }

    final class Adapter extends BaseAdapter {
        public int getCount() {
            return media.size();
        }

        public Object getItem(int p) {
            return media.get(p);
        }

        public long getItemId(int p) {
            return media.get(p).id;
        }

        public View getView(int p, View cv, ViewGroup parent) {
            Tile t = cv instanceof Tile ? (Tile) cv : new Tile(s.a);
            t.bind(media.get(p));
            return t;
        }
    }

    final class Tile extends FrameLayout {
        final ImageView img;
        final TextView dur, check;
        M m;

        Tile(Context c) {
            super(c);
            setBackgroundColor(T.dark ? 0x14ffffff : 0x0f000000);
            img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            addView(img, new LayoutParams(W.MP, W.MP));
            dur = W.text(c, "", 11, 600, 0xffffffff);
            dur.setPadding(T.dp(6), T.dp(2), T.dp(6), T.dp(2));
            dur.setBackground(W.round(0x8c000000, 99));
            LayoutParams dp = new LayoutParams(W.WC, W.WC, Gravity.BOTTOM | Gravity.START);
            dp.leftMargin = dp.bottomMargin = T.dp(5);
            addView(dur, dp);
            check = W.text(c, "", 12, 700, T.accentFg);
            check.setGravity(Gravity.CENTER);
            LayoutParams cp = new LayoutParams(T.dp(24), T.dp(24), Gravity.TOP | Gravity.END);
            cp.topMargin = cp.rightMargin = T.dp(6);
            addView(check, cp);
            FrameLayout hit = new FrameLayout(c);
            addView(hit, new LayoutParams(T.dp(44), T.dp(44), Gravity.TOP | Gravity.END));
            hit.setOnClickListener(v -> toggle(m));
            setOnClickListener(v -> {
                if (!picked.isEmpty()) toggle(m);
                else {
                    List<Uri> one = new ArrayList<>();
                    one.add(m.uri);
                    dismiss();
                    Attach.pickedUris(s, one);
                }
            });
            setOnLongClickListener(v -> {
                toggle(m);
                return true;
            });
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, w);
        }

        void bind(M nm) {
            boolean same = m == nm;
            m = nm;
            int idx = picked.indexOf(m);
            check.setText(idx >= 0 ? String.valueOf(idx + 1) : "");
            check.setBackground(idx >= 0 ? W.round(T.accent, 99, 0xffffffff, 1.5f) : W.round(0x33000000, 99, 0xffffffff, 1.5f));
            img.setAlpha(idx >= 0 ? .82f : 1f);
            dur.setVisibility(m.video ? View.VISIBLE : View.GONE);
            if (m.video) dur.setText(Fmt.dur(m.dur / 1000.0));
            if (same && img.getDrawable() != null) return;
            Bitmap b = thumbs.get(m.id);
            img.setImageBitmap(b);
            if (b != null) return;
            M want = m;
            int sz = T.dp(140);
            pool.execute(() -> {
                if (m != want) return;
                Bitmap r = null;
                try {
                    if (Build.VERSION.SDK_INT >= 29) r = s.a.getContentResolver().loadThumbnail(want.uri, new Size(sz, sz), null);
                    else if (want.video) r = MediaStore.Video.Thumbnails.getThumbnail(s.a.getContentResolver(), want.id, MediaStore.Video.Thumbnails.MINI_KIND, null);
                    else r = MediaStore.Images.Thumbnails.getThumbnail(s.a.getContentResolver(), want.id, MediaStore.Images.Thumbnails.MINI_KIND, null);
                } catch (Throwable e) {
                }
                if (r == null) return;
                thumbs.put(want.id, r);
                Bitmap fr = r;
                App.ui(() -> {
                    if (m == want) {
                        img.setImageBitmap(fr);
                        img.setAlpha(0f);
                        img.animate().alpha(picked.contains(m) ? .82f : 1f).setDuration(120).start();
                    }
                });
            });
        }
    }
}
