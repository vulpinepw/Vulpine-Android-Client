package com.vulpine.at;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import com.vulpine.at.ui.Screen;
import com.vulpine.at.ui.T;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    public static MainActivity cur;
    public FrameLayout host;
    public final List<Screen> stack = new ArrayList<>();
    public int insetTop, insetBottom, insetIme;
    private boolean animating;

    @Override
    public void startActivity(android.content.Intent i, Bundle o) {
        try {
            android.net.Uri d = i.getData();
            if (i.getComponent() == null || !getPackageName().equals(i.getComponent().getPackageName())) {
                StringBuilder b = new StringBuilder("Vulpine " + App.VERSION_NAME + " (" + App.VERSION_CODE + ")\nOPENED EXTERNAL " + i.getAction() + " " + d + " " + i.getType() + "\n");
                for (StackTraceElement e : new Throwable().getStackTrace()) b.append("    at ").append(e).append('\n');
                App.report(this, "ext.txt", b.toString());
            }
        } catch (Throwable e) {
        }
        super.startActivity(i, o);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cur = this;
        App.init(getApplicationContext());
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        else w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        host = new FrameLayout(this);
        host.setBackgroundColor(T.bg);
        setContentView(host);
        host.setOnApplyWindowInsetsListener((v, ins) -> {
            int top, bot, ime;
            if (Build.VERSION.SDK_INT >= 30) {
                top = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).top;
                bot = ins.getInsets(WindowInsets.Type.systemBars()).bottom;
                ime = ins.getInsets(WindowInsets.Type.ime()).bottom;
            } else {
                top = ins.getSystemWindowInsetTop();
                bot = ins.getStableInsetBottom();
                ime = Math.max(0, ins.getSystemWindowInsetBottom() - bot);
            }
            if (top != insetTop || bot != insetBottom || ime != insetIme) {
                insetTop = top;
                insetBottom = bot;
                insetIme = ime;
                for (Screen s : stack) s.onInsets(top, bot, ime);
            }
            return ins;
        });
        if (Build.VERSION.SDK_INT >= 30) w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        else w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        applyBars();
        App.boot(this);
        handleIntent(getIntent());
    }

    public void applyBars() {
        Window w = getWindow();
        host.setBackgroundColor(T.bg);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int m = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(T.dark ? 0 : m, m);
            }
        } else {
            View d = w.getDecorView();
            int f = d.getSystemUiVisibility();
            if (T.dark) f &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            else f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            d.setSystemUiVisibility(f);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent(i);
    }

    void handleIntent(Intent i) {
        App.handleIntent(this, i);
    }

    public Screen top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    public void setRoot(Screen s) {
        for (Screen x : new ArrayList<>(stack)) {
            x.onHide();
            x.onDestroy();
        }
        stack.clear();
        host.removeAllViews();
        s.a = this;
        s.root = s.build();
        stack.add(s);
        host.addView(s.root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        s.onInsets(insetTop, insetBottom, insetIme);
        s.onShow();
    }

    public void push(Screen s) {
        Screen under = top();
        s.a = this;
        s.root = s.build();
        stack.add(s);
        host.addView(s.root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        s.onInsets(insetTop, insetBottom, insetIme);
        int wpx = host.getWidth() > 0 ? host.getWidth() : getResources().getDisplayMetrics().widthPixels;
        s.root.setTranslationX(wpx);
        animating = true;
        s.root.animate().translationX(0).setDuration(T.lowfx ? T.DUR : T.DUR_SLOW).setInterpolator(T.EASE).withEndAction(() -> {
            animating = false;
            if (under != null && s.opaque) under.root.setVisibility(View.INVISIBLE);
        }).start();
        if (under != null) {
            under.root.animate().translationX(-wpx * .25f).setDuration(T.lowfx ? T.DUR : T.DUR_SLOW).setInterpolator(T.EASE).start();
            under.onHide();
        }
        s.onShow();
    }

    public void pop() {
        if (stack.size() < 2) return;
        Screen s = stack.remove(stack.size() - 1);
        Screen under = top();
        int wpx = host.getWidth();
        under.root.setVisibility(View.VISIBLE);
        under.root.animate().translationX(0).setDuration(T.DUR).setInterpolator(T.EASE).start();
        s.onHide();
        s.root.animate().translationX(wpx).setDuration(T.DUR).setInterpolator(T.EASE).withEndAction(() -> {
            host.removeView(s.root);
            s.onDestroy();
        }).start();
        under.onShow();
    }

    public void finishSwipe(Screen s) {
        if (stack.size() < 2 || top() != s) return;
        stack.remove(stack.size() - 1);
        Screen under = top();
        under.root.setVisibility(View.VISIBLE);
        under.root.setTranslationX(0);
        s.onHide();
        host.removeView(s.root);
        s.onDestroy();
        under.onShow();
    }

    public void popTo(Screen target) {
        while (stack.size() > 1 && top() != target) {
            Screen s = stack.remove(stack.size() - 1);
            s.onHide();
            host.removeView(s.root);
            s.onDestroy();
        }
        Screen t = top();
        if (t != null) {
            t.root.setVisibility(View.VISIBLE);
            t.root.setTranslationX(0);
            t.onShow();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        Pin.touch();
    }

    @Override
    public void onBackPressed() {
        if (App.overlayBack()) return;
        Screen t = top();
        if (t != null && t.onBack()) return;
        if (stack.size() > 1) {
            pop();
            return;
        }
        moveTaskToBack(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        App.foreground(true);
        if (com.vulpine.core.Session.s != null) App.H.postDelayed(() -> {
            if (!isFinishing() && (!Pin.cachedHasPin() || Pin.unlocked)) Updater.check(this);
        }, 1500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        App.foreground(false);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        App.permResult(code, perms, res);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        App.activityResult(this, req, res, data);
    }
}
