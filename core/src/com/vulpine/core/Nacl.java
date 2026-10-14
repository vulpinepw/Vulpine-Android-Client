package com.vulpine.core;

import java.security.MessageDigest;

final class Nacl {
    private static long[] gf() {
        return new long[16];
    }

    private static long[] gf(long[] init) {
        long[] r = new long[16];
        System.arraycopy(init, 0, r, 0, init.length);
        return r;
    }

    private static final long[] GF0 = gf();
    private static final long[] GF1 = gf(new long[]{1});
    private static final long[] D = gf(new long[]{0x78a3, 0x1359, 0x4dca, 0x75eb, 0xd8ab, 0x4141, 0x0a4d, 0x0070, 0xe898, 0x7779, 0x4079, 0x8cc7, 0xfe73, 0x2b6f, 0x6cee, 0x5203});
    private static final long[] D2 = gf(new long[]{0xf159, 0x26b2, 0x9b94, 0xebd6, 0xb156, 0x8283, 0x149a, 0x00e0, 0xd130, 0xeef3, 0x80f2, 0x198e, 0xfce7, 0x56df, 0xd9dc, 0x2406});
    private static final long[] X = gf(new long[]{0xd51a, 0x8f25, 0x2d60, 0xc956, 0xa7b2, 0x9525, 0xc760, 0x692c, 0xdc5c, 0xfdd6, 0xe231, 0xc0a4, 0x53fe, 0xcd6e, 0x36d3, 0x2169});
    private static final long[] Y = gf(new long[]{0x6658, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666});
    private static final long[] I = gf(new long[]{0xa0b0, 0x4a0e, 0x1b27, 0xc4ee, 0xe478, 0xad2f, 0x1806, 0x2f43, 0xd7a7, 0x3dfb, 0x0099, 0x2b4d, 0xdf0b, 0x4fc1, 0x2480, 0x2b83});
    private static final long[] L = {0xed, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58, 0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10};
    private static final long[] A121665 = gf(new long[]{0xdb41, 1});

    private static void set25519(long[] r, long[] a) {
        System.arraycopy(a, 0, r, 0, 16);
    }

    private static void car25519(long[] o) {
        for (int i = 0; i < 16; i++) {
            o[i] += 65536;
            long c = Math.floorDiv(o[i], 65536);
            if (i < 15) o[i + 1] += c - 1;
            else o[0] += 38 * (c - 1);
            o[i] -= c * 65536;
        }
    }

    private static void sel25519(long[] p, long[] q, int b) {
        long c = ~(b - 1);
        for (int i = 0; i < 16; i++) {
            long t = c & (p[i] ^ q[i]);
            p[i] ^= t;
            q[i] ^= t;
        }
    }

    private static void pack25519(byte[] o, long[] n) {
        long[] m = gf(), t = gf();
        set25519(t, n);
        car25519(t);
        car25519(t);
        car25519(t);
        for (int j = 0; j < 2; j++) {
            m[0] = t[0] - 0xffed;
            for (int i = 1; i < 15; i++) {
                m[i] = t[i] - 0xffff - ((m[i - 1] >> 16) & 1);
                m[i - 1] &= 0xffff;
            }
            m[15] = t[15] - 0x7fff - ((m[14] >> 16) & 1);
            int b = (int) ((m[15] >> 16) & 1);
            m[14] &= 0xffff;
            sel25519(t, m, 1 - b);
        }
        for (int i = 0; i < 16; i++) {
            o[2 * i] = (byte) (t[i] & 0xff);
            o[2 * i + 1] = (byte) (t[i] >> 8);
        }
    }

    private static boolean neq25519(long[] a, long[] b) {
        byte[] c = new byte[32], d = new byte[32];
        pack25519(c, a);
        pack25519(d, b);
        return !MessageDigest.isEqual(c, d);
    }

    private static int par25519(long[] a) {
        byte[] d = new byte[32];
        pack25519(d, a);
        return d[0] & 1;
    }

    private static void unpack25519(long[] o, byte[] n) {
        for (int i = 0; i < 16; i++) o[i] = (n[2 * i] & 0xff) + ((long) (n[2 * i + 1] & 0xff) << 8);
        o[15] &= 0x7fff;
    }

    private static void A(long[] o, long[] a, long[] b) {
        for (int i = 0; i < 16; i++) o[i] = a[i] + b[i];
    }

    private static void Z(long[] o, long[] a, long[] b) {
        for (int i = 0; i < 16; i++) o[i] = a[i] - b[i];
    }

