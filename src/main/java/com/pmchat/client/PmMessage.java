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

    /** Опрос (если сообщение — опрос). */
    public String pollQuestion;
    public java.util.List<String> pollOptions;
    public boolean pollMulti;
    public java.util.List<Integer> pollMyVotes;    // мой выбор
    public java.util.List<Integer> pollOtherVotes; // выбор собеседника

    public boolean isPoll() {
        return pollQuestion != null && pollOptions != null;
    }

    /** Голосов за вариант i (я + собеседник). */
    public int pollCount(int i) {
        int c = 0;
        if (pollMyVotes != null && pollMyVotes.contains(i)) c++;
        if (pollOtherVotes != null && pollOtherVotes.contains(i)) c++;
        return c;
    }

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
