package com.vulpine.at.home;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

public final class StoriesPill {
    static void attach(Home h, FrameLayout left) {
        com.vulpine.at.stories.Stories.attachPill(h, left);
    }

    static View strip(Context c, ChatsTab t) {
        return com.vulpine.at.stories.Stories.strip(c, t);
    }

    static void onScroll(ChatsTab t) {
        com.vulpine.at.stories.Stories.onListScroll(t);
    }

    static boolean pullFirst(ChatsTab t) {
        return com.vulpine.at.stories.Stories.pullFirst(t);
    }

    static void dragStrip(ChatsTab t, float dy) {
        com.vulpine.at.stories.Stories.dragStrip(t, dy);
    }

    static void releaseStrip(ChatsTab t) {
        com.vulpine.at.stories.Stories.releaseStrip(t);
    }
}
