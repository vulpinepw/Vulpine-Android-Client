package com.vulpine.at.settings;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;
import java.util.ArrayList;
import java.util.List;

public class VoicePage extends SetPage {
    public VoicePage() {
        super("voice", "Voice & Video");
    }

    public static String mic() {
        return App.prefs.getString("dev.mic", "");
    }

    public static String out() {
        return App.prefs.getString("dev.out", "");
    }

    public static String cam() {
        return App.prefs.getString("dev.cam", "front");
    }

    protected void content(LinearLayout body) {
        Context c = a;
        List<String[]> ins = new ArrayList<>(), outs = new ArrayList<>();
        ins.add(new String[]{"", "Default"});
        ins.add(new String[]{"builtin", "Phone microphone"});
        outs.add(new String[]{"", "Default"});
        outs.add(new String[]{"speaker", "Speaker"});
        outs.add(new String[]{"earpiece", "Earpiece"});
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                String k = kind(d.getType());
                if (k != null && !has(ins, k)) ins.add(new String[]{k, label(k, d)});
            }
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                String k = kind(d.getType());
                if (k != null && !k.equals("builtin") && !has(outs, k)) outs.add(new String[]{k, label(k, d)});
            }
        } catch (Exception e) {
        }
        add(card(c, "Input device (microphone)", "dev.mic", "", ins, "Applied next time you join a call. Restart an ongoing call to switch."));
        add(card(c, "Output device (speaker)", "dev.out", "", outs, "Applied immediately to calls. Names appear after microphone permission is granted."));
        List<String[]> cams = new ArrayList<>();
        cams.add(new String[]{"front", "Front camera"});
        cams.add(new String[]{"back", "Back camera"});
        add(card(c, "Camera", "dev.cam", "front", cams, "Used when you turn on your camera in a call. You can still flip it during the call."));
    }

    static boolean has(List<String[]> l, String k) {
        for (String[] x : l) if (x[0].equals(k)) return true;
        return false;
    }

    static String kind(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "builtin";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "headset";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "bluetooth";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "usb";
        }
        return null;
    }

    static String label(String k, AudioDeviceInfo d) {
        String n = d.getProductName() == null ? "" : d.getProductName().toString();
        switch (k) {
            case "headset": return "Wired headset";
            case "bluetooth": return n.isEmpty() ? "Bluetooth" : "Bluetooth (" + n + ")";
            case "usb": return n.isEmpty() ? "USB audio" : "USB (" + n + ")";
        }
        return "Phone microphone";
    }

    View card(Context c, String title, String key, String def, List<String[]> opts, String hint) {
        LinearLayout card = S.card(c, 16, 16);
        card.addView(S.label(c, title, 600));
        String cur = App.prefs.getString(key, def);
        String lab = opts.get(0)[1];
        for (String[] o : opts) if (o[0].equals(cur)) lab = o[1];
        TextView sel = W.text(c, lab, 14, 400, T.text);
        sel.setPadding(T.dp(10), T.dp(8), T.dp(10), T.dp(8));
        T.lh(sel, 21);
        sel.setPadding(T.dp(10), T.dp(8) + sel.getPaddingTop(), T.dp(10), T.dp(8) + sel.getPaddingBottom());
        sel.setBackground(W.ripple(W.round(T.surface2, 8), 8));
        sel.setOnClickListener(v -> {
            Ov.Menu m = new Ov.Menu(c);
            String now = App.prefs.getString(key, def);
            for (String[] o : opts) m.item(o[1], o[0].equals(now) ? Svg.stroke(18, T.text, 2, "M20 6 9 17l-5-5") : new android.graphics.drawable.ColorDrawable(0), false, () -> {
                App.prefs.edit().putString(key, o[0]).apply();
                sel.setText(o[1]);
            });
            int[] l = new int[2];
            v.getLocationInWindow(l);
            m.showAt(l[0], l[1] + v.getHeight() + T.dp(4), false);
        });
        S.add(card, sel, 8);
        S.add(card, S.secondary(c, hint, 13), 8);
        return card;
    }
}
