package com.vulpine.at.call;

import android.content.Intent;
import android.view.View;
import java.util.Set;

public abstract class CallMedia {
    public interface Res {
        void on(int w, int h);
    }

    public Sig sig;
    public boolean ns = true, ec = true, agc = true, aggressive;
    public int eqBass, eqMid, eqTreble;

    public abstract void acquire(boolean video, Sig.Cb<Boolean> ok);

    public abstract void release();

    public abstract Sig.Peer newPeer(String uid);

    public abstract boolean localVideo();

    public abstract boolean camOn();

    public abstract boolean screenOn();

    public abstract void setMic(boolean on);

    public abstract void setDeaf(boolean on);

    public abstract void setCam(boolean on, Sig.Cb<Boolean> done);

    public abstract void switchCam();

    public abstract boolean canSwitchCam();

    public abstract void startScreen(Intent data, Sig.Cb<Boolean> done);

    public abstract void stopScreen();

    public abstract View videoView(String uid, Res res);

    public abstract void bindVideo(View v, String uid);

    public abstract void unbindVideo(View v);

    public abstract boolean remoteVideo(String uid);

    public abstract Set<String> speaking();

    public abstract void applyRemote(String uid, double volume, boolean audible, boolean video);

    public abstract void applyAudio(boolean reacquire);

    public abstract void applyEq();
}
