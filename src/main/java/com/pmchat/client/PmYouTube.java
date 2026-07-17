package com.pmchat.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NEW (5.0): резолвер YouTube-ссылок в прямые ссылки на видеопоток.
 *
 * Зачем: сам VLC 3.x почти никогда не может открыть страницу YouTube — его
 * встроенный скрипт youtube.lua годами отстаёт от изменений сайта, поэтому
 * плеер «висел» на чёрном экране. Мы спрашиваем внутренний API YouTube
 * (innertube, тот же, что используют официальные приложения) от имени
 * клиентов, которым отдают простые прямые ссылки без подписи:
 * ANDROID_VR → прогрессивный MP4, IOS → HLS-манифест (VLC играет и то и то).
 *
 * Всё best-effort: если ни один клиент не ответил — возвращаем null, и
 * вызывающий код отдаёт VLC исходную ссылку (вдруг его lua всё же работает),
 * а дальше сработает кнопка «Открыть в браузере».
 */
public final class PmYouTube {

    private static final Logger LOGGER = LoggerFactory.getLogger("pmchat-youtube");
    private static final String PLAYER_ENDPOINT = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false";

    /** Прямые ссылки googlevideo живут часами — короткий кэш, чтобы повторный клик был мгновенным. */
    private static final Map<String, String> cache = new ConcurrentHashMap<>();
    private static final Map<String, Long> cacheAt = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30 * 60_000L;

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

    /**
     * Синхронно (звать с фонового потока!) резолвит ссылку YouTube в прямой
     * URL потока. null — не получилось ни одним клиентом.
     */
    public static String resolve(String url) {
        String id = videoId(url);
        if (id == null) return null;

        Long at = cacheAt.get(id);
        if (at != null && System.currentTimeMillis() - at < CACHE_TTL_MS) {
            return cache.get(id);
        }

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // Клиенты по очереди — от самого «щедрого» к запасным
        for (ClientProfile profile : PROFILES) {
            try {
                String direct = tryClient(http, profile, id);
                if (direct != null) {
                    LOGGER.info("Resolved YouTube {} via {} client", id, profile.name);
                    cache.put(id, direct);
                    cacheAt.put(id, System.currentTimeMillis());
                    return direct;
                }
            } catch (Exception e) {
                LOGGER.warn("YouTube resolve via {} failed: {}", profile.name, e.toString());
            }
        }
        LOGGER.warn("Could not resolve YouTube video {} with any client", id);
        return null;
    }

    // ---------- внутренности ----------

    private record ClientProfile(String name, String version, String userAgent, Map<String, Object> extra) {
    }

    private static final ClientProfile[] PROFILES = {
            // Клиент VR-гарнитуры: YouTube не требует от него PoToken и отдаёт прямые url
            new ClientProfile("ANDROID_VR", "1.62.27",
                    "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                    mapOf("deviceMake", "Oculus", "deviceModel", "Quest 3",
                            "osName", "Android", "osVersion", "12L", "androidSdkVersion", 32)),
            // iOS-клиент: почти всегда даёт hlsManifestUrl — VLC отлично играет HLS
            new ClientProfile("IOS", "19.45.4",
                    "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                    mapOf("deviceMake", "Apple", "deviceModel", "iPhone16,2",
                            "osName", "iPhone", "osVersion", "18.1.0.22B83")),
            // Обычный Android — запасной вариант
            new ClientProfile("ANDROID", "19.44.38",
                    "com.google.android.youtube/19.44.38 (Linux; U; Android 11) gzip",
                    mapOf("osName", "Android", "osVersion", "11", "androidSdkVersion", 30)),
    };

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String tryClient(HttpClient http, ClientProfile profile, String videoId) throws Exception {
        JsonObject client = new JsonObject();
        client.addProperty("clientName", profile.name);
        client.addProperty("clientVersion", profile.version);
        client.addProperty("hl", "en");
        client.addProperty("gl", "US");
        for (Map.Entry<String, Object> e : profile.extra.entrySet()) {
            if (e.getValue() instanceof Number n) client.addProperty(e.getKey(), n);
            else client.addProperty(e.getKey(), String.valueOf(e.getValue()));
        }
        JsonObject context = new JsonObject();
        context.add("client", client);
        JsonObject body = new JsonObject();
        body.add("context", context);
        body.addProperty("videoId", videoId);
        body.addProperty("contentCheckOk", true);
        body.addProperty("racyCheckOk", true);

        HttpRequest req = HttpRequest.newBuilder(URI.create(PLAYER_ENDPOINT))
                .timeout(Duration.ofSeconds(7))
                .header("Content-Type", "application/json")
                .header("User-Agent", profile.userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            LOGGER.warn("YouTube {}: HTTP {}", profile.name, resp.statusCode());
            return null;
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject playability = root.getAsJsonObject("playabilityStatus");
        String status = playability != null && playability.has("status")
                ? playability.get("status").getAsString() : "?";
        if (!"OK".equalsIgnoreCase(status)) {
            LOGGER.warn("YouTube {}: playability {}", profile.name, status);
            return null;
        }
        JsonObject streaming = root.getAsJsonObject("streamingData");
        if (streaming == null) return null;

        // 1) Прогрессивные форматы (видео+звук одним файлом) — берём лучший по высоте
        String best = null;
        int bestHeight = -1;
        if (streaming.has("formats")) {
            JsonArray formats = streaming.getAsJsonArray("formats");
            for (JsonElement el : formats) {
                JsonObject f = el.getAsJsonObject();
                if (!f.has("url")) continue; // подписанные пропускаем — расшифровка не наша лига
                String mime = f.has("mimeType") ? f.get("mimeType").getAsString().toLowerCase(Locale.ROOT) : "";
                if (!mime.startsWith("video/")) continue;
                int h = f.has("height") ? f.get("height").getAsInt() : 0;
                if (h > bestHeight) {
                    bestHeight = h;
                    best = f.get("url").getAsString();
                }
            }
        }
        if (best != null) return best;

        // 2) HLS-манифест (обычно у IOS-клиента) — VLC разберёт сам
        if (streaming.has("hlsManifestUrl")) {
            return streaming.get("hlsManifestUrl").getAsString();
        }
        return null;
    }
}
