package com.vulpine.at;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import com.vulpine.at.ui.Ov;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class Files {
    public static void saveText(MainActivity a, String name, String text) {
        save(a, name, "text/plain", new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Environment.DIRECTORY_DOWNLOADS);
    }

    public static boolean save(MainActivity a, String name, String mime, InputStream in, String dir) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, dir + "/Vulpine");
                Uri col = mime.startsWith("image/") ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : mime.startsWith("video/") ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : mime.startsWith("audio/") ? MediaStore.Audio.Media.EXTERNAL_CONTENT_URI : MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                Uri u = a.getContentResolver().insert(col, v);
                if (u == null) throw new Exception("insert");
                try (OutputStream o = a.getContentResolver().openOutputStream(u)) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) o.write(b, 0, n);
                }
            } else {
                File d = new File(Environment.getExternalStoragePublicDirectory(dir), "Vulpine");
                d.mkdirs();
                try (OutputStream o = new java.io.FileOutputStream(new File(d, name))) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) o.write(b, 0, n);
                }
            }
            Ov.toast("Saved to " + dir.replace("Pictures", "Photos") + "/Vulpine");
            return true;
        } catch (Exception e) {
            Ov.toast("Could not save", true);
            return false;
        } finally {
            try {
                in.close();
            } catch (Exception e) {
            }
        }
    }

    public static boolean saveFile(MainActivity a, File f, String name, String mime) {
        try {
            String dir = mime.startsWith("image/") || mime.startsWith("video/") ? (mime.startsWith("video/") ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) : Environment.DIRECTORY_DOWNLOADS;
            return save(a, name, mime, new FileInputStream(f), dir);
        } catch (Exception e) {
            return false;
        }
    }
}
