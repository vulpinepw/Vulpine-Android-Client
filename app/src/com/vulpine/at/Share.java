package com.vulpine.at;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

public class Share extends ContentProvider {
    public static final String AUTH = "com.vulpine.at.files";

    public static Uri uriFor(Context c, File src, String name) throws Exception {
        File dir = new File(c.getCacheDir(), "share");
        dir.mkdirs();
        String safe = name.replaceAll("[^\\w.\\- ]", "_");
        File dst = new File(dir, safe);
        try (FileInputStream in = new FileInputStream(src); FileOutputStream o = new FileOutputStream(dst)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
        }
        return Uri.parse("content://" + AUTH + "/" + Uri.encode(safe));
    }

    File file(Uri u) throws FileNotFoundException {
        String n = u.getLastPathSegment();
        if (n == null || n.contains("/") || n.contains("..")) throw new FileNotFoundException();
        File f = new File(new File(getContext().getCacheDir(), "share"), n);
        if (!f.exists()) throw new FileNotFoundException();
        return f;
    }

    public boolean onCreate() {
        return true;
    }

    public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        try {
            File f = file(u);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    public String getType(Uri u) {
        String n = u.getLastPathSegment() == null ? "" : u.getLastPathSegment();
        if (n.toLowerCase().endsWith(".apk")) return "application/vnd.android.package-archive";
        return com.vulpine.at.ui.Fmt.mimeFromName(n);
    }

    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        File f = file(u);
        if (mode != null && mode.contains("w") && f.getName().startsWith("cam-")) return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE);
        return ParcelFileDescriptor.open(file(u), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    public Uri insert(Uri u, ContentValues v) {
        return null;
    }

    public int delete(Uri u, String s, String[] a) {
        return 0;
    }

    public int update(Uri u, ContentValues v, String s, String[] a) {
        return 0;
    }
}
