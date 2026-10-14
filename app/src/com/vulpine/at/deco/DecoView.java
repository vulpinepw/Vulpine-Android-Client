package com.vulpine.at.deco;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.View;
import com.vulpine.at.Prefs;
import com.vulpine.at.ui.T;

public class DecoView extends View {
    public final String kind;
    String uid, itemId;
    DecoItem it;
    boolean live = true;
    int avatarPx;
    float radius = 16;
    long t0 = SystemClock.uptimeMillis();
    Bitmap still;
    String stillKey;
    final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
    static final LruCache<String, Bitmap> stills = new LruCache<String, Bitmap>(12 << 20) {
        protected int sizeOf(String k, Bitmap v) {
            return v.getByteCount();
        }
    };
    public static int FRAME_MS = 33;

    final Deco.L sub = this::refresh;

    public DecoView(Context c, String kind) {
        super(c);
        this.kind = kind;
        setWillNotDraw(false);
    }

    public void setUser(String uid, boolean live) {
        if (this.uid != null && !this.uid.equals(uid)) Deco.unsubscribe(this.uid, sub);
        this.uid = uid;
        this.live = live;
        if (uid != null && isAttachedToWindow()) Deco.subscribe(uid, sub);
        refresh();
    }

    public void setItem(String id, boolean live) {
        this.uid = null;
        this.live = live;
        setId0(id);
    }

    void refresh() {
        setId0(uid == null ? null : Deco.itemFor(uid, kind));
    }

    void setId0(String id) {
        if (id == null ? itemId == null : id.equals(itemId)) {
            if (it == null && id != null) load(id);
            return;
        }
        itemId = id;
        it = null;
        still = null;
        if (id == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        if ("frame".equals(kind)) roomFor(id);
        load(id);
    }

    void roomFor(String id) {
        if (!(getParent() instanceof android.view.ViewGroup)) {
            post(() -> {
                if (id.equals(itemId) && getParent() instanceof android.view.ViewGroup) roomFor(id);
            });
            return;
        }
        android.view.ViewGroup card = (android.view.ViewGroup) getParent();
        card.setClipToOutline(false);
        card.setClipChildren(false);
        if (card.getTag(Deco.ROOM_TAG) instanceof Deco.Room) {
            ((Deco.Room) card.getTag(Deco.ROOM_TAG)).on(id);
            return;
        }
        if (card.getTag(0x7f0d0e01) != null) return;
        android.view.ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams m = (android.view.ViewGroup.MarginLayoutParams) lp;
            int[] r = Deco.frameRoom(id);
            card.setTag(0x7f0d0e01, Boolean.TRUE);
            m.topMargin = Math.max(m.topMargin, T.dp(r[1]));
            m.bottomMargin = Math.max(m.bottomMargin, T.dp(r[3]));
            m.leftMargin = Math.max(m.leftMargin, T.dp(Math.max(0, r[0] - 16)));
            m.rightMargin = Math.max(m.rightMargin, T.dp(Math.max(0, r[2] - 16)));
            card.setLayoutParams(m);
        }
    }

    void load(String id) {
        boolean small = "avatar".equals(kind) && !live;
        DecoItem c = Deco.peekItem(id, small);
        if (c != null) {
            it = c;
            t0 = SystemClock.uptimeMillis();
            invalidate();
            return;
        }
        Deco.item(id, small, x -> {
            if (!id.equals(itemId)) return;
            it = x;
            t0 = SystemClock.uptimeMillis();
            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (uid != null) {
            Deco.subscribe(uid, sub);
            refresh();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (uid != null) Deco.unsubscribe(uid, sub);
    }

    boolean frozen() {
        return !live || Prefs.powerSaving();
    }

    @Override
    protected void onDraw(Canvas c) {
        DecoItem x = it;
        if (x == null) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float d = T.density;
        if (frozen()) {
            String key = x.id + "|" + w + "x" + h + "|" + kind;
            if (still == null || !key.equals(stillKey)) {
                still = stills.get(key);
                if (still == null && "avatar".equals(kind)) {
                    still = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    paint(new Canvas(still), x, 0, w, h, d);
                    stills.put(key, still);
                }
                stillKey = key;
            }
            if (still != null) c.drawBitmap(still, 0, 0, bp);
            else paint(c, x, 0, w, h, d);
            return;
        }
        float t = SystemClock.uptimeMillis() - t0;
        paint(c, x, t, w, h, d);
        postInvalidateDelayed(FRAME_MS);
    }

    void paint(Canvas c, DecoItem x, float t, int w, int h, float d) {
        if ("avatar".equals(kind)) {
            float a = w / 1.5f;
            x.draw(c, t, 104, 104, a * .25f, a * .25f, a / 104f);
        } else x.draw(c, t, w / d, h / d, 0, 0, d);
    }
}
