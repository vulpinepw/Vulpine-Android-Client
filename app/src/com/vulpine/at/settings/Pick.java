package com.vulpine.at.settings;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class Pick {
    public interface Cb {
        void on(File f, String name, String mime);
    }

    public static void file(MainActivity a, String[] mimes, Cb cb) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mimes.length == 1 ? mimes[0] : "*/*");
        if (mimes.length > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        App.startForResult(a, i, (res, data) -> {
            if (res != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
            Uri u = data.getData();
            App.bg(() -> {
                String name = "file";
                try (Cursor c = a.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst()) name = c.getString(0);
                } catch (Exception e) {
                }
                String mime = a.getContentResolver().getType(u);
                File out = new File(a.getCacheDir(), "up-" + System.currentTimeMillis());
                try (InputStream in = a.getContentResolver().openInputStream(u); OutputStream o = new FileOutputStream(out)) {
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) o.write(b, 0, n);
                } catch (Exception e) {
                    return;
                }
                String nm = name, mm = mime == null ? "application/octet-stream" : mime;
                App.ui(() -> cb.on(out, nm, mm));
            });
        });
    }

    public static String sniff(File f) {
        byte[] h = new byte[12];
        try (InputStream in = new java.io.FileInputStream(f)) {
            int n = in.read(h);
            if (n < 4) return null;
        } catch (Exception e) {
            return null;
        }
        if ((h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "image/png";
        if ((h[0] & 0xff) == 0xff && (h[1] & 0xff) == 0xd8) return "image/jpeg";
        if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F') return "image/gif";
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "image/webp";
        return null;
    }
}
