package com.vulpine.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class WsConn {
    public static final int MAX_FRAME = 16 * 1024 * 1024;
    private final Socket sock;
    private final InputStream in;
    private final OutputStream out;
    private final SecureRandom rnd = new SecureRandom();
    public volatile int closeCode = 0;
    private volatile boolean closed;
    private final java.util.concurrent.ExecutorService wr = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ws-write");
        t.setDaemon(true);
        return t;
    });

    private WsConn(Socket s) throws IOException {
        sock = s;
        in = s.getInputStream();
        out = s.getOutputStream();
    }

    public static WsConn connect(String url, String origin, String ua) throws IOException {
        Http.guard("WS " + url);
        URI u = URI.create(url);
        boolean tls = "wss".equalsIgnoreCase(u.getScheme());
        String host = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : (tls ? 443 : 80);
        Socket raw = new Socket();
        raw.setTcpNoDelay(true);
        raw.connect(new InetSocketAddress(host, port), 15000);
        Socket s = raw;
        if (tls) {
            SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket ss = (SSLSocket) f.createSocket(raw, host, port, true);
            ss.setSoTimeout(20000);
            SSLParameters sp = ss.getSSLParameters();
            sp.setEndpointIdentificationAlgorithm("HTTPS");
            ss.setSSLParameters(sp);
            ss.startHandshake();
            if (Platform.isAndroid() && !HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ss.getSession())) {
                ss.close();
                throw new IOException("hostname mismatch");
            }
            s = ss;
        }
        s.setSoTimeout(20000);
        WsConn ws = new WsConn(s);
        byte[] k = new byte[16];
        ws.rnd.nextBytes(k);
        String key = Base64.getEncoder().encodeToString(k);
        String path = (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath()) + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
        String hostHdr = host + (u.getPort() > 0 ? ":" + u.getPort() : "");
        String req = "GET " + path + " HTTP/1.1\r\nHost: " + hostHdr + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key
                + "\r\nSec-WebSocket-Version: 13\r\nOrigin: " + origin + "\r\nUser-Agent: " + ua + "\r\n\r\n";
        ws.out.write(req.getBytes(StandardCharsets.UTF_8));
        ws.out.flush();
        String status = ws.readLine();
        if (status == null || !status.startsWith("HTTP/1.1 101")) {
            s.close();
            throw new IOException("upgrade failed: " + status);
        }
        String line;
        while ((line = ws.readLine()) != null && !line.isEmpty()) {
        }
        s.setSoTimeout(75000);
        return ws;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) throw new IOException("header too long");
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private void readFully(byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("eof");
            off += n;
        }
    }

    public void sendFrame(int opcode, byte[] data) throws IOException {
        if (closed) throw new IOException("closed");
        ByteArrayOutputStream b = new ByteArrayOutputStream(data.length + 14);
        b.write(0x80 | opcode);
        int n = data.length;
        if (n < 126) b.write(0x80 | n);
        else if (n < 65536) {
            b.write(0x80 | 126);
            b.write(n >> 8);
            b.write(n & 0xff);
        } else {
            b.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) b.write(i >= 4 ? 0 : (n >> (8 * i)) & 0xff);
        }
        byte[] mask = new byte[4];
        rnd.nextBytes(mask);
        b.write(mask);
        for (int i = 0; i < n; i++) b.write(data[i] ^ mask[i & 3]);
        byte[] frame = b.toByteArray();
        try {
            wr.execute(() -> {
                if (closed) return;
                try {
                    out.write(frame);
                    out.flush();
                } catch (IOException e) {
                    close();
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            throw new IOException("closed");
        }
    }

    public void sendText(String s) throws IOException {
        sendFrame(1, s.getBytes(StandardCharsets.UTF_8));
    }

    public String readText() throws IOException {
        ByteArrayOutputStream msg = null;
        while (true) {
            byte[] h = new byte[2];
            readFully(h);
            boolean fin = (h[0] & 0x80) != 0;
            int op = h[0] & 0x0f;
            long len = h[1] & 0x7f;
            if (len == 126) {
                byte[] e = new byte[2];
                readFully(e);
                len = ((e[0] & 0xff) << 8) | (e[1] & 0xff);
            } else if (len == 127) {
                byte[] e = new byte[8];
                readFully(e);
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | (e[i] & 0xff);
            }
            if (len < 0 || len > MAX_FRAME) throw new IOException("frame too large");
            byte[] mask = null;
            if ((h[1] & 0x80) != 0) {
                mask = new byte[4];
                readFully(mask);
            }
            byte[] data = new byte[(int) len];
            readFully(data);
            if (mask != null) for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
            if (op == 9) {
                sendFrame(10, data);
                continue;
            }
            if (op == 10) continue;
            if (op == 8) {
                closeCode = data.length >= 2 ? ((data[0] & 0xff) << 8) | (data[1] & 0xff) : 1005;
                try {
                    sendFrame(8, new byte[0]);
                } catch (IOException e) {
                }
                throw new IOException("closed " + closeCode);
            }
            if (op == 1 || op == 2 || op == 0) {
                if (msg == null) msg = new ByteArrayOutputStream();
                msg.write(data);
                if (msg.size() > MAX_FRAME) throw new IOException("message too large");
                if (fin) return new String(msg.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        wr.shutdown();
        Thread t = new Thread(() -> {
            try {
                sock.close();
            } catch (IOException e) {
            }
        }, "ws-close");
        t.setDaemon(true);
        t.start();
    }
}
