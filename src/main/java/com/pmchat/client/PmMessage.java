package com.pmchat.client;

/**
 * Одно сообщение диалога. money > 0 — это денежный перевод.
 * clientAddedAt — не сохраняется, служит для анимации появления.
 */
public class PmMessage {

    public boolean out;
    public String text;
    public long time;
    public long money;

    /** Хэш цитируемого сообщения ([re:hash]) или null. */
    public String replyTo;

    /** Автор (только для общего чата; в ЛС null). */
    public String sender;

    /** Прочитано собеседником (по метке seen, только между модами). */
    public boolean read;

    /** Реакции: моя и собеседника (символ из PmWire.REACTIONS). */
    public String reactMine;
    public String reactOther;

    /** Ник исходного автора, если сообщение переслано. */
    public String forwardFrom;

    public transient long clientAddedAt;

    public PmMessage() {
    }

    public PmMessage(boolean out, String text, long time, long money) {
        this.out = out;
        this.text = text;
        this.time = time;
        this.money = money;
        this.clientAddedAt = System.currentTimeMillis();
    }
}
