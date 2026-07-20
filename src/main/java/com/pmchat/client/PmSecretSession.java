package com.pmchat.client;

import javax.crypto.SecretKey;
import java.security.KeyPair;

/**
 * состояние секретного чата (6.10) с одним собеседником.
 * Живёт только в памяти — не сохраняется на диск и исчезает при выходе
 * из игры, как секретные чаты в Telegram.
 */
public class PmSecretSession {

    public enum State { NONE, PENDING, ACTIVE }

    public State state = State.NONE;
    public KeyPair myKeyPair;
    public SecretKey aesKey;

    /** Таймер самоуничтожения по умолчанию для новых сообщений (сек, 0 — выкл). */
    public int ttlSeconds = 0;

    public boolean isActive() {
        return state == State.ACTIVE && aesKey != null;
    }
}