    private static void M(long[] o, long[] a, long[] b) {
        long[] t = new long[31];
        for (int i = 0; i < 16; i++) for (int j = 0; j < 16; j++) t[i + j] += a[i] * b[j];
        for (int i = 0; i < 15; i++) t[i] += 38 * t[i + 16];
        System.arraycopy(t, 0, o, 0, 16);
        car25519(o);
        car25519(o);
    }

    private static void S(long[] o, long[] a) {
        M(o, a, a);
    }

    private static void inv25519(long[] o, long[] i) {
        long[] c = gf();
        set25519(c, i);
        for (int a = 253; a >= 0; a--) {
            S(c, c);
            if (a != 2 && a != 4) M(c, c, i);
        }
        set25519(o, c);
    }

    private static void pow2523(long[] o, long[] i) {
        long[] c = gf();
        set25519(c, i);
        for (int a = 250; a >= 0; a--) {
            S(c, c);
            if (a != 1) M(c, c, i);
        }
        set25519(o, c);
    }

    static byte[] scalarMult(byte[] n, byte[] p) {
        byte[] z = new byte[32];
        System.arraycopy(n, 0, z, 0, 32);
        z[31] = (byte) ((z[31] & 127) | 64);
        z[0] &= (byte) 248;
        long[] x = new long[80];
        long[] a = gf(), b = gf(), c = gf(), d = gf(), e = gf(), f = gf();
        unpack25519(x, p);
        for (int i = 0; i < 16; i++) {
            b[i] = x[i];
            d[i] = a[i] = c[i] = 0;
        }
        a[0] = d[0] = 1;
        for (int i = 254; i >= 0; --i) {
            int r = ((z[i >>> 3] & 0xff) >>> (i & 7)) & 1;
            sel25519(a, b, r);
            sel25519(c, d, r);
            A(e, a, c);
            Z(a, a, c);
            A(c, b, d);
            Z(b, b, d);
            S(d, e);
            S(f, a);
            M(a, c, a);
            M(c, b, e);
            A(e, a, c);
            Z(a, a, c);
            S(b, a);
            Z(c, d, f);
            M(a, c, A121665);
            A(a, a, d);
            M(c, c, a);
            M(a, d, f);
            M(d, b, x);
            S(b, e);
            sel25519(a, b, r);
            sel25519(c, d, r);
        }
        long[] x16 = new long[16], x32 = new long[16], x48 = new long[16];
        System.arraycopy(a, 0, x16, 0, 16);
        System.arraycopy(c, 0, x32, 0, 16);
        System.arraycopy(b, 0, x48, 0, 16);
        inv25519(x32, x32);
        M(x16, x16, x32);
        byte[] q = new byte[32];
        pack25519(q, x16);
        return q;
    }

    static byte[] scalarMultBase(byte[] n) {
        byte[] nine = new byte[32];
        nine[0] = 9;
        return scalarMult(n, nine);
    }

    private static void add(long[][] p, long[][] q) {
        long[] a = gf(), b = gf(), c = gf(), d = gf(), e = gf(), f = gf(), g = gf(), h = gf(), t = gf();
        Z(a, p[1], p[0]);
        Z(t, q[1], q[0]);
        M(a, a, t);
        A(b, p[0], p[1]);
        A(t, q[0], q[1]);
        M(b, b, t);
        M(c, p[3], q[3]);
        M(c, c, D2);
        M(d, p[2], q[2]);
        A(d, d, d);
        Z(e, b, a);
        Z(f, d, c);
        A(g, d, c);
        A(h, b, a);
        M(p[0], e, f);
        M(p[1], h, g);
        M(p[2], g, f);
        M(p[3], e, h);
    }

    private static void cswap(long[][] p, long[][] q, int b) {
        for (int i = 0; i < 4; i++) sel25519(p[i], q[i], b);
    }

    private static void pack(byte[] r, long[][] p) {
        long[] tx = gf(), ty = gf(), zi = gf();
        inv25519(zi, p[2]);
        M(tx, p[0], zi);
        M(ty, p[1], zi);
        pack25519(r, ty);
        r[31] ^= (byte) (par25519(tx) << 7);
    }

    private static void scalarmult(long[][] p, long[][] q, byte[] s) {
        set25519(p[0], GF0);
        set25519(p[1], GF1);
        set25519(p[2], GF1);
        set25519(p[3], GF0);
        for (int i = 255; i >= 0; --i) {
            int b = ((s[i / 8] & 0xff) >> (i & 7)) & 1;
            cswap(p, q, b);
            add(q, p);
            add(p, p);
            cswap(p, q, b);
        }
    }

    private static void scalarbase(long[][] p, byte[] s) {
        long[][] q = {gf(), gf(), gf(), gf()};
        set25519(q[0], X);
        set25519(q[1], Y);
        set25519(q[2], GF1);
        M(q[3], X, Y);
        scalarmult(p, q, s);
    }

