package com.pmchat.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    // "title":"..." из oEmbed-ответа (значение — JSON-строка с экранированием).
    private static final Pattern OEMBED_TITLE = Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

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

    /**
     * Название ролика через публичный oEmbed-эндпоинт YouTube (быстрый HTTP-запрос,
     * без ключа и без входа). Звать с фонового потока — блокирует. null, если не
     * удалось (нет сети / ролик приватный).
     */
    public static String fetchTitle(String url) {
        String id = videoId(url);
        if (id == null) return null;
        try {
            String oembed = "https://www.youtube.com/oembed?format=json&url="
                    + URLEncoder.encode("https://www.youtube.com/watch?v=" + id, StandardCharsets.UTF_8);
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(oembed))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "pmchat-mod")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return null;
            Matcher m = OEMBED_TITLE.matcher(resp.body());
            if (!m.find()) return null;
            String title = unescapeJson(m.group(1)).trim();
            return title.isEmpty() ? null : title;
        } catch (Exception e) {
            return null;
        }
    }

    /** Минимальная распаковка JSON-строки (\", \\, \/, \n, \t, \uXXXX) для значения title. */
    private static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                sb.append(n);
                            }
                        }
                    }
                    default -> sb.append(n); // \" \\ \/ и прочее — как есть
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
