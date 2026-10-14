package com.vulpine.at.chat;

import android.content.Intent;
import android.net.Uri;
import com.vulpine.at.App;
import com.vulpine.at.Files;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import java.io.File;

public final class Download {
    public static void open(MainActivity a, Fmt.Att att) {
        Ov.toast("Downloading " + att.name + "…");
        App.bg(() -> {
            File f = Img.file(att.url);
            App.ui(() -> {
                if (f == null) {
                    Ov.toast("Download failed", true);
                    return;
                }
                Files.saveFile(a, f, att.name, att.mime);
            });
        });
    }

    public static void save(MainActivity a, String url, String name, String mime) {
        App.bg(() -> {
            File f = Img.file(url);
            App.ui(() -> {
                if (f == null) Ov.toast("Save failed", true);
                else Files.saveFile(a, f, name, mime);
            });
        });
    }

    public static void openExternal(MainActivity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Ov.toast("No app can open this link", true);
        }
    }
}
