package com.vulpine.at.chat;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import com.vulpine.core.Session;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public final class Attach {
    public static final class Item {
        public File file;
        public String name, mime;
        public int w, h;
    }

    static void pick(ChatScreen s) {
        if (!s.composer.canSend()) return;
        if (s.conv != null && s.conv.group() && !s.composer.isAdmin()) {
            JSONObject me = s.conv.member(Session.userId());
            JSONObject perms = me == null ? null : Composer.rolePerms(s.conv, me.optString("role", "member"));
            if (perms != null && !perms.optBoolean("sendAttachments", true)) {
                Ov.toast("You don't have permission to send images & files in this group.", true);
                return;
            }
        }
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        App.startForResult(s.a, Intent.createChooser(i, null), (res, data) -> {
            if (res != Activity.RESULT_OK || data == null) return;
            List<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) for (int k = 0; k < cd.getItemCount(); k++) uris.add(cd.getItemAt(k).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            if (uris.isEmpty()) return;
            if (uris.size() > 5) {
                Ov.toast("Up to 5 items per message");
                uris = uris.subList(0, 5);
            }
            List<Uri> fu = uris;
            App.bg(() -> {
                List<Item> items = new ArrayList<>();
                for (Uri u : fu) {
                    Item it = copy(s.a, u);
                    if (it != null) items.add(it);
                }
                App.ui(() -> route(s, items));
            });
        });
    }

    static void route(ChatScreen s, List<Item> items) {
        if (items.isEmpty()) return;
        if (items.size() == 1) {
            Item it = items.get(0);
            if (it.mime.startsWith("image/") && !it.mime.equals("image/gif") && !it.mime.contains("svg")) {
                ImageEditor.open(s, it);
                return;
            }
            String cap = s.composer.input.getText().toString().trim();
            s.composer.input.setText("");
            sendItems(s, items, cap, false);
            return;
        }
        AlbumSheet.open(s, items);
    }

    public static Item copy(Context c, Uri u) {
        try {
            String name = "file", mime = c.getContentResolver().getType(u);
            try (Cursor cu = c.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cu != null && cu.moveToFirst()) name = cu.getString(0);
            }
            if (mime == null) mime = Fmt.mimeFromName(name);
            File dir = new File(c.getCacheDir(), "up");
            dir.mkdirs();
            File f = new File(dir, System.nanoTime() + "-" + name.replaceAll("[^\\w.\\-]", "_"));
            try (InputStream in = c.getContentResolver().openInputStream(u); OutputStream o = new FileOutputStream(f)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) o.write(b, 0, n);
            }
            Item it = new Item();
            it.file = f;
            it.name = name;
            it.mime = mime;
            dims(it);
            return it;
        } catch (Exception e) {
            return null;
        }
    }

    static void dims(Item it) {
        try {
            if (it.mime.startsWith("image/")) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(it.file.getPath(), o);
                int rot = 0;
                try {
                    android.media.ExifInterface ex = new android.media.ExifInterface(it.file.getPath());
                    int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                    if (or == 6 || or == 8 || or == 5 || or == 7) rot = 90;
                } catch (Exception e) {
                }
                it.w = rot == 90 ? o.outHeight : o.outWidth;
                it.h = rot == 90 ? o.outWidth : o.outHeight;
            } else if (it.mime.startsWith("video/")) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                r.setDataSource(it.file.getPath());
                int w = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)), h = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)), rot = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                r.release();
                it.w = rot % 180 == 0 ? w : h;
                it.h = rot % 180 == 0 ? h : w;
            }
        } catch (Exception e) {
        }
    }

    static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    public static Item compress(Item it) {
        if (!it.mime.startsWith("image/") || it.mime.equals("image/gif") || it.mime.contains("svg") || it.file.length() < 200 * 1024) return it;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(it.file.getPath(), o);
            int max = Math.max(o.outWidth, o.outHeight);
            boolean needs = max > 1600;
            if (!needs && it.file.length() < 600 * 1024) return it;
            Bitmap b = com.vulpine.at.ui.Img.decode(it.file, 1600);
            if (b == null) return it;
            int rot = 0;
            try {
                android.media.ExifInterface ex = new android.media.ExifInterface(it.file.getPath());
                int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                rot = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0;
            } catch (Exception e) {
            }
            float sc = Math.min(1f, 1600f / Math.max(b.getWidth(), b.getHeight()));
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postScale(sc, sc);
            m.postRotate(rot);
            Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            File out = new File(it.file.getParentFile(), it.file.getName().replaceAll("\\.[^.]*$", "") + ".webp");
            try (FileOutputStream fo = new FileOutputStream(out)) {
                r.compress(android.os.Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP, 85, fo);
            }
            if (out.length() >= it.file.length()) {
                out.delete();
                return it;
            }
            Item n = new Item();
            n.file = out;
            n.name = it.name.replaceAll("\\.[^.]*$", "") + ".webp";
            n.mime = "image/webp";
            n.w = r.getWidth();
            n.h = r.getHeight();
            return n;
        } catch (Throwable e) {
            return it;
        }
    }

    static long limit() {
        return (Session.s != null && Session.s.optBoolean("pro") ? 100L : 30L) * 1024 * 1024;
    }

    static String frag(Item it, boolean car) {
        String f = "";
        if ((it.mime.startsWith("image/") || it.mime.startsWith("video/")) && it.w > 0 && it.h > 0) f = "#vw=" + it.w + "&vh=" + it.h;
        if (car) f = f.isEmpty() ? "#vcar=1" : f + "&vcar=1";
        return f;
    }

    static String safeName(String n) {
        return n.replace("|", "_").replace("\n", " ");
    }

    static Chats.Msg local(ChatScreen s, String text) {
        Chats.Msg m = new Chats.Msg();
        m.tempId = "up-" + System.nanoTime();
        m.cid = s.cid;
        m.senderId = Session.userId();
        m.createdAt = System.currentTimeMillis();
        m.text = text;
        m.state = Chats.Msg.SENDING;
        List<Chats.Msg> l = Chats.I.list(s.cid);
        synchronized (l) {
            l.add(m);
        }
        s.rebuild(true);
        s.animateLast();
        return m;
    }

    static void unlocal(ChatScreen s, Chats.Msg m) {
        List<Chats.Msg> l = Chats.I.list(m.cid);
        synchronized (l) {
            l.remove(m);
        }
        if (ChatScreen.I == s) s.rebuild(false);
    }

    static void finish(ChatScreen s, Chats.Msg loc, String text, String toast) {
        Chats.Conv kc = Chats.I.conv(loc.cid);
        if (kc != null) Chats.I.key(kc);
        App.ui(() -> {
            boolean stuck = s.atBottom();
            List<Chats.Msg> l = Chats.I.list(loc.cid);
            synchronized (l) {
                l.remove(loc);
            }
            Chats.Msg m = Chats.I.send(loc.cid, text, null);
            if (m == null) {
                Ov.toast("Not connected - try again", true);
                if (ChatScreen.I == s) s.rebuild(false);
                return;
            }
            if (toast != null) Ov.toast(toast);
            if (ChatScreen.I == s) s.rebuild(stuck);
        });
    }

    public static void sendItems(ChatScreen s, List<Item> items, String caption, boolean carousel) {
        for (Item it : items) if (it.file.length() > limit()) {
            Ov.toast("File too large (max " + (limit() / 1048576) + "MB)" + (items.size() > 1 ? ": " + it.name : ""), true);
            return;
        }
        StringBuilder lb = new StringBuilder();
        for (int k = 0; k < items.size(); k++) {
            Item it = items.get(k);
            if (lb.length() > 0) lb.append('\n');
            lb.append("📎|").append(safeName(it.name)).append('|').append(it.mime).append("|file:").append(it.file.getPath()).append(frag(it, carousel && k == 0));
        }
        if (caption != null && !caption.isEmpty()) lb.append('\n').append(caption);
        String localText = lb.toString();
        Chats.Msg loc = local(s, localText);
        App.bg(() -> {
            List<String> lines = new ArrayList<>();
            int k = 0;
            for (Item raw : items) {
                k++;
                Item it = compress(raw);
                String label = items.size() > 1 ? it.name + " (" + k + "/" + items.size() + ")" : it.name;
                JSONObject r = upload(s, it, label);
                if (r == null) {
                    App.ui(() -> unlocal(s, loc));
                    return;
                }
                String url = r.optString("url");
                Img.prime(url, it.file);
                Media.alias("file:" + raw.file.getPath(), url);
                String nm = safeName(r.optString("name", it.name));
                lines.add("📎|" + nm + "|" + r.optString("type", it.mime) + "|" + url + frag(it.w > 0 ? it : raw, carousel && lines.isEmpty()));
            }
            StringBuilder b = new StringBuilder();
            for (String l : lines) {
                if (b.length() > 0) b.append('\n');
                b.append(l);
            }
            if (caption != null && !caption.isEmpty()) b.append('\n').append(caption);
            String text = b.toString();
            Forward.grant(s.cid, text);
            finish(s, loc, text, items.size() > 1 ? "Album sent" : "Attachment sent");
        });
    }

    public static void uploadFile(ChatScreen s, File f, String name, String mime, String label, String caption) {
        Item it = new Item();
        it.file = f;
        it.name = name;
        it.mime = mime;
        String cap = caption == null || caption.isEmpty() ? "" : "\n" + caption;
        Chats.Msg loc = local(s, "📎|" + safeName(name) + "|" + mime + "|file:" + f.getPath() + cap);
        App.bg(() -> {
            JSONObject r = upload(s, it, label);
            if (r == null) {
                App.ui(() -> unlocal(s, loc));
                return;
            }
            String url = r.optString("url");
            Img.prime(url, f);
            String text = "📎|" + safeName(name) + "|" + r.optString("type", mime) + "|" + url + cap;
            finish(s, loc, text, null);
        });
    }

    static JSONObject upload(ChatScreen s, Item it, String label) {
        Tray.Row row = Tray.add(s.a, label);
        Http.Resp r = Http.upload("/messages/" + s.cid + "/attachment", "file", it.file, it.name, it.mime, (sent, total) -> {
            if (row.cancelled) throw new RuntimeException("cancelled");
            row.progress(sent / (double) total);
        });
        App.ui(row::done);
        if (row.cancelled) {
            App.ui(() -> Ov.toast("Upload cancelled", true));
            return null;
        }
        if (!r.ok()) {
            String e = r.error();
            String msg = "file_too_large".equals(e) ? "File too large (max " + r.obj().optString("limit", "30MB") + ")" : "attachments_not_allowed".equals(e) ? "You don't have permission to send images & files in this group." : "network".equals(e) ? "Network error" : r.status > 0 ? "Upload failed" : "Network error";
            App.ui(() -> Ov.toast(msg, true));
            return null;
        }
        return r.obj();
    }

    static final class Tray {
        static LinearLayout box;

        static final class Row {
            View v;
            ProgressBar bar;
            TextView pct;
            volatile boolean cancelled;
            long start = System.currentTimeMillis();

            void progress(double p) {
                App.ui(() -> {
                    bar.setProgress((int) Math.round(p * 1000));
                    pct.setText(Math.round(p * 100) + "%");
                });
            }

            void done() {
                if (box != null) box.removeView(v);
            }
        }

        static Row add(MainActivity a, String label) {
            Row r = new Row();
            App.ui(() -> {
                Context c = a;
                if (box == null || box.getParent() == null) {
                    box = W.col(c);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.BOTTOM);
                    lp.leftMargin = lp.rightMargin = T.dp(12);
                    lp.bottomMargin = a.insetBottom + T.dp(130);
                    a.host.addView(box, lp);
                    box.setElevation(T.dpf(30));
                }
                LinearLayout row = W.row(c);
                row.setPadding(T.dp(16), T.dp(12), T.dp(16), T.dp(12));
                row.setBackground(W.round(T.surface, 12));
                row.setElevation(T.dpf(6));
                ImageView ic = new ImageView(c);
                ic.setImageDrawable(Svg.stroke(14, 0xffffffff, 2.2f, "M12 19V5", "M5 12l7-7 7 7"));
                ic.setScaleType(ImageView.ScaleType.CENTER);
                ic.setBackground(W.oval(T.accent));
                row.addView(ic, W.lp(T.dp(24), T.dp(24)));
                LinearLayout body = W.col(c);
                TextView nm = W.text(c, label, 14, 500, T.text);
                W.ellipsize(nm);
                body.addView(nm);
                ProgressBar pb = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
                pb.setMax(1000);
                pb.setProgressTintList(android.content.res.ColorStateList.valueOf(T.accent));
                pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(T.surface3));
                body.addView(pb, W.lpm(W.MP, T.dp(6), 0, 6, 0, 6));
                LinearLayout line = W.row(c);
                TextView pc = W.text(c, "0%", 13, 400, T.textMuted);
                line.addView(pc);
                line.addView(W.flex(c));
                TextView cancel = W.text(c, "Cancel", 13, 500, T.accent);
                cancel.setOnClickListener(v -> r.cancelled = true);
                line.addView(cancel);
                body.addView(line);
                row.addView(body, W.lpm(0, W.WC, 12, 0, 0, 0));
                ((LinearLayout.LayoutParams) body.getLayoutParams()).weight = 1;
                row.setVisibility(View.GONE);
                App.H.postDelayed(() -> {
                    if (row.getParent() != null && row.getVisibility() != View.VISIBLE) {
                        row.setVisibility(View.VISIBLE);
                        row.setAlpha(0);
                        row.setTranslationX(T.dp(40));
                        row.animate().alpha(1).translationX(0).setDuration(180).setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f)).start();
                    }
                }, 250);
                r.v = row;
                r.bar = pb;
                r.pct = pc;
                box.addView(row, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
            });
            while (r.v == null) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
            return r;
        }
    }
}
