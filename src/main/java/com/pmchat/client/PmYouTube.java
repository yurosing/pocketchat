package com.pmchat.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Хелпер распознавания YouTube-ссылок. Само проигрывание идёт через
 * {@link PmYtDlp} (напрямую отдать VLC ссылку на YouTube к 2026 нельзя —
 * потоки заперты proof-of-origin токеном).
 */
public final class PmYouTube {

    private static final Pattern VIDEO_ID = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?[^#]*v=|shorts/|live/|embed/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    private PmYouTube() {
    }

    /** Вытащить 11-символьный id ролика из любой формы ссылки; null, если это не YouTube. */
    public static String videoId(String url) {
        if (url == null) return null;
        Matcher m = VIDEO_ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** Похожа ли ссылка на YouTube-видео. */
    public static boolean isYouTube(String url) {
        return videoId(url) != null;
    }
}
