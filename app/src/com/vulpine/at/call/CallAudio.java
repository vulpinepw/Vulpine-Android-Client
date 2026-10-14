package com.vulpine.at.call;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import com.vulpine.at.App;
import com.vulpine.core.Session;
import java.io.File;

public final class CallAudio {
    public static final int SPEAKER = 0, EARPIECE = 1, BLUETOOTH = 2, WIRED = 3;
    static boolean active;
    static int prevMode = AudioManager.MODE_NORMAL;
    static PowerManager.WakeLock wake, prox;
    static WifiManager.WifiLock wifi;
    static AudioFocusRequest focus;
    public static int route = SPEAKER;
    static boolean userPicked;
    static AudioDeviceCallback devCb;
    static MediaPlayer ring;
    static Runnable vib;

    static AudioManager am() {
        return (AudioManager) App.ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    public static void start() {
        if (active) return;
        active = true;
        Context c = App.ctx;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vulpine:call");
            wake.setReferenceCounted(false);
            wake.acquire(4 * 60 * 60 * 1000L);
        } catch (Exception e) {
        }
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifi = wm.createWifiLock(Build.VERSION.SDK_INT >= 29 ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY : WifiManager.WIFI_MODE_FULL_HIGH_PERF, "vulpine:call");
            wifi.setReferenceCounted(false);
            wifi.acquire();
        } catch (Exception e) {
        }
        AudioManager am = am();
        try {
            prevMode = am.getMode();
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } catch (Exception e) {
        }
        try {
            AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(aa).setOnAudioFocusChangeListener(ch -> {
            }).build();
            am.requestAudioFocus(focus);
        } catch (Exception e) {
        }
        userPicked = false;
        route = headset() >= 0 ? headset() : SPEAKER;
        String pref = App.prefs == null ? "" : App.prefs.getString("dev.out", "");
        int h0 = headset();
        if ("speaker".equals(pref)) route = SPEAKER;
        else if ("earpiece".equals(pref) && hasEarpiece()) route = EARPIECE;
        else if ("bluetooth".equals(pref) && h0 == BLUETOOTH) route = BLUETOOTH;
        else if (("headset".equals(pref) || "usb".equals(pref)) && h0 == WIRED) route = WIRED;
        if (!pref.isEmpty()) userPicked = true;
        apply();
        try {
            devCb = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] d) {
                    App.H.postDelayed(CallAudio::devicesChanged, 300);
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] d) {
                    App.H.postDelayed(CallAudio::devicesChanged, 300);
                }
            };
            am.registerAudioDeviceCallback(devCb, App.H);
        } catch (Exception e) {
        }
    }

    static void devicesChanged() {
        if (!active) return;
        int h = headset();
        if (h >= 0 && (route == SPEAKER || route == EARPIECE) && !userPicked) route = h;
        else if (h < 0 && (route == BLUETOOTH || route == WIRED)) route = SPEAKER;
        else if (h >= 0 && route != h && (route == BLUETOOTH || route == WIRED)) route = h;
        apply();
        if (Calls.ui != null) Calls.ui.refresh();
    }

    public static void stop() {
        if (!active) return;
        active = false;
        AudioManager am = am();
        try {
            if (devCb != null) am.unregisterAudioDeviceCallback(devCb);
        } catch (Exception e) {
        }
        devCb = null;
        try {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice();
            else {
                am.setSpeakerphoneOn(false);
                am.stopBluetoothSco();
                am.setBluetoothScoOn(false);
            }
            am.setMode(prevMode == AudioManager.MODE_IN_COMMUNICATION ? AudioManager.MODE_NORMAL : prevMode);
        } catch (Exception e) {
        }
        try {
            if (focus != null) am.abandonAudioFocusRequest(focus);
        } catch (Exception e) {
        }
        focus = null;
        proximity(false);
        try {
            if (wake != null && wake.isHeld()) wake.release();
        } catch (Exception e) {
        }
        try {
            if (wifi != null && wifi.isHeld()) wifi.release();
        } catch (Exception e) {
        }
        wake = null;
        wifi = null;
    }

    public static int headset() {
        try {
            for (AudioDeviceInfo d : am().getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_BLE_HEADSET)) return BLUETOOTH;
            }
            for (AudioDeviceInfo d : am().getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_USB_HEADSET) return WIRED;
            }
        } catch (Exception e) {
        }
        return -1;
    }

    public static boolean hasEarpiece() {
        try {
            for (AudioDeviceInfo d : am().getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) return true;
        } catch (Exception e) {
        }
        return false;
    }

    public static void set(int r) {
        route = r;
        userPicked = true;
        if (App.prefs != null) App.prefs.edit().putString("dev.out", r == SPEAKER ? "speaker" : r == EARPIECE ? "earpiece" : r == BLUETOOTH ? "bluetooth" : "headset").apply();
        apply();
    }

    public static boolean speakerOn() {
        return route == SPEAKER;
    }

    static void apply() {
        AudioManager am = am();
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                int[] want;
                if (route == BLUETOOTH) want = new int[]{AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET};
                else if (route == WIRED) want = new int[]{AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES};
                else if (route == EARPIECE) want = new int[]{AudioDeviceInfo.TYPE_BUILTIN_EARPIECE};
                else want = new int[]{AudioDeviceInfo.TYPE_BUILTIN_SPEAKER};
                boolean set = false;
                for (int w : want) {
                    for (AudioDeviceInfo d : am.getAvailableCommunicationDevices())
                        if (d.getType() == w) {
                            set = am.setCommunicationDevice(d);
                            break;
                        }
                    if (set) break;
                }
                if (!set) am.clearCommunicationDevice();
            } else {
                if (route == BLUETOOTH) {
                    am.setSpeakerphoneOn(false);
                    am.startBluetoothSco();
                    am.setBluetoothScoOn(true);
                } else {
                    if (am.isBluetoothScoOn()) {
                        am.setBluetoothScoOn(false);
                        am.stopBluetoothSco();
                    }
                    am.setSpeakerphoneOn(route == SPEAKER);
                }
            }
        } catch (Exception e) {
        }
        proximity(route == EARPIECE && !(Calls.media != null && Calls.media.localVideo()));
    }

    static void proximity(boolean on) {
        try {
            if (on) {
                if (prox == null) {
                    PowerManager pm = (PowerManager) App.ctx.getSystemService(Context.POWER_SERVICE);
                    if (!pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return;
                    prox = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "vulpine:prox");
                    prox.setReferenceCounted(false);
                }
                if (!prox.isHeld()) prox.acquire(4 * 60 * 60 * 1000L);
            } else if (prox != null && prox.isHeld()) prox.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
        } catch (Exception e) {
        }
    }

    public static void ringStart() {
        ringStop();
        AudioManager am = am();
        int mode = AudioManager.RINGER_MODE_NORMAL;
        try {
            mode = am.getRingerMode();
        } catch (Exception e) {
        }
        final boolean sound = mode == AudioManager.RINGER_MODE_NORMAL, vibe = mode != AudioManager.RINGER_MODE_SILENT;
        if (sound) {
            final MediaPlayer mp = new MediaPlayer();
            ring = mp;
            try {
                mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                mp.setLooping(true);
                mp.setVolume(.9f, .9f);
                String url = Session.s == null ? null : Session.s.optString("ringtoneUrl", null);
                if (url != null && (url.isEmpty() || "null".equals(url))) url = null;
                final String u = url;
                App.bg(() -> {
                    File f = null;
                    if (u != null) try {
                        f = com.vulpine.at.ui.Img.file(u);
                    } catch (Exception e) {
                    }
                    final File ff = f;
                    App.H.post(() -> {
                        if (ring != mp) return;
                        try {
                            if (ff != null && ff.length() > 0) mp.setDataSource(ff.getAbsolutePath());
                            else {
                                AssetFileDescriptor fd = App.ctx.getAssets().openFd("default-ringtone.mp3");
                                mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                                fd.close();
                            }
                            mp.setOnPreparedListener(MediaPlayer::start);
                            mp.prepareAsync();
                        } catch (Exception e) {
                            try {
                                AssetFileDescriptor fd = App.ctx.getAssets().openFd("default-ringtone.mp3");
                                mp.reset();
                                mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                                fd.close();
                                mp.setOnPreparedListener(MediaPlayer::start);
                                mp.prepareAsync();
                            } catch (Exception e2) {
                            }
                        }
                    });
                });
            } catch (Exception e) {
            }
        }
        if (vibe) {
            vib = new Runnable() {
                public void run() {
                    if (vib != this) return;
                    try {
                        Vibrator v = (Vibrator) App.ctx.getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 150, 300}, -1), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
                    } catch (Exception e) {
                    }
                    App.H.postDelayed(this, 3000);
                }
            };
            vib.run();
        }
    }

    public static void ringStop() {
        MediaPlayer mp = ring;
        ring = null;
        if (mp != null) try {
            mp.stop();
        } catch (Exception e) {
        }
        if (mp != null) try {
            mp.release();
        } catch (Exception e) {
        }
        if (vib != null) {
            App.H.removeCallbacks(vib);
            vib = null;
            try {
                Vibrator v = (Vibrator) App.ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.cancel();
            } catch (Exception e) {
            }
        }
    }

    public static void beep(boolean join) {
        App.bg(() -> {
            try {
                int sr = 24000, n = (int) (sr * .3);
                short[] pcm = new short[n];
                double[] fq = join ? new double[]{520, 660} : new double[]{560, 440};
                for (int k = 0; k < 2; k++) {
                    int off = (int) (sr * .12 * k);
                    for (int i = 0; i < (int) (sr * .16) && off + i < n; i++) {
                        double t = i / (double) sr, g;
                        if (t < .02) g = .16 * t / .02;
                        else if (t < .14) g = .16 * Math.pow(.001 / .16, (t - .02) / .12);
                        else g = 0;
                        int v = pcm[off + i] + (int) (Math.sin(2 * Math.PI * fq[k] * t) * g * 32767);
                        pcm[off + i] = (short) Math.max(-32768, Math.min(32767, v));
                    }
                }
                AudioTrack at = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(n * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                at.write(pcm, 0, n);
                at.play();
                Thread.sleep(400);
                at.release();
            } catch (Exception e) {
            }
        });
    }
}
