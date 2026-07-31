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

        AccountInfo(String username, boolean verified, boolean official, String avatarUrl) {
            this.username = username;
            this.verified = verified;
            this.official = official;
            this.avatarUrl = avatarUrl;
        }
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
        }, cb);
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

    public static void wallet(Callback<Long> cb) {
        getJson("/v1/wallet?token=" + enc(PmChatClient.getConfig().backendToken), json -> {
            long balance = json != null && json.has("balance") ? json.get("balance").getAsLong() : 0;
            run(cb, json != null, balance, json != null ? null : "request failed");
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
                    json.has("avatarUrl") && !json.get("avatarUrl").isJsonNull() ? json.get("avatarUrl").getAsString() : null);
            run(cb, true, info, null);
        });
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
