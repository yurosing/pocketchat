package com.pmchat.client;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;

/**
 * NEW: сквозное шифрование секретных чатов (6.10).
 * X25519 для обмена ключом + AES-256-GCM для самих сообщений. Ключи живут
 * только в памяти клиента (не сохраняются на диск) — как в секретных чатах
 * Telegram: перезапуск игры полностью стирает сессию.
 */
public final class PmCrypto {

    private static final int NONCE_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private PmCrypto() {
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("X25519 unavailable", e);
        }
    }

    /** Публичный ключ как 32 «сырых» байта, little-endian (стандарт RFC 7748). */
    public static byte[] rawPublicKey(KeyPair kp) {
        XECPublicKey pub = (XECPublicKey) kp.getPublic();
        return bigIntToLe32(pub.getU());
    }

    /** Общий секрет по своему приватному ключу + сырому публичному ключу собеседника (32 байта). */
    public static byte[] agree(KeyPair mine, byte[] peerRawPublic) throws GeneralSecurityException {
        BigInteger u = leToBigInt(peerRawPublic);
        KeyFactory kf = KeyFactory.getInstance("XDH");
        PublicKey peerPub = kf.generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, u));
        KeyAgreement ka = KeyAgreement.getInstance("XDH");
        ka.init(mine.getPrivate());
        ka.doPhase(peerPub, true);
        return ka.generateSecret();
    }

    /** Общий секрет -> ключ AES-256 (SHA-256 в качестве простого KDF). */
    public static SecretKey deriveAesKey(byte[] sharedSecret) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(sharedSecret);
        return new SecretKeySpec(digest, "AES");
    }

    /** {nonceHex, cipherHex} — шифрует UTF-8 текст, готово к вставке в pmc-сообщение (только hex-символы). */
    public static String[] encrypt(SecretKey key, String plaintext) throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_LEN];
        new SecureRandom().nextBytes(nonce);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] cipher = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return new String[]{hex(nonce), hex(cipher)};
    }

    /** Расшифровка обратно в текст или null, если ключ не подходит / данные повреждены. */
    public static String decrypt(SecretKey key, String nonceHex, String cipherHex) {
        try {
            byte[] nonce = unhex(nonceHex);
            byte[] cipher = unhex(cipherHex);
            if (nonce.length != NONCE_LEN) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(c.doFinal(cipher), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- hex (0-9a-f — безопасно для анти-рекламных фильтров) ----------

    public static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static byte[] unhex(String h) {
        if (h == null || (h.length() & 1) != 0) return new byte[0];
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(h.charAt(i * 2), 16);
            int lo = Character.digit(h.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return new byte[0];
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    // ---------- BigInteger <-> little-endian 32 байт (raw X25519 u-координата) ----------

    private static byte[] bigIntToLe32(BigInteger u) {
        byte[] be = u.toByteArray();
        byte[] out = new byte[32];
        int n = Math.min(be.length, 32);
        for (int i = 0; i < n; i++) {
            out[i] = be[be.length - 1 - i];
        }
        return out;
    }

    private static BigInteger leToBigInt(byte[] le) {
        byte[] withSign = new byte[le.length + 1];
        for (int i = 0; i < le.length; i++) {
            withSign[le.length - i] = le[i];
        }
        return new BigInteger(withSign);
    }
}
