package com.pmchat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BiConsumer;

/**
 * Клиент отдельного бэкенда PocketChat (репозиторий {@code server-pocketchat}):
 * логин/пароль (не Mojang), верификация (зелёная галочка), официальный аккаунт
 * и админ-панель. Всё выключено, если {@link PmConfig#backendUrl} пусто — мод
 * продолжает работать как раньше, через обычные строки чата.
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
        /** Модерация (см. PmAdminScreen): временно замучен / забанен. */
        public final boolean muted;
        public final boolean banned;
        /** Ключ должности, назначенной вручную в админ-панели (null — не назначена, см. {@link RoleDef}). */
        public final String roleKey;
        /** Это бот (см. таблицу bots) — ЛС ему идут через Bot API бэкенда, а не через /m. */
        public final boolean bot;

        AccountInfo(String username, boolean verified, boolean official, String avatarUrl, long lastSeenAt,
                    boolean sharePrecise, boolean muted, boolean banned, String roleKey, boolean bot) {
            this.username = username;
            this.verified = verified;
            this.official = official;
            this.avatarUrl = avatarUrl;
            this.lastSeenAt = lastSeenAt;
            this.sharePrecise = sharePrecise;
            this.muted = muted;
            this.banned = banned;
            this.roleKey = roleKey;
            this.bot = bot;
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
    public static net.minecraft.network.chat.Component humanizeLastSeen(long lastSeenAtMs) {
        return humanizeLastSeen(lastSeenAtMs, false);
    }

    /**
     * @param precise точный вариант («N ч./дн. назад») вместо расплывчатых «недавно/на этой
     *                неделе/давно» — вызывающий код должен передавать true только когда ОБЕ
     *                стороны включили {@link PmConfig#preciseLastSeen} (см. AccountInfo.sharePrecise).
     */
    public static net.minecraft.network.chat.Component humanizeLastSeen(long lastSeenAtMs, boolean precise) {
        if (lastSeenAtMs <= 0) return net.minecraft.network.chat.Component.translatable("pmchat.profile.lastseen.unknown");
        long diff = System.currentTimeMillis() - lastSeenAtMs;
        if (diff < 90_000L) return net.minecraft.network.chat.Component.translatable("pmchat.profile.lastseen.online");
        if (precise) {
            if (diff < 3_600_000L) {
                return net.minecraft.network.chat.Component.translatable("pmchat.profile.lastseen.minutes", diff / 60_000L);
            }
            if (diff < 24 * 3_600_000L) {
                return net.minecraft.network.chat.Component.translatable("pmchat.profile.lastseen.hours", diff / 3_600_000L);
            }
            if (diff < 30L * 24 * 3_600_000L) {
                return net.minecraft.network.chat.Component.translatable("pmchat.profile.lastseen.days", diff / (24 * 3_600_000L));
            }
            return net.minecraft.network.chat.Component.translatable("pmchat.profile.lastseen.long");
        }
        if (diff < 3_600_000L) return net.minecraft.network.chat.Component.translatable("pmchat.profile.lastseen.recent");
        if (diff < 7 * 24 * 3_600_000L) return net.minecraft.network.chat.Component.translatable("pmchat.profile.lastseen.week");
        return net.minecraft.network.chat.Component.translatable("pmchat.profile.lastseen.long");
    }

    // ---------- логин/пароль (своя система, не Mojang) ----------

    public static void register(String username, String password, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        postJson("/v1/register", body, resp -> {
            if (resp == null) return;
            // Свежий аккаунт не должен разом получить всю историю прошлых рассылок:
            // baseline выставляем ДО токена (в этом же ответе), чтобы не было гонки
            // с опросом рассылок в тике — раньше id брался отдельным запросом уже
            // после того, как hasAccount() становился true, и всё успевало прийти.
            if (resp.has("broadcastBaseline") && !resp.get("broadcastBaseline").isJsonNull()) {
                PmChatClient.getConfig().lastBroadcastId = resp.get("broadcastBaseline").getAsLong();
            }
            if (resp.has("token")) PmChatClient.getConfig().backendToken = resp.get("token").getAsString();
            PmChatClient.getConfig().save();
            applySelfStatus(resp);
        }, cb);
    }

    public static void login(String username, String password, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        postJson("/v1/login", body, resp -> {
            if (resp == null) return;
            // На свежей установке (lastBroadcastId ещё 0) вход в существующий
            // аккаунт тоже не должен вывалить всю историю рассылок — стартуем с
            // текущего baseline. Уже настроенный клиент своё значение не трогаем.
            if (PmChatClient.getConfig().lastBroadcastId <= 0
                    && resp.has("broadcastBaseline") && !resp.get("broadcastBaseline").isJsonNull()) {
                PmChatClient.getConfig().lastBroadcastId = resp.get("broadcastBaseline").getAsLong();
            }
            if (resp.has("token")) PmChatClient.getConfig().backendToken = resp.get("token").getAsString();
            PmChatClient.getConfig().save();
            applySelfStatus(resp);
        }, cb);
    }

    // ---------- модерация: свой статус (мут/бан), обновляется через ping/login ----------

    private static volatile boolean selfMuted = false;
    private static volatile long selfMutedUntilAt = 0L;
    private static volatile boolean selfBanned = false;
    /** Является ли ЗАЛОГИНЕННЫЙ аккаунт бэкенда админом — проверено сервером по токену,
     *  а не по нику Minecraft (см. server-pocketchat ADMIN_USERNAME). Обновляется на
     *  login/register/ping. */
    private static volatile boolean selfAdmin = false;

    private static void applySelfStatus(JsonObject resp) {
        if (resp == null) return;
        boolean wasMuted = selfMuted;
        boolean wasBanned = selfBanned;
        if (resp.has("muted")) selfMuted = resp.get("muted").getAsBoolean();
        if (resp.has("mutedUntil") && !resp.get("mutedUntil").isJsonNull()) {
            selfMutedUntilAt = parseIsoMillis(resp.get("mutedUntil").getAsString());
        }
        if (resp.has("banned")) selfBanned = resp.get("banned").getAsBoolean();
        if (resp.has("admin")) selfAdmin = resp.get("admin").getAsBoolean();
        // Уведомляем только на переходе false→true — иначе на каждом пинге (раз в
        // минуту, пока мут/бан ещё активен) сообщение сыпалось бы заново.
        if (!wasBanned && selfBanned) {
            PmChatClient.announceRestriction(true, 0L);
        } else if (!wasMuted && selfMuted && selfMutedUntilAt > System.currentTimeMillis()) {
            PmChatClient.announceRestriction(false, selfMutedUntilAt);
        }
    }

    /**
     * Замучен или забанен прямо сейчас — единственный способ (клиентская сторона)
     * заблокировать отправку ЛС/голосовых/фото, раз бэкенд не видит {@code /m}.
     * Статус обновляется раз в минуту через {@link #ping()} (и сразу после
     * {@link #login}), так что применяется с задержкой до минуты.
     */
    public static boolean selfRestricted() {
        if (!isConfigured() || !hasAccount()) return false;
        return selfBanned || (selfMuted && selfMutedUntilAt > System.currentTimeMillis());
    }

    /**
     * Подтверждён ли ЭТОТ клиент сервером как админ: залогинен в аккаунт бэкенда
     * {@code ADMIN_USERNAME} (нужен пароль — т.е. регистрация/вход), а не просто
     * зашёл в Minecraft под ником админа. Именно это, а не совпадение ника,
     * должно открывать админ-панель.
     */
    public static boolean isSelfAdmin() {
        return isConfigured() && hasAccount() && selfAdmin;
    }

    public static void setPassword(String password, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("password", password);
        postJson("/v1/set-password", body, null, cb);
    }

    // ---------- должности (роли) игроков: назначает админ, отдельно от встроенных C/H/M/E/D ----------

    public static final class RoleDef {
        public final String key;
        public final String name;
        public final String prefix;
        /** Цвет в формате 0xAARRGGBB, разобранный из hex-строки бэкенда (#RRGGBB). */
        public final int color;

        RoleDef(String key, String name, String prefix, int color) {
            this.key = key;
            this.name = name;
            this.prefix = prefix;
            this.color = color;
        }
    }

    private static int parseHexColor(String hex) {
        if (hex == null || hex.isBlank()) return 0xFFFFFFFF;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return h.length() > 6 ? (int) Long.parseLong(h, 16) : 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }

    private static volatile java.util.List<RoleDef> cachedRoleDefs = null;
    private static volatile boolean rolesInFlight = false;

    /** Список должностей, созданных админом — кэшируется один раз за сессию. */
    public static java.util.List<RoleDef> cachedRoles() {
        if (!isConfigured()) return java.util.List.of();
        if (cachedRoleDefs == null && !rolesInFlight) {
            rolesInFlight = true;
            getJson("/v1/roles", json -> {
                rolesInFlight = false;
                if (json == null || !json.has("roles")) return;
                java.util.List<RoleDef> list = new java.util.ArrayList<>();
                for (var el : json.getAsJsonArray("roles")) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new RoleDef(
                            o.get("key").getAsString(),
                            o.get("name").getAsString(),
                            o.has("prefix") ? o.get("prefix").getAsString() : "",
                            parseHexColor(o.has("color") ? o.get("color").getAsString() : null)));
                }
                cachedRoleDefs = list;
            });
        }
        return cachedRoleDefs != null ? cachedRoleDefs : java.util.List.of();
    }

    /** Должность игрока, назначенная вручную (см. {@link AccountInfo#roleKey}), или {@code null}. */
    public static RoleDef roleOf(String username) {
        if (!isConfigured() || username == null || username.isBlank()) return null;
        AccountInfo info = cachedAccountInfo(username);
        if (info == null || info.roleKey == null || info.roleKey.isBlank()) return null;
        for (RoleDef r : cachedRoles()) {
            if (r.key.equalsIgnoreCase(info.roleKey)) return r;
        }
        return null;
    }

    public static void adminListRoles(Callback<java.util.List<RoleDef>> cb) {
        String path = "/v1/roles";
        getJson(path, json -> {
            if (json == null || !json.has("roles")) {
                run(cb, false, null, "request failed");
                return;
            }
            java.util.List<RoleDef> list = new java.util.ArrayList<>();
            for (var el : json.getAsJsonArray("roles")) {
                JsonObject o = el.getAsJsonObject();
                list.add(new RoleDef(
                        o.get("key").getAsString(),
                        o.get("name").getAsString(),
                        o.has("prefix") ? o.get("prefix").getAsString() : "",
                        parseHexColor(o.has("color") ? o.get("color").getAsString() : null)));
            }
            run(cb, true, list, null);
        });
    }

    public static void adminUpsertRole(String key, String name, String prefix, String colorHex, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("key", key);
        body.addProperty("name", name);
        body.addProperty("prefix", prefix);
        body.addProperty("color", colorHex);
        postJson("/v1/admin/roles/upsert", body, resp -> cachedRoleDefs = null, cb);
    }

    public static void adminDeleteRole(String key, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("key", key);
        postJson("/v1/admin/roles/delete", body, resp -> {
            cachedRoleDefs = null;
            ACCOUNT_CACHE.clear();
        }, cb);
    }

    public static void adminAssignRole(String username, String roleKey, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("username", username);
        if (roleKey != null && !roleKey.isBlank()) body.addProperty("roleKey", roleKey);
        postJson("/v1/admin/roles/assign", body, resp ->
                ACCOUNT_CACHE.remove(username == null ? "" : username.toLowerCase(java.util.Locale.ROOT)), cb);
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
                    json.has("sharePrecise") && json.get("sharePrecise").getAsBoolean(),
                    json.has("muted") && json.get("muted").getAsBoolean(),
                    json.has("banned") && json.get("banned").getAsBoolean(),
                    json.has("roleKey") && !json.get("roleKey").isJsonNull() ? json.get("roleKey").getAsString() : null,
                    json.has("bot") && json.get("bot").getAsBoolean());
            run(cb, true, info, null);
        });
    }

    // ---------- боты (как в Telegram): владелец создаёт/удаляет, ЛС боту идут сюда ----------

    /** Бот, принадлежащий текущему аккаунту (см. server-pocketchat, таблица bots). */
    public static final class BotInfo {
        public final String username;
        public final String name;
        /** Bot-токен — показываем владельцу, он вставляет его в свою программу-бота. */
        public final String token;

        BotInfo(String username, String name, String token) {
            this.username = username;
            this.name = name;
            this.token = token;
        }
    }

    /** Это бот (по кэшу публичного профиля) — ЛС ему шлём через Bot API, а не через /m. */
    public static boolean isBot(String username) {
        AccountInfo info = cachedAccountInfo(username);
        return info != null && info.bot;
    }

    public static void createBot(String botUsername, String name, Callback<BotInfo> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("botUsername", botUsername);
        body.addProperty("name", name);
        postJson("/v1/bots/create", body,
                resp -> {
                    if (resp != null && resp.has("token")) {
                        run(cb, true, new BotInfo(
                                resp.has("botUsername") ? resp.get("botUsername").getAsString() : botUsername,
                                name, resp.get("token").getAsString()), null);
                    }
                },
                (ok, v, err) -> { if (!ok) run(cb, false, null, err); });
    }

    public static void listBots(Callback<java.util.List<BotInfo>> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        getJson("/v1/bots?token=" + enc(PmChatClient.getConfig().backendToken), json -> {
            java.util.List<BotInfo> list = new java.util.ArrayList<>();
            if (json != null && json.has("bots")) {
                for (com.google.gson.JsonElement el : json.getAsJsonArray("bots")) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new BotInfo(
                            o.get("username").getAsString(),
                            o.has("name") ? o.get("name").getAsString() : "",
                            o.has("token") ? o.get("token").getAsString() : ""));
                }
            }
            run(cb, json != null, list, json == null ? "request failed" : null);
        });
    }

    public static void deleteBot(String botUsername, Callback<Void> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("botUsername", botUsername);
        postJson("/v1/bots/delete", body, null, cb);
    }

    /**
     * Редактирует своего бота: любое из {@code newUsername}/{@code name} можно
     * оставить null/пустым, чтобы не менять — меняется только присланное.
     * {@code regenerateToken} — перевыпустить токен (старый сразу перестаёт
     * работать, как «Revoke token» в BotFather).
     */
    public static void editBot(String botUsername, String newUsername, String name,
                                boolean regenerateToken, Callback<BotInfo> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("botUsername", botUsername);
        if (newUsername != null && !newUsername.isBlank()) body.addProperty("newBotUsername", newUsername);
        if (name != null) body.addProperty("name", name);
        if (regenerateToken) body.addProperty("regenerateToken", true);
        postJson("/v1/bots/edit", body,
                resp -> {
                    if (resp != null && resp.has("token") && resp.has("botUsername")) {
                        run(cb, true, new BotInfo(
                                resp.get("botUsername").getAsString(),
                                resp.has("name") ? resp.get("name").getAsString() : "",
                                resp.get("token").getAsString()), null);
                    }
                },
                (ok, v, err) -> { if (!ok) run(cb, false, null, err); });
    }

    /** ЛС от игрока боту — уходит в очередь входящих бота (bot_updates), а не через /m. */
    public static void sendToBot(String botUsername, String wire, Callback<Void> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("botUsername", botUsername);
        body.addProperty("wire", wire);
        postJson("/v1/bots/message", body, null, cb);
    }

    /** Публичный поиск ботов по подстроке @username (без токенов/владельцев). */
    public static void searchBots(String query, Callback<java.util.List<BotInfo>> cb) {
        getJson("/v1/bots/search?q=" + enc(query), json -> {
            java.util.List<BotInfo> list = new java.util.ArrayList<>();
            if (json != null && json.has("bots")) {
                for (com.google.gson.JsonElement el : json.getAsJsonArray("bots")) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new BotInfo(o.get("username").getAsString(),
                            o.has("name") ? o.get("name").getAsString() : "", ""));
                }
            }
            run(cb, json != null, list, json == null ? "request failed" : null);
        });
    }

    // ---------- магазин готовых ботов-файлов ----------

    public static final class BotListing {
        public final long id;
        public final String owner;
        public final String name;
        public final String description;
        public final String status;    // pending/approved/rejected — только в "мои заявки"

        BotListing(long id, String owner, String name, String description, String status) {
            this.id = id;
            this.owner = owner;
            this.name = name;
            this.description = description;
            this.status = status;
        }
    }

    /** Загружает произвольный файл на бэкенд (POST /v1/media) — для заявки в магазин ботов. */
    public static void uploadBotFile(java.nio.file.Path file, Callback<String> cb) {
        if (!isConfigured()) { run(cb, false, null, "backend not configured"); return; }
        Thread t = new Thread(() -> {
            String fileId = null;
            String error = null;
            try {
                byte[] data = java.nio.file.Files.readAllBytes(file);
                String filename = file.getFileName().toString();
                String boundary = "----pmchat" + System.nanoTime();
                java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
                String head = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\n";
                body.write(head.getBytes(StandardCharsets.UTF_8));
                body.write(data);
                body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base() + "/v1/media"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 == 2) {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (json.has("id")) fileId = json.get("id").getAsString();
                    else error = "no file id in response";
                } else {
                    error = "HTTP " + resp.statusCode();
                }
            } catch (Exception e) {
                error = e.toString();
            }
            String finalFileId = fileId;
            String finalError = error;
            run(cb, finalFileId != null, finalFileId, finalError);
        }, "pmchat-botstore-upload");
        t.setDaemon(true);
        t.start();
    }

    public static void submitBotListing(String name, String description, String fileId, Callback<Long> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("name", name);
        body.addProperty("description", description);
        body.addProperty("fileId", fileId);
        postJson("/v1/botstore/submit", body,
                resp -> {
                    if (resp != null && resp.has("id")) run(cb, true, resp.get("id").getAsLong(), null);
                },
                (ok, v, err) -> { if (!ok) run(cb, false, null, err); });
    }

    /** Мои заявки (любого статуса — pending/approved/rejected). */
    public static void myBotListings(Callback<java.util.List<BotListing>> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        getJson("/v1/botstore/mine?token=" + enc(PmChatClient.getConfig().backendToken), json -> {
            java.util.List<BotListing> list = new java.util.ArrayList<>();
            if (json != null && json.has("listings")) {
                for (com.google.gson.JsonElement el : json.getAsJsonArray("listings")) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new BotListing(o.get("id").getAsLong(), null,
                            o.get("name").getAsString(),
                            o.has("description") ? o.get("description").getAsString() : "",
                            o.get("status").getAsString()));
                }
            }
            run(cb, json != null, list, json == null ? "request failed" : null);
        });
    }

    /** Открытый рынок — только одобренные. query — необязательный поиск по названию/автору. */
    public static void botstoreMarket(String query, Callback<java.util.List<BotListing>> cb) {
        String q = query == null ? "" : query.trim();
        getJson("/v1/botstore/market" + (q.isEmpty() ? "" : "?q=" + enc(q)), json -> {
            java.util.List<BotListing> list = new java.util.ArrayList<>();
            if (json != null && json.has("listings")) {
                for (com.google.gson.JsonElement el : json.getAsJsonArray("listings")) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new BotListing(o.get("id").getAsLong(), o.get("owner").getAsString(),
                            o.get("name").getAsString(),
                            o.has("description") ? o.get("description").getAsString() : "",
                            null));
                }
            }
            run(cb, json != null, list, json == null ? "request failed" : null);
        });
    }

    /** Покупка (или получение бесплатного) листинга — cb отдаёт fileId для скачивания. */
    public static void buyBotListing(long listingId, Callback<String> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("listingId", listingId);
        postJson("/v1/botstore/buy", body,
                resp -> {
                    if (resp != null && resp.has("fileId")) run(cb, true, resp.get("fileId").getAsString(), null);
                },
                (ok, v, err) -> { if (!ok) run(cb, false, null, err); });
    }

    /** URL для скачивания купленного файла (браузером) — GET /v1/media/:id отдаёт сырые байты. */
    public static String botFileUrl(String fileId) {
        return base() + "/v1/media/" + fileId;
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
        postJson("/v1/account/ping", body, PmBackend::applySelfStatus, null);
    }

    /** Явный сигнал «вышел» при дисконнекте — чтобы статус не «висел» в «в сети» до истечения окна пинга. */
    public static void goOffline() {
        if (!isConfigured() || !hasAccount()) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        postJson("/v1/account/offline", body, null, null);
    }

    // ---------- жалобы и поддержка ----------

    /** Пожаловаться на игрока — видно только админу ({@link #adminListReports}). */
    public static void report(String targetUsername, String reason, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("targetUsername", targetUsername);
        body.addProperty("reason", reason);
        postJson("/v1/report", body, null, cb);
    }

    /** Обращение в поддержку — видно только админу ({@link #adminListSupport}). */
    public static void support(String message, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("message", message);
        postJson("/v1/support", body, null, cb);
    }

    // ---------- переключатели фич (GET /v1/features, публичное) ----------

    private static final java.util.Map<String, Boolean> FEATURE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long featuresFetchedAt = 0;
    private static volatile boolean featuresInFlight = false;
    private static final long FEATURES_TTL_MS = 30_000L;

    /**
     * Включена ли фича ({@code reports}/{@code support}) прямо сейчас —
     * читает кэш (обновляется в фоне раз в 30с), по умолчанию {@code true} (в т.ч. пока
     * бэкенд не настроен), чтобы ничего не блокировать без явного отключения админом.
     */
    public static boolean isFeatureEnabled(String name) {
        if (!isConfigured()) return true;
        long now = System.currentTimeMillis();
        if (now - featuresFetchedAt > FEATURES_TTL_MS && !featuresInFlight) {
            featuresInFlight = true;
            getJson("/v1/features", json -> {
                featuresInFlight = false;
                featuresFetchedAt = System.currentTimeMillis();
                if (json == null || !json.has("features")) return;
                JsonObject f = json.getAsJsonObject("features");
                for (var e : f.entrySet()) {
                    FEATURE_CACHE.put(e.getKey(), e.getValue().getAsBoolean());
                }
            });
        }
        return FEATURE_CACHE.getOrDefault(name, true);
    }

    // ---------- правила мода (GET /v1/rules, публичное) — редактируются без релиза ----------

    public static final class RuleLocale {
        public final String eula, freedom, header, footer;
        public final java.util.List<String> rules;

        public RuleLocale(String eula, String freedom, String header, java.util.List<String> rules, String footer) {
            this.eula = eula;
            this.freedom = freedom;
            this.header = header;
            this.rules = rules;
            this.footer = footer;
        }
    }

    public static final class RulesContent {
        public final int version;
        public final RuleLocale ru, en;

        public RulesContent(int version, RuleLocale ru, RuleLocale en) {
            this.version = version;
            this.ru = ru;
            this.en = en;
        }

        /** Локаль под текущий язык клиента (см. PmChatClient.isRussian). */
        public RuleLocale active() {
            return PmChatClient.isRussian() ? ru : en;
        }
    }

    private static volatile RulesContent cachedRules = null;
    private static volatile long rulesFetchedAt = 0;
    private static volatile boolean rulesInFlight = false;
    private static final long RULES_TTL_MS = 60_000L;

    private static RuleLocale parseRuleLocale(JsonObject o) {
        if (o == null) return null;
        java.util.List<String> rules = new java.util.ArrayList<>();
        if (o.has("rules")) {
            for (var el : o.getAsJsonArray("rules")) rules.add(el.getAsString());
        }
        return new RuleLocale(
                o.has("eula") ? o.get("eula").getAsString() : "",
                o.has("freedom") ? o.get("freedom").getAsString() : "",
                o.has("header") ? o.get("header").getAsString() : "",
                rules,
                o.has("footer") ? o.get("footer").getAsString() : "");
    }

    /**
     * Текст правил мода (экран при первом запуске), с фоновым обновлением по TTL.
     * Возвращает {@code null}, пока бэкенд не настроен или ответ ещё не пришёл —
     * вызывающий код (PmRulesScreen) в этом случае должен показать встроенный
     * запасной текст, а не ждать.
     */
    public static RulesContent cachedRules() {
        if (!isConfigured()) return null;
        long now = System.currentTimeMillis();
        if ((cachedRules == null || now - rulesFetchedAt > RULES_TTL_MS) && !rulesInFlight) {
            rulesInFlight = true;
            getJson("/v1/rules", json -> {
                rulesInFlight = false;
                rulesFetchedAt = System.currentTimeMillis();
                RulesContent parsed = parseRulesContent(json);
                if (parsed != null) cachedRules = parsed;
            });
        }
        return cachedRules;
    }

    private static RulesContent parseRulesContent(JsonObject json) {
        if (json == null || !json.has("ru") || !json.has("en")) return null;
        return new RulesContent(
                json.has("version") ? json.get("version").getAsInt() : 1,
                parseRuleLocale(json.getAsJsonObject("ru")),
                parseRuleLocale(json.getAsJsonObject("en")));
    }

    /** Свежий (не кэшированный) фетч — для экрана редактирования правил в админ-панели. */
    public static void fetchRulesForEdit(Callback<RulesContent> cb) {
        getJson("/v1/rules", json -> {
            RulesContent parsed = parseRulesContent(json);
            if (parsed != null) {
                cachedRules = parsed;
                rulesFetchedAt = System.currentTimeMillis();
                run(cb, true, parsed, null);
            } else {
                run(cb, false, null, "request failed");
            }
        });
    }

    private static JsonObject ruleLocaleJson(RuleLocale loc) {
        JsonObject o = new JsonObject();
        o.addProperty("eula", loc.eula);
        o.addProperty("freedom", loc.freedom);
        o.addProperty("header", loc.header);
        o.addProperty("footer", loc.footer);
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (String r : loc.rules) arr.add(r);
        o.add("rules", arr);
        return o;
    }

    /** Меняет текст правил целиком (оба языка обязательны) — версия растёт, старые принятия сбрасываются. */
    public static void adminSetRules(RuleLocale ru, RuleLocale en, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.add("ru", ruleLocaleJson(ru));
        body.add("en", ruleLocaleJson(en));
        postJson("/v1/admin/rules", body, resp -> rulesFetchedAt = 0, cb);
    }

    // ---------- рассылки официального аккаунта ----------

    /** Опрашивает новые рассылки один раз (id больше lastBroadcastId) и зовёт onEach(from, message) на игровом потоке. */
    public static void pollBroadcastsOnce(BiConsumer<String, String> onEach) {
        if (!isConfigured()) return;
        long since = PmChatClient.getConfig().lastBroadcastId;
        String self = PmChatClient.selfName();
        getJson("/v1/broadcast?since=" + since + (self != null ? "&username=" + enc(self) : ""), json -> {
            if (json == null || !json.has("broadcasts")) return;
            long maxId = since;
            for (var el : json.getAsJsonArray("broadcasts")) {
                JsonObject b = el.getAsJsonObject();
                long id = b.get("id").getAsLong();
                String from = b.has("from") ? b.get("from").getAsString() : "PocketChat";
                String message = b.has("message") ? b.get("message").getAsString() : "";
                maxId = Math.max(maxId, id);
                Minecraft.getInstance().execute(() -> onEach.accept(from, message));
            }
            if (maxId > since) {
                final long newSince = maxId;
                Minecraft.getInstance().execute(() -> {
                    PmChatClient.getConfig().lastBroadcastId = newSince;
                    PmChatClient.getConfig().save();
                });
            }
        });
    }

    // ---------- почтовый ящик офлайн-сообщений ----------

    /** Сообщение, положенное в ящик, пока получателя не было в таб-листе отправителя. */
    public static final class MailboxMessage {
        public final String from;
        public final String wire;
        public final long at;

        MailboxMessage(String from, String wire, long at) {
            this.from = from;
            this.wire = wire;
            this.at = at;
        }
    }

    /**
     * Кладёт {@code wire} в ящик {@code target} вместо {@code /m} — вызывается, когда
     * получателя нет в таб-листе текущего Minecraft-сервера (см. {@code PmChatClient#sendMessage}).
     */
    public static void sendMailbox(String target, String wire, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("targetUsername", target);
        body.addProperty("wire", wire);
        postJson("/v1/mailbox/send", body, resp -> { }, cb);
    }

    /** Забирает свои недоставленные сообщения (от старых к новым) и зовёт onEach на игровом потоке. */
    public static void pollMailbox(java.util.function.Consumer<MailboxMessage> onEach) {
        if (!isConfigured() || !hasAccount()) return;
        getJson("/v1/mailbox?token=" + enc(PmChatClient.getConfig().backendToken), json -> {
            if (json == null || !json.has("messages")) return;
            for (var el : json.getAsJsonArray("messages")) {
                JsonObject m = el.getAsJsonObject();
                String from = m.has("from") ? m.get("from").getAsString() : "";
                String wire = m.has("wire") ? m.get("wire").getAsString() : "";
                long at = m.has("at") && !m.get("at").isJsonNull() ? parseIsoMillis(m.get("at").getAsString()) : 0L;
                if (from.isEmpty() || wire.isEmpty()) continue;
                MailboxMessage mm = new MailboxMessage(from, wire, at);
                Minecraft.getInstance().execute(() -> onEach.accept(mm));
            }
        });
    }

    // ---------- анонимные звонки (релей поверх WebSocket, без группы SVC) ----------

    /**
     * Просит бэкенд выдать одноразовый {@code callId} для звонка {@code target}.
     * Собеседник узнаёт о звонке только через {@link #callPoll}, никакого
     * сообщения через {@code /m} не отправляется.
     */
    public static void callInvite(String target, java.util.function.Consumer<String> onCallId) {
        if (!isConfigured() || !hasAccount()) {
            onCallId.accept(null);
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("targetUsername", target);
        postJson("/v1/call/invite", body, resp -> {
            String callId = resp != null && resp.has("callId") ? resp.get("callId").getAsString() : null;
            onCallId.accept(callId);
        }, (ok, v, err) -> {
            if (!ok) onCallId.accept(null);
        });
    }

    /** Короткий опрос: не звонит ли кто-то нам прямо сейчас (callId ещё без нашего сокета). */
    public static void callPoll(java.util.function.BiConsumer<String, String> onResult) {
        if (!isConfigured() || !hasAccount()) return;
        getJson("/v1/call/poll?token=" + enc(PmChatClient.getConfig().backendToken), json -> {
            if (json == null || !json.has("callId")) return;
            String callId = json.get("callId").getAsString();
            String from = json.has("from") ? json.get("from").getAsString() : null;
            if (callId.isBlank() || from == null) return;
            Minecraft.getInstance().execute(() -> onResult.accept(callId, from));
        });
    }

    /** Отменяет/завершает звонок на стороне сигналинга (не влияет на уже открытый WS-сокет). */
    public static void callCancel(String callId) {
        if (!isConfigured() || !hasAccount() || callId == null) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("callId", callId);
        postJson("/v1/call/cancel", body, null, null);
    }

    /** URL WebSocket-релея для звонка (ws:// или wss:// в зависимости от backendUrl), null — бэкенд не настроен. */
    public static String callWsUrl(String callId) {
        if (!isConfigured() || !hasAccount() || callId == null) return null;
        String httpBase = base();
        String wsBase = httpBase.startsWith("https://")
                ? "wss://" + httpBase.substring("https://".length())
                : httpBase.startsWith("http://")
                ? "ws://" + httpBase.substring("http://".length())
                : httpBase;
        return wsBase + "/v1/call/ws?callId=" + enc(callId) + "&token=" + enc(PmChatClient.getConfig().backendToken);
    }

    // ---------- админ-панель ----------

    public static void adminBroadcast(String message, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("message", message);
        postJson("/v1/admin/broadcast", body, null, cb);
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

    /** Личное сообщение от официального аккаунта PocketChat одному игроку (не рассылка всем). */
    public static void adminMessage(String targetUsername, String message, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("targetUsername", targetUsername);
        body.addProperty("message", message);
        postJson("/v1/admin/message", body, null, cb);
    }

    /** Временный мут; {@code minutes <= 0} снимает мут. */
    public static void adminMute(String targetUsername, int minutes, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("targetUsername", targetUsername);
        body.addProperty("minutes", minutes);
        postJson("/v1/admin/mute", body, null, cb);
    }

    public static void adminBan(String targetUsername, boolean banned, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("targetUsername", targetUsername);
        body.addProperty("banned", banned);
        postJson("/v1/admin/ban", body, null, cb);
    }

    /** Включить/выключить фичу целиком ({@code reports}/{@code support}). */
    public static void adminSetFeature(String name, boolean enabled, int minutes, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("name", name);
        body.addProperty("enabled", enabled);
        if (!enabled && minutes > 0) body.addProperty("minutes", minutes);
        postJson("/v1/admin/feature", body, resp -> FEATURE_CACHE.clear(), cb);
    }

    public static final class ReportEntry {
        public final long id;
        public final String reporter;
        public final String target;
        public final String reason;
        public final long at;
        public final boolean resolved;

        ReportEntry(long id, String reporter, String target, String reason, long at, boolean resolved) {
            this.id = id;
            this.reporter = reporter;
            this.target = target;
            this.reason = reason;
            this.at = at;
            this.resolved = resolved;
        }
    }

    public static final class SupportEntry {
        public final long id;
        public final String username;
        public final String message;
        public final long at;
        public final boolean resolved;

        SupportEntry(long id, String username, String message, long at, boolean resolved) {
            this.id = id;
            this.username = username;
            this.message = message;
            this.at = at;
            this.resolved = resolved;
        }
    }

    public static final class AdminStatus {
        public final long uptimeSec;
        public final int accounts;
        public final int onlineNow;
        public final int openReports;
        public final int openTickets;

        AdminStatus(long uptimeSec, int accounts, int onlineNow, int openReports, int openTickets) {
            this.uptimeSec = uptimeSec;
            this.accounts = accounts;
            this.onlineNow = onlineNow;
            this.openReports = openReports;
            this.openTickets = openTickets;
        }
    }

    /** Жалобы для админ-панели, по умолчанию только нерешённые. */
    public static void adminListReports(boolean onlyOpen, Callback<java.util.List<ReportEntry>> cb) {
        String path = "/v1/admin/reports?token=" + enc(PmChatClient.getConfig().backendToken)
                + "&adminSecret=" + enc(PmChatClient.getConfig().backendAdminSecret)
                + "&resolved=" + (onlyOpen ? "false" : "true");
        getJson(path, json -> {
            if (json == null || !json.has("reports")) {
                run(cb, false, null, "request failed");
                return;
            }
            java.util.List<ReportEntry> list = new java.util.ArrayList<>();
            for (var el : json.getAsJsonArray("reports")) {
                JsonObject r = el.getAsJsonObject();
                list.add(new ReportEntry(
                        r.get("id").getAsLong(),
                        r.get("reporter").getAsString(),
                        r.get("target").getAsString(),
                        r.get("reason").getAsString(),
                        r.has("at") && !r.get("at").isJsonNull() ? parseIsoMillis(r.get("at").getAsString()) : 0L,
                        r.has("resolved") && r.get("resolved").getAsBoolean()));
            }
            run(cb, true, list, null);
        });
    }

    public static void adminResolveReport(long id, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("id", id);
        postJson("/v1/admin/report/resolve", body, null, cb);
    }

    /** Обращения в поддержку для админ-панели, по умолчанию только нерешённые. */
    public static void adminListSupport(boolean onlyOpen, Callback<java.util.List<SupportEntry>> cb) {
        String path = "/v1/admin/support?token=" + enc(PmChatClient.getConfig().backendToken)
                + "&adminSecret=" + enc(PmChatClient.getConfig().backendAdminSecret)
                + "&resolved=" + (onlyOpen ? "false" : "true");
        getJson(path, json -> {
            if (json == null || !json.has("tickets")) {
                run(cb, false, null, "request failed");
                return;
            }
            java.util.List<SupportEntry> list = new java.util.ArrayList<>();
            for (var el : json.getAsJsonArray("tickets")) {
                JsonObject t = el.getAsJsonObject();
                list.add(new SupportEntry(
                        t.get("id").getAsLong(),
                        t.get("username").getAsString(),
                        t.get("message").getAsString(),
                        t.has("at") && !t.get("at").isJsonNull() ? parseIsoMillis(t.get("at").getAsString()) : 0L,
                        t.has("resolved") && t.get("resolved").getAsBoolean()));
            }
            run(cb, true, list, null);
        });
    }

    public static void adminResolveSupport(long id, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("id", id);
        postJson("/v1/admin/support/resolve", body, null, cb);
    }

    /** Сводка для дашборда админ-панели: аптайм бэкенда + счётчики аккаунтов/жалоб/тикетов. */
    public static void adminStatus(Callback<AdminStatus> cb) {
        String path = "/v1/admin/status?token=" + enc(PmChatClient.getConfig().backendToken)
                + "&adminSecret=" + enc(PmChatClient.getConfig().backendAdminSecret);
        getJson(path, json -> {
            if (json == null) {
                run(cb, false, null, "request failed");
                return;
            }
            AdminStatus status = new AdminStatus(
                    json.has("uptimeSec") ? json.get("uptimeSec").getAsLong() : 0L,
                    json.has("accounts") ? json.get("accounts").getAsInt() : 0,
                    json.has("onlineNow") ? json.get("onlineNow").getAsInt() : 0,
                    json.has("openReports") ? json.get("openReports").getAsInt() : 0,
                    json.has("openTickets") ? json.get("openTickets").getAsInt() : 0);
            run(cb, true, status, null);
        });
    }

    private static JsonObject adminBody() {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("adminSecret", PmChatClient.getConfig().backendAdminSecret);
        return body;
    }

    /** Заявка в магазин ботов, ожидающая решения админа. */
    public static final class BotListingPending {
        public final long id;
        public final String owner;
        public final String name;
        public final String description;
        public final String fileId;

        BotListingPending(long id, String owner, String name, String description, String fileId) {
            this.id = id;
            this.owner = owner;
            this.name = name;
            this.description = description;
            this.fileId = fileId;
        }
    }

    public static void adminBotstorePending(Callback<java.util.List<BotListingPending>> cb) {
        getJson("/v1/admin/botstore/pending?token=" + enc(PmChatClient.getConfig().backendToken)
                + "&adminSecret=" + enc(PmChatClient.getConfig().backendAdminSecret), json -> {
            java.util.List<BotListingPending> list = new java.util.ArrayList<>();
            if (json != null && json.has("listings")) {
                for (com.google.gson.JsonElement el : json.getAsJsonArray("listings")) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new BotListingPending(o.get("id").getAsLong(), o.get("owner").getAsString(),
                            o.get("name").getAsString(),
                            o.has("description") ? o.get("description").getAsString() : "",
                            o.get("fileId").getAsString()));
                }
            }
            run(cb, json != null, list, json == null ? "request failed" : null);
        });
    }

    public static void adminReviewBotListing(long listingId, boolean approve, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("listingId", listingId);
        body.addProperty("approve", approve);
        postJson("/v1/admin/botstore/review", body, null, cb);
    }

    // ---------- публикации на страничке профиля (стена) ----------

    public static final class ProfilePost {
        public final long id;
        public final String author;
        public final String content;
        public final long at;

        ProfilePost(long id, String author, String content, long at) {
            this.id = id;
            this.author = author;
            this.content = content;
            this.at = at;
        }
    }

    public static void createProfilePost(String content, Callback<Void> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("content", content);
        postJson("/v1/profile/posts", body, null, cb);
    }

    public static void profilePosts(String username, Callback<java.util.List<ProfilePost>> cb) {
        getJson("/v1/profile/posts/" + enc(username), json -> {
            java.util.List<ProfilePost> list = new java.util.ArrayList<>();
            if (json != null && json.has("posts")) {
                for (com.google.gson.JsonElement el : json.getAsJsonArray("posts")) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new ProfilePost(o.get("id").getAsLong(), o.get("author").getAsString(),
                            o.get("content").getAsString(),
                            o.has("at") && !o.get("at").isJsonNull() ? parseIsoMillis(o.get("at").getAsString()) : 0L));
                }
            }
            run(cb, json != null, list, json == null ? "request failed" : null);
        });
    }

    public static void deleteProfilePost(long postId, Callback<Void> cb) {
        if (!isConfigured() || !hasAccount()) { run(cb, false, null, "no account"); return; }
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("postId", postId);
        postJson("/v1/profile/posts/delete", body, null, cb);
    }

    public static final class AdminAccount {
        public final String username;
        public final boolean verified;
        public final boolean official;
        public final long lastSeenAt;
        public final boolean sharePrecise;

        AdminAccount(String username, boolean verified, boolean official, long lastSeenAt, boolean sharePrecise) {
            this.username = username;
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
            Minecraft.getInstance().execute(() -> {
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
        Minecraft client = Minecraft.getInstance();
        Runnable r = () -> cb.onResult(ok, (T) value, error);
        if (client.isSameThread()) {
            r.run();
        } else {
            client.execute(r);
        }
    }
}
