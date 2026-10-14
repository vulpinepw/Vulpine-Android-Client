package com.vulpine.at.stories;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public final class Transcode {
    public interface Progress {
        void on(float f);
    }

    static final long TIMEOUT = 10000;

    static final class Sample {
        final byte[] data;
        final long pts;
        final int flags;

        Sample(byte[] d, long p, int f) {
            data = d;
            pts = p;
            flags = f;
        }
    }

    public static boolean run(File in, File out, int maxSide, int bitrate, Progress pr) {
        MediaExtractor vx = new MediaExtractor();
        MediaCodec dec = null, enc = null;
        MediaMuxer mux = null;
        Gl gl = null;
        HandlerThread ht = null;
        try {
            vx.setDataSource(in.getPath());
            int vi = -1, ai = -1;
            for (int i = 0; i < vx.getTrackCount(); i++) {
                String m = vx.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (m == null) continue;
                if (vi < 0 && m.startsWith("video/")) vi = i;
                if (ai < 0 && m.startsWith("audio/")) ai = i;
            }
            if (vi < 0) return false;
            MediaFormat af = null;
            List<Sample> audio = new ArrayList<>();
            if (ai >= 0) af = audioTrack(in, ai, audio);
            MediaFormat vf = vx.getTrackFormat(vi);
            int w = vf.getInteger(MediaFormat.KEY_WIDTH), h = vf.getInteger(MediaFormat.KEY_HEIGHT);
            int rot = vf.containsKey("rotation-degrees") ? vf.getInteger("rotation-degrees") : 0;
            long durUs = vf.containsKey(MediaFormat.KEY_DURATION) ? vf.getLong(MediaFormat.KEY_DURATION) : 0;
            float sc = Math.min(1f, maxSide / (float) Math.max(w, h));
            int ow = Math.max(2, Math.round(w * sc / 2f) * 2), oh = Math.max(2, Math.round(h * sc / 2f) * 2);
            int fps = vf.containsKey(MediaFormat.KEY_FRAME_RATE) ? Math.min(30, vf.getInteger(MediaFormat.KEY_FRAME_RATE)) : 30;
            MediaFormat ef = MediaFormat.createVideoFormat("video/avc", ow, oh);
            ef.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            ef.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            ef.setInteger(MediaFormat.KEY_FRAME_RATE, Math.max(1, fps));
            ef.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            enc = MediaCodec.createEncoderByType("video/avc");
            enc.configure(ef, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface encIn = enc.createInputSurface();
            enc.start();
            ht = new HandlerThread("vtc");
            ht.start();
            gl = new Gl(encIn, new Handler(ht.getLooper()));
            dec = MediaCodec.createDecoderByType(vf.getString(MediaFormat.KEY_MIME));
            dec.configure(vf, gl.surface, null, 0);
            dec.start();
            vx.selectTrack(vi);
            mux = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mux.setOrientationHint(rot);
            int muxV = -1, muxA = -1;
            boolean started = false, inDone = false, decDone = false, encDone = false;
            MediaCodec.BufferInfo di = new MediaCodec.BufferInfo(), ei = new MediaCodec.BufferInfo();
            while (!encDone) {
                if (!inDone) {
                    int ix = dec.dequeueInputBuffer(TIMEOUT);
                    if (ix >= 0) {
                        ByteBuffer b = dec.getInputBuffer(ix);
                        int n = vx.readSampleData(b, 0);
                        if (n < 0) {
                            dec.queueInputBuffer(ix, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            dec.queueInputBuffer(ix, 0, n, vx.getSampleTime(), 0);
                            vx.advance();
                        }
                    }
                }
                boolean encBusy = true;
                while (encBusy) {
                    int ox = enc.dequeueOutputBuffer(ei, decDone ? TIMEOUT : 0);
                    if (ox == MediaCodec.INFO_TRY_AGAIN_LATER) encBusy = false;
                    else if (ox == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxV = mux.addTrack(enc.getOutputFormat());
                        if (af != null) muxA = mux.addTrack(af);
                        mux.start();
                        started = true;
                    } else if (ox >= 0) {
                        ByteBuffer ob = enc.getOutputBuffer(ox);
                        if ((ei.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) ei.size = 0;
                        if (ei.size > 0 && started) {
                            ob.position(ei.offset);
                            ob.limit(ei.offset + ei.size);
                            mux.writeSampleData(muxV, ob, ei);
                            if (pr != null && durUs > 0) pr.on(Math.min(1f, ei.presentationTimeUs / (float) durUs));
                        }
                        enc.releaseOutputBuffer(ox, false);
                        if ((ei.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encDone = true;
                            encBusy = false;
                        }
                    }
                }
                if (!decDone) {
                    int ox = dec.dequeueOutputBuffer(di, TIMEOUT);
                    if (ox >= 0) {
                        boolean render = di.size > 0;
                        dec.releaseOutputBuffer(ox, render);
                        if (render) {
                            gl.await();
                            gl.draw();
                            gl.swap(di.presentationTimeUs * 1000);
                        }
                        if ((di.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decDone = true;
                            enc.signalEndOfInputStream();
                        }
                    }
                }
            }
            if (!started) return false;
            if (muxA >= 0) {
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                for (Sample s : audio) {
                    bi.set(0, s.data.length, s.pts, s.flags);
                    mux.writeSampleData(muxA, ByteBuffer.wrap(s.data), bi);
                }
            }
            mux.stop();
            return out.length() > 0;
        } catch (Throwable e) {
            return false;
        } finally {
            try {
                vx.release();
            } catch (Throwable e) {
            }
            if (dec != null) try {
                dec.stop();
                dec.release();
            } catch (Throwable e) {
            }
            if (enc != null) try {
                enc.stop();
                enc.release();
            } catch (Throwable e) {
            }
            if (mux != null) try {
                mux.release();
            } catch (Throwable e) {
            }
            if (gl != null) gl.release();
            if (ht != null) ht.quitSafely();
        }
    }

    static MediaFormat audioTrack(File in, int ai, List<Sample> out) throws Exception {
        MediaExtractor ax = new MediaExtractor();
        try {
            ax.setDataSource(in.getPath());
            MediaFormat f = ax.getTrackFormat(ai);
            ax.selectTrack(ai);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if ("audio/mp4a-latm".equals(mime)) {
                ByteBuffer b = ByteBuffer.allocate(f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) ? Math.max(65536, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)) : 1 << 20);
                while (true) {
                    b.clear();
                    int n = ax.readSampleData(b, 0);
                    if (n < 0) break;
                    byte[] d = new byte[n];
                    b.position(0);
                    b.get(d, 0, n);
                    out.add(new Sample(d, ax.getSampleTime(), (ax.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0));
                    ax.advance();
                }
                return f;
            }
            return reencodeAudio(ax, f, out);
        } finally {
            ax.release();
        }
    }

    static MediaFormat reencodeAudio(MediaExtractor ax, MediaFormat f, List<Sample> out) throws Exception {
        int rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE), ch = Math.min(2, f.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        MediaCodec dec = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME));
        dec.configure(f, null, null, 0);
        dec.start();
        MediaFormat ef = MediaFormat.createAudioFormat("audio/mp4a-latm", rate, ch);
        ef.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        ef.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        ef.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 << 16);
        MediaCodec enc = MediaCodec.createEncoderByType("audio/mp4a-latm");
        enc.configure(ef, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        enc.start();
        MediaFormat outF = null;
        try {
            boolean inDone = false, decDone = false, encDone = false;
            MediaCodec.BufferInfo di = new MediaCodec.BufferInfo(), ei = new MediaCodec.BufferInfo();
            ByteBuffer pending = null;
            long pendPts = 0;
            boolean pendEos = false;
            while (!encDone) {
                if (!inDone) {
                    int ix = dec.dequeueInputBuffer(TIMEOUT);
                    if (ix >= 0) {
                        ByteBuffer b = dec.getInputBuffer(ix);
                        int n = ax.readSampleData(b, 0);
                        if (n < 0) {
                            dec.queueInputBuffer(ix, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            dec.queueInputBuffer(ix, 0, n, ax.getSampleTime(), 0);
                            ax.advance();
                        }
                    }
                }
                if (pending == null && !decDone) {
                    int ox = dec.dequeueOutputBuffer(di, TIMEOUT);
                    if (ox >= 0) {
                        ByteBuffer ob = dec.getOutputBuffer(ox);
                        ByteBuffer copy = ByteBuffer.allocate(di.size);
                        ob.position(di.offset);
                        ob.limit(di.offset + di.size);
                        copy.put(ob);
                        copy.flip();
                        dec.releaseOutputBuffer(ox, false);
                        pending = copy;
                        pendPts = di.presentationTimeUs;
                        pendEos = (di.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        if (pendEos) decDone = true;
                    }
                }
                if (pending != null) {
                    int ix = enc.dequeueInputBuffer(TIMEOUT);
                    if (ix >= 0) {
                        ByteBuffer b = enc.getInputBuffer(ix);
                        b.clear();
                        int n = Math.min(b.remaining(), pending.remaining());
                        ByteBuffer slice = pending.duplicate();
                        slice.limit(slice.position() + n);
                        b.put(slice);
                        pending.position(pending.position() + n);
                        boolean last = !pending.hasRemaining();
                        enc.queueInputBuffer(ix, 0, n, pendPts, last && pendEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        if (last) pending = null;
                    }
                }
                int ox = enc.dequeueOutputBuffer(ei, 0);
                if (ox == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outF = enc.getOutputFormat();
                else if (ox >= 0) {
                    ByteBuffer ob = enc.getOutputBuffer(ox);
                    if ((ei.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && ei.size > 0) {
                        byte[] d = new byte[ei.size];
                        ob.position(ei.offset);
                        ob.get(d);
                        out.add(new Sample(d, ei.presentationTimeUs, ei.flags & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM));
                    }
                    enc.releaseOutputBuffer(ox, false);
                    if ((ei.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encDone = true;
                }
            }
        } finally {
            try {
                dec.stop();
                dec.release();
                enc.stop();
                enc.release();
            } catch (Throwable e) {
            }
        }
        return outF;
    }

    static final class Gl implements SurfaceTexture.OnFrameAvailableListener {
        static final String VS = "attribute vec4 aPos;attribute vec4 aTex;uniform mat4 uST;varying vec2 v;void main(){gl_Position=aPos;v=(uST*aTex).xy;}";
        static final String FS = "#extension GL_OES_EGL_image_external : require\nprecision mediump float;varying vec2 v;uniform samplerExternalOES s;void main(){gl_FragColor=texture2D(s,v);}";
        EGLDisplay dpy;
        EGLContext ctx;
        EGLSurface es;
        final SurfaceTexture st;
        final Surface surface;
        final Object lock = new Object();
        boolean avail;
        int prog, tex;
        final float[] m = new float[16];
        final FloatBuffer quad;

        Gl(Surface encIn, Handler h) {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] v = new int[2];
            EGL14.eglInitialize(dpy, v, 0, v, 1);
            int[] attr = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142, 1, EGL14.EGL_NONE};
            EGLConfig[] cf = new EGLConfig[1];
            int[] n = new int[1];
            EGL14.eglChooseConfig(dpy, attr, 0, cf, 0, 1, n, 0);
            ctx = EGL14.eglCreateContext(dpy, cf[0], EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            es = EGL14.eglCreateWindowSurface(dpy, cf[0], encIn, new int[]{EGL14.EGL_NONE}, 0);
            EGL14.eglMakeCurrent(dpy, es, es, ctx);
            int[] t = new int[1];
            GLES20.glGenTextures(1, t, 0);
            tex = t[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            st = new SurfaceTexture(tex);
            st.setOnFrameAvailableListener(this, h);
            surface = new Surface(st);
            prog = program();
            float[] q = {-1, -1, 0, 0, 1, -1, 1, 0, -1, 1, 0, 1, 1, 1, 1, 1};
            quad = ByteBuffer.allocateDirect(q.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            quad.put(q).position(0);
        }

        static int shader(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            return s;
        }

        static int program() {
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, VS));
            GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, FS));
            GLES20.glLinkProgram(p);
            return p;
        }

        public void onFrameAvailable(SurfaceTexture s) {
            synchronized (lock) {
                avail = true;
                lock.notifyAll();
            }
        }

        void await() throws InterruptedException {
            synchronized (lock) {
                long end = System.currentTimeMillis() + 2500;
                while (!avail) {
                    long w = end - System.currentTimeMillis();
                    if (w <= 0) throw new RuntimeException("frame timeout");
                    lock.wait(w);
                }
                avail = false;
            }
            st.updateTexImage();
        }

        void draw() {
            st.getTransformMatrix(m);
            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(prog);
            int aPos = GLES20.glGetAttribLocation(prog, "aPos"), aTex = GLES20.glGetAttribLocation(prog, "aTex");
            quad.position(0);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(aPos);
            quad.position(2);
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad);
            GLES20.glEnableVertexAttribArray(aTex);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uST"), 1, false, m, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        void swap(long ns) {
            EGLExt.eglPresentationTimeANDROID(dpy, es, ns);
            EGL14.eglSwapBuffers(dpy, es);
        }

        void release() {
            try {
                surface.release();
                st.release();
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroySurface(dpy, es);
                EGL14.eglDestroyContext(dpy, ctx);
                EGL14.eglTerminate(dpy);
            } catch (Throwable e) {
            }
        }
    }
}
