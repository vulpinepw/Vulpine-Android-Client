package com.vulpine.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

public final class VCrypto {
    public static final SecureRandom RND = new SecureRandom();
    public static String[] words;

    public static final class Identity {
        public String mnemonic = "", signPrivkey, signPubkey, encPrivkey, encPubkey;

        public JSONObject json() {
            try {
                return new JSONObject().put("mnemonic", mnemonic).put("signPrivkey", signPrivkey).put("signPubkey", signPubkey).put("encPrivkey", encPrivkey).put("encPubkey", encPubkey);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static Identity from(JSONObject o) {
            if (o == null || o.optString("signPrivkey").isEmpty() || o.optString("encPrivkey").isEmpty()) return null;
            Identity i = new Identity();
            i.mnemonic = o.optString("mnemonic");
            i.signPrivkey = o.optString("signPrivkey");
            i.signPubkey = o.optString("signPubkey");
            i.encPrivkey = o.optString("encPrivkey");
            i.encPubkey = o.optString("encPubkey");
            return i;
        }
    }

    public static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    public static byte[] unb64(String s) {
        return Base64.getDecoder().decode(s.trim());
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] random(int n) {
        byte[] b = new byte[n];
        RND.nextBytes(b);
        return b;
    }

    public static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] hmac(String alg, byte[] key, byte[]... data) {
        try {
            Mac m = Mac.getInstance(alg);
            m.init(new SecretKeySpec(key.length == 0 ? new byte[1] : key, alg));
            if (key.length == 0) {
                m = Mac.getInstance(alg);
                m.init(new SecretKeySpec(new byte[alg.endsWith("512") ? 64 : 32], alg));
            }
            for (byte[] d : data) m.update(d);
            return m.doFinal();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] hkdf(byte[] ikm, byte[] salt, String info, int len) {
        byte[] prk = hmac("HmacSHA256", salt == null ? new byte[32] : salt, ikm);
        byte[] out = new byte[len];
        byte[] t = new byte[0];
        int pos = 0;
        for (int i = 1; pos < len; i++) {
            t = hmac("HmacSHA256", prk, t, utf8(info), new byte[]{(byte) i});
            int n = Math.min(t.length, len - pos);
            System.arraycopy(t, 0, out, pos, n);
            pos += n;
        }
        return out;
    }

    public static byte[] pbkdf2(String alg, byte[] pass, byte[] salt, int iter, int len) {
        try {
            Mac m = Mac.getInstance(alg);
            m.init(new SecretKeySpec(pass.length == 0 ? new byte[m.getMacLength()] : pass, alg));
            int hl = m.getMacLength();
            byte[] out = new byte[len];
            int blocks = (len + hl - 1) / hl;
            for (int b = 1; b <= blocks; b++) {
                m.update(salt);
                m.update(new byte[]{(byte) (b >>> 24), (byte) (b >>> 16), (byte) (b >>> 8), (byte) b});
                byte[] u = m.doFinal();
                byte[] t = u.clone();
                for (int i = 1; i < iter; i++) {
                    u = m.doFinal(u);
                    for (int k = 0; k < t.length; k++) t[k] ^= u[k];
                }
                System.arraycopy(t, 0, out, (b - 1) * hl, Math.min(hl, len - (b - 1) * hl));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] aesEncrypt(byte[] key, byte[] iv, byte[] pt) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return c.doFinal(pt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] aesDecrypt(byte[] key, byte[] iv, byte[] ct) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return c.doFinal(ct);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONObject encrypt(String plaintext, byte[] key) {
        return encrypt(plaintext, key, random(12));
    }

    public static JSONObject encrypt(String plaintext, byte[] key, byte[] iv) {
        try {
            return new JSONObject().put("ciphertext", b64(aesEncrypt(key, iv, utf8(plaintext)))).put("nonce", b64(iv));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(String ciphertext, String nonce, byte[] key) {
        if (ciphertext == null || nonce == null || key == null) return null;
        try {
            byte[] p = aesDecrypt(key, unb64(nonce), unb64(ciphertext));
            return p == null ? null : new String(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] x25519(byte[] priv, byte[] pub) {
        return Nacl.scalarMult(priv, pub);
    }

    public static byte[] x25519Public(byte[] priv) {
        return Nacl.scalarMultBase(priv);
    }

    public static byte[] edPublic(byte[] seed) {
        return Nacl.edPublic(seed);
    }

    public static byte[] edSign(byte[] msg, byte[] seed) {
        return Nacl.edSign(msg, seed);
    }

    public static boolean edVerify(byte[] msg, byte[] sig, byte[] pub) {
        return Nacl.edVerify(msg, sig, pub);
    }

    public static byte[] dmKey(String myEncPriv, String peerEncPub) {
        return hkdf(x25519(unb64(myEncPriv), unb64(peerEncPub)), null, "vulpine/dm/v1", 32);
    }

    public static Identity fromSeed(byte[] seed) {
        Identity i = new Identity();
        byte[] sp = hkdf(seed, null, "vulpine/sign/v1", 32);
        byte[] ep = hkdf(seed, null, "vulpine/enc/v1", 32);
        i.signPrivkey = b64(sp);
        i.signPubkey = b64(edPublic(sp));
        i.encPrivkey = b64(ep);
        i.encPubkey = b64(x25519Public(ep));
        return i;
    }

    public static byte[] mnemonicSeed(String mnemonic) {
        String m = Normalizer.normalize(mnemonic, Normalizer.Form.NFKD);
        return pbkdf2("HmacSHA512", utf8(m), utf8(Normalizer.normalize("mnemonic", Normalizer.Form.NFKD)), 2048, 64);
    }

    public static String normalizePhrase(String p) {
        return p.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public static boolean validMnemonic(String phrase) {
        if (words == null) return false;
        String[] w = Normalizer.normalize(phrase, Normalizer.Form.NFKD).trim().split(" +");
        if (w.length % 3 != 0 || w.length < 12 || w.length > 24) return false;
        int bits = w.length * 11;
        boolean[] b = new boolean[bits];
        for (int i = 0; i < w.length; i++) {
            int idx = java.util.Arrays.asList(words).indexOf(w[i]);
            if (idx < 0) return false;
            for (int k = 0; k < 11; k++) b[i * 11 + k] = ((idx >> (10 - k)) & 1) == 1;
        }
        int cs = bits / 33, ent = bits - cs;
        byte[] e = new byte[ent / 8];
        for (int i = 0; i < ent; i++) if (b[i]) e[i / 8] |= (byte) (1 << (7 - i % 8));
        byte[] h = sha256(e);
        for (int i = 0; i < cs; i++) if (((h[i / 8] >> (7 - i % 8)) & 1) == 1 != b[ent + i]) return false;
        return true;
    }

    public static String generateMnemonic() {
        byte[] e = random(32);
        byte[] h = sha256(e);
        StringBuilder sb = new StringBuilder();
        int bits = 264;
        for (int i = 0; i < 24; i++) {
            int idx = 0;
            for (int k = 0; k < 11; k++) {
                int bit = i * 11 + k;
                int v = bit < 256 ? (e[bit / 8] >> (7 - bit % 8)) & 1 : (h[0] >> (7 - (bit - 256))) & 1;
                idx = (idx << 1) | v;
            }
            if (i > 0) sb.append(' ');
            sb.append(words[idx]);
        }
        return sb.toString();
    }

    public static Identity recover(String phrase) {
        Identity i = fromSeed(mnemonicSeed(phrase));
        i.mnemonic = phrase;
        return i;
    }

    public static Identity generate() {
        String m = generateMnemonic();
        return recover(m);
    }

    public static String token() {
        return b64(random(32));
    }

    public static String hashToken(String t) {
        return b64(sha256(utf8(t)));
    }

    public static byte[] secretKey(String pass, String saltB64) {
        return pbkdf2("HmacSHA256", utf8(pass), unb64(saltB64), 600000, 32);
    }

    public static byte[] seal(byte[] key, byte[] data) {
        return seal(key, data, random(12));
    }

    public static byte[] seal(byte[] key, byte[] data, byte[] iv) {
        byte[] ct = aesEncrypt(key, iv, data);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    public static byte[] open(byte[] key, byte[] blob) {
        if (blob == null || blob.length < 28) return null;
        byte[] iv = new byte[12], ct = new byte[blob.length - 12];
        System.arraycopy(blob, 0, iv, 0, 12);
        System.arraycopy(blob, 12, ct, 0, ct.length);
        return aesDecrypt(key, iv, ct);
    }

    public static int[] callEmojiIdx(String localSdp, String remoteSdp) {
        java.util.regex.Pattern re = java.util.regex.Pattern.compile("a=fingerprint:(\\S+) (\\S+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher x = re.matcher(localSdp), y = re.matcher(remoteSdp);
        if (!x.find() || !y.find()) return null;
        return callEmojiIdxFp(x.group(1).toLowerCase() + " " + x.group(2).toUpperCase(), y.group(1).toLowerCase() + " " + y.group(2).toUpperCase());
    }

    public static int[] callEmojiIdxFp(String a, String b) {
        String s = a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
        byte[] h = sha256(utf8("vulpine-call-v1|" + s));
        return new int[]{h[0] & 127, h[1] & 127, h[2] & 127, h[3] & 127};
    }
}
