package com.pmchat.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST-клиент собственного бэкенда игрока на Railway (репозиторий
 * pocketchat-backend): медиа, кошелёк (своя валюта) и подарки без серверного
 * плагина/Vault. Игрок опознаётся по нику + токену установки, выданному
 * бэкендом при первой регистрации — тот же уровень доверия, что и у остальной
 * части мода (парсинг /m по нику, без Mojang-auth).
 */
public final class PmBackend {

    public record Gift(String id, String name, String icon, double price, String rarity) {
    }

    public record ReceivedGift(String giftId, String from, String at, boolean seen) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile boolean accountEnsured = false;

    // ---------- Реактивный кэш для экранов (без блокировки render-потока) ----------

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "pmchat-backend");
        t.setDaemon(true);
        return t;
    });

    private static volatile double cachedBalance = -1;
    private static volatile boolean balanceLoading = false;

    private static volatile List<Gift> cachedCatalog;
    private static volatile boolean catalogLoading = false;

    private static final Map<String, List<ReceivedGift>> INBOX_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> INBOX_LOADING = new ConcurrentHashMap<>();

    private static volatile String lastResultMsg;
    private static volatile boolean lastResultOk;
    private static volatile long lastResultAt;

    /** Баланс из кэша (-1 — ещё не загружен); одновременно запускает обновление. */
    public static double cachedBalance() {
        if (!balanceLoading) {
            balanceLoading = true;
            EXECUTOR.submit(() -> {
                try {
                    cachedBalance = getBalance();
                } catch (Exception e) {
                    PmChatClient.LOGGER.debug("Backend balance fetch failed: {}", e.toString());
                } finally {
                    balanceLoading = false;
                }
            });
        }
        return cachedBalance;
    }

    /** Каталог из кэша (null — ещё не загружен); одновременно запускает обновление. */
    public static List<Gift> cachedCatalog() {
        if (cachedCatalog == null && !catalogLoading) {
            catalogLoading = true;
            EXECUTOR.submit(() -> {
                try {
                    cachedCatalog = getCatalog();
                } catch (Exception e) {
                    PmChatClient.LOGGER.warn("Backend catalog fetch failed: {}", e.toString());
                } finally {
                    catalogLoading = false;
                }
            });
        }
        return cachedCatalog;
    }

    /** Подарки игрока из кэша (пустой список — ещё не загружены/нет); запускает обновление. */
    public static List<ReceivedGift> cachedInbox(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        if (!Boolean.TRUE.equals(INBOX_LOADING.get(key))) {
            INBOX_LOADING.put(key, true);
            EXECUTOR.submit(() -> {
                try {
                    INBOX_CACHE.put(key, getInbox(username));
                } catch (Exception e) {
                    PmChatClient.LOGGER.debug("Backend inbox fetch failed: {}", e.toString());
                } finally {
                    INBOX_LOADING.put(key, false);
                }
            });
        }
        return INBOX_CACHE.getOrDefault(key, List.of());
    }

    public static String lastResultMsg() {
        return lastResultMsg;
    }

    public static boolean lastResultOk() {
        return lastResultOk;
    }

    public static long lastResultAt() {
        return lastResultAt;
    }

    /** Покупка/отправка подарка — асинхронно, результат появится в lastResultMsg()/lastResultOk(). */
    public static void buyGiftAsync(String targetUsername, String giftId) {
        EXECUTOR.submit(() -> {
            try {
                cachedBalance = sendGift(targetUsername, giftId);
                lastResultOk = true;
                lastResultMsg = "";
                INBOX_CACHE.remove(targetUsername.toLowerCase(Locale.ROOT));
            } catch (Exception e) {
                lastResultOk = false;
                lastResultMsg = e.getMessage();
            } finally {
                lastResultAt = System.currentTimeMillis();
            }
        });
    }

    /** Забрать ежедневный бонус — асинхронно, результат появится в lastResultMsg(). */
    public static void claimDailyBonusAsync() {
        EXECUTOR.submit(() -> {
            try {
                cachedBalance = claimDailyBonus();
                lastResultOk = true;
                lastResultMsg = "bonus";
            } catch (Exception e) {
                lastResultOk = false;
                lastResultMsg = e.getMessage();
            } finally {
                lastResultAt = System.currentTimeMillis();
            }
        });
    }

    private static volatile long lastPollAt = 0;
    private static final long POLL_INTERVAL_MS = 30_000;
    private static volatile int knownGiftCount = -1;

    /**
     * Зовётся каждый клиентский тик (как {@link PmServerMedia#tick()}): раз в
     * {@link #POLL_INTERVAL_MS} проверяет свой инбокс подарков, чтобы показать
     * тост даже если профиль не был открыт (бэкенд не пушит события сам —
     * обычный REST-опрос).
     */
    public static void tick() {
        if (!isConfigured()) return;
        long now = System.currentTimeMillis();
        if (now - lastPollAt < POLL_INTERVAL_MS) return;
        lastPollAt = now;
        String self = PmChatClient.selfName();
        if (self == null || self.isBlank()) return;
        EXECUTOR.submit(() -> {
            try {
                List<ReceivedGift> gifts = getInbox(self);
                INBOX_CACHE.put(self.toLowerCase(Locale.ROOT), gifts);
                if (knownGiftCount >= 0 && gifts.size() > knownGiftCount) {
                    List<Gift> catalog = cachedCatalog();
                    for (int i = 0; i < gifts.size() - knownGiftCount; i++) {
                        ReceivedGift g = gifts.get(i);
                        String icon = giftIconOrDot(catalog, g.giftId());
                        String name = giftNameOrId(catalog, g.giftId());
                        PmChatClient.giftToast(g.from(), name, icon);
                    }
                }
                knownGiftCount = gifts.size();
            } catch (Exception e) {
                PmChatClient.LOGGER.debug("Backend inbox poll failed: {}", e.toString());
            }
        });
    }

    private static String giftIconOrDot(List<Gift> catalog, String giftId) {
        if (catalog != null) for (Gift g : catalog) if (g.id().equals(giftId)) return g.icon();
        return "🎁";
    }

    private static String giftNameOrId(List<Gift> catalog, String giftId) {
        if (catalog != null) for (Gift g : catalog) if (g.id().equals(giftId)) return g.name();
        return giftId;
    }

    private PmBackend() {
    }

    private static PmConfig cfg() {
        return PmChatClient.getConfig();
    }

    public static boolean isConfigured() {
        String url = cfg().backendUrl;
        return url != null && !url.isBlank();
    }

    private static String baseUrl() {
        String url = cfg().backendUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Регистрирует/обновляет ник для установки при первом обращении за сессию. */
    private static synchronized void ensureAccount() throws Exception {
        if (accountEnsured && !cfg().backendToken.isBlank()) return;
        JsonObject body = new JsonObject();
        String token = cfg().backendToken;
        if (token != null && !token.isBlank()) body.addProperty("token", token);
        body.addProperty("username", PmChatClient.selfName());
        JsonObject resp = postJson("/v1/account", body);
        cfg().backendToken = resp.get("token").getAsString();
        cfg().save();
        accountEnsured = true;
    }

    private static String token() throws Exception {
        ensureAccount();
        return cfg().backendToken;
    }

    // ---------- HTTP helpers ----------

    private static JsonObject postJson(String path, JsonObject body) throws Exception {
        HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build(), HttpResponse.BodyHandlers.ofString());
        return parseOrThrow(resp);
    }

    private static JsonObject getJson(String path) throws Exception {
        HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        return parseOrThrow(resp);
    }

    private static JsonObject parseOrThrow(HttpResponse<String> resp) {
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("backend HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    // ---------- Кошелёк ----------

    public static double getBalance() throws Exception {
        JsonObject resp = getJson("/v1/wallet?token=" + urlEnc(token()));
        return resp.get("balance").getAsDouble();
    }

    /** Бросает исключение, если бонус ещё не доступен (сообщение — время следующей выдачи). */
    public static double claimDailyBonus() throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("token", token());
        JsonObject resp = postJson("/v1/wallet/bonus", body);
        if (!resp.get("ok").getAsBoolean()) {
            throw new IllegalStateException("bonus_not_ready:" + resp.get("nextAt").getAsString());
        }
        return resp.get("balance").getAsDouble();
    }

    // ---------- Подарки ----------

    public static List<Gift> getCatalog() throws Exception {
        JsonObject resp = getJson("/v1/catalog");
        JsonArray arr = resp.getAsJsonArray("gifts");
        List<Gift> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject g = arr.get(i).getAsJsonObject();
            out.add(new Gift(g.get("id").getAsString(), g.get("name").getAsString(),
                    g.get("icon").getAsString(), g.get("price").getAsDouble(), g.get("rarity").getAsString()));
        }
        return out;
    }

    public static double sendGift(String targetUsername, String giftId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("token", token());
        body.addProperty("targetUsername", targetUsername);
        body.addProperty("giftId", giftId);
        JsonObject resp = postJson("/v1/gift/send", body);
        return resp.get("balance").getAsDouble();
    }

    /** Витрина подарков игрока (свой или чужой профиль — публично видно всем). */
    public static List<ReceivedGift> getInbox(String username) throws Exception {
        JsonObject resp = getJson("/v1/gift/inbox?token=" + urlEnc(token()) + "&username=" + urlEnc(username));
        JsonArray arr = resp.getAsJsonArray("gifts");
        List<ReceivedGift> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject g = arr.get(i).getAsJsonObject();
            out.add(new ReceivedGift(g.get("giftId").getAsString(), g.get("from").getAsString(),
                    g.get("at").getAsString(), g.get("seen").getAsBoolean()));
        }
        return out;
    }

    // ---------- Медиа ----------

    private static String mimeOf(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".mp4")) return "video/mp4";
        return "image/png";
    }

    /** Грузит файл на бэкенд. @return id файла (например "ab12...ef.png"). */
    public static String uploadFile(byte[] data, String filename) throws Exception {
        String boundary = "----pmchat" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mimeOf(filename) + "\r\n\r\n";
        body.write(head.getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/v1/media"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(), HttpResponse.BodyHandlers.ofString());
        JsonObject json = parseOrThrow(resp);
        return json.get("id").getAsString();
    }

    /** Качает файл с бэкенда по id, как вернул {@link #uploadFile}. */
    public static byte[] downloadFile(String fileId) throws Exception {
        HttpResponse<byte[]> resp = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/v1/media/" + urlEnc(fileId)))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) throw new IllegalStateException("backend HTTP " + resp.statusCode());
        return resp.body();
    }

    private static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
