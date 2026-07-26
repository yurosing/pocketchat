package com.pmchat.client;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * AES-256-GCM шифрование локального хранилища (1.7.8, #15): история и другие
 * данные шифруются «на диске» ключом из {@code loadOrCreateKey()}. Ключ живёт в
 * файле рядом с конфигом — это защита от случайного просмотра, не E2E.
 */
public final class PmCrypto {

    private static final int NONCE_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private PmCrypto() {
    }

    // ---------- Шифрование «на диске» (1.7.8, #15): произвольные байты ----------

    /** AES-ключ из 32 сырых байт. */
    public static SecretKey aesKey(byte[] raw32) {
        return new SecretKeySpec(raw32, "AES");
    }

    /** Зашифровать байты: результат = nonce(12) ‖ ciphertext+tag. */
    public static byte[] seal(SecretKey key, byte[] plain) throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_LEN];
        new SecureRandom().nextBytes(nonce);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[NONCE_LEN + ct.length];
        System.arraycopy(nonce, 0, out, 0, NONCE_LEN);
        System.arraycopy(ct, 0, out, NONCE_LEN, ct.length);
        return out;
    }

    /** Расшифровать байты формата seal(). Бросает исключение при неверном ключе/повреждении. */
    public static byte[] open(SecretKey key, byte[] blob) throws GeneralSecurityException {
        if (blob.length < NONCE_LEN + 16) throw new GeneralSecurityException("blob too short");
        byte[] nonce = new byte[NONCE_LEN];
        System.arraycopy(blob, 0, nonce, 0, NONCE_LEN);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] ct = new byte[blob.length - NONCE_LEN];
        System.arraycopy(blob, NONCE_LEN, ct, 0, ct.length);
        return c.doFinal(ct);
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
}
