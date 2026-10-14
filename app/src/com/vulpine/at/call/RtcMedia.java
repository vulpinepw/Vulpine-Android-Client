package com.vulpine.at.call;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.projection.MediaProjection;
import android.util.DisplayMetrics;
import android.view.TextureView;
import android.view.View;
import com.vulpine.at.App;
import com.vulpine.core.Http;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.AudioTrackSink;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.ExternalAudioProcessingFactory;
import org.webrtc.GlRectDrawer;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStats;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioProcessingOptions;
import org.webrtc.audio.JavaAudioDeviceModule;

public final class RtcMedia extends CallMedia {
    static boolean inited;
    static EglBase egl;
    PeerConnectionFactory f;
    JavaAudioDeviceModule adm;
    ExternalAudioProcessingFactory apf;
    final Eq eq = new Eq();
    AudioSource aSrc;
    AudioTrack aTrack;
    VideoSource camSrc, scrSrc;
    VideoTrack camTrack, scrTrack;
    VideoCapturer cam;
    ScreenCapturerAndroid scr;
    SurfaceTextureHelper camTex, scrTex;
    String camName;
    boolean mic = true, deaf;
    final String streamId = UUID.randomUUID().toString();
    final List<PeerConnection.IceServer> ice = new ArrayList<>();
    final Map<String, RtcPeer> peers = new HashMap<>();
    final Map<String, Double> levels = new ConcurrentHashMap<>();
    final Set<String> speak = new HashSet<>();
    volatile double localLevel;
    final boolean lite;
    final Runnable tick = this::tick;
    long lastTune;

    public RtcMedia() {
        boolean l = false;
        try {
            ActivityManager am = (ActivityManager) App.ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            l = Runtime.getRuntime().availableProcessors() <= 4 || mi.totalMem <= 3L * 1024 * 1024 * 1024;
        } catch (Exception e) {
        }
        lite = l;
    }

