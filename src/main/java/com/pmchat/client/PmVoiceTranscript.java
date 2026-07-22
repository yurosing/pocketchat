package com.pmchat.client;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * СЕКРЕТНАЯ фича, недоступная в открытой версии мода: расшифровка голосовых
 * сообщений в текст под пузырём (как в Telegram). ПКМ по голосовому →
 * «Расшифровать» — под пузырём появляется распознанный текст; повторный ПКМ
 * → «Скрыть расшифровку» — прячет его снова (сам текст остаётся в кэше,
 * повторно распознавать не нужно). Работает офлайн через уже встроенный в
 * мод движок Vosk (тот же, что у голосового набора текста).
 */
public final class PmVoiceTranscript {

    private static final Map<String, String> TEXT = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> ERROR = ConcurrentHashMap.newKeySet();
    private static final Set<String> SHOWN = ConcurrentHashMap.newKeySet();

    private PmVoiceTranscript() {
    }

    public static boolean isShown(String id) {
        return SHOWN.contains(id);
    }

    public static boolean isLoading(String id) {
        return LOADING.contains(id);
    }

    public static boolean hasError(String id) {
        return ERROR.contains(id);
    }

    /** Готовый текст расшифровки или null, если ещё не запрашивали/не готов. */
    public static String textOf(String id) {
        return TEXT.get(id);
    }

    /** ПКМ по голосовому: показать/скрыть расшифровку. Запрашивает распознавание при первом показе. */
    public static void toggle(String hostCode, String id) {
        if (SHOWN.contains(id)) {
            SHOWN.remove(id);
            return;
        }
        SHOWN.add(id);
        if (TEXT.containsKey(id) || LOADING.contains(id)) return;
        request(hostCode, id);
    }

    private static void request(String hostCode, String id) {
        LOADING.add(id);
        ERROR.remove(id);
        CompletableFuture
                .supplyAsync(() -> PmVoice.fetchBytes(hostCode, id))
                .thenAccept(bytes -> {
                    if (bytes == null) {
                        LOADING.remove(id);
                        ERROR.add(id);
                        return;
                    }
                    PmStt.transcribeAsync(bytes,
                            text -> {
                                LOADING.remove(id);
                                TEXT.put(id, text == null || text.isBlank() ? "…" : text);
                            },
                            err -> {
                                LOADING.remove(id);
                                ERROR.add(id);
                            });
                });
    }
}