    private static void modL(byte[] r, long[] x) {
        long carry;
        for (int i = 63; i >= 32; --i) {
            carry = 0;
            int j;
            for (j = i - 32; j < i - 12; ++j) {
                x[j] += carry - 16 * x[i] * L[j - (i - 32)];
                carry = Math.floorDiv(x[j] + 128, 256);
                x[j] -= carry * 256;
            }
            x[j] += carry;
            x[i] = 0;
        }
        carry = 0;
        for (int j = 0; j < 32; j++) {
            x[j] += carry - (x[31] >> 4) * L[j];
            carry = x[j] >> 8;
            x[j] &= 255;
        }
        for (int j = 0; j < 32; j++) x[j] -= carry * L[j];
        for (int i = 0; i < 32; i++) {
            x[i + 1] += x[i] >> 8;
            r[i] = (byte) (x[i] & 255);
        }
    }

    private static void reduce(byte[] r) {
        long[] x = new long[64];
        for (int i = 0; i < 64; i++) x[i] = r[i] & 0xff;
        for (int i = 0; i < 64; i++) r[i] = 0;
        modL(r, x);
    }

    private static byte[] sha512(byte[]... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            for (byte[] p : parts) md.update(p);
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] edPublic(byte[] seed) {
        byte[] d = sha512(seed);
        d[0] &= (byte) 248;
        d[31] &= 127;
        d[31] |= 64;
        long[][] p = {gf(), gf(), gf(), gf()};
        scalarbase(p, d);
        byte[] pk = new byte[32];
        pack(pk, p);
        return pk;
    }

    static byte[] edSign(byte[] msg, byte[] seed) {
        byte[] d = sha512(seed);
        d[0] &= (byte) 248;
        d[31] &= 127;
        d[31] |= 64;
        byte[] pk = edPublic(seed);
        byte[] prefix = new byte[32];
        System.arraycopy(d, 32, prefix, 0, 32);
        byte[] r = sha512(prefix, msg);
        reduce(r);
        long[][] p = {gf(), gf(), gf(), gf()};
        scalarbase(p, r);
        byte[] sm = new byte[64];
        byte[] R = new byte[32];
        pack(R, p);
        System.arraycopy(R, 0, sm, 0, 32);
        byte[] h = sha512(R, pk, msg);
        reduce(h);
        long[] x = new long[64];
        for (int i = 0; i < 32; i++) x[i] = r[i] & 0xff;
        for (int i = 0; i < 32; i++) for (int j = 0; j < 32; j++) x[i + j] += (long) (h[i] & 0xff) * (d[j] & 0xff);
        byte[] s = new byte[32];
        modL(s, x);
        System.arraycopy(s, 0, sm, 32, 32);
        return sm;
    }

    private static boolean unpackneg(long[][] r, byte[] p) {
        long[] t = gf(), chk = gf(), num = gf(), den = gf(), den2 = gf(), den4 = gf(), den6 = gf();
        set25519(r[2], GF1);
        unpack25519(r[1], p);
        S(num, r[1]);
        M(den, num, D);
        Z(num, num, r[2]);
        A(den, r[2], den);
        S(den2, den);
        S(den4, den2);
        M(den6, den4, den2);
        M(t, den6, num);
        M(t, t, den);
        pow2523(t, t);
        M(t, t, num);
        M(t, t, den);
        M(t, t, den);
        M(r[0], t, den);
        S(chk, r[0]);
        M(chk, chk, den);
        if (neq25519(chk, num)) M(r[0], r[0], I);
        S(chk, r[0]);
        M(chk, chk, den);
        if (neq25519(chk, num)) return false;
        if (par25519(r[0]) == ((p[31] & 0xff) >> 7)) Z(r[0], GF0, r[0]);
        M(r[3], r[0], r[1]);
        return true;
    }

    static boolean edVerify(byte[] msg, byte[] sig, byte[] pk) {
        if (sig.length != 64 || pk.length != 32) return false;
        long[][] p = {gf(), gf(), gf(), gf()}, q = {gf(), gf(), gf(), gf()};
        if (!unpackneg(q, pk)) return false;
        byte[] R = new byte[32], s = new byte[32];
        System.arraycopy(sig, 0, R, 0, 32);
        System.arraycopy(sig, 32, s, 0, 32);
        byte[] h = sha512(R, pk, msg);
        reduce(h);
        scalarmult(p, q, h);
        scalarbase(q, s);
        add(p, q);
        byte[] t = new byte[32];
        pack(t, p);
        return MessageDigest.isEqual(t, R);
    }
}
