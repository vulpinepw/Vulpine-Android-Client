package com.vulpine.core;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class FileKv implements Kv {
    private final File dir;
    private final ConcurrentHashMap<String, String> mem = new ConcurrentHashMap<>();
    private static final String NULL = "\u0000";

    public FileKv(File dir) {
        this.dir = dir;
        dir.mkdirs();
    }

    private File f(String k) {
        StringBuilder b = new StringBuilder();
        for (char c : k.toCharArray()) b.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '~');
        return new File(dir, b + "." + Integer.toHexString(k.hashCode()));
    }

    public String get(String k) {
        String v = mem.get(k);
        if (v != null) return v == NULL ? null : v;
        File f = f(k);
        try {
            v = f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            v = null;
        }
        mem.put(k, v == null ? NULL : v);
        return v;
    }

    public void put(String k, String v) {
        if (v == null) {
            remove(k);
            return;
        }
        mem.put(k, v);
        File f = f(k), t = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(t)) {
            o.write(v.getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        } catch (Exception e) {
            return;
        }
        t.renameTo(f);
    }

    public void remove(String k) {
        mem.put(k, NULL);
        f(k).delete();
    }

    public List<String> keys(String prefix) {
        List<String> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        String p = f(prefix).getName();
        p = p.substring(0, p.lastIndexOf('.'));
        for (File x : fs) if (x.getName().startsWith(p) && !x.getName().endsWith(".tmp")) out.add(x.getName());
        return out;
    }

    public void clear(String prefix) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        String p = f(prefix).getName();
        p = p.substring(0, p.lastIndexOf('.'));
        for (File x : fs) if (x.getName().startsWith(p)) x.delete();
        mem.clear();
    }
}
