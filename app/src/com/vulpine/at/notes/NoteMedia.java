package com.vulpine.at.notes;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.Files;
import com.vulpine.at.MainActivity;
import com.vulpine.at.stories.Media;
import com.vulpine.at.stories.Sheets;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Ic;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.io.File;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

public final class NoteMedia {
    public interface Url {
        void on(String url);
    }

    public static void open(MainActivity a, Fmt.Att att) {
        if (!att.image() && !att.gif() && !att.video()) {
            Ov.toast("Downloading…");
            App.bg(() -> {
                File f = Img.file(Http.abs(att.url));
                App.ui(() -> {
                    if (f == null) Ov.toast("Could not download", true);
                    else Files.saveFile(a, f, att.name, att.mime);
                });
            });
            return;
        }
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0xf2000000);
        root.setClickable(true);
        MediaPlayer[] mp = new MediaPlayer[1];
        Runnable[] close = new Runnable[1];
        close[0] = () -> {
            Ov.remove(root);
            if (mp[0] != null) try {
                mp[0].release();
            } catch (Exception e) {
            }
            mp[0] = null;
            root.animate().alpha(0).setDuration(180).withEndAction(() -> a.host.removeView(root)).start();
        };
        if (att.video()) {
            TextureView tv = new TextureView(a);
            root.addView(tv, W.flp(W.MP, W.MP, Gravity.CENTER));
            tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                    try {
                        MediaPlayer p = new MediaPlayer();
                        mp[0] = p;
                        p.setSurface(new Surface(st));
                        p.setDataSource(a, Uri.parse(Http.abs(att.url)), Media.headers());
                        p.setOnPreparedListener(x -> {
                            int vw = x.getVideoWidth(), vh = x.getVideoHeight();
                            if (vw > 0 && vh > 0) {
                                float s = Math.min(root.getWidth() / (float) vw, root.getHeight() / (float) vh);
                                tv.setLayoutParams(W.flp(Math.round(vw * s), Math.round(vh * s), Gravity.CENTER));
                            }
                            x.start();
                        });
                        p.prepareAsync();
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
            });
            tv.setOnClickListener(v -> {
                MediaPlayer p = mp[0];
                if (p == null) return;
                try {
                    if (p.isPlaying()) p.pause();
                    else p.start();
                } catch (Exception e) {
                }
            });
        } else {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            root.addView(iv, new FrameLayout.LayoutParams(W.MP, W.MP));
            if (att.gif()) Img.anim(iv, att.url, 1600, true);
            else Img.into(iv, att.url, 2048);
            iv.setOnClickListener(v -> close[0].run());
        }
        ImageView x = new ImageView(a);
        x.setImageDrawable(Ic.get("st.sheets.x", 22, 0xffffffff));
        x.setScaleType(ImageView.ScaleType.CENTER);
        x.setBackground(W.oval(0x33ffffff));
        x.setContentDescription("Close");
        x.setOnClickListener(v -> close[0].run());
        FrameLayout.LayoutParams xp = W.flp(T.dp(40), T.dp(40), Gravity.TOP | Gravity.END);
        xp.topMargin = Math.max(a.insetTop, T.dp(24)) + T.dp(8);
        xp.rightMargin = T.dp(12);
        root.addView(x, xp);
        ImageView dl = new ImageView(a);
        dl.setImageDrawable(Ic.get("nt.notestab.m_exp", 20, 0xffffffff));
        dl.setScaleType(ImageView.ScaleType.CENTER);
        dl.setBackground(W.oval(0x33ffffff));
        dl.setContentDescription("Save");
        dl.setOnClickListener(v -> App.bg(() -> {
            File f = Img.file(Http.abs(att.url));
            App.ui(() -> {
                if (f == null) Ov.toast("Could not download", true);
                else Files.saveFile(a, f, att.name, att.mime);
            });
        }));
        FrameLayout.LayoutParams dp = W.flp(T.dp(40), T.dp(40), Gravity.TOP | Gravity.END);
        dp.topMargin = xp.topMargin;
        dp.rightMargin = T.dp(60);
        root.addView(dl, dp);
        a.host.addView(root, new FrameLayout.LayoutParams(W.MP, W.MP));
        root.setAlpha(0);
        root.animate().alpha(1).setDuration(180).start();
        Ov.push(root, close[0]);
    }

    public static void gifPicker(MainActivity a, Url cb) {
        Sheets.Sheet s = new Sheets.Sheet(a, "GIF");
        EditText q = new EditText(a);
        q.setHint("Search GIFs…");
        q.setHintTextColor(T.textMuted);
        T.text(q, 14, 400, T.text);
        q.setSingleLine(true);
        q.setBackground(W.round(T.surface2, 999));
        q.setPadding(T.dp(14), 0, T.dp(14), 0);
        s.box.addView(q, W.lpm(W.MP, T.dp(38), 0, 0, 0, 6));
        GridLayout g = new GridLayout(a);
        g.setColumnCount(3);
        s.box.addView(g, W.lp(W.MP, W.WC));
        TextView note = W.text(a, "Loading…", 12, 400, T.textMuted);
        note.setGravity(Gravity.CENTER);
        s.box.addView(note, W.lpm(W.MP, W.WC, 0, 8, 0, 8));
        int cell = (a.getResources().getDisplayMetrics().widthPixels - T.dp(24) - T.dp(8)) / 3;
        String[] cur = {""};
        Runnable load = () -> {
            String query = cur[0];
            note.setText("Loading…");
            g.removeAllViews();
            App.bg(() -> {
                Http.Resp r;
                try {
                    r = Http.get(query.isEmpty() ? "/gif/trending?&limit=24&offset=0" : "/gif/search?q=" + URLEncoder.encode(query, "UTF-8") + "&limit=24&offset=0");
                } catch (Exception e) {
                    return;
                }
                App.ui(() -> {
                    if (!query.equals(cur[0])) return;
                    if (r.status == 503) {
                        note.setText("Coming soon.");
                        return;
                    }
                    if (!r.ok()) {
                        note.setText("GIF search unavailable (HTTP " + r.status + ")");
                        return;
                    }
                    JSONArray d = r.obj().optJSONArray("data");
                    if (d == null || d.length() == 0) {
                        note.setText("No GIFs found");
                        return;
                    }
                    note.setText("");
                    for (int i = 0; i < d.length(); i++) {
                        JSONObject im = d.optJSONObject(i).optJSONObject("images");
                        if (im == null) continue;
                        String send = first(im, "downsized_medium", "original", "fixed_width");
                        String th = first(im, "fixed_width_small", "preview_gif", "fixed_width");
                        if (send == null) continue;
                        String fs = send.replaceAll("\\?.*$", "");
                        ImageView iv = new ImageView(a);
                        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        iv.setBackground(W.round(T.surface2, 8));
                        iv.setClipToOutline(true);
                        iv.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
                        Img.anim(iv, th == null ? fs : th, cell, !T.power);
                        iv.setOnClickListener(v -> {
                            s.close();
                            cb.on(fs);
                        });
                        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                        lp.width = cell;
                        lp.height = cell;
                        lp.setMargins(T.dp(1), T.dp(1), T.dp(1), T.dp(1));
                        g.addView(iv, lp);
                    }
                });
            });
        };
        Runnable[] deb = new Runnable[1];
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence x, int b, int c, int d) {
            }

            public void onTextChanged(CharSequence x, int b, int c, int d) {
            }

            public void afterTextChanged(Editable e) {
                if (deb[0] != null) q.removeCallbacks(deb[0]);
                deb[0] = () -> {
                    cur[0] = e.toString().trim();
                    load.run();
                };
                q.postDelayed(deb[0], 300);
            }
        });
        load.run();
        s.show();
    }

    static String first(JSONObject im, String... keys) {
        for (String k : keys) {
            JSONObject o = im.optJSONObject(k);
            if (o == null) continue;
            String u = o.optString("url", "");
            if (u.isEmpty()) u = o.optString("webp", "");
            if (!u.isEmpty()) return u;
        }
        return null;
    }
}
