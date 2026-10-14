package com.vulpine.at.home;

import android.content.Context;
import android.view.View;
import com.vulpine.at.App;
import com.vulpine.at.Sync;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Svg;
import com.vulpine.at.ui.T;
import com.vulpine.core.Chats;
import com.vulpine.core.Http;
import com.vulpine.core.Session;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SelMenu {
    static android.graphics.drawable.Drawable ic(String... d) {
        return Svg.stroke(18, T.text, 2, d);
    }

    static android.graphics.drawable.Drawable wi(String inner) {
        return com.vulpine.at.ui.Ic.svg("<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">" + inner + "</svg>", 18, T.text);
    }

    static final String I_MUTE = "<polygon points=\"11 5 6 9 2 9 2 15 6 15 11 19 11 5\"/><line x1=\"23\" y1=\"9\" x2=\"17\" y2=\"15\"/><line x1=\"17\" y1=\"9\" x2=\"23\" y2=\"15\"/>";
    static final String I_UNMUTE = "<polygon points=\"11 5 6 9 2 9 2 15 6 15 11 19 11 5\"/><path d=\"M15.54 8.46a5 5 0 0 1 0 7.07\"/><path d=\"M19.07 4.93a10 10 0 0 1 0 14.14\"/>";
    static final String I_ARCH = "<polyline points=\"21 8 21 21 3 21 3 8\"/><rect x=\"1\" y=\"3\" width=\"22\" height=\"5\"/><line x1=\"10\" y1=\"12\" x2=\"14\" y2=\"12\"/>";
    static final String I_DEL = "<polyline points=\"3 6 5 6 21 6\"/><path d=\"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2\"/>";
    static final String I_PIN = "<line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"22\"/><path d=\"M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z\"/>";
    static final String I_FOLDER = "<path d=\"M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z\"/>";
    static final String I_READ = "<path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"/><polyline points=\"22 4 12 14.01 9 11.01\"/>";
    static final String I_UNREAD = "<path d=\"M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9\"/><path d=\"M13.73 21a2 2 0 0 1-3.46 0\"/>";
    static final String I_BLOCK = "<circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"4.93\" y1=\"4.93\" x2=\"19.07\" y2=\"19.07\"/>";
    static final String I_UNBLOCK = "<circle cx=\"12\" cy=\"12\" r=\"10\"/><path d=\"M8 12h8\"/>";
    static final String I_LEAVE = "<path d=\"M9 21H5V3h4\"/><path d=\"M16 17l5-5-5-5\"/><path d=\"M21 12H9\"/>";
    static final String I_TIMER = "<circle cx=\"12\" cy=\"13\" r=\"8\"/><path d=\"M12 9v4l2.5 2.5\"/><path d=\"M9 2h6\"/>";
    static final String I_LOCK = "<rect x=\"4\" y=\"11\" width=\"16\" height=\"10\" rx=\"2\"/><path d=\"M8 11V7a4 4 0 0 1 8 0v4\"/>";
    static final String I_CHECK = "<polyline points=\"20 6 9 17 4 12\"/>";

    static void vcf(Context c, String msg, Runnable yes) {
        android.widget.FrameLayout h = Ov.host();
        if (h == null) return;
        android.widget.FrameLayout ov = new android.widget.FrameLayout(c);
        ov.setBackgroundColor(0x80000000);
        ov.setClickable(true);
        ov.setPadding(T.dp(24), T.dp(24), T.dp(24), T.dp(24));
        android.widget.LinearLayout card = com.vulpine.at.ui.W.col(c);
        card.setBackground(com.vulpine.at.ui.W.round(T.surface, 16));
        card.setPadding(T.dp(20), T.dp(20), T.dp(20), T.dp(20));
        card.setClickable(true);
        android.widget.TextView p = com.vulpine.at.ui.W.text(c, msg, 14, 400, T.text);
        com.vulpine.at.ui.W.lines(p, 21);
        card.addView(p, com.vulpine.at.ui.W.lpm(com.vulpine.at.ui.W.MP, com.vulpine.at.ui.W.WC, 0, 0, 0, 16));
        android.widget.LinearLayout row = com.vulpine.at.ui.W.row(c);
        Runnable[] close = new Runnable[1];
        android.widget.TextView no = btn2(c, "Cancel", T.mixA(T.text, .09f), T.text);
        android.widget.TextView ok = btn2(c, "Delete", T.danger, 0xffffffff);
        row.addView(no, com.vulpine.at.ui.W.lp(0, com.vulpine.at.ui.W.WC, 1));
        android.widget.LinearLayout.LayoutParams okp = com.vulpine.at.ui.W.lp(0, com.vulpine.at.ui.W.WC, 1);
        okp.leftMargin = T.dp(10);
        row.addView(ok, okp);
        card.addView(row);
        android.widget.FrameLayout.LayoutParams cp = new android.widget.FrameLayout.LayoutParams(Math.min(T.dp(340), (h.getWidth() > 0 ? h.getWidth() : c.getResources().getDisplayMetrics().widthPixels) - T.dp(48)), com.vulpine.at.ui.W.WC, android.view.Gravity.CENTER);
        ov.addView(card, cp);
        h.addView(ov, new android.widget.FrameLayout.LayoutParams(com.vulpine.at.ui.W.MP, com.vulpine.at.ui.W.MP));
        ov.setAlpha(0);
        ov.animate().alpha(1).setDuration(240).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
        card.setAlpha(0);
        card.setTranslationY(T.dp(14));
        card.setScaleX(.96f);
        card.setScaleY(.96f);
        card.animate().alpha(1).translationY(0).scaleX(1).scaleY(1).setDuration(300).setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .25f, 1f)).start();
        close[0] = () -> {
            Ov.remove(ov);
            ov.animate().alpha(0).setDuration(200).withEndAction(() -> h.removeView(ov)).start();
            card.animate().alpha(0).translationY(T.dp(10)).scaleX(.97f).scaleY(.97f).setDuration(200).setInterpolator(T.EASE_IN).start();
        };
        no.setOnClickListener(v -> close[0].run());
        ok.setOnClickListener(v -> {
            close[0].run();
            yes.run();
        });
        Ov.push(ov, close[0]);
    }

    static android.widget.TextView btn2(Context c, String label, int bg, int fg) {
        android.widget.TextView t = com.vulpine.at.ui.W.text(c, label, 14, 700, fg);
        T.lh(t, 21);
        t.setPadding(T.dp(12), t.getPaddingTop() + T.dp(11), T.dp(12), t.getPaddingBottom() + T.dp(11));
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackground(com.vulpine.at.ui.W.round(bg, 12));
        return t;
    }

    public static final long[] TIMER_MS = {0, 600000L, 3600000L, 86400000L, 604800000L, 2592000000L};
    public static final String[] TIMER_LABEL = {"Off", "10 minutes", "1 hour", "1 day", "1 week", "1 month"};

    public static String longTimer(long ms) {
        for (int i = TIMER_MS.length - 1; i > 0; i--) if (ms >= TIMER_MS[i]) return TIMER_LABEL[i];
        return "Off";
    }

    static void show(ChatsTab t, View anchor) {
        Context c = anchor.getContext();
        List<String> ids = t.selectedIds();
        List<Chats.Conv> cs = new ArrayList<>();
        for (String id : ids) if (Chats.I.conv(id) != null) cs.add(Chats.I.conv(id));
        if (cs.isEmpty()) {
            Ov.toast("No selectable chats");
            return;
        }
        JSONObject s = Session.settings();
        boolean allMuted = true, allPinned = true, anyGroup = false, anyUnread = false, anyInFolder = false;
        for (Chats.Conv x : cs) {
            if (!Sync.contains(s.optJSONArray("mutedChatIds"), x.id)) allMuted = false;
            if (!Sync.contains(s.optJSONArray("pinnedChatIds"), x.id)) allPinned = false;
            if (x.group()) anyGroup = true;
            if (Sync.unread().optInt(x.id) > 0) anyUnread = true;
            JSONArray f = s.optJSONArray("chatFolders");
            if (f != null) for (int i = 0; i < f.length(); i++) if (Sync.contains(f.optJSONObject(i).optJSONArray("pinnedChatIds"), x.id)) anyInFolder = true;
        }
        Ov.Menu m = new Ov.Menu(c).dd();
        final boolean muted = allMuted, pinned = allPinned, unread = anyUnread;
        m.item(muted ? "Unmute chats" : "Mute chats", wi(muted ? I_UNMUTE : I_MUTE), false, () -> {
            for (Chats.Conv x : cs) Sync.toggle("mutedChatIds", x.id, !muted);
            done(t);
        });
        m.item("Archive chats", wi(I_ARCH), false, () -> {
            for (Chats.Conv x : cs) Sync.toggle("hiddenChatIds", x.id, true);
            done(t);
        });
        if (anyGroup) m.item("Leave groups", wi(I_LEAVE), false, () -> {
            List<Chats.Conv> gs = new ArrayList<>();
            for (Chats.Conv x : cs) if (x.group()) gs.add(x);
            vcf(c, "Leave " + gs.size() + " group" + (gs.size() == 1 ? "" : "s") + "? You will need an invite to rejoin.", () -> App.bg(() -> {
                for (Chats.Conv x : gs) Http.delete("/conversations/" + x.id + "/leave", null);
                App.ui(() -> {
                    for (Chats.Conv x : gs) {
                        Chats.I.order.remove(x.id);
                        Chats.I.convs.remove(x.id);
                    }
                    done(t);
                });
            }));
        });
        final boolean grp = anyGroup;
        m.item("Delete chats", wi(I_DEL), false, () -> vcf(c, "Delete " + cs.size() + " chat" + (cs.size() == 1 ? "" : "s") + "?" + (grp ? " Groups will be left." : ""), () -> App.bg(() -> {
            for (Chats.Conv x : cs) {
                if (x.group()) Http.delete("/conversations/" + x.id + "/leave", null);
                else Http.delete("/conversations/" + x.id + "/messages", null);
            }
            App.ui(() -> {
                for (Chats.Conv x : cs) {
                    Sync.toggle("hiddenChatIds", x.id, true);
                    Sync.unread().remove(x.id);
                    Chats.I.list(x.id).clear();
                    App.kv.remove("vulpine.msgs." + x.id);
                    if (x.group()) {
                        Chats.I.order.remove(x.id);
                        Chats.I.convs.remove(x.id);
                    }
                }
                done(t);
            });
        })));
        m.sep();
        m.item(pinned ? "Unpin chats" : "Pin chats", wi(I_PIN), false, () -> {
            for (Chats.Conv x : cs) Sync.toggle("pinnedChatIds", x.id, !pinned);
            done(t);
        });
        JSONArray folders = s.optJSONArray("chatFolders");
        if (folders != null) for (int i = 0; i < folders.length(); i++) {
            JSONObject f = folders.optJSONObject(i);
            m.item("Add to " + f.optString("name"), wi(I_FOLDER), false, () -> {
                try {
                    JSONArray inc = f.optJSONArray("pinnedChatIds");
                    if (inc == null) {
                        inc = new JSONArray();
                        f.put("pinnedChatIds", inc);
                    }
                    for (Chats.Conv x : cs) if (!Sync.contains(inc, x.id)) inc.put(x.id);
                    JSONArray ex = f.optJSONArray("excludedChatIds");
                    if (ex != null) {
                        JSONArray n = new JSONArray();
                        for (int k = 0; k < ex.length(); k++) if (!ids.contains(ex.optString(k))) n.put(ex.optString(k));
                        f.put("excludedChatIds", n);
                    }
                } catch (Exception e) {
                }
                Sync.save();
                done(t);
            });
        }
        if (anyInFolder) m.item("Remove from folders", wi(I_FOLDER), false, () -> {
            JSONArray fs = Session.settings().optJSONArray("chatFolders");
            if (fs != null) for (int i = 0; i < fs.length(); i++) {
                JSONObject f = fs.optJSONObject(i);
                for (String key : new String[]{"pinnedChatIds", "pins"}) {
                    JSONArray a = f.optJSONArray(key);
                    if (a == null) continue;
                    JSONArray n = new JSONArray();
                    for (int k = 0; k < a.length(); k++) if (!ids.contains(a.optString(k))) n.put(a.optString(k));
                    try {
                        f.put(key, n);
                    } catch (Exception e) {
                    }
                }
            }
            Sync.save();
            done(t);
        });
        m.sep();
        if (unread) m.item("Mark as read", wi(I_READ), false, () -> {
            for (Chats.Conv x : cs) {
                Sync.unread().remove(x.id);
                App.bg(() -> Http.post("/conversations/" + x.id + "/read", null));
            }
            Sync.save();
            done(t);
        });
        else m.item("Mark as unread", wi(I_UNREAD), false, () -> {
            for (Chats.Conv x : cs) try {
                Sync.unread().put(x.id, 1);
            } catch (Exception e) {
            }
            Sync.save();
            done(t);
        });
        if (cs.size() == 1) {
            Chats.Conv x = cs.get(0);
            JSONObject o = x.o;
            boolean pend = o.optBoolean("pendingRequest");
            boolean can = !x.group() || o.optBoolean("canAutoDelete");
            if (!pend && can) {
                m.sep();
                long ms = o.optLong("autoDeleteMs");
                String by = o.optString("autoDeleteBy", "");
                boolean locked = !x.group() && ms > 0 && "default".equals(o.optString("autoDeleteSrc")) && !by.isEmpty() && !"null".equals(by) && !by.equals(Session.userId());
                if (locked) {
                    String name = o.optString("peerDisplayName");
                    m.item("Auto-delete " + ChatRow.shortTimer(ms) + " (set by " + name + ")", wi(I_TIMER), false, () -> Ov.toast("Only " + name + " can change this timer", true));
                } else m.item("Auto-delete", wi(I_TIMER), false, () -> anchor.post(() -> timerMenu(t, anchor, x)));
            }
            if (!x.group() && !o.optString("peerId").isEmpty() && !"_deleted".equals(o.optString("peerHandle")) && !Badges.isBot(o.optString("peerHandle")))
                m.item("Open secret chat", wi(I_LOCK), false, () -> {
                    t.exitSelect();
                    com.vulpine.at.secret.Secret.openOrCreate(x);
                });
        }
        List<Chats.Conv> dms = new ArrayList<>();
        for (Chats.Conv x : cs) if (!x.group()) dms.add(x);
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        if (!dms.isEmpty()) {
            App.bg(() -> {
                Http.Resp r = Http.get("/me/blocked");
                java.util.Set<String> blocked = new java.util.HashSet<>();
                Object any = r.any();
                JSONArray arr = any instanceof JSONArray ? (JSONArray) any : r.obj().optJSONArray("blocked");
                if (arr != null) for (int i = 0; i < arr.length(); i++) {
                    Object e = arr.opt(i);
                    if (e instanceof JSONObject) blocked.add(((JSONObject) e).optString("handle"));
                    else blocked.add(String.valueOf(e));
                }
                App.ui(() -> {
                    boolean anyBlocked = false;
                    for (Chats.Conv x : dms) if (blocked.contains(x.o.optString("peerHandle").toLowerCase()) || blocked.contains(x.o.optString("peerHandle"))) anyBlocked = true;
                    final boolean ub = anyBlocked;
                    m.item(ub ? "Unblock users" : "Block users", wi(ub ? I_UNBLOCK : I_BLOCK), false, () -> App.bg(() -> {
                        for (Chats.Conv x : dms) {
                            String h = x.o.optString("peerHandle");
                            if ("_deleted".equals(h)) continue;
                            if (ub) Http.delete("/users/" + h + "/block", null);
                            else Http.post("/users/" + h + "/block", null);
                        }
                        App.ui(() -> {
                            Ov.toast(ub ? "User unblocked" : "User blocked - they now see you as a deleted account");
                            t.exitSelect();
                            App.bg(() -> Chats.I.loadList(0));
                        });
                    }));
                    m.showAt(l[0] + anchor.getWidth(), l[1] + anchor.getHeight() + T.dp(6), true);
                });
            });
        } else m.showAt(l[0] + anchor.getWidth(), l[1] + anchor.getHeight() + T.dp(6), true);
    }

    static void timerMenu(ChatsTab t, View anchor, Chats.Conv x) {
        Ov.Menu m = new Ov.Menu(anchor.getContext()).dd();
        long cur = x.o.optLong("autoDeleteMs");
        for (int i = 0; i < TIMER_MS.length; i++) {
            long ms = TIMER_MS[i];
            boolean on = ms == cur;
            m.item(TIMER_LABEL[i], wi(on ? I_CHECK : I_TIMER), false, () -> {
                t.exitSelect();
                if (ms != cur) setTimer(x.id, ms);
            });
        }
        int[] l = new int[2];
        anchor.getLocationInWindow(l);
        m.showAt(l[0] + anchor.getWidth(), l[1] + anchor.getHeight() + T.dp(6), true);
    }

    public static void setTimer(String cid, long ms) {
        App.bg(() -> {
            Http.Resp r = Http.post("/conversations/" + cid + "/auto-delete", App.jo("ms", ms));
            App.ui(() -> {
                if (r.ok()) {
                    Chats.Conv c = Chats.I.conv(cid);
                    if (c != null) try {
                        c.o.put("autoDeleteMs", ms);
                        c.o.put("autoDeleteBy", Session.userId());
                        c.o.put("autoDeleteSrc", "manual");
                    } catch (Exception e) {
                    }
                    Ov.toast(ms > 0 ? "Messages will auto-delete after " + longTimer(ms) : "Auto-delete is off");
                    if (Home.I != null) for (Home.Tab tb : Home.I.tabs.values()) if (tb instanceof ChatsTab) ((ChatsTab) tb).refresh();
                } else {
                    String e = r.error();
                    if (e.contains("not_owner") || e.contains("set_by")) Ov.toast("Only the person who set this timer can change it", true);
                    else if (r.status == 403) Ov.toast("Only admins can change auto-delete in this group", true);
                    else Ov.toast("Could not change auto-delete", true);
                }
            });
        });
    }

    static void done(ChatsTab t) {
        Sync.save();
        Ov.toast("Done");
        t.exitSelect();
    }
}
