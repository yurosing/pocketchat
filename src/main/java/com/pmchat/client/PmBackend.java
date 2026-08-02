package com.pmchat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * Клиент отдельного бэкенда PocketChat (репозиторий {@code server-pocketchat}):
 * своя валюта, логин/пароль (не Mojang), верификация (зелёная галочка),
 * официальный аккаунт и админ-панель. Всё выключено, если
 * {@link PmConfig#backendUrl} пусто — мод продолжает работать как раньше,
 * через обычные строки чата.
 */
public final class PmBackend {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private PmBackend() {
    }

    public interface Callback<T> {
        void onResult(boolean ok, T value, String error);
    }

    public static boolean isConfigured() {
        String url = PmChatClient.getConfig().backendUrl;
        return url != null && !url.isBlank();
    }

    public static boolean hasAccount() {
        String t = PmChatClient.getConfig().backendToken;
        return t != null && !t.isBlank();
    }

    private static String base() {
        String url = PmChatClient.getConfig().backendUrl;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static final class AccountInfo {
        public final String username;
        public final boolean verified;
        public final boolean official;
        public final String avatarUrl;
        /** Эпоха в мс последнего пинга/активности аккаунта на бэкенде, 0 — неизвестно. */
        public final long lastSeenAt;
        /** Включил ли сам этот игрок точный статус «был(а) N часов/дней назад» (см. humanizeLastSeen). */
        public final boolean sharePrecise;

        AccountInfo(String username, boolean verified, boolean official, String avatarUrl, long lastSeenAt, boolean sharePrecise) {
            this.username = username;
            this.verified = verified;
            this.official = official;
            this.avatarUrl = avatarUrl;
            this.lastSeenAt = lastSeenAt;
            this.sharePrecise = sharePrecise;
        }
    }

    /** Парсит ISO-8601 timestamp сервера (например "2026-07-31T16:31:37.123Z") в эпоху мс, 0 при ошибке. */
    private static long parseIsoMillis(String s) {
        if (s == null || s.isBlank()) return 0L;
        try {
            return java.time.Instant.parse(s).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    // ---------- кэш публичного профиля для отрисовки галочки в UI (без блокировки рендера) ----------

    private static final class CacheEntry {
        final AccountInfo info;
        final long at;

        CacheEntry(AccountInfo info, long at) {
            this.info = info;
            this.at = at;
        }
    }

    private static final java.util.Map<String, CacheEntry> ACCOUNT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> ACCOUNT_IN_FLIGHT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final long ACCOUNT_CACHE_TTL_MS = 60_000L;

    /**
     * Синхронно отдаёт последний известный публичный профиль игрока (для отрисовки
     * галочки/официального статуса в рендере), фоново обновляя его, если устарел
     * или ещё не запрашивался. Возвращает null, пока ответ не пришёл.
     */
    public static AccountInfo cachedAccountInfo(String username) {
        if (!isConfigured() || username == null || username.isBlank()) return null;
        String key = username.toLowerCase(java.util.Locale.ROOT);
        CacheEntry e = ACCOUNT_CACHE.get(key);
        boolean stale = e == null || System.currentTimeMillis() - e.at > ACCOUNT_CACHE_TTL_MS;
        if (stale && ACCOUNT_IN_FLIGHT.add(key)) {
            accountInfo(username, (ok, info, err) -> {
                ACCOUNT_IN_FLIGHT.remove(key);
                if (ok && info != null) ACCOUNT_CACHE.put(key, new CacheEntry(info, System.currentTimeMillis()));
            });
        }
        return e != null ? e.info : null;
    }

    /**
     * Человекочитаемый статус «был(а) в сети» по последнему пингу присутствия —
     * кросс-серверный (не зависит от таб-листа текущего Minecraft-сервера).
     */
    /** Расплывчатый статус (недавно/на этой неделе/давно) — по умолчанию, без взаимного согласия на точность. */
    public static net.minecraft.text.Text humanizeLastSeen(long lastSeenAtMs) {
        return humanizeLastSeen(lastSeenAtMs, false);
    }

    /**
     * @param precise точный вариант («N ч./дн. назад») вместо расплывчатых «недавно/на этой
     *                неделе/давно» — вызывающий код должен передавать true только когда ОБЕ
     *                стороны включили {@link PmConfig#preciseLastSeen} (см. AccountInfo.sharePrecise).
     */
    public static net.minecraft.text.Text humanizeLastSeen(long lastSeenAtMs, boolean precise) {
        if (lastSeenAtMs <= 0) return net.minecraft.text.Text.translatable("pmchat.profile.lastseen.unknown");
        long diff = System.currentTimeMillis() - lastSeenAtMs;
        if (diff < 90_000L) return net.minecraft.text.Text.translatable("pmchat.profile.lastseen.online");
        if (precise) {
            if (diff < 3_600_000L) {
                return net.minecraft.text.Text.translatable("pmchat.profile.lastseen.minutes", diff / 60_000L);
            }
            if (diff < 24 * 3_600_000L) {
                return net.minecraft.text.Text.translatable("pmchat.profile.lastseen.hours", diff / 3_600_000L);
            }
            if (diff < 30L * 24 * 3_600_000L) {
                return net.minecraft.text.Text.translatable("pmchat.profile.lastseen.days", diff / (24 * 3_600_000L));
            }
            return net.minecraft.text.Text.translatable("pmchat.profile.lastseen.long");
        }
        if (diff < 3_600_000L) return net.minecraft.text.Text.translatable("pmchat.profile.lastseen.recent");
        if (diff < 7 * 24 * 3_600_000L) return net.minecraft.text.Text.translatable("pmchat.profile.lastseen.week");
        return net.minecraft.text.Text.translatable("pmchat.profile.lastseen.long");
    }

    // ---------- логин/пароль (своя система, не Mojang) ----------

    public static void register(String username, String password, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        postJson("/v1/register", body, resp -> {
            if (resp == null) return;
            if (resp.has("token")) PmChatClient.getConfig().backendToken = resp.get("token").getAsString();
            PmChatClient.getConfig().save();
            // Свежий аккаунт не должен разом получить всю историю прошлых рассылок —
            // стартуем опрос с текущего "последнего" id, а не с нуля.
            skipBroadcastHistory();
        }, cb);
    }

    /** Ставит lastBroadcastId на текущий максимум, чтобы не присылать старые рассылки. */
    private static void skipBroadcastHistory() {
        getJson("/v1/broadcast/latest", json -> {
            if (json == null || !json.has("id")) return;
            long id = json.get("id").getAsLong();
            MinecraftClient.getInstance().execute(() -> {
                PmChatClient.getConfig().lastBroadcastId = id;
                PmChatClient.getConfig().save();
            });
        });
    }

    public static void login(String username, String password, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        postJson("/v1/login", body, resp -> {
            if (resp == null) return;
            if (resp.has("token")) PmChatClient.getConfig().backendToken = resp.get("token").getAsString();
            PmChatClient.getConfig().save();
        }, cb);
    }

    public static void setPassword(String password, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("password", password);
        postJson("/v1/set-password", body, null, cb);
    }

    // ---------- кошелёк ----------

    private static volatile Long cachedSelfBalance = null;
    private static volatile long selfBalanceFetchedAt = 0;
    private static volatile boolean selfBalanceInFlight = false;
    private static final long SELF_BALANCE_TTL_MS = 20_000L;

    /**
     * Синхронно отдаёт последний известный баланс своей валюты (для отрисовки в
     * профиле без блокировки рендера), фоново обновляя, если устарел. Возвращает
     * null, пока ответ ещё не пришёл или если аккаунт не настроен.
     */
    public static Long cachedSelfBalance() {
        if (!isConfigured() || !hasAccount()) return null;
        long now = System.currentTimeMillis();
        if ((cachedSelfBalance == null || now - selfBalanceFetchedAt > SELF_BALANCE_TTL_MS) && !selfBalanceInFlight) {
            selfBalanceInFlight = true;
            wallet((ok, bal, err) -> {
                selfBalanceInFlight = false;
                if (ok) {
                    cachedSelfBalance = bal;
                    selfBalanceFetchedAt = System.currentTimeMillis();
                }
            });
        }
        return cachedSelfBalance;
    }

    public static void wallet(Callback<Long> cb) {
        getJson("/v1/wallet?token=" + enc(PmChatClient.getConfig().backendToken), json -> {
            long balance = json != null && json.has("balance") ? json.get("balance").getAsLong() : 0;
            run(cb, json != null, balance, json != null ? null : "request failed");
        });
    }

    // ---------- подарки (каталог/инвентарь) ----------

    public static final class Gift {
        public final String id;
        public final String name;
        public final String icon;
        public final long price;

        Gift(String id, String name, String icon, long price) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.price = price;
        }
    }

    public static final class ReceivedGift {
        public final String giftId;
        public final String from;

        ReceivedGift(String giftId, String from) {
            this.giftId = giftId;
            this.from = from;
        }
    }

    private static volatile java.util.List<Gift> cachedCatalog = null;
    private static volatile boolean catalogInFlight = false;

    /** Каталог подарков — кэшируется один раз (цены не меняются на лету). */
    public static java.util.List<Gift> cachedCatalog() {
        if (!isConfigured()) return java.util.List.of();
        if (cachedCatalog == null && !catalogInFlight) {
            catalogInFlight = true;
            getJson("/v1/catalog", json -> {
                catalogInFlight = false;
                if (json == null || !json.has("gifts")) return;
                java.util.List<Gift> list = new java.util.ArrayList<>();
                for (var el : json.getAsJsonArray("gifts")) {
                    JsonObject g = el.getAsJsonObject();
                    list.add(new Gift(
                            g.get("id").getAsString(),
                            g.has("name") ? g.get("name").getAsString() : g.get("id").getAsString(),
                            g.has("icon") ? g.get("icon").getAsString() : "*",
                            g.get("price").getAsLong()));
                }
                cachedCatalog = list;
            });
        }
        return cachedCatalog != null ? cachedCatalog : java.util.List.of();
    }

    private static final class InboxEntry {
        final java.util.List<ReceivedGift> gifts;
        final long at;

        InboxEntry(java.util.List<ReceivedGift> gifts, long at) {
            this.gifts = gifts;
            this.at = at;
        }
    }

    private static final java.util.Map<String, InboxEntry> INBOX_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> INBOX_IN_FLIGHT = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final long INBOX_TTL_MS = 15_000L;

    /** Полученные подарки игрока (для витрины в профиле), с фоновым обновлением по TTL. */
    public static java.util.List<ReceivedGift> cachedGiftInbox(String username) {
        if (!isConfigured() || !hasAccount() || username == null || username.isBlank()) return java.util.List.of();
        String key = username.toLowerCase(java.util.Locale.ROOT);
        InboxEntry e = INBOX_CACHE.get(key);
        boolean stale = e == null || System.currentTimeMillis() - e.at > INBOX_TTL_MS;
        if (stale && INBOX_IN_FLIGHT.add(key)) {
            String path = "/v1/gift/inbox?token=" + enc(PmChatClient.getConfig().backendToken) + "&username=" + enc(username);
            getJson(path, json -> {
                INBOX_IN_FLIGHT.remove(key);
                if (json == null || !json.has("gifts")) return;
                java.util.List<ReceivedGift> list = new java.util.ArrayList<>();
                for (var el : json.getAsJsonArray("gifts")) {
                    JsonObject g = el.getAsJsonObject();
                    list.add(new ReceivedGift(g.get("giftId").getAsString(), g.get("from").getAsString()));
                }
                INBOX_CACHE.put(key, new InboxEntry(list, System.currentTimeMillis()));
            });
        }
        return e != null ? e.gifts : java.util.List.of();
    }

    /** Купить подарок {@code giftId} и отправить игроку {@code target}. */
    public static void sendGift(String target, String giftId, Callback<Long> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("targetUsername", target);
        body.addProperty("giftId", giftId);
        postJson("/v1/gift/send", body, resp -> {
            // баланс мог измениться — забудем кэш, следующий cachedSelfBalance() перечитает
            cachedSelfBalance = null;
        }, (ok, v, err) -> {
            if (ok) INBOX_CACHE.remove(target == null ? "" : target.toLowerCase(java.util.Locale.ROOT));
            if (cb != null) cb.onResult(ok, null, err);
        });
    }

    // ---------- публичный профиль: галочка верификации + официальный аккаунт ----------

    public static void accountInfo(String username, Callback<AccountInfo> cb) {
        getJson("/v1/account/" + enc(username), json -> {
            if (json == null) {
                run(cb, false, null, "request failed");
                return;
            }
            AccountInfo info = new AccountInfo(
                    json.has("username") ? json.get("username").getAsString() : username,
                    json.has("verified") && json.get("verified").getAsBoolean(),
                    json.has("official") && json.get("official").getAsBoolean(),
                    json.has("avatarUrl") && !json.get("avatarUrl").isJsonNull() ? json.get("avatarUrl").getAsString() : null,
                    json.has("lastSeen") && !json.get("lastSeen").isJsonNull()
                            ? parseIsoMillis(json.get("lastSeen").getAsString()) : 0L,
                    json.has("sharePrecise") && json.get("sharePrecise").getAsBoolean());
            run(cb, true, info, null);
        });
    }

    /**
     * Переключает свой точный статус «был(а) N часов/дней назад» (взаимно — см.
     * {@link #humanizeLastSeen}). Сбрасывает свой кэш, чтобы UI сразу подхватил.
     */
    public static void setPrecisePresence(boolean sharePrecise, Callback<Void> cb) {
        if (!isConfigured() || !hasAccount()) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("sharePrecise", sharePrecise);
        postJson("/v1/account/privacy", body, resp -> {
            String self = PmChatClient.selfName();
            if (self != null) ACCOUNT_CACHE.remove(self.toLowerCase(java.util.Locale.ROOT));
        }, cb);
    }

    /** «Пинг» присутствия — держит lastSeen свежим, пока открыт мессенджер (см. PmChatClient). */
    public static void ping() {
        if (!isConfigured() || !hasAccount()) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        postJson("/v1/account/ping", body, null, null);
    }

    /** Явный сигнал «вышел» при дисконнекте — чтобы статус не «висел» в «в сети» до истечения окна пинга. */
    public static void goOffline() {
        if (!isConfigured() || !hasAccount()) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        postJson("/v1/account/offline", body, null, null);
    }

    // ---------- рассылки официального аккаунта ----------

    /** Опрашивает новые рассылки один раз (id больше lastBroadcastId) и зовёт onEach(from, message) на игровом потоке. */
    public static void pollBroadcastsOnce(BiConsumer<String, String> onEach) {
        if (!isConfigured()) return;
        long since = PmChatClient.getConfig().lastBroadcastId;
        getJson("/v1/broadcast?since=" + since, json -> {
            if (json == null || !json.has("broadcasts")) return;
            long maxId = since;
            for (var el : json.getAsJsonArray("broadcasts")) {
                JsonObject b = el.getAsJsonObject();
                long id = b.get("id").getAsLong();
                String from = b.has("from") ? b.get("from").getAsString() : "PocketChat";
                String message = b.has("message") ? b.get("message").getAsString() : "";
                maxId = Math.max(maxId, id);
                MinecraftClient.getInstance().execute(() -> onEach.accept(from, message));
            }
            if (maxId > since) {
                final long newSince = maxId;
                MinecraftClient.getInstance().execute(() -> {
                    PmChatClient.getConfig().lastBroadcastId = newSince;
                    PmChatClient.getConfig().save();
                });
            }
        });
    }

    // ---------- админ-панель ----------

    public static void adminBroadcast(String message, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("message", message);
        postJson("/v1/admin/broadcast", body, null, cb);
    }

    public static void adminGrantCurrency(String targetUsername, long amount, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("targetUsername", targetUsername);
        body.addProperty("amount", amount);
        postJson("/v1/admin/grant-currency", body, null, cb);
    }

    public static void adminVerify(String targetUsername, boolean verified, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("targetUsername", targetUsername);
        body.addProperty("verified", verified);
        postJson("/v1/admin/verify", body, null, cb);
    }

    public static void adminSetOfficial(String targetUsername, boolean official, String avatarUrl, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("targetUsername", targetUsername);
        body.addProperty("official", official);
        if (avatarUrl != null) body.addProperty("avatarUrl", avatarUrl);
        postJson("/v1/admin/set-official", body, null, cb);
    }

    private static JsonObject adminBody() {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("adminSecret", PmChatClient.getConfig().backendAdminSecret);
        return body;
    }

    public static final class AdminAccount {
        public final String username;
        public final long balance;
        public final boolean verified;
        public final boolean official;
        public final long lastSeenAt;
        public final boolean sharePrecise;

        AdminAccount(String username, long balance, boolean verified, boolean official, long lastSeenAt, boolean sharePrecise) {
            this.username = username;
            this.balance = balance;
            this.verified = verified;
            this.official = official;
            this.lastSeenAt = lastSeenAt;
            this.sharePrecise = sharePrecise;
        }
    }

    /** Список зарегистрированных аккаунтов для админ-панели (см. GET /v1/admin/accounts). */
    public static void adminListAccounts(String query, Callback<java.util.List<AdminAccount>> cb) {
        if (!isConfigured()) {
            run(cb, false, null, "backend not configured");
            return;
        }
        String q = query == null ? "" : query.trim();
        String path = "/v1/admin/accounts?token=" + enc(PmChatClient.getConfig().backendToken)
                + "&adminSecret=" + enc(PmChatClient.getConfig().backendAdminSecret)
                + (q.isEmpty() ? "" : "&q=" + enc(q));
        Thread t = new Thread(() -> {
            java.util.List<AdminAccount> list = null;
            String error = null;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base() + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET().build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 == 2) {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    list = new java.util.ArrayList<>();
                    if (json.has("accounts")) {
                        for (var el : json.getAsJsonArray("accounts")) {
                            JsonObject a = el.getAsJsonObject();
                            list.add(new AdminAccount(
                                    a.get("username").getAsString(),
                                    a.get("balance").getAsLong(),
                                    a.has("verified") && a.get("verified").getAsBoolean(),
                                    a.has("official") && a.get("official").getAsBoolean(),
                                    a.has("lastSeen") && !a.get("lastSeen").isJsonNull()
                                            ? parseIsoMillis(a.get("lastSeen").getAsString()) : 0L,
                                    a.has("sharePrecise") && a.get("sharePrecise").getAsBoolean()));
                        }
                    }
                } else {
                    JsonObject json = resp.body().isBlank() ? null : JsonParser.parseString(resp.body()).getAsJsonObject();
                    error = json != null && json.has("error") ? json.get("error").getAsString() : ("HTTP " + resp.statusCode());
                }
            } catch (Exception e) {
                error = e.toString();
                PmChatClient.LOGGER.debug("PmBackend admin/accounts failed: {}", e.toString());
            }
            java.util.List<AdminAccount> finalList = list;
            String finalError = error;
            run(cb, finalList != null, finalList, finalError);
        }, "pmchat-backend-admin-accounts");
        t.setDaemon(true);
        t.start();
    }

    // ---------- HTTP-обвязка ----------

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static void getJson(String path, java.util.function.Consumer<JsonObject> onResponse) {
        if (!isConfigured()) {
            onResponse.accept(null);
            return;
        }
        Thread t = new Thread(() -> {
            JsonObject result = null;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base() + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET().build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 == 2) {
                    result = JsonParser.parseString(resp.body()).getAsJsonObject();
                }
            } catch (Exception e) {
                PmChatClient.LOGGER.debug("PmBackend GET {} failed: {}", path, e.toString());
            }
            onResponse.accept(result);
        }, "pmchat-backend-get");
        t.setDaemon(true);
        t.start();
    }

    /** onSuccess (если не null) вызывается на игровом потоке до cb, для применения побочных эффектов (сохранить токен и т.п.). */
    private static <T> void postJson(String path, JsonObject body,
                                      java.util.function.Consumer<JsonObject> onSuccess, Callback<T> cb) {
        if (!isConfigured()) {
            run(cb, false, null, "backend not configured");
            return;
        }
        Thread t = new Thread(() -> {
            JsonObject json = null;
            int status = -1;
            String error = null;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base() + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                status = resp.statusCode();
                if (!resp.body().isBlank()) {
                    json = JsonParser.parseString(resp.body()).getAsJsonObject();
                }
                if (status / 100 != 2) {
                    error = json != null && json.has("error") ? json.get("error").getAsString() : ("HTTP " + status);
                }
            } catch (Exception e) {
                error = e.toString();
                PmChatClient.LOGGER.debug("PmBackend POST {} failed: {}", path, e.toString());
            }
            boolean ok = status / 100 == 2 && error == null;
            JsonObject finalJson = json;
            String finalError = error;
            MinecraftClient.getInstance().execute(() -> {
                if (ok && onSuccess != null) onSuccess.accept(finalJson);
                run(cb, ok, null, finalError);
            });
        }, "pmchat-backend-post");
        t.setDaemon(true);
        t.start();
    }

    @SuppressWarnings("unchecked")
    private static <T> void run(Callback<T> cb, boolean ok, Object value, String error) {
        if (cb == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Runnable r = () -> cb.onResult(ok, (T) value, error);
        if (client.isOnThread()) {
            r.run();
        } else {
            client.execute(r);
        }
    }
}
