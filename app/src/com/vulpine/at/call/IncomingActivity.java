package com.vulpine.at.call;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Prefs;
import com.vulpine.at.ui.AvatarView;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public class IncomingActivity extends Activity {
    public static final String ACCEPT = "com.vulpine.at.call.ACCEPT";
    static IncomingActivity cur;
    String callId, cid;

    static void dismiss() {
        IncomingActivity a = cur;
        if (a != null) a.finish();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        App.init(getApplicationContext());
        cur = this;
        Intent i = getIntent();
        callId = i.getStringExtra("callId");
        cid = i.getStringExtra("cid");
        if (ACCEPT.equals(i.getAction())) {
            accept();
            return;
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        setContentView(build(this, i.getStringExtra("name"), i.getStringExtra("avatarColor"), i.getStringExtra("avatarUrl")));
    }

    View build(Context c, String name, String color, String url) {
        FrameLayout root = new FrameLayout(c);
        root.addView(new CallBg(c, false), new FrameLayout.LayoutParams(W.MP, W.MP));
        LinearLayout col = W.col(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        AvatarView av = new AvatarView(c);
        av.set(name == null ? "Vulpine" : name, name == null ? null : color, name == null ? null : url);
        col.addView(av, W.lp(T.dp(96), T.dp(96)));
        TextView nm = W.text(c, name == null ? "Vulpine" : name, 20, 600, T.text);
        nm.setGravity(Gravity.CENTER);
        T.lh(nm, 30);
        col.addView(nm, W.lpm(W.WC, W.WC, 0, 20, 0, 0));
        TextView sub = W.text(c, "Incoming call…", 14, 400, T.textMuted);
        T.lh(sub, 21);
        col.addView(sub, W.lpm(W.WC, W.WC, 0, 4, 0, 0));
        root.addView(col, new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.CENTER));
        LinearLayout row = W.row(c);
        row.setGravity(Gravity.CENTER);
        Btn dec = new Btn(c).set("call.hangup", "Decline", false, true);
        dec.setOnClickListener(v -> {
            Calls.declineFrom(callId, cid);
            finish();
        });
        Btn acc = new Btn(c).set("call.phone", "Accept", true, false);
        acc.setOnClickListener(v -> accept());
        row.addView(dec, W.lp(T.dp(64), T.dp(64)));
        row.addView(acc, W.lpm(T.dp(64), T.dp(64), 64, 0, 0, 0));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(W.MP, W.WC, Gravity.BOTTOM);
        rp.bottomMargin = T.dp(96);
        root.addView(row, rp);
        return root;
    }

    void accept() {
        Calls.acceptFrom(callId);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Intent m = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (km != null && km.isKeyguardLocked() && Build.VERSION.SDK_INT >= 26) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    startActivity(m);
                    finish();
                }

                @Override
                public void onDismissCancelled() {
                    finish();
                }

                @Override
                public void onDismissError() {
                    startActivity(m);
                    finish();
                }
            });
            return;
        }
        startActivity(m);
        finish();
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    protected void onDestroy() {
        if (cur == this) cur = null;
        super.onDestroy();
    }
}