    static synchronized void init() {
        if (inited) return;
        inited = true;
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(App.ctx).createInitializationOptions());
        egl = EglBase.create();
    }

    void fetchIce() {
        ice.clear();
        try {
            Http.Resp r = Http.get("/turn/ice");
            JSONArray a = r.ok() ? r.obj().optJSONArray("iceServers") : null;
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject s = a.optJSONObject(i);
                if (s == null) continue;
                List<String> urls = new ArrayList<>();
                Object u = s.opt("urls");
                if (u instanceof JSONArray) for (int k = 0; k < ((JSONArray) u).length(); k++) urls.add(((JSONArray) u).optString(k));
                else if (u != null) urls.add(String.valueOf(u));
                List<String> ok = new ArrayList<>();
                for (String x : urls) if (x.matches("(?i)^(stun|turn|turns):.*")) ok.add(x);
                if (ok.isEmpty()) continue;
                PeerConnection.IceServer.Builder b = PeerConnection.IceServer.builder(ok);
                if (s.has("username")) b.setUsername(s.optString("username"));
                if (s.has("credential")) b.setPassword(s.optString("credential"));
                ice.add(b.createIceServer());
            }
        } catch (Exception e) {
        }
        if (ice.isEmpty()) ice.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
    }

    @Override
    public void acquire(boolean video, Sig.Cb<Boolean> ok) {
        App.bg(() -> {
            boolean good;
            try {
                init();
                fetchIce();
                build();
                good = true;
            } catch (Throwable t) {
                good = false;
            }
            final boolean g = good;
            App.H.post(() -> {
                if (!g) {
                    release();
                    ok.on(false);
                    return;
                }
                if (video) setCam(true, x -> ok.on(true));
                else ok.on(true);
                App.H.removeCallbacks(tick);
                App.H.postDelayed(tick, 140);
            });
        });
    }

    void build() {
        apf = new ExternalAudioProcessingFactory();
        apf.setCapturePostProcessing(eq);
        applyEq();
        adm = JavaAudioDeviceModule.builder(App.ctx)
                .setUseHardwareAcousticEchoCanceler(ec && JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(ns && JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setSamplesReadyCallback(s -> localLevel = rms16(s.getData(), s.getData().length))
                .createAudioDeviceModule();
        preferMic();
        EglBase.Context ctx = egl.getEglBaseContext();
        f = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setAudioProcessingFactory(apf)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(ctx, true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(ctx))
                .createPeerConnectionFactory();
        aSrc = f.createAudioSource(audioConstraints());
        aTrack = f.createAudioTrack(UUID.randomUUID().toString(), aSrc);
        aTrack.setEnabled(mic);
    }

    void preferMic() {
        String want = App.prefs == null ? "" : App.prefs.getString("dev.mic", "");
        if (want.isEmpty() || adm == null) return;
        try {
            android.media.AudioManager am = (android.media.AudioManager) App.ctx.getSystemService(Context.AUDIO_SERVICE);
            for (android.media.AudioDeviceInfo d : am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)) {
                int t = d.getType();
                boolean m = "builtin".equals(want) ? t == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC
                        : "headset".equals(want) ? t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                        : "bluetooth".equals(want) ? t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        : "usb".equals(want) && (t == android.media.AudioDeviceInfo.TYPE_USB_DEVICE || t == android.media.AudioDeviceInfo.TYPE_USB_HEADSET);
                if (m) {
                    adm.setPreferredInputDevice(d);
                    return;
                }
            }
        } catch (Exception e) {
        }
    }

    MediaConstraints audioConstraints() {
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", String.valueOf(ec)));
        c.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", String.valueOf(agc)));
        c.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", String.valueOf(ns)));
        c.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", String.valueOf(ns && aggressive)));
        c.mandatory.add(new MediaConstraints.KeyValuePair("googTypingNoiseDetection", String.valueOf(ns && aggressive)));
        return c;
    }

    @Override
    public void release() {
        App.H.removeCallbacks(tick);
        for (RtcPeer p : new ArrayList<>(peers.values())) p.close();
        peers.clear();
        stopCam();
        stopScreen();
        try {
            if (aTrack != null) aTrack.dispose();
        } catch (Exception e) {
        }
        try {
            if (aSrc != null) aSrc.dispose();
        } catch (Exception e) {
        }
        aTrack = null;
        aSrc = null;
        final PeerConnectionFactory ff = f;
        final JavaAudioDeviceModule aa = adm;
        final ExternalAudioProcessingFactory pp = apf;
        f = null;
        adm = null;
        apf = null;
        App.bg(() -> {
            try {
                if (ff != null) ff.dispose();
            } catch (Exception e) {
            }
            try {
                if (aa != null) aa.release();
            } catch (Exception e) {
            }
            try {
                if (pp != null) pp.destroy();
            } catch (Exception e) {
            }
        });
        levels.clear();
        speak.clear();
        mic = true;
        deaf = false;
    }

    @Override
    public Sig.Peer newPeer(String uid) {
        RtcPeer old = peers.remove(uid);
        if (old != null) old.close();
        RtcPeer p = new RtcPeer(uid);
        peers.put(uid, p);
        return p;
    }

    @Override
    public boolean localVideo() {
        return camTrack != null || scrTrack != null;
    }

    @Override
    public boolean camOn() {
        return camTrack != null;
    }

    @Override
    public boolean screenOn() {
        return scrTrack != null;
    }

    @Override
    public void setMic(boolean on) {
        mic = on;
        if (aTrack != null) aTrack.setEnabled(on);
    }

    @Override
    public void setDeaf(boolean on) {
        deaf = on;
        for (RtcPeer p : peers.values()) p.applyAudio();
    }

    @Override
    public void setCam(boolean on, Sig.Cb<Boolean> done) {
        if (f == null) {
            done.on(false);
            return;
        }
        if (on) {
            if (camTrack != null) {
                done.on(true);
                return;
            }
            if (scrTrack != null) stopScreen();
            try {
                CameraEnumerator en = Camera2Enumerator.isSupported(App.ctx) ? new Camera2Enumerator(App.ctx) : new Camera1Enumerator(true);
                String pick = null;
                boolean back = App.prefs != null && "back".equals(App.prefs.getString("dev.cam", ""));
                for (String n : en.getDeviceNames()) if (camName != null ? n.equals(camName) : back ? en.isBackFacing(n) : en.isFrontFacing(n)) pick = n;
                if (pick == null) for (String n : en.getDeviceNames()) {
                    pick = n;
                    break;
                }
                if (pick == null) {
                    done.on(false);
                    return;
                }
                camName = pick;
                cam = en.createCapturer(pick, null);
                camTex = SurfaceTextureHelper.create("vcam", egl.getEglBaseContext());
                camSrc = f.createVideoSource(false);
                cam.initialize(camTex, App.ctx, camSrc.getCapturerObserver());
                if (lite) cam.startCapture(640, 360, 24);
                else cam.startCapture(960, 540, 30);
                camTrack = f.createVideoTrack("camera-" + UUID.randomUUID(), camSrc);
                for (RtcPeer p : peers.values()) p.addVideo(camTrack);
                done.on(true);
            } catch (Exception e) {
                stopCam();
                done.on(false);
            }
        } else {
            VideoTrack t = camTrack;
            for (RtcPeer p : peers.values()) p.removeVideo(t);
            stopCam();
            done.on(true);
        }
    }

    void stopCam() {
        try {
            if (cam != null) cam.stopCapture();
        } catch (Exception e) {
        }
        try {
            if (cam != null) cam.dispose();
        } catch (Exception e) {
        }
        try {
            if (camTrack != null) camTrack.dispose();
        } catch (Exception e) {
        }
        try {
            if (camSrc != null) camSrc.dispose();
        } catch (Exception e) {
        }
        try {
            if (camTex != null) camTex.dispose();
        } catch (Exception e) {
        }
        cam = null;
        camTrack = null;
        camSrc = null;
        camTex = null;
    }

    @Override
    public boolean canSwitchCam() {
        try {
            CameraEnumerator en = Camera2Enumerator.isSupported(App.ctx) ? new Camera2Enumerator(App.ctx) : new Camera1Enumerator(true);
            return en.getDeviceNames().length > 1;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void switchCam() {
        if (!(cam instanceof CameraVideoCapturer)) return;
        ((CameraVideoCapturer) cam).switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            public void onCameraSwitchDone(boolean front) {
                try {
                    CameraEnumerator en = Camera2Enumerator.isSupported(App.ctx) ? new Camera2Enumerator(App.ctx) : new Camera1Enumerator(true);
                    for (String n : en.getDeviceNames()) if (front ? en.isFrontFacing(n) : en.isBackFacing(n)) camName = n;
                    if (App.prefs != null) App.prefs.edit().putString("dev.cam", front ? "front" : "back").apply();
                } catch (Exception e) {
                }
            }

            public void onCameraSwitchError(String s) {
            }
        });
    }

    @Override
    public void startScreen(Intent data, Sig.Cb<Boolean> done) {
        if (f == null || data == null) {
            done.on(false);
            return;
        }
        if (camTrack != null) setCam(false, x -> {
        });
        try {
            scr = new ScreenCapturerAndroid(data, new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    App.H.post(() -> {
                        if (scrTrack != null && Calls.sig != null) Calls.stopScreen();
                    });
                }
            });
            scrTex = SurfaceTextureHelper.create("vscr", egl.getEglBaseContext());
            scrSrc = f.createVideoSource(true);
            scr.initialize(scrTex, App.ctx, scrSrc.getCapturerObserver());
            DisplayMetrics dm = App.ctx.getResources().getDisplayMetrics();
            int w = dm.widthPixels, h = dm.heightPixels;
            double s = Math.min(1, Math.min(1280.0 / Math.max(w, h), 720.0 / Math.min(w, h)));
            int cw = Math.max(16, (int) Math.round(w * s / 16.0) * 16), ch = Math.max(16, (int) Math.round(h * s / 16.0) * 16);
            scr.startCapture(cw, ch, 30);
            scrTrack = f.createVideoTrack("screen-" + UUID.randomUUID(), scrSrc);
            for (RtcPeer p : peers.values()) p.addVideo(scrTrack);
            done.on(true);
        } catch (Exception e) {
            stopScreenTrack();
            done.on(false);
        }
    }

    @Override
    public void stopScreen() {
        VideoTrack t = scrTrack;
        if (t != null) for (RtcPeer p : peers.values()) p.removeVideo(t);
        stopScreenTrack();
    }

    void stopScreenTrack() {
        try {
            if (scr != null) scr.stopCapture();
        } catch (Exception e) {
        }
        try {
            if (scr != null) scr.dispose();
        } catch (Exception e) {
        }
        try {
            if (scrTrack != null) scrTrack.dispose();
        } catch (Exception e) {
        }
        try {
            if (scrSrc != null) scrSrc.dispose();
        } catch (Exception e) {
        }
        try {
            if (scrTex != null) scrTex.dispose();
        } catch (Exception e) {
        }
        scr = null;
        scrTrack = null;
        scrSrc = null;
        scrTex = null;
    }

    @Override
    public View videoView(String uid, Res res) {
        return new VideoView(App.ctx, res);
    }

    @Override
    public void bindVideo(View v, String uid) {
        if (!(v instanceof VideoView)) return;
        VideoTrack t = "local".equals(uid) ? (scrTrack != null ? scrTrack : camTrack) : null;
        if (t == null) {
            RtcPeer p = peers.get(uid);
            if (p != null) t = p.video();
        }
        ((VideoView) v).bind(t);
    }

    @Override
    public void unbindVideo(View v) {
        if (v instanceof VideoView) ((VideoView) v).bind(null);
    }

    @Override
    public boolean remoteVideo(String uid) {
        RtcPeer p = peers.get(uid);
        return p != null && p.video() != null && p.frames;
    }

    @Override
    public Set<String> speaking() {
        return speak;
    }

    @Override
    public void applyRemote(String uid, double volume, boolean audible, boolean video) {
        RtcPeer p = peers.get(uid);
        if (p == null) return;
        p.volume = volume;
        p.audible = audible;
        p.applyAudio();
    }

    @Override
    public void applyAudio(boolean reacquire) {
        if (aTrack == null || f == null) return;
        boolean done = false;
        if (!reacquire) try {
            done = aTrack.setAudioProcessingOptions(new AudioProcessingOptions(ec, ns, agc, ns && aggressive)).isSuccess();
        } catch (Throwable t) {
        }
        if (done) return;
        AudioSource ns0 = f.createAudioSource(audioConstraints());
        AudioTrack nt = f.createAudioTrack(UUID.randomUUID().toString(), ns0);
        nt.setEnabled(mic);
        for (RtcPeer p : peers.values()) p.replaceAudio(aTrack, nt);
        AudioTrack ot = aTrack;
        AudioSource os = aSrc;
        aTrack = nt;
        aSrc = ns0;
        try {
            ot.dispose();
            os.dispose();
        } catch (Exception e) {
        }
    }

    @Override
    public void applyEq() {
        eq.set(eqBass, eqMid, eqTreble);
    }

    void tick() {
        if (f == null) return;
        Set<String> s = new HashSet<>();
        if (mic && localLevel > 0.012) s.add("local");
        for (Map.Entry<String, Double> e : levels.entrySet()) if (e.getValue() > 0.012) s.add(e.getKey());
        if (!s.equals(speak)) {
            speak.clear();
            speak.addAll(s);
            if (Calls.ui != null) Calls.ui.speakingChanged();
        }
        long now = System.currentTimeMillis();
        if (now - lastTune >= 2000) {
            lastTune = now;
            for (RtcPeer p : peers.values()) {
                p.tune();
                p.judge();
            }
        }
        App.H.postDelayed(tick, lite ? 260 : 140);
    }

    static double rms16(byte[] b, int len) {
        long sum = 0;
        int n = len / 2;
        if (n == 0) return 0;
        for (int i = 0; i + 1 < len; i += 2) {
            int v = (short) ((b[i] & 0xff) | (b[i + 1] << 8));
            sum += (long) v * v;
        }
        return Math.sqrt(sum / (double) n) / 32768.0;
    }

    static final class Eq implements ExternalAudioProcessingFactory.AudioProcessing {
        volatile double[][] co;
        double[][] st = new double[3][4];
        int rate = 48000;
        volatile int gb, gm, gt;
        volatile boolean dirty = true;

        void set(int b, int m, int t) {
            gb = b;
            gm = m;
            gt = t;
            dirty = true;
        }

        public void initialize(int sampleRate, int channels) {
            rate = sampleRate > 0 ? sampleRate : 48000;
            dirty = true;
        }

        public void reset(int sampleRate) {
            rate = sampleRate > 0 ? sampleRate : rate;
            st = new double[3][4];
            dirty = true;
        }

        double[] shelf(boolean low, double fc, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * fc / rate, cs = Math.cos(w), sn = Math.sin(w);
            double al = sn / 2 * Math.sqrt(2), sa = 2 * Math.sqrt(A) * al;
            double b0, b1, b2, a0, a1, a2;
            if (low) {
                b0 = A * ((A + 1) - (A - 1) * cs + sa);
                b1 = 2 * A * ((A - 1) - (A + 1) * cs);
                b2 = A * ((A + 1) - (A - 1) * cs - sa);
                a0 = (A + 1) + (A - 1) * cs + sa;
                a1 = -2 * ((A - 1) + (A + 1) * cs);
                a2 = (A + 1) + (A - 1) * cs - sa;
            } else {
                b0 = A * ((A + 1) + (A - 1) * cs + sa);
                b1 = -2 * A * ((A - 1) + (A + 1) * cs);
                b2 = A * ((A + 1) + (A - 1) * cs - sa);
                a0 = (A + 1) - (A - 1) * cs + sa;
                a1 = 2 * ((A - 1) - (A + 1) * cs);
                a2 = (A + 1) - (A - 1) * cs - sa;
            }
            return new double[]{b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
        }

        double[] peak(double fc, double q, double db) {
            double A = Math.pow(10, db / 40), w = 2 * Math.PI * fc / rate, al = Math.sin(w) / (2 * q), cs = Math.cos(w);
            double a0 = 1 + al / A;
            return new double[]{(1 + al * A) / a0, -2 * cs / a0, (1 - al * A) / a0, -2 * cs / a0, (1 - al / A) / a0};
        }

        public void process(int bands, int frames, ByteBuffer buf) {
            if (gb == 0 && gm == 0 && gt == 0) return;
            if (dirty) {
                co = new double[][]{shelf(true, 200, gb), peak(1000, 1, gm), shelf(false, 4000, gt)};
                dirty = false;
            }
            double[][] c = co;
            if (c == null || buf == null) return;
            buf.order(ByteOrder.nativeOrder());
            int n = Math.min(frames > 0 ? frames : buf.capacity() / 4, buf.capacity() / 4);
            for (int i = 0; i < n; i++) {
                double x = buf.getFloat(i * 4);
                for (int k = 0; k < 3; k++) {
                    double[] q = c[k], s = st[k];
                    double y = q[0] * x + q[1] * s[0] + q[2] * s[1] - q[3] * s[2] - q[4] * s[3];
                    s[1] = s[0];
                    s[0] = x;
                    s[3] = s[2];
                    s[2] = y;
                    x = y;
                }
                buf.putFloat(i * 4, (float) x);
            }
        }
    }

    final class VideoView extends TextureView implements TextureView.SurfaceTextureListener, VideoSink {
        final EglRenderer r = new EglRenderer("vv");
        final Res res;
        VideoTrack track;
        boolean surface, inited2;
        int fw, fh;

        VideoView(Context c, Res res) {
            super(c);
            this.res = res;
            setSurfaceTextureListener(this);
            setOpaque(false);
        }

        void bind(VideoTrack t) {
            if (t == track) return;
            try {
                if (track != null) track.removeSink(this);
            } catch (Exception e) {
            }
            track = t;
            if (t != null) {
                if (!inited2) {
                    r.init(egl.getEglBaseContext(), EglBase.CONFIG_PLAIN, new GlRectDrawer());
                    inited2 = true;
                }
                try {
                    t.addSink(this);
                } catch (Exception e) {
                }
            } else if (inited2) r.clearImage();
        }

        public void onFrame(VideoFrame fr) {
            int w = fr.getRotatedWidth(), h = fr.getRotatedHeight();
            if (w != fw || h != fh) {
                fw = w;
                fh = h;
                App.H.post(() -> {
                    if (res != null) res.on(w, h);
                });
            }
            r.onFrame(fr);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w > 0 && h > 0) r.setLayoutAspectRatio(w / (float) h);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            bind(null);
            if (inited2) {
                r.release();
                inited2 = false;
            }
        }

        public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
            if (!inited2) {
                r.init(egl.getEglBaseContext(), EglBase.CONFIG_PLAIN, new GlRectDrawer());
                inited2 = true;
            }
            r.createEglSurface(s);
            if (w > 0 && h > 0) r.setLayoutAspectRatio(w / (float) h);
            surface = true;
        }

        public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {
            if (w > 0 && h > 0) r.setLayoutAspectRatio(w / (float) h);
        }

        public boolean onSurfaceTextureDestroyed(SurfaceTexture s) {
            final Object lock = new Object();
            r.releaseEglSurface(() -> {
                synchronized (lock) {
                    lock.notifyAll();
                }
            });
            synchronized (lock) {
                try {
                    lock.wait(500);
                } catch (InterruptedException e) {
                }
            }
            surface = false;
            return true;
        }

        public void onSurfaceTextureUpdated(SurfaceTexture s) {
        }
    }

    static String sig(PeerConnection.SignalingState s) {
        if (s == null) return "closed";
        switch (s) {
            case STABLE:
                return "stable";
            case HAVE_LOCAL_OFFER:
                return "have-local-offer";
            case HAVE_LOCAL_PRANSWER:
                return "have-local-pranswer";
            case HAVE_REMOTE_OFFER:
                return "have-remote-offer";
            case HAVE_REMOTE_PRANSWER:
                return "have-remote-pranswer";
            default:
                return "closed";
        }
    }

    static String conn(PeerConnection.PeerConnectionState s) {
        return s == null ? "new" : s.name().toLowerCase();
    }

    final class RtcPeer implements Sig.Peer, PeerConnection.Observer {
        final String uid;
        final PeerConnection pc;
        volatile String sigS = "stable", connS = "new";
        boolean closed, frames;
        double volume = 1;
        boolean audible = true;
        int lvl, good, bad;
        final List<AudioTrack> rAudio = new ArrayList<>();
        final Map<AudioTrack, AudioTrackSink> sinks = new HashMap<>();
        VideoSink probe;
        VideoTrack probed;

        RtcPeer(String uid) {
            this.uid = uid;
            PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(new ArrayList<>(ice));
            cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            cfg.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
            cfg.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
            cfg.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
            cfg.keyType = PeerConnection.KeyType.ECDSA;
            cfg.enableImplicitRollback = true;
            pc = f.createPeerConnection(cfg, this);
            List<String> sid = Collections.singletonList(streamId);
            if (aTrack != null) pc.addTrack(aTrack, sid);
            if (scrTrack != null) pc.addTrack(scrTrack, sid);
            else if (camTrack != null) pc.addTrack(camTrack, sid);
        }

        void addVideo(VideoTrack t) {
            if (closed || t == null) return;
            try {
                pc.addTrack(t, Collections.singletonList(streamId));
            } catch (Exception e) {
            }
        }

        void removeVideo(VideoTrack t) {
            if (closed || t == null) return;
            for (RtpSender s : pc.getSenders()) {
                MediaStreamTrack x = s.track();
                if (x != null && x.id().equals(t.id())) pc.removeTrack(s);
            }
        }

        void replaceAudio(AudioTrack old, AudioTrack nt) {
            if (closed) return;
            for (RtpSender s : pc.getSenders()) {
                MediaStreamTrack x = s.track();
                if (x != null && old != null && x.id().equals(old.id())) s.setTrack(nt, false);
            }
        }

        VideoTrack video() {
            if (closed) return null;
            VideoTrack best = null;
            for (RtpTransceiver t : pc.getTransceivers()) {
                if (t.getMediaType() != MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO || t.isStopped()) continue;
                RtpTransceiver.RtpTransceiverDirection d = t.getCurrentDirection();
                if (d != RtpTransceiver.RtpTransceiverDirection.SEND_RECV && d != RtpTransceiver.RtpTransceiverDirection.RECV_ONLY) continue;
                MediaStreamTrack tr = t.getReceiver().track();
                if (tr instanceof VideoTrack && tr.state() == MediaStreamTrack.State.LIVE) best = (VideoTrack) tr;
            }
            if (best != probed) {
                if (probed != null && probe != null) try {
                    probed.removeSink(probe);
                } catch (Exception e) {
                }
                probed = best;
                frames = false;
                if (best != null) {
                    probe = fr -> {
                        if (!frames) {
                            frames = true;
                            App.H.post(() -> {
                                if (Calls.ui != null) Calls.ui.refresh();
                            });
                        }
                    };
                    best.addSink(probe);
                }
            }
            return best;
        }

        void applyAudio() {
            for (AudioTrack t : rAudio) try {
                t.setVolume(deaf || !audible ? 0 : volume);
            } catch (Exception e) {
            }
        }

        public String sigState() {
            return closed ? "closed" : sig(pc.signalingState());
        }

        public String connState() {
            return closed ? "closed" : conn(pc.connectionState());
        }

        public boolean hasRemote() {
            return !closed && pc.getRemoteDescription() != null;
        }

        public String localSdp() {
            SessionDescription d = closed ? null : pc.getLocalDescription();
            return d == null ? null : d.description;
        }

        public String remoteSdp() {
            SessionDescription d = closed ? null : pc.getRemoteDescription();
            return d == null ? null : d.description;
        }

        abstract class Obs implements SdpObserver {
            public void onCreateSuccess(SessionDescription d) {
            }

            public void onSetSuccess() {
            }

            public void onCreateFailure(String e) {
            }

            public void onSetFailure(String e) {
            }
        }

        public void createOffer(boolean iceRestart, Sig.Cb<String> cb) {
            if (closed) {
                cb.on(null);
                return;
            }
            MediaConstraints c = new MediaConstraints();
            if (iceRestart) c.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "true"));
            pc.createOffer(new Obs() {
                public void onCreateSuccess(SessionDescription d) {
                    App.H.post(() -> cb.on(d.description));
                }

                public void onCreateFailure(String e) {
                    App.H.post(() -> cb.on(null));
                }
            }, c);
        }

        public void createAnswer(Sig.Cb<String> cb) {
            if (closed) {
                cb.on(null);
                return;
            }
            pc.createAnswer(new Obs() {
                public void onCreateSuccess(SessionDescription d) {
                    App.H.post(() -> cb.on(d.description));
                }

                public void onCreateFailure(String e) {
                    App.H.post(() -> cb.on(null));
                }
            }, new MediaConstraints());
        }

        SessionDescription sd(String type, String sdp) {
            SessionDescription.Type t = "offer".equals(type) ? SessionDescription.Type.OFFER : "answer".equals(type) ? SessionDescription.Type.ANSWER : "pranswer".equals(type) ? SessionDescription.Type.PRANSWER : SessionDescription.Type.ROLLBACK;
            return new SessionDescription(t, sdp == null ? "" : sdp);
        }

        public void setLocal(String type, String sdp, Sig.Cb<Boolean> ok) {
            if (closed) {
                ok.on(false);
                return;
            }
            pc.setLocalDescription(new Obs() {
                public void onSetSuccess() {
                    App.H.post(() -> ok.on(true));
                }

                public void onSetFailure(String e) {
                    App.H.post(() -> ok.on(false));
                }
            }, sd(type, sdp));
        }

        public void setRemote(String type, String sdp, Sig.Cb<Boolean> ok) {
            if (closed) {
                ok.on(false);
                return;
            }
            pc.setRemoteDescription(new Obs() {
                public void onSetSuccess() {
                    App.H.post(() -> ok.on(true));
                }

                public void onSetFailure(String e) {
                    App.H.post(() -> ok.on(false));
                }
            }, sd(type, sdp));
        }

        public void addIce(JSONObject c) {
            if (closed) return;
            String cand = c.optString("candidate", "");
            if (cand.isEmpty()) return;
            String mid = c.isNull("sdpMid") ? "" : c.optString("sdpMid", "");
            int idx = c.optInt("sdpMLineIndex", 0);
            try {
                pc.addIceCandidate(new IceCandidate(mid, idx, cand));
            } catch (Exception e) {
            }
        }

        public void restartIce() {
            if (!closed) pc.restartIce();
        }

        public void close() {
            if (closed) return;
            closed = true;
            for (Map.Entry<AudioTrack, AudioTrackSink> e : sinks.entrySet()) try {
                e.getKey().removeSink(e.getValue());
            } catch (Exception x) {
            }
            sinks.clear();
            try {
                if (probed != null && probe != null) probed.removeSink(probe);
            } catch (Exception e) {
            }
            levels.remove(uid);
            if (peers.get(uid) == this) peers.remove(uid);
            try {
                pc.dispose();
            } catch (Exception e) {
            }
        }

        public void onSignalingChange(PeerConnection.SignalingState s) {
            String v = sig(s);
            App.H.post(() -> {
                sigS = v;
                if (!closed && Calls.sig != null) Calls.sig.onSigState(uid, this, v);
                if (!closed && Calls.ui != null && "stable".equals(v)) Calls.ui.refresh();
            });
        }

        public void onConnectionChange(PeerConnection.PeerConnectionState s) {
            String v = conn(s);
            App.H.post(() -> {
                connS = v;
                if (!closed && Calls.sig != null) Calls.sig.onConn(uid, this, v);
                if ("connected".equals(v)) tune();
            });
        }

        public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
        }

        public void onIceConnectionReceivingChange(boolean b) {
        }

        public void onIceGatheringChange(PeerConnection.IceGatheringState s) {
        }

        public void onIceCandidate(IceCandidate c) {
            JSONObject o = new JSONObject();
            try {
                o.put("candidate", c.sdp).put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(" ufrag (\\S+)").matcher(c.sdp);
                if (m.find()) o.put("usernameFragment", m.group(1));
            } catch (Exception e) {
            }
            App.H.post(() -> {
                if (!closed && Calls.sig != null) Calls.sig.onIce(uid, this, o);
            });
        }

        public void onIceCandidatesRemoved(IceCandidate[] c) {
        }

        public void onAddStream(MediaStream s) {
        }

        public void onRemoveStream(MediaStream s) {
        }

        public void onDataChannel(DataChannel d) {
        }

        public void onRenegotiationNeeded() {
            App.H.postDelayed(this::tune, 400);
        }

        public void onTrack(RtpTransceiver t) {
            MediaStreamTrack tr = t.getReceiver().track();
            App.H.post(() -> {
                if (closed) return;
                if (tr instanceof AudioTrack) {
                    AudioTrack at = (AudioTrack) tr;
                    if (!rAudio.contains(at)) {
                        rAudio.add(at);
                        AudioTrackSink sk = (data, bits, rate, ch, frames, ts) -> {
                            if (bits == 16 && data != null) {
                                int len = data.remaining();
                                byte[] b = new byte[Math.min(len, 1920)];
                                data.duplicate().get(b);
                                levels.put(uid, rms16(b, b.length));
                            }
                        };
                        try {
                            at.addSink(sk);
                            sinks.put(at, sk);
                        } catch (Exception e) {
                        }
                        applyAudio();
                    }
                }
                if (Calls.sig != null) Calls.sig.onTrack(uid, this);
                if (Calls.ui != null) Calls.ui.refresh();
            });
        }

        boolean isScreen(MediaStreamTrack t) {
            return t != null && t.id().startsWith("screen");
        }

        void tune() {
            if (closed) return;
            int n = Math.max(1, peers.size());
            double cut = new double[]{1, .8, .6, .42}[lvl];
            for (RtpSender s : pc.getSenders()) {
                MediaStreamTrack t = s.track();
                if (t == null) continue;
                try {
                    RtpParameters p = s.getParameters();
                    if (p.encodings == null || p.encodings.isEmpty()) continue;
                    RtpParameters.Encoding e = p.encodings.get(0);
                    boolean ch = false;
                    if (t instanceof AudioTrack) {
                        if (e.networkPriority != 3) {
                            e.networkPriority = 3;
                            e.bitratePriority = 4;
                            ch = true;
                        }
                    } else {
                        int br, fps;
                        double sc;
                        if (isScreen(t)) {
                            br = (int) Math.round(Math.max(600000, (lite ? 1500000 : 3000000) / Math.sqrt(n)) * cut);
                            fps = lvl >= 2 ? 15 : (lite ? 20 : 30);
                            sc = 1;
                        } else {
                            br = (int) Math.round(Math.max(250000, (lite ? 700000 : 1500000) / Math.sqrt(n)) * cut);
                            fps = lvl >= 2 ? 15 : (lite ? 24 : 30);
                            sc = lvl >= 3 ? 2 : lvl >= 2 ? 1.5 : 1;
                            int w = lite ? 640 : 960;
                            if (w / sc < 160) sc = Math.max(1, w / 160.0);
                        }
                        if (e.maxBitrateBps == null || e.maxBitrateBps != br) {
                            e.maxBitrateBps = br;
                            ch = true;
                        }
                        if (e.maxFramerate == null || e.maxFramerate != fps) {
                            e.maxFramerate = fps;
                            ch = true;
                        }
                        if (e.scaleResolutionDownBy == null || e.scaleResolutionDownBy != sc) {
                            e.scaleResolutionDownBy = sc;
                            ch = true;
                        }
                        RtpParameters.DegradationPreference dp = isScreen(t) ? RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION : RtpParameters.DegradationPreference.BALANCED;
                        if (p.degradationPreference != dp) {
                            p.degradationPreference = dp;
                            ch = true;
                        }
                    }
                    if (ch) s.setParameters(p);
                } catch (Exception x) {
                }
            }
        }

        void judge() {
            if (closed || !"connected".equals(connState())) return;
            pc.getStats(rep -> {
                double loss = 0, rtt = 0;
                String lim = "";
                boolean vid = false;
                for (RTCStats s : rep.getStatsMap().values()) {
                    Map<String, Object> m = s.getMembers();
                    if ("remote-inbound-rtp".equals(s.getType())) {
                        Object fl = m.get("fractionLost"), rt = m.get("roundTripTime");
                        if (fl instanceof Number) loss = Math.max(loss, ((Number) fl).doubleValue());
                        if (rt instanceof Number) rtt = Math.max(rtt, ((Number) rt).doubleValue());
                    }
                    if ("outbound-rtp".equals(s.getType()) && "video".equals(m.get("kind"))) {
                        vid = true;
                        Object q = m.get("qualityLimitationReason");
                        if (q != null && !"none".equals(q)) lim = String.valueOf(q);
                    }
                    if ("candidate-pair".equals(s.getType()) && Boolean.TRUE.equals(m.get("nominated")) && m.get("currentRoundTripTime") instanceof Number)
                        rtt = Math.max(rtt, ((Number) m.get("currentRoundTripTime")).doubleValue());
                }
                final double L = loss, R = rtt;
                final String Q = lim;
                final boolean V = vid;
                App.H.post(() -> {
                    if (!V || closed) return;
                    boolean cpu = "cpu".equals(Q);
                    boolean poor = L > .06 || R > .45;
                    if (cpu || poor) {
                        good = 0;
                        bad++;
                        if (lvl < 3 && (cpu || bad >= 2)) {
                            lvl++;
                            bad = 0;
                            tune();
                        }
                    } else {
                        bad = 0;
                        if (L < .02 && R < .25 && ++good >= 5 && lvl > 0) {
                            lvl--;
                            good = 0;
                            tune();
                        }
                    }
                });
            });
        }
    }
}
