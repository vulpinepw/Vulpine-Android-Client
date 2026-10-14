package com.vulpine.at.chat;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Fmt;
import com.vulpine.at.ui.Img;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Chats;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Voice {
    public static final class Track {
        public String url, name, cid, mid, sender;
        public MediaPlayer mp;
        public boolean loading, prepared, playing;
        public float rate = 1f;
        public int durMs = -1;
        public List<String[]> next = new ArrayList<>();
    }

    static final Map<String, Track> tracks = new ConcurrentHashMap<>();
    public static Track cur;
    static final List<Runnable> ls = new CopyOnWriteArrayList<>();
    static final float[] SPEEDS = {1f, 1.25f, 2f};
    static AudioManager am;
    static AudioFocusRequest afr;
    static final Runnable release = Voice::abandon;

    static void fire() {
        App.ui(() -> {
            for (Runnable r : ls) r.run();
        });
    }

    public static void on(Runnable r) {
        ls.add(r);
    }

    public static void off(Runnable r) {
        ls.remove(r);
    }

    static int toInt32(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return 0;
        double t = d < 0 ? Math.ceil(d) : Math.floor(d);
        double m = t % 4294967296.0;
        if (m < 0) m += 4294967296.0;
        return (int) (long) m;
    }

    public static float[] bars(String url, double dur) {
        int nb = (int) Math.max(16, Math.min(44, Math.round((dur > 0 ? dur : 8) * 3)));
        double seed = 0;
        for (int i = 0; i < url.length(); i++) {
            seed = seed * 31 + url.charAt(i);
            seed = seed % 4294967296.0;
            if (seed < 0) seed += 4294967296.0;
        }
        float[] out = new float[nb];
        for (int b = 0; b < nb; b++) {
            double v = seed * 1103515245.0 + 12345.0;
            seed = toInt32(v) & 0x7fffffff;
            double rnd = seed / (double) 0x7fffffff;
            out[b] = (26 + (int) Math.floor(rnd * 74)) / 100f;
        }
        return out;
    }

    static Track track(String url) {
        Track t = tracks.get(url);
        if (t == null) {
            t = new Track();
            t.url = url;
            tracks.put(url, t);
        }
        return t;
    }

    public static View player(ChatScreen s, Fmt.Att a, Chats.Msg m) {
        Context c = s.a;
        Track t = track(a.url);
        t.cid = s.cid;
        t.mid = m == null ? null : m.id;
        t.name = m == null ? "Voice message" : (m.mine() ? "You" : s.senderName(m));
        t.sender = a.voice() ? "Voice message" : "Audio";
        View[] wv = new View[1];
        LinearLayout row = new LinearLayout(c) {
            @Override
            protected void onMeasure(int ws, int hs) {
                android.view.ViewGroup.LayoutParams lp = wv[0].getLayoutParams();
                if (lp.width != T.dp(120)) lp.width = T.dp(120);
                super.onMeasure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), hs);
                int need = getMeasuredWidth(), max = MeasureSpec.getSize(ws);
                if (MeasureSpec.getMode(ws) != MeasureSpec.UNSPECIFIED && need > max) lp.width = Math.max(T.dp(40), T.dp(120) - (need - max));
                super.onMeasure(ws, hs);
            }
        };
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(T.dp(4), T.dp(4), T.dp(6), T.dp(4));
        FrameLayout pp = new FrameLayout(c);
        pp.setBackground(W.oval(T.accent));
        pp.setElevation(T.dpf(2));
        ImageView ic = new ImageView(c);
        ic.setScaleType(ImageView.ScaleType.CENTER);
        pp.addView(ic, new FrameLayout.LayoutParams(W.MP, W.MP));
        W.Spin spin = new W.Spin(c, T.accentFg, 2);
        pp.addView(spin, new FrameLayout.LayoutParams(T.dp(16), T.dp(16), Gravity.CENTER));
        row.addView(pp, W.lp(T.dp(30), T.dp(30)));
        Wave wave = new Wave(c, a.url);
        wv[0] = wave;
        row.addView(wave, W.lpm(T.dp(120), T.dp(30), 8, 0, 0, 0));
        LinearLayout meta = W.row(c);
        meta.setAlpha(.85f);
        TextView time = W.text(c, "0:00", 11, 400, T.text);
        time.setFontFeatureSettings("tnum");
        time.setSingleLine(true);
        meta.addView(time);
        TextView rate = W.text(c, "1x", 10, 700, T.text);
        rate.setSingleLine(true);
        rate.setPadding(T.dp(6), T.dp(2), T.dp(6), T.dp(2));
        rate.setBackground(W.round(0, 99, T.text, 1));
        rate.setAlpha(.7f);
        meta.addView(rate, W.lpm(W.WC, W.WC, 5, 0, 0, 0));
        row.addView(meta, W.lpm(W.WC, W.WC, 8, 0, 0, 0));
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            boolean playing = t.mp != null && t.prepared && t.mp.isPlaying();
            ic.setImageDrawable(playing ? Svg.fill(16, T.accentFg, "M6 5h4v14H6zM14 5h4v14h-4z") : Svg.fill(16, T.accentFg, "M8 5v14l11-7z"));
            ic.setVisibility(t.loading ? View.INVISIBLE : View.VISIBLE);
            spin.setVisibility(t.loading ? View.VISIBLE : View.GONE);
            int d = t.durMs > 0 ? t.durMs : 0;
            int ct = t.loading || t.mp == null || !t.prepared ? 0 : t.mp.getCurrentPosition();
            time.setText(d > 0 ? Fmt.dur(ct / 1000.0) + " / " + Fmt.dur(d / 1000.0) : Fmt.dur(ct / 1000.0));
            rate.setText((t.rate == 1.25f ? "1.25" : String.valueOf((int) t.rate)) + "x");
            if (d > 0) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) wave.getLayoutParams();
                int w = T.dp(Math.max(70, Math.min(220, d / 1000f * 20)));
                if (lp.width != w) {
                    lp.width = w;
                    wave.setLayoutParams(lp);
                }
            }
            wave.progress = d > 0 ? ct / (float) d : 0;
            wave.invalidate();
        };
        render[0].run();
        Runnable tick = new Runnable() {
            public void run() {
                if (!row.isAttachedToWindow()) return;
                render[0].run();
                if (t.mp != null && t.prepared && t.mp.isPlaying()) row.postDelayed(this, 120);
            }
        };
        Runnable listener = () -> {
            render[0].run();
            row.removeCallbacks(tick);
            row.post(tick);
        };
        row.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {
                on(listener);
                listener.run();
            }

            public void onViewDetachedFromWindow(View v) {
                off(listener);
            }
        });
        pp.setOnClickListener(v -> {
            if (t.loading) return;
            if (t.mp != null && t.prepared && t.mp.isPlaying()) pause(t);
            else play(s, t);
        });
        W.press(pp, .9f);
        rate.setOnClickListener(v -> {
            int i = 0;
            for (int k = 0; k < SPEEDS.length; k++) if (SPEEDS[k] == t.rate) i = k;
            t.rate = SPEEDS[(i + 1) % SPEEDS.length];
            applyRate(t);
            fire();
        });
        wave.setOnTouchListener((v, e) -> {
            if (t.durMs <= 0 || t.mp == null || !t.prepared) return true;
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                float f = Math.max(0, Math.min(1, e.getX() / v.getWidth()));
                t.mp.seekTo(Math.round(f * t.durMs));
                fire();
            }
            return true;
        });
        return row;
    }

    static class Wave extends View {
        final float[] bars;
        float progress;
        final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        Wave(Context c, String url) {
            super(c);
            bars = Voice.bars(url, 8);
        }

        protected void onDraw(android.graphics.Canvas c) {
            int n = bars.length;
            float gap = T.dpf(2), w = (getWidth() - gap * (n - 1)) / n, h = getHeight();
            int on = Math.round(progress * n);
            for (int i = 0; i < n; i++) {
                p.setColor(T.text);
                p.setAlpha(i < on ? 255 : 77);
                float bh = Math.max(T.dpf(2), h * bars[i]);
                float x = i * (w + gap), y = (h - bh) / 2;
                c.drawRoundRect(x, y, x + Math.max(T.dpf(2), w), y + bh, T.dpf(2), T.dpf(2), p);
            }
        }
    }

    static void applyRate(Track t) {
        if (t.mp == null || !t.prepared || Build.VERSION.SDK_INT < 23) return;
        try {
            boolean was = t.mp.isPlaying();
            t.mp.setPlaybackParams(t.mp.getPlaybackParams().setSpeed(t.rate));
            if (!was) t.mp.pause();
        } catch (Exception e) {
        }
    }

    public static void play(ChatScreen s, Track t) {
        if (cur != null && cur != t) pause(cur);
        cur = t;
        if (s != null) t.next = s.voiceAfter(t.url);
        if (t.mp != null && t.prepared) {
            focus();
            applyRate(t);
            t.mp.start();
            t.playing = true;
            fire();
            return;
        }
        t.loading = true;
        fire();
        App.bg(() -> {
            File f = Img.file(t.url);
            App.ui(() -> {
                if (f == null) {
                    t.loading = false;
                    Ov.toast("Could not load the voice message", true);
                    fire();
                    return;
                }
                try {
                    MediaPlayer mp = new MediaPlayer();
                    mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    mp.setDataSource(f.getPath());
                    mp.setOnPreparedListener(x -> {
                        t.prepared = true;
                        t.loading = false;
                        t.durMs = x.getDuration();
                        if (cur == t) {
                            focus();
                            applyRate(t);
                            x.start();
                            t.playing = true;
                        }
                        fire();
                    });
                    mp.setOnCompletionListener(x -> {
                        t.playing = false;
                        x.seekTo(0);
                        fire();
                        ended(t);
                    });
                    mp.setOnErrorListener((x, w, e) -> {
                        t.loading = false;
                        fire();
                        return true;
                    });
                    t.mp = mp;
                    mp.prepareAsync();
                } catch (Exception e) {
                    t.loading = false;
                    fire();
                }
            });
        });
    }

    static void ended(Track t) {
        if (cur != t) return;
        if (t.next != null && !t.next.isEmpty()) {
            String[] n = t.next.get(0);
            Track nt = track(n[0]);
            nt.cid = t.cid;
            nt.mid = n[1];
            nt.name = n[2];
            nt.sender = n[3];
            nt.rate = t.rate;
            nt.next = new ArrayList<>(t.next.subList(1, t.next.size()));
            if (nt.mp != null && nt.prepared) nt.mp.seekTo(0);
            play(null, nt);
            return;
        }
        cur = null;
        App.H.postDelayed(release, 700);
        fire();
    }

    public static void pause(Track t) {
        if (t.mp != null && t.prepared && t.mp.isPlaying()) t.mp.pause();
        t.playing = false;
        App.H.removeCallbacks(release);
        App.H.postDelayed(release, 700);
        fire();
    }

    public static void stop() {
        Track t = cur;
        cur = null;
        if (t != null) pause(t);
        fire();
    }

    static void focus() {
        if (com.vulpine.at.call.Calls.inCall()) return;
        App.H.removeCallbacks(release);
        if (am == null) am = (AudioManager) App.ctx.getSystemService(Context.AUDIO_SERVICE);
        if (afr == null) afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener(ch -> {
            if (ch == AudioManager.AUDIOFOCUS_LOSS || ch == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) App.ui(() -> {
                if (cur != null && !Recorder.active) pause(cur);
            });
        }).build();
        am.requestAudioFocus(afr);
    }

    static void abandon() {
        if (am != null && afr != null && (cur == null || !cur.playing) && !Recorder.active) am.abandonAudioFocusRequest(afr);
    }

    public static final class Recorder {
        public static volatile boolean active;
        static MediaRecorder rec;
        static File out;
        static long started;
        static Track resumeAfter;

        public static boolean start(Context c) {
            if (active) return false;
            try {
                if (cur != null && cur.playing) {
                    resumeAfter = cur;
                    pause(cur);
                }
                if (am == null) am = (AudioManager) App.ctx.getSystemService(Context.AUDIO_SERVICE);
                am.requestAudioFocus(new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build());
                MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(c) : new MediaRecorder();
                r.setAudioSource(MediaRecorder.AudioSource.MIC);
                boolean ogg = Build.VERSION.SDK_INT >= 29;
                out = new File(c.getCacheDir(), "voice-" + System.currentTimeMillis() + (ogg ? ".ogg" : ".m4a"));
                if (ogg) {
                    r.setOutputFormat(MediaRecorder.OutputFormat.OGG);
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS);
                    r.setAudioEncodingBitRate(32000);
                    r.setAudioSamplingRate(48000);
                } else {
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    r.setAudioEncodingBitRate(64000);
                    r.setAudioSamplingRate(44100);
                }
                r.setAudioChannels(1);
                r.setOutputFile(out.getPath());
                r.prepare();
                r.start();
                rec = r;
                started = System.currentTimeMillis();
                active = true;
                return true;
            } catch (Exception e) {
                active = false;
                Ov.toast("Microphone access denied", true);
                return false;
            }
        }

        public static long elapsed() {
            return active ? System.currentTimeMillis() - started : 0;
        }

        public static File stop(boolean keep) {
            File f = out;
            long dur = System.currentTimeMillis() - started;
            try {
                if (rec != null) rec.stop();
            } catch (Exception e) {
                keep = false;
            }
            try {
                if (rec != null) rec.release();
            } catch (Exception e) {
            }
            rec = null;
            active = false;
            Track r = resumeAfter;
            resumeAfter = null;
            if (r != null) App.H.postDelayed(() -> {
                if (!com.vulpine.at.call.Calls.inCall()) play(null, r);
            }, 300);
            else App.H.postDelayed(release, 700);
            if (!keep) {
                if (f != null) f.delete();
                return null;
            }
            if (dur < 1000) {
                Ov.toast("Hold longer - min 1 second", true);
                if (f != null) f.delete();
                return null;
            }
            return f;
        }
    }
}
