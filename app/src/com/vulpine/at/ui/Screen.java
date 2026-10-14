package com.vulpine.at.ui;

import android.view.View;
import com.vulpine.at.MainActivity;

public abstract class Screen {
    public MainActivity a;
    public View root;
    public boolean hideNav = true;
    public boolean opaque = true;

    public abstract View build();

    public void onShow() {
    }

    public void onHide() {
    }

    public void onDestroy() {
    }

    public boolean onBack() {
        return false;
    }

    public void onInsets(int top, int bottom, int ime) {
    }
}
