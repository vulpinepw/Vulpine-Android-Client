package com.vulpine.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONObject;

public final class Rt {
    public interface Listener {
        void on(JSONObject m);
    }

    public interface StatusListener {
        void on(String status);
    }

    public static final Rt I = new Rt();
    public volatile String url = "wss://vulpine.pw/ws";
    public volatile boolean background;
    private final List<Listener> ls = new CopyOnWriteArrayList<>();
    private final List<StatusListener> sls = new CopyOnWriteArrayList<>();
    private final List<JSONObject> queue = new ArrayList<>();
    private final Map<String, Object[]> pend = new LinkedHashMap<>();
    private volatile WsConn conn;
    private volatile boolean authed, running, sawPong;
    private volatile long lastRx, pingAt;
    private volatile String status = "idle";
    private int tries, authFails;
    private Thread th, hb;
    private final Object wake = new Object();
    public volatile boolean foregroundHint = true;
    public volatile boolean callHint;

    public void on(Listener l) {
        ls.add(l);
    }

    public void off(Listener l) {
        ls.remove(l);
    }

    public void onStatus(StatusListener l) {
        sls.add(l);
    }

    public String status() {
        return status;
    }

    public boolean authed() {
        return authed;
    }

    private void setStatus(String s) {
        status = s;
        for (StatusListener l : sls) try {
            l.on(s);
        } catch (Exception e) {
        }
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        th = new Thread(this::loop, "vrt");
        th.setDaemon(true);
        th.start();
        hb = new Thread(this::heart, "vrt-hb");
        hb.setDaemon(true);
        hb.start();
    }

    public synchronized void stop() {
        running = false;
        WsConn c = conn;
        if (c != null) c.close();
        synchronized (queue) {
            queue.clear();
            pend.clear();
        }
        synchronized (wake) {
            wake.notifyAll();
        }
    }

    public void wakeUp(boolean force) {
        if (force) authFails = 0;
        WsConn c = conn;
        if (c == null) synchronized (wake) {
            wake.notifyAll();
        }
        else ping(true);
    }

    public boolean send(JSONObject m) {
        String t = m.optString("type");
        WsConn c = conn;
        if (c != null && authed) {
            if ("message.send".equals(t)) synchronized (queue) {
                pend.put(m.optString("tempId"), new Object[]{m, System.currentTimeMillis()});
            }
            try {
                c.sendText(m.toString());
                return true;
            } catch (Exception e) {
                if ("message.send".equals(t)) return true;
                return false;
            }
        }
        if ("message.send".equals(t) || c != null) {
            synchronized (queue) {
                queue.add(m);
            }
            return true;
        }
        return false;
    }

    public boolean retry(String tempId) {
        Object[] p;
        synchronized (queue) {
            p = pend.remove(tempId);
        }
        return p != null && send((JSONObject) p[0]);
    }

    private void emit(JSONObject m) {
        for (Listener l : ls) try {
            l.on(m);
        } catch (Exception e) {
        }
    }

    private void ping(boolean force) {
        WsConn c = conn;
        if (c == null || !authed || pingAt != 0) return;
        if (!force && System.currentTimeMillis() - lastRx < 20000) return;
        pingAt = System.currentTimeMillis();
        try {
            c.sendText("{\"type\":\"ping\"}");
        } catch (Exception e) {
            c.close();
        }
    }

    private void heart() {
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.currentTimeMillis();
            WsConn c = conn;
            if (c != null && pingAt != 0 && now - pingAt > 9000 && lastRx < pingAt && sawPong) {
                pingAt = 0;
                c.close();
                continue;
            }
            if (now % 5000 < 1000 && (foregroundHint || callHint || background)) ping(false);
            List<String> failed = new ArrayList<>();
            synchronized (queue) {
                if (authed) for (Iterator<Map.Entry<String, Object[]>> it = pend.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, Object[]> e = it.next();
                    if (now - (Long) e.getValue()[1] > 15000 && e.getValue().length == 2) {
                        failed.add(e.getKey());
                        e.setValue(new Object[]{e.getValue()[0], e.getValue()[1], true});
                    }
                }
            }
            for (String f : failed) {
                try {
                    JSONObject m = (JSONObject) pend.get(f)[0];
                    emit(new JSONObject().put("type", "local.outbox").put("tempId", f).put("state", "failed").put("conversationId", m.optString("conversationId")));
                } catch (Exception e) {
                }
            }
        }
    }

    private void loop() {
        while (running) {
            String tok = Http.token;
            long delay;
            if (tok == null) {
                delay = 2000;
            } else {
                int code = 0;
                try {
                    setStatus("connecting");
                    WsConn c = WsConn.connect(url, Http.origin, Http.userAgent);
                    conn = c;
                    JSONObject a = new JSONObject().put("type", "auth").put("token", tok);
                    if (background) a.put("bg", true);
                    c.sendText(a.toString());
                    while (running) {
                        String s = c.readText();
                        lastRx = System.currentTimeMillis();
                        pingAt = 0;
                        JSONObject m;
                        try {
                            m = new JSONObject(s);
                        } catch (Exception e) {
                            continue;
                        }
                        String t = m.optString("type");
                        if ("auth.ok".equals(t)) {
                            authed = true;
                            tries = 0;
                            authFails = 0;
                            setStatus("authed");
                            List<JSONObject> q;
                            synchronized (queue) {
                                q = new ArrayList<>(queue);
                                queue.clear();
                            }
                            for (JSONObject x : q) send(x);
                        } else if ("pong".equals(t)) {
                            sawPong = true;
                            continue;
                        } else if ("message.ack".equals(t)) {
                            synchronized (queue) {
                                pend.remove(m.optString("tempId"));
                            }
                        }
                        emit(m);
                    }
                } catch (Exception e) {
                } finally {
                    WsConn c = conn;
                    if (c != null) {
                        code = c.closeCode;
                        c.close();
                    }
                    conn = null;
                    authed = false;
                    pingAt = 0;
                    if (running) setStatus("closed");
                }
                if (code == 4001 || code == 4003) {
                    authFails++;
                    emit(new JSONObject(java.util.Collections.singletonMap("type", "local.authlost")));
                    if (authFails >= 6) {
                        setStatus("error");
                        delay = 60000;
                    } else delay = Math.min(30000, 1000L << authFails);
                } else if (code == 4009 || code == 4010) delay = 15000;
                else {
                    delay = tries == 0 ? 250 : Math.min(8000, 1000L << Math.min(tries, 4));
                    tries++;
                }
                delay = (long) (delay * (0.8 + Math.random() * 0.4));
            }
            if (!running) break;
            synchronized (wake) {
                try {
                    wake.wait(delay);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
        setStatus("idle");
    }
}
