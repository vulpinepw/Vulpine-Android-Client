package com.vulpine.at.settings;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Screen;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import com.vulpine.core.Http;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class QrLogin extends Screen {
    static final String PREFIX = "vulpine-qr-login:";
    FrameLayout root, confirm;
    TextView top, hint, close;
    LinearLayout manual;
    EditText input;
    TextureView tv;
    Camera cam;
    int camW, camH, camRot;
    volatile boolean busy, closed, decoding;
    String lastRejected;
    long lastDecode;
    final ExecutorService dec = Executors.newSingleThreadExecutor();

    public static void open(MainActivity a) {
        a.push(new QrLogin());
    }

    public View build() {
        Context c = a;
        root = new FrameLayout(c) {
            protected void onWindowVisibilityChanged(int vis) {
                super.onWindowVisibilityChanged(vis);
                if (vis != VISIBLE) stopCamera();
                else if (!closed && cam == null && tv.isAvailable() && a.top() == QrLogin.this) startCamera();
            }
        };
        root.setBackgroundColor(0xff000000);
        root.setClickable(true);
        tv = new TextureView(c);
        root.addView(tv, new FrameLayout.LayoutParams(W.MP, W.MP));
        top = W.text(c, "Starting camera…", 13, 400, 0xffffffff);
        top.setGravity(Gravity.CENTER);
        top.setPadding(T.dp(12), T.dp(8), T.dp(12), T.dp(8));
        top.setMinHeight(T.dp(34));
        top.setBackground(W.round(0x8c000000, 10));
        root.addView(top, W.flp(W.MP, W.WC, Gravity.TOP));
        close = W.text(c, "✕", 17, 400, 0xffffffff);
        close.setGravity(Gravity.CENTER);
        close.setBackground(W.ripple(W.oval(0x24ffffff), 99));
        close.setContentDescription("Close");
        close.setOnClickListener(v -> a.onBackPressed());
        root.addView(close, W.flp(T.dp(38), T.dp(38), Gravity.TOP | Gravity.END));
        hint = W.text(c, "Point the camera at the QR code shown on the login page", 14, 400, 0xffffffff);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(T.dp(14), T.dp(10), T.dp(14), T.dp(10));
        hint.setBackground(W.round(0x8c000000, 12));
        root.addView(hint, W.flp(W.MP, W.WC, Gravity.BOTTOM));
        manual = W.row(c);
        input = new EditText(c);
        T.text(input, 14, 400, 0xffffffff);
        input.setHint("e.g. 1a2b3c4d5e6f7a8b");
        input.setHintTextColor(0x80ffffff);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        input.setPadding(T.dp(12), T.dp(10), T.dp(12), T.dp(10));
        input.setBackground(W.round(0x1fffffff, 10));
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, id, e) -> {
            manualEntry();
            return true;
        });
        manual.addView(input, W.lp(0, W.WC, 1));
        TextView ok = W.text(c, "OK", 14, 600, 0xffffffff);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(T.dp(16), 0, T.dp(16), 0);
        ok.setBackground(W.ripple(W.accentBg(10), 10));
        ok.setOnClickListener(v -> manualEntry());
        LinearLayout.LayoutParams op = W.lp(W.WC, W.MP);
        op.leftMargin = T.dp(8);
        manual.addView(ok, op);
        root.addView(manual, W.flp(W.MP, W.WC, Gravity.BOTTOM));
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
                startCamera();
            }

            public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {
                fit();
            }

            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) {
                stopCamera();
                return true;
            }

            public void onSurfaceTextureUpdated(SurfaceTexture s) {
            }
        });
        return root;
    }

    public void onInsets(int topI, int bottom, int ime) {
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) top.getLayoutParams();
        p.topMargin = T.dp(18) + topI;
        p.leftMargin = T.dp(16);
        p.rightMargin = T.dp(64);
        top.setLayoutParams(p);
        FrameLayout.LayoutParams x = (FrameLayout.LayoutParams) close.getLayoutParams();
        x.topMargin = T.dp(12) + topI;
        x.rightMargin = T.dp(12);
        close.setLayoutParams(x);
        FrameLayout.LayoutParams h = (FrameLayout.LayoutParams) hint.getLayoutParams();
        h.bottomMargin = T.dp(24) + bottom;
        h.leftMargin = h.rightMargin = T.dp(16);
        hint.setLayoutParams(h);
        FrameLayout.LayoutParams m = (FrameLayout.LayoutParams) manual.getLayoutParams();
        m.bottomMargin = T.dp(84) + Math.max(bottom, ime - T.dp(60));
        m.leftMargin = m.rightMargin = T.dp(16);
        manual.setLayoutParams(m);
    }

    void status(String s) {
        App.ui(() -> top.setText(s));
    }

    volatile boolean opening;

    void startCamera() {
        if (opening || cam != null || closed) return;
        opening = true;
        App.requestPerms(a, new String[]{"android.permission.CAMERA"}, ok -> {
            if (!ok) {
                opening = false;
                status("Camera blocked - allow camera access for this site, or type the code");
                return;
            }
            App.bg(() -> {
                try {
                    openCam();
                } finally {
                    opening = false;
                }
            });
        });
    }

    void openCam() {
        if (closed) return;
        try {
            int id = -1;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    id = i;
                    break;
                }
            }
            if (id < 0 && Camera.getNumberOfCameras() > 0) id = 0;
            if (id < 0) {
                status("Camera not available on this device - type the code instead");
                return;
            }
            Camera.getCameraInfo(id, info);
            Camera c = Camera.open(id);
            Camera.Parameters p = c.getParameters();
            Camera.Size best = null;
            for (Camera.Size s : p.getSupportedPreviewSizes()) {
                if (s.width > 1920 || s.height > 1080) continue;
                if (best == null || s.width * s.height > best.width * best.height) best = s;
            }
            if (best != null) p.setPreviewSize(best.width, best.height);
            if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            else if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            c.setParameters(p);
            Camera.Size ps = c.getParameters().getPreviewSize();
            camW = ps.width;
            camH = ps.height;
            int rot = a.getWindowManager().getDefaultDisplay().getRotation();
            int deg = rot == Surface.ROTATION_90 ? 90 : rot == Surface.ROTATION_180 ? 180 : rot == Surface.ROTATION_270 ? 270 : 0;
            camRot = (info.orientation - deg + 360) % 360;
            c.setDisplayOrientation(camRot);
            if (closed) {
                c.release();
                return;
            }
            cam = c;
            App.ui(() -> {
                try {
                    if (closed || tv.getSurfaceTexture() == null) return;
                    fit();
                    cam.setPreviewTexture(tv.getSurfaceTexture());
                    cam.setPreviewCallback((data, cm) -> onFrame(data));
                    cam.startPreview();
                    top.setText("");
                } catch (Exception e) {
                    status("Camera not available on this device - type the code instead");
                }
            });
        } catch (Exception e) {
            status("Camera not available on this device - type the code instead");
        }
    }

    void fit() {
        if (camW == 0 || tv.getWidth() == 0) return;
        float vw = tv.getWidth(), vh = tv.getHeight();
        boolean swap = camRot == 90 || camRot == 270;
        float pw = swap ? camH : camW, ph = swap ? camW : camH;
        float s = Math.max(vw / pw, vh / ph);
        Matrix m = new Matrix();
        m.setScale(pw * s / vw, ph * s / vh, vw / 2, vh / 2);
        tv.setTransform(m);
    }

    void onFrame(byte[] data) {
        long now = System.currentTimeMillis();
        if (busy || decoding || closed || now - lastDecode < 300 || data == null) return;
        lastDecode = now;
        decoding = true;
        byte[] copy = data.clone();
        int w = camW, h = camH;
        dec.execute(() -> {
            String v = null;
            try {
                v = QrDecode.yuv(copy, w, h);
            } catch (Throwable e) {
            }
            decoding = false;
            if (v != null) {
                String fv = v;
                App.ui(() -> onScan(fv));
            }
        });
    }

    void onScan(String v) {
        if (v == null || !v.startsWith(PREFIX) || closed) return;
        String code = v.substring(PREFIX.length());
        if (busy || code.equals(lastRejected)) return;
        busy = true;
        confirm(code);
    }

    void manualEntry() {
        String code = input.getText().toString().replaceAll("[^a-fA-F0-9]", "").toLowerCase();
        if (code.length() != 16) {
            top.setText("Enter the 16 character code from the login page");
            return;
        }
        if (busy) return;
        busy = true;
        W.hideKb(input);
        approve(code);
    }

    void confirm(String code) {
        if (confirm != null) return;
        Context c = a;
        confirm = new FrameLayout(c);
        confirm.setBackgroundColor(0xa6000000);
        confirm.setClickable(true);
        LinearLayout box = W.col(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(W.round(0xff17181c, 16, 0x17ffffff, 1));
        box.setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(20));
        TextView t = W.text(c, "Approve login on another device?", 16, 600, 0xffffffff);
        t.setGravity(Gravity.CENTER);
        box.addView(t, W.lpm(W.MP, W.WC, 0, 0, 0, 8));
        TextView sub = W.text(c, "You scanned a login code. Approving signs your account in on the device that shows it.", 13, 400, 0xff9ca3af);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub, W.lpm(W.MP, W.WC, 0, 0, 0, 6));
        TextView who = W.text(c, "", 13, 400, 0xff9ca3af);
        who.setGravity(Gravity.CENTER);
        box.addView(who, W.lpm(W.MP, W.WC, 0, 0, 0, 6));
        App.bg(() -> {
            Http.Resp r = Http.get("/me");
            String hd = r.ok() ? r.obj().optString("handle") : "";
            App.ui(() -> who.setText(hd.isEmpty() ? "" : "@" + hd + " will be signed in there"));
        });
        LinearLayout row = W.row(c);
        TextView no = W.text(c, "Cancel", 14, 600, 0xffffffff);
        no.setGravity(Gravity.CENTER);
        no.setPadding(0, T.dp(11), 0, T.dp(11));
        no.setBackground(W.ripple(W.round(0x14ffffff, 10), 10));
        no.setOnClickListener(v -> {
            lastRejected = code;
            dismissConfirm();
            busy = false;
            top.setText("");
        });
        row.addView(no, W.lp(0, W.WC, 1));
        TextView yes = W.text(c, "Approve", 14, 600, 0xffffffff);
        yes.setGravity(Gravity.CENTER);
        yes.setPadding(0, T.dp(11), 0, T.dp(11));
        yes.setBackground(W.ripple(W.accentBg(10), 10));
        yes.setOnClickListener(v -> {
            dismissConfirm();
            approve(code);
        });
        LinearLayout.LayoutParams yp = W.lp(0, W.WC, 1);
        yp.leftMargin = T.dp(10);
        row.addView(yes, yp);
        box.addView(row, W.lpm(W.MP, W.WC, 0, 10, 0, 0));
        FrameLayout.LayoutParams bp = W.flp(Math.min(T.dp(320), root.getWidth() - T.dp(48)), W.WC, Gravity.CENTER);
        confirm.addView(box, bp);
        root.addView(confirm, new FrameLayout.LayoutParams(W.MP, W.MP));
    }

    void dismissConfirm() {
        if (confirm != null) root.removeView(confirm);
        confirm = null;
    }

    void approve(String code) {
        top.setText("Approving…");
        App.bg(() -> {
            Http.Resp r = Http.post("/auth/qr/approve", App.jo("code", code));
            App.ui(() -> {
                if (r.ok()) {
                    Ov.toast("Approved - check your computer");
                    if (!closed && a.top() == this) a.pop();
                } else {
                    busy = false;
                    JSONObject o = r.obj();
                    top.setText("invalid_or_expired_code".equals(o.optString("error")) ? "This code expired - refresh the login page" : "Could not approve this login");
                }
            });
        });
    }

    public boolean onBack() {
        if (confirm != null) {
            lastRejected = null;
            dismissConfirm();
            busy = false;
            return true;
        }
        return false;
    }

    void stopCamera() {
        Camera c = cam;
        cam = null;
        if (c != null) {
            try {
                c.setPreviewCallback(null);
                c.stopPreview();
            } catch (Exception e) {
            }
            c.release();
        }
    }

    public void onHide() {
        stopCamera();
    }

    public void onShow() {
        if (!closed && cam == null && tv.isAvailable()) startCamera();
    }

    public void onDestroy() {
        closed = true;
        stopCamera();
        dec.shutdownNow();
    }
}
