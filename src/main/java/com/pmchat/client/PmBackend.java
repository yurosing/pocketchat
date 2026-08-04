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
        /** Модерация (см. PmAdminScreen): временно замучен / забанен. */
        public final boolean muted;
        public final boolean banned;
        /** Цена в монетах за входящее ЛС этому игроку (0 — бесплатно/фича paid_dm не активна). */
        public final long dmPrice;
        /** Ключ должности, назначенной вручную в админ-панели (null — не назначена, см. {@link RoleDef}). */
        public final String roleKey;

        AccountInfo(String username, boolean verified, boolean official, String avatarUrl, long lastSeenAt,
                    boolean sharePrecise, boolean muted, boolean banned, long dmPrice, String roleKey) {
            this.username = username;
            this.verified = verified;
            this.official = official;
            this.avatarUrl = avatarUrl;
            this.lastSeenAt = lastSeenAt;
            this.sharePrecise = sharePrecise;
            this.muted = muted;
            this.banned = banned;
            this.dmPrice = dmPrice;
            this.roleKey = roleKey;
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
            applySelfStatus(resp);
        }, cb);
    }

    // ---------- модерация: свой статус (мут/бан), обновляется через ping/login ----------

    private static volatile boolean selfMuted = false;
    private static volatile long selfMutedUntilAt = 0L;
    private static volatile boolean selfBanned = false;

    private static void applySelfStatus(JsonObject resp) {
        if (resp == null) return;
        if (resp.has("muted")) selfMuted = resp.get("muted").getAsBoolean();
        if (resp.has("mutedUntil") && !resp.get("mutedUntil").isJsonNull()) {
            selfMutedUntilAt = parseIsoMillis(resp.get("mutedUntil").getAsString());
        }
        if (resp.has("banned")) selfBanned = resp.get("banned").getAsBoolean();
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
        /** common/rare/epic/legendary — определяет цвет свечения в UI (см. {@link #rarityColor}). */
        public final String rarity;

        Gift(String id, String name, String icon, long price, String rarity) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.price = price;
            this.rarity = rarity;
        }
    }

    public static final class ReceivedGift {
        public final String giftId;
        public final String from;
        /** Эпоха в мс получения подарка, 0 — неизвестно (старый формат ответа). */
        public final long at;
        public final boolean seen;

        ReceivedGift(String giftId, String from, long at, boolean seen) {
            this.giftId = giftId;
            this.from = from;
            this.at = at;
            this.seen = seen;
        }
    }

    /** Цвет свечения по редкости подарка — общая для профиля и всплывающей анимации. */
    public static int rarityColor(String rarity) {
        if (rarity == null) return 0xFFBFC6CC;
        return switch (rarity) {
            case "rare" -> 0xFF5AA0E0;
            case "epic" -> 0xFFB07AE0;
            case "legendary" -> 0xFFE0B040;
            default -> 0xFFBFC6CC;
        };
    }

    /** Значок и цвет валюты PocketChat — везде, где показывается баланс/цена в монетах. */
    public static final String CURRENCY_ICON = "Ⓒ";
    public static final int CURRENCY_COLOR = 0xFF38D94E;

    /** "Ⓒ 123" — единый вид суммы монет PocketChat в UI. */
    public static String formatCoins(long amount) {
        return CURRENCY_ICON + " " + amount;
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
                            g.get("price").getAsLong(),
                            g.has("rarity") ? g.get("rarity").getAsString() : "common"));
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
                    list.add(new ReceivedGift(g.get("giftId").getAsString(), g.get("from").getAsString(),
                            g.has("at") && !g.get("at").isJsonNull() ? parseIsoMillis(g.get("at").getAsString()) : 0L,
                            !g.has("seen") || g.get("seen").getAsBoolean()));
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

    /** Подарок из каталога по id, или {@code null}, если каталог ещё не подгрузился/id неизвестен. */
    public static Gift giftById(String id) {
        if (id == null) return null;
        for (Gift g : cachedCatalog()) {
            if (g.id.equals(id)) return g;
        }
        return null;
    }

    /**
     * Фоновая проверка новых подарков — вызывается периодически из тика клиента
     * (см. {@code PmChatClient}). Сверяет свежий (по TTL) собственный инбокс с
     * {@link PmConfig#lastGiftNotifiedAt} и зовёт {@code onNewGift} для каждого
     * ещё не показанного подарка (от старых к новым), затем сдвигает отметку.
     */
    public static void checkNewGifts(BiConsumer<ReceivedGift, Gift> onNewGift) {
        if (!isConfigured() || !hasAccount()) return;
        String self = PmChatClient.selfName();
        if (self.isBlank()) return;
        java.util.List<ReceivedGift> gifts = cachedGiftInbox(self);
        if (gifts.isEmpty()) return;
        PmConfig config = PmChatClient.getConfig();
        if (config.lastGiftNotifiedAt == 0) {
            // Первый запуск после обновления: не показываем анимацию для всех подарков,
            // полученных раньше — просто ставим отметку на самый свежий из уже известных.
            long baseline = 0;
            for (ReceivedGift g : gifts) baseline = Math.max(baseline, g.at);
            config.lastGiftNotifiedAt = Math.max(baseline, System.currentTimeMillis());
            config.save();
            return;
        }
        long newest = config.lastGiftNotifiedAt;
        java.util.List<ReceivedGift> fresh = new java.util.ArrayList<>();
        for (ReceivedGift g : gifts) {
            if (g.at > config.lastGiftNotifiedAt) {
                fresh.add(g);
                if (g.at > newest) newest = g.at;
            }
        }
        if (fresh.isEmpty()) return;
        config.lastGiftNotifiedAt = newest;
        config.save();
        // От старых к новым, как они и пришли бы в реальном времени.
        for (int i = fresh.size() - 1; i >= 0; i--) {
            ReceivedGift g = fresh.get(i);
            onNewGift.accept(g, giftById(g.giftId));
        }
    }

    /** Прямой перевод монет игроку (не подарок — без каталога/иконки). */
    public static void sendCoins(String target, long amount, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("targetUsername", target);
        body.addProperty("amount", amount);
        postJson("/v1/coins/send", body, resp -> cachedSelfBalance = null, cb);
    }

    // ---------- магазин возможностей (оформление/функции за монеты, ограниченный срок) ----------

    public static final class ShopItem {
        public final long id;
        public final String name, description, kind, featureKey;
        public final long price;
        public final int durationDays;

        ShopItem(long id, String name, String description, String kind, String featureKey, long price, int durationDays) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.kind = kind;
            this.featureKey = featureKey;
            this.price = price;
            this.durationDays = durationDays;
        }
    }

    public static final class MyFeature {
        public final String featureKey;
        public final long expiresAt;

        MyFeature(String featureKey, long expiresAt) {
            this.featureKey = featureKey;
            this.expiresAt = expiresAt;
        }
    }

    private static volatile java.util.List<ShopItem> cachedShop = null;
    private static volatile boolean shopInFlight = false;

    /** Товары магазина — кэшируются один раз за сессию (список меняется редко). */
    public static java.util.List<ShopItem> cachedShopItems() {
        if (!isConfigured()) return java.util.List.of();
        if (cachedShop == null && !shopInFlight) {
            shopInFlight = true;
            getJson("/v1/shop", json -> {
                shopInFlight = false;
                if (json == null || !json.has("items")) return;
                java.util.List<ShopItem> list = new java.util.ArrayList<>();
                for (var el : json.getAsJsonArray("items")) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new ShopItem(
                            o.get("id").getAsLong(),
                            o.get("name").getAsString(),
                            o.has("description") ? o.get("description").getAsString() : "",
                            o.has("kind") ? o.get("kind").getAsString() : "feature",
                            o.has("featureKey") && !o.get("featureKey").isJsonNull() ? o.get("featureKey").getAsString() : null,
                            o.get("price").getAsLong(),
                            o.get("durationDays").getAsInt()));
                }
                cachedShop = list;
            });
        }
        return cachedShop != null ? cachedShop : java.util.List.of();
    }

    private static volatile java.util.List<MyFeature> cachedMyFeatures = null;
    private static volatile long myFeaturesFetchedAt = 0;
    private static volatile boolean myFeaturesInFlight = false;
    private static final long MY_FEATURES_TTL_MS = 20_000L;

    /** Свои активные покупки — с фоновым обновлением по TTL. */
    public static java.util.List<MyFeature> cachedMyFeatures() {
        if (!isConfigured() || !hasAccount()) return java.util.List.of();
        long now = System.currentTimeMillis();
        if ((cachedMyFeatures == null || now - myFeaturesFetchedAt > MY_FEATURES_TTL_MS) && !myFeaturesInFlight) {
            myFeaturesInFlight = true;
            getJson("/v1/shop/mine?token=" + enc(PmChatClient.getConfig().backendToken), json -> {
                myFeaturesInFlight = false;
                myFeaturesFetchedAt = System.currentTimeMillis();
                if (json == null || !json.has("features")) return;
                java.util.List<MyFeature> list = new java.util.ArrayList<>();
                for (var el : json.getAsJsonArray("features")) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new MyFeature(o.get("featureKey").getAsString(), parseIsoMillis(o.get("expiresAt").getAsString())));
                }
                cachedMyFeatures = list;
            });
        }
        return cachedMyFeatures != null ? cachedMyFeatures : java.util.List.of();
    }

    /** Активна ли прямо сейчас купленная фича (см. {@link #cachedMyFeatures}). */
    public static boolean hasActiveFeature(String featureKey) {
        long now = System.currentTimeMillis();
        for (MyFeature f : cachedMyFeatures()) {
            if (f.featureKey.equals(featureKey) && f.expiresAt > now) return true;
        }
        return false;
    }

    public static void buyShopItem(long itemId, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("itemId", itemId);
        postJson("/v1/shop/buy", body, resp -> {
            cachedSelfBalance = null;
            myFeaturesFetchedAt = 0;
        }, cb);
    }

    /** Своя цена за входящее ЛС — требует активную фичу {@code paid_dm}. */
    public static void setDmPrice(long price, Callback<Void> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("price", price);
        postJson("/v1/dm-price", body, resp -> {
            String self = PmChatClient.selfName();
            if (self != null) ACCOUNT_CACHE.remove(self.toLowerCase(java.util.Locale.ROOT));
        }, cb);
    }

    /**
     * Списывает цену получателя за входящее ЛС перед фактической отправкой (бэкенд
     * не видит {@code /m} сам). {@code charged} — сколько реально списано (0, если
     * фича {@code paid_dm} у получателя не активна). При нехватке монет ok=false и
     * cb получает цену через {@link #lastChargeRequiredPrice()}.
     */
    private static volatile long lastChargeRequiredPrice = 0;

    public static long lastChargeRequiredPrice() {
        return lastChargeRequiredPrice;
    }

    public static void chargeDm(String target, Callback<Long> cb) {
        JsonObject body = new JsonObject();
        body.addProperty("token", PmChatClient.getConfig().backendToken);
        body.addProperty("targetUsername", target);
        if (!isConfigured() || !hasAccount()) {
            run(cb, true, 0L, null);
            return;
        }
        Thread t = new Thread(() -> {
            long charged = 0;
            boolean ok;
            String error = null;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(base() + "/v1/dm/charge"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject json = resp.body().isBlank() ? null : JsonParser.parseString(resp.body()).getAsJsonObject();
                ok = resp.statusCode() / 100 == 2;
                if (ok && json != null && json.has("charged")) charged = json.get("charged").getAsLong();
                if (!ok) {
                    error = json != null && json.has("error") ? json.get("error").getAsString() : ("HTTP " + resp.statusCode());
                    if (json != null && json.has("price")) lastChargeRequiredPrice = json.get("price").getAsLong();
                }
            } catch (Exception e) {
                ok = false;
                error = e.toString();
                PmChatClient.LOGGER.debug("PmBackend dm/charge failed: {}", e.toString());
            }
            if (ok) cachedSelfBalance = null;
            boolean finalOk = ok;
            long finalCharged = charged;
            String finalError = error;
            run(cb, finalOk, finalCharged, finalError);
        }, "pmchat-backend-dm-charge");
        t.setDaemon(true);
        t.start();
    }

    // ---------- админ: товары магазина ----------

    public static void adminListShop(Callback<java.util.List<ShopItem>> cb) {
        String path = "/v1/admin/shop?token=" + enc(PmChatClient.getConfig().backendToken)
                + "&adminSecret=" + enc(PmChatClient.getConfig().backendAdminSecret);
        getJson(path, json -> {
            if (json == null || !json.has("items")) {
                run(cb, false, null, "request failed");
                return;
            }
            java.util.List<ShopItem> list = new java.util.ArrayList<>();
            for (var el : json.getAsJsonArray("items")) {
                JsonObject o = el.getAsJsonObject();
                list.add(new ShopItem(
                        o.get("id").getAsLong(),
                        o.get("name").getAsString(),
                        o.has("description") ? o.get("description").getAsString() : "",
                        o.has("kind") ? o.get("kind").getAsString() : "feature",
                        o.has("featureKey") && !o.get("featureKey").isJsonNull() ? o.get("featureKey").getAsString() : null,
                        o.get("price").getAsLong(),
                        o.get("durationDays").getAsInt()));
            }
            run(cb, true, list, null);
        });
    }

    /** {@code id <= 0} создаёт новый товар вместо изменения существующего. */
    public static void adminUpsertShopItem(long id, String name, String description, String featureKey,
                                           long price, int durationDays, Callback<Void> cb) {
        JsonObject body = adminBody();
        if (id > 0) body.addProperty("id", id);
        body.addProperty("name", name);
        body.addProperty("description", description);
        body.addProperty("kind", "feature");
        if (featureKey != null && !featureKey.isBlank()) body.addProperty("featureKey", featureKey);
        body.addProperty("price", price);
        body.addProperty("durationDays", durationDays);
        postJson("/v1/admin/shop/upsert", body, resp -> cachedShop = null, cb);
    }

    public static void adminDeleteShopItem(long id, Callback<Void> cb) {
        JsonObject body = adminBody();
        body.addProperty("id", id);
        postJson("/v1/admin/shop/delete", body, resp -> cachedShop = null, cb);
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
                    json.has("dmPrice") ? json.get("dmPrice").getAsLong() : 0L,
                    json.has("roleKey") && !json.get("roleKey").isJsonNull() ? json.get("roleKey").getAsString() : null);
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
     * Включена ли фича ({@code gifts}/{@code reports}/{@code support}) прямо сейчас —
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

    /** Включить/выключить фичу целиком ({@code gifts}/{@code reports}/{@code support}). */
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
