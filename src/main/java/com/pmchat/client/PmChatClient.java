package com.pmchat.client;

import com.pmchat.screen.PmScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class PmChatClient implements ClientModInitializer {

    public static final String MOD_ID = "pmchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static PmConfig config;
    private static PmHistory history;
    private static KeyBinding openKey;

    private static Pattern incoming;
    private static Pattern outgoing;
    private static Pattern global;
    private static float lastHealth = 20f;

    /** Сентинел «диалога» общего чата. */
    public static final String GLOBAL = "§global";
    /** Сентинел «Избранное» — личный чат с собой, только локально. */
    public static final String SAVED = "§saved";
    /** Сентинел ленты логов CoreProtect (6.3) — только чтение. */
    public static final String COREPROTECT = "§cp";

    private static Pattern coreProtect;
    private static long coreProtectActiveUntil = 0; // окно захвата блока результата
    private static final java.util.List<PmMessage> coreProtectFeed =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    public static boolean isCoreProtect(String name) {
        return COREPROTECT.equals(name);
    }

    public static java.util.List<PmMessage> getCoreProtectFeed() {
        return coreProtectFeed;
    }

    public static boolean coreProtectHasMessages() {
        return config.coreProtectEnabled && !coreProtectFeed.isEmpty();
    }

    public static void clearCoreProtect() {
        coreProtectFeed.clear();
    }

    /** Локальные диалоги (не отправляются на сервер): начинаются с §.
     *  Группы (§grp:) исключаем — они рассылаются на сервер через /m. */
    public static boolean isLocalChat(String name) {
        return name != null && name.startsWith("§") && !name.startsWith(GROUP_PREFIX);
    }

    /** Сохранить сообщение в Избранное (копия локально). */
    public static void saveToFavorites(PmMessage src) {
        String content = src.text != null ? src.text : "";
        if (content.isEmpty() && src.money <= 0) return;
        PmMessage m = history.add(SAVED, true, content, src.money);
        applyPoll(m, content);
        if (src.forwardFrom != null) m.forwardFrom = src.forwardFrom;
        history.save();
    }
    private static final int GLOBAL_LIMIT = 300;
    private static final java.util.List<PmMessage> globalChat =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /** Ожидаемые эхо-строки наших собственных отправок: "ник|текст" -> срок годности. */
    private static final Deque<String[]> pendingEcho = new ArrayDeque<>();


    /** Кто сейчас печатает: ник -> активно до (мс). */
    private static final java.util.Map<String, Long> typingUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> lastTypingSent = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> lastSeenSent = new java.util.HashMap<>();

    /** NEW (6.10): секретные чаты — сессия на собеседника, только в памяти. */
    private static final java.util.Map<String, PmSecretSession> secretSessions =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static long lastSecretSweep = 0;

    @Override
    public void onInitializeClient() {
        // Разблокируем AWT-буфер обмена для Ctrl+V картинок (кроме macOS,
        // где AWT конфликтует с GLFW). Должно случиться до первого касания AWT.
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            System.setProperty("java.awt.headless", "false");
        }

        config = PmConfig.load();
        history = PmHistory.load();
        compilePatterns();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pmchat.open",
                GLFW.GLFW_KEY_J,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "category"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Только ОТКРЫВАЕМ по клавише (закрытие — Esc/крестик), иначе на русской
            // раскладке клавиша J = «о» закрывала бы меню при вводе текста.
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new PmScreen());
                }
            }
            // Закрыть меню при получении урона (если включено в настройках)
            if (config.closeOnDamage && client.currentScreen instanceof PmScreen && client.player != null) {
                float hp = client.player.getHealth();
                if (hp < lastHealth - 0.01f) {
                    client.setScreen(null);
                }
                lastHealth = hp;
            } else if (client.player != null) {
                lastHealth = client.player.getHealth();
            }

            // NEW (6.10): самоуничтожение секретных сообщений — проверяем раз в секунду
            long now = System.currentTimeMillis();
            if (now - lastSecretSweep >= 1000) {
                lastSecretSweep = now;
                history.sweepExpiredSecrets(now); // истёкшие и так никогда не были на диске
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("pm")
                        .executes(ctx -> {
                            MinecraftClient client = ctx.getSource().getClient();
                            client.execute(() -> client.setScreen(new PmScreen()));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("hosts")
                                .executes(ctx -> {
                                    testHosts(ctx.getSource().getClient());
                                    return 1;
                                }))
        ));

        // Перехват системных строк чата (Essentials шлёт ЛС именно так)
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;
            int matched = handleChatLine(message); // 0 нет, 1 ЛС, 2 служебная метка
            if (matched == 2) return false;        // мету прячем всегда
            return !(matched == 1 && config.hideChatLines);
        });

        LOGGER.info("PM Messenger initialized. /pm или клавиша J.");
    }

    /** /pm hosts — проверка каждого хостинга: загрузка + скачивание, отчёт в чат. */
    private static void testHosts(MinecraftClient client) {
        // 1×1 прозрачный PNG
        byte[] probe = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
        chatLine(client, "§7[pmchat] Проверяю хостинги…");
        new Thread(() -> {
            for (String code : new String[]{"k", "x", "q", "l", "c"}) {
                String label = switch (code) {
                    case "k" -> "kappa.lol";
                    case "q" -> "qu.ax";
                    case "l" -> "pomf.lain.la";
                    case "x" -> "x0.at";
                    default -> "catbox.moe";
                };
                String result;
                long start = System.currentTimeMillis();
                try {
                    PmConfig cfg = config;
                    String savedOrder = cfg.uploadOrder;
                    cfg.uploadOrder = code; // проверяем только этот хост
                    String[] up;
                    try {
                        up = PmHosts.upload(probe, "pmtest.png");
                    } finally {
                        cfg.uploadOrder = savedOrder;
                    }
                    java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofSeconds(5)).build();
                    java.net.http.HttpResponse<byte[]> down = http.send(
                            java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(PmHosts.baseUrl(up[0]) + up[1]))
                                    .timeout(java.time.Duration.ofSeconds(10))
                                    .header("User-Agent", "pmchat-mod/1.0").GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                    long ms = System.currentTimeMillis() - start;
                    result = down.statusCode() == 200 && down.body().length > 0
                            ? "§a✔ OK (" + ms + " мс)"
                            : "§c✖ скачивание HTTP " + down.statusCode();
                } catch (Exception e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    if (reason.length() > 40) reason = reason.substring(0, 40) + "…";
                    result = "§c✖ " + reason;
                }
                String line = "§7[pmchat] §f" + label + " (" + code + "): " + result;
                client.execute(() -> chatLine(client, line));
            }
        }, "pmchat-hosttest").start();
    }

    private static void chatLine(MinecraftClient client, String legacy) {
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal(legacy));
        }
    }

    private static void compilePatterns() {
        try {
            incoming = Pattern.compile(config.incomingPattern);
        } catch (Exception e) {
            LOGGER.warn("Bad incoming pattern, disabled: {}", e.getMessage());
            incoming = null;
        }
        try {
            outgoing = Pattern.compile(config.outgoingPattern);
        } catch (Exception e) {
            LOGGER.warn("Bad outgoing pattern, disabled: {}", e.getMessage());
            outgoing = null;
        }
        try {
            global = Pattern.compile(config.globalPattern);
        } catch (Exception e) {
            LOGGER.warn("Bad global pattern, disabled: {}", e.getMessage());
            global = null;
        }
        try {
            coreProtect = config.coreProtectPattern == null || config.coreProtectPattern.isBlank()
                    ? null : Pattern.compile(config.coreProtectPattern);
        } catch (Exception e) {
            LOGGER.warn("Bad CoreProtect pattern, disabled: {}", e.getMessage());
            coreProtect = null;
        }
    }

    /** @return 0 — не ЛС, 1 — обычное ЛС, 2 — служебная метка (прятать всегда) */
    private static int handleChatLine(Text message) {
        String plain = message.getString().replaceAll("§.", "").trim();
        if (plain.isEmpty()) return 0;

        if (incoming != null) {
            Matcher m = incoming.matcher(plain);
            if (m.find()) {
                return onIncoming(m.group(1), m.group(2).trim());
            }
        }
        if (outgoing != null) {
            Matcher m = outgoing.matcher(plain);
            if (m.find()) {
                return onOutgoingEcho(m.group(1), m.group(2).trim());
            }
        }
        // Логи CoreProtect (6.3) — блочный захват: от заголовка ловим весь
        // последующий вывод (результаты/контейнеры/пагинация), даже если строки
        // не совпадают с паттерном (напр. плагин на другом языке).
        if (config.coreProtectEnabled) {
            long now = System.currentTimeMillis();
            boolean strong = coreProtect != null && coreProtect.matcher(plain).find();
            // В «слабом» окне (после заголовка) НЕ проглатываем строки, которые
            // на самом деле являются обычным чатом — иначе сообщения общего чата,
            // каналов и ЛС попадают в ленту CoreProtect. Заголовок (strong) ловим
            // всегда.
            if (strong || (now < coreProtectActiveUntil && !looksLikeKnownChat(plain))) {
                PmMessage log = new PmMessage(false, plain, now, 0);
                coreProtectFeed.add(log);
                while (coreProtectFeed.size() > GLOBAL_LIMIT) coreProtectFeed.remove(0);
                coreProtectActiveUntil = now + 2500; // продлеваем, пока блок идёт
                return 0;
            }
        }
        // Каналы (клан/альянс/группа) — специфичнее глобального, проверяем раньше
        if (handleChannelLine(plain)) {
            return 0; // строку в ванильном чате не прячем
        }
        // Общий чат: копим у себя, но строку из ванильного чата никогда не прячем
        if (global != null) {
            Matcher m = global.matcher(plain);
            if (m.find()) {
                addGlobal(m.group(1), m.group(2).trim());
            }
        }
        return 0;
    }

    /**
     * Похожа ли строка на обычный чат (общий/канал/ЛС) — только проверка
     * паттернов, без побочных эффектов. Нужно, чтобы блочный захват CoreProtect
     * не проглатывал сообщения общего чата, каналов и личек.
     */
    private static boolean looksLikeKnownChat(String plain) {
        if (global != null && global.matcher(plain).find()) return true;
        if (incoming != null && incoming.matcher(plain).find()) return true;
        if (outgoing != null && outgoing.matcher(plain).find()) return true;
        for (PmConfig.PmChannel channel : config.channels) {
            Pattern pattern = channelPatterns.computeIfAbsent(channel.id, k -> {
                try {
                    return Pattern.compile(channel.pattern,
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                } catch (Exception e) {
                    return Pattern.compile("$^");
                }
            });
            if (pattern.matcher(plain).find()) return true;
        }
        return false;
    }

    // ---------- Каналы серверного чата ----------

    private static final java.util.Map<String, java.util.List<PmMessage>> channelFeeds =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Integer> channelUnread =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Pattern> channelPatterns = new java.util.HashMap<>();

    private static boolean handleChannelLine(String plain) {
        for (PmConfig.PmChannel channel : config.channels) {
            Pattern pattern = channelPatterns.computeIfAbsent(channel.id, k -> {
                try {
                    return Pattern.compile(channel.pattern,
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                } catch (Exception e) {
                    LOGGER.warn("Bad channel pattern '{}': {}", channel.id, e.getMessage());
                    return Pattern.compile("$^"); // никогда не матчится
                }
            });
            Matcher m = pattern.matcher(plain);
            if (!m.find()) continue;

            String name = groupOr(m, "name", 1);
            String text = groupOr(m, "text", 2);
            if (name == null || text == null) return false;
            String clan = groupOr(m, "clan", -1);

            PmMessage msg = new PmMessage(name.equalsIgnoreCase(selfName()), text.trim(),
                    System.currentTimeMillis(), 0);
            msg.sender = clan != null ? name + " [" + clan + "]" : name;
            java.util.List<PmMessage> feed = channelFeeds.computeIfAbsent(channel.id,
                    k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
            feed.add(msg);
            while (feed.size() > GLOBAL_LIMIT) {
                feed.remove(0);
            }

            MinecraftClient client = MinecraftClient.getInstance();
            boolean viewing = client.currentScreen instanceof PmScreen screen
                    && screen.isViewing(CHANNEL_PREFIX + channel.id);
            if (!viewing && !msg.out) {
                channelUnread.merge(channel.id, 1, Integer::sum);
                if (mentionsMe(text, name) && !config.dnd) notifyMention(client, name, text);
            }
            return true;
        }
        return false;
    }

    private static String groupOr(Matcher m, String groupName, int fallbackIndex) {
        try {
            String value = m.group(groupName);
            if (value != null) return value;
        } catch (Exception ignored) {
        }
        if (fallbackIndex > 0 && fallbackIndex <= m.groupCount()) {
            return m.group(fallbackIndex);
        }
        return null;
    }

    /** Сентинел-префикс «диалога» канала. */
    public static final String CHANNEL_PREFIX = "§ch:";

    public static java.util.List<PmMessage> getChannelFeed(String id) {
        return channelFeeds.getOrDefault(id, java.util.List.of());
    }

    public static boolean channelHasMessages(String id) {
        return !getChannelFeed(id).isEmpty();
    }

    public static int channelUnread(String id) {
        return channelUnread.getOrDefault(id, 0);
    }

    public static void clearChannelUnread(String id) {
        channelUnread.remove(id);
    }

    /** «Удалить чат»: очищает ленту — вкладка исчезает до новых сообщений. */
    public static void clearChannel(String id) {
        channelFeeds.remove(id);
        channelUnread.remove(id);
    }

    public static void sendChannel(String id, String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || text.isBlank()) return;
        for (PmConfig.PmChannel channel : config.channels) {
            if (channel.id.equals(id)) {
                client.player.networkHandler.sendChatCommand(channel.command + " " + text);
                return;
            }
        }
    }

    public static PmConfig.PmChannel channelById(String id) {
        for (PmConfig.PmChannel channel : config.channels) {
            if (channel.id.equals(id)) return channel;
        }
        return null;
    }

    // ---------- Групповые чаты (6.9) ----------

    /** Сентинел-префикс «диалога» группы. */
    public static final String GROUP_PREFIX = "§grp:";

    private static final java.util.Map<String, java.util.List<PmMessage>> groupFeeds =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Integer> groupUnread =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isGroup(String name) {
        return name != null && name.startsWith(GROUP_PREFIX);
    }

    public static String groupId(String name) {
        return isGroup(name) ? name.substring(GROUP_PREFIX.length()) : null;
    }

    /** Детерминированный id группы из состава (не зависит от порядка/регистра). */
    public static String computeGroupId(java.util.Collection<String> roster) {
        java.util.List<String> norm = new java.util.ArrayList<>();
        for (String r : roster) {
            if (r != null && !r.isBlank()) norm.add(r.trim().toLowerCase(Locale.ROOT));
        }
        java.util.Collections.sort(norm);
        return PmHistory.msgHash(String.join("", norm));
    }

    public static java.util.List<PmMessage> getGroupFeed(String id) {
        return groupFeeds.getOrDefault(id, java.util.List.of());
    }

    public static int groupUnread(String id) {
        return groupUnread.getOrDefault(id, 0);
    }

    public static void clearGroupUnread(String id) {
        groupUnread.remove(id);
    }

    /** Создать группу локально; возвращает id для выбора. */
    public static String createGroup(String name, java.util.List<String> members) {
        java.util.List<String> clean = new java.util.ArrayList<>();
        String self = selfName();
        for (String m : members) {
            if (m == null) continue;
            String t = m.trim();
            if (t.isEmpty() || t.equalsIgnoreCase(self)) continue;
            if (clean.stream().noneMatch(x -> x.equalsIgnoreCase(t))) clean.add(t);
        }
        if (clean.isEmpty()) return null;
        java.util.List<String> roster = new java.util.ArrayList<>();
        roster.add(self);
        roster.addAll(clean);
        String id = computeGroupId(roster);
        if (config.findGroup(id) == null) {
            config.groups.add(new PmConfig.PmGroup(id, name == null || name.isBlank() ? "Группа" : name.trim(), clean));
            config.save();
        }
        return id;
    }

    /** «Удалить группу»: убирает из конфига и очищает ленту. */
    public static void deleteGroup(String id) {
        config.groups.removeIf(g -> g.id.equals(id));
        config.save();
        groupFeeds.remove(id);
        groupUnread.remove(id);
    }

    /** Отправить сообщение в группу: рассылка /m каждому участнику. */
    public static void sendGroup(String id, String text) {
        PmConfig.PmGroup g = config.findGroup(id);
        if (g == null || text == null || text.isBlank()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        String self = selfName();

        // Локальное эхо своего сообщения
        PmMessage mine = new PmMessage(true, text, System.currentTimeMillis(), 0);
        mine.sender = self;
        addToGroupFeed(id, mine);

        java.util.List<String> roster = new java.util.ArrayList<>();
        roster.add(self);
        roster.addAll(g.members);
        String hexName = PmWire.hex(g.name);

        for (String member : g.members) {
            if (member.equalsIgnoreCase(self)) continue;
            String out;
            if (config.isModUser(member)) {
                out = PmWire.group(hexName, roster, text);
            } else {
                // Без мода: человекочитаемо, с пометкой группы
                out = "[" + g.name + "] " + text;
            }
            client.player.networkHandler.sendChatCommand(config.msgCommand + " " + member + " " + out);
            synchronized (pendingEcho) {
                pendingEcho.add(new String[]{member, out, String.valueOf(System.currentTimeMillis() + 5000)});
            }
        }
    }

    private static void addToGroupFeed(String id, PmMessage msg) {
        java.util.List<PmMessage> feed = groupFeeds.computeIfAbsent(id,
                k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
        feed.add(msg);
        while (feed.size() > GLOBAL_LIMIT) feed.remove(0);
    }

    /** Входящее групповое сообщение (pmc grp) от участника с модом. */
    private static void handleIncomingGroup(String sender, String name, String[] roster, String text) {
        String self = selfName();
        java.util.List<String> members = new java.util.ArrayList<>();
        for (String r : roster) {
            if (r == null || r.isBlank()) continue;
            if (r.equalsIgnoreCase(self)) continue;
            if (members.stream().noneMatch(x -> x.equalsIgnoreCase(r))) members.add(r.trim());
        }
        if (members.isEmpty()) return;
        String id = computeGroupId(java.util.Arrays.asList(roster));
        PmConfig.PmGroup g = config.findGroup(id);
        if (g == null) {
            config.groups.add(new PmConfig.PmGroup(id, name == null || name.isBlank() ? "Группа" : name, members));
            config.save();
        }

        PmMessage msg = new PmMessage(false, text, System.currentTimeMillis(), 0);
        msg.sender = sender;
        addToGroupFeed(id, msg);

        MinecraftClient client = MinecraftClient.getInstance();
        boolean viewing = client.currentScreen instanceof PmScreen screen
                && screen.isViewing(GROUP_PREFIX + id);
        if (!viewing) {
            groupUnread.merge(id, 1, Integer::sum);
            if (!config.dnd) {
                client.getToastManager().add(new PmToast(name + " · " + sender, previewOf(text)));
                playNotifySound(client);
            }
        }
    }

    private static void addGlobal(String sender, String text) {
        PmMessage msg = new PmMessage(sender.equalsIgnoreCase(selfName()), text, System.currentTimeMillis(), 0);
        msg.sender = sender;
        globalChat.add(msg);
        while (globalChat.size() > GLOBAL_LIMIT) {
            globalChat.remove(0);
        }

        // Озвучка общего чата системным голосом (свои сообщения не читаем)
        if (config.ttsGlobal && !msg.out) {
            speak(sender + ": " + previewOf(text));
        }
        // Упоминание в общем чате — пинг + тост
        if (!msg.out && mentionsMe(text, sender)) {
            MinecraftClient client = MinecraftClient.getInstance();
            boolean viewing = client.currentScreen instanceof PmScreen s && s.isViewing(GLOBAL);
            if (!viewing && !config.dnd) notifyMention(client, sender, text);
        }
    }

    /** TTS через встроенный в Minecraft движок Mojang text2speech. */
    public static void speak(String text) {
        try {
            String clipped = text.length() > 200 ? text.substring(0, 200) : text;
            float volume = Math.max(0.05f, config.notifyVolume / 100f);
            com.mojang.text2speech.Narrator.getNarrator().say(clipped, false, volume);
        } catch (Throwable t) {
            LOGGER.debug("TTS unavailable: {}", t.toString());
        }
    }

    public static java.util.List<PmMessage> getGlobalChat() {
        return globalChat;
    }

    public static void clearGlobalChat() {
        globalChat.clear();
    }

    /** Сообщение в общий чат сервера; префикс из настроек (обычно "!"). */
    public static void sendGlobal(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || text.isBlank()) return;
        String prefix = config.globalPrefix == null ? "" : config.globalPrefix.trim();
        // Не дублируем префикс, если игрок уже его набрал
        String out = (!prefix.isEmpty() && !text.startsWith(prefix)) ? prefix + text : text;
        client.player.networkHandler.sendChatMessage(out);
    }

    private static int onIncoming(String sender, String text) {
        // Служебные метки от чужого мода
        if (PmWire.isHi(text)) {
            config.addModUser(sender);
            sendHi(sender); // ответное рукопожатие (один раз, дальше заглушено)
            return 2;
        }
        if (PmWire.isTyping(text)) {
            config.addModUser(sender);
            typingUntil.put(sender, System.currentTimeMillis() + 7000);
            return 2;
        }
        if (PmWire.isSeen(text)) {
            config.addModUser(sender);
            history.markAllOutgoingRead(sender);
            return 2;
        }

        // NEW (6.10): секретные чаты — запрос/подтверждение/конец сессии, зашифрованное сообщение
        String reqPub = PmWire.parseSecretRequest(text);
        if (reqPub != null) {
            config.addModUser(sender);
            onSecretRequest(sender, reqPub);
            return 2;
        }
        String ackPub = PmWire.parseSecretAck(text);
        if (ackPub != null) {
            config.addModUser(sender);
            onSecretAck(sender, ackPub);
            return 2;
        }
        if (PmWire.isSecretEnd(text)) {
            config.addModUser(sender);
            secretSessions.remove(sender.toLowerCase(Locale.ROOT));
            return 2;
        }
        String[] secMsg = PmWire.parseSecretMessage(text);
        if (secMsg != null) {
            config.addModUser(sender);
            onSecretMessage(sender, Integer.parseInt(secMsg[0]), secMsg[1], secMsg[2]);
            return 1;
        }
        String[] rx = PmWire.parseReaction(text);
        if (rx != null) {
            config.addModUser(sender);
            PmMessage reacted = history.findByHash(sender, rx[0]);
            if (reacted != null) {
                reacted.reactOther = rx[1];
                history.save();
            }
            return 2;
        }
        String pinHash = PmWire.parsePin(text);
        if (pinHash != null) {
            config.addModUser(sender);
            if (pinHash.equals("-")) config.clearPins(sender);
            else config.addPin(sender, pinHash);
            return 2;
        }
        String unpinHash = PmWire.parseUnpin(text);
        if (unpinHash != null) {
            config.addModUser(sender);
            config.removePin(sender, unpinHash);
            return 2;
        }
        String[] edit = PmWire.parseEdit(text);
        if (edit != null) {
            config.addModUser(sender);
            PmMessage orig = history.findByHash(sender, edit[0]);
            if (orig != null && !orig.out) {
                orig.text = edit[1];
                orig.edited = true;
                orig.editTime = System.currentTimeMillis();
                applyPoll(orig, edit[1]);
                history.save();
            }
            return 2;
        }
        Object[] vote = PmWire.parseVote(text);
        if (vote != null) {
            config.addModUser(sender);
            @SuppressWarnings("unchecked")
            java.util.List<Integer> idx = (java.util.List<Integer>) vote[1];
            PmMessage poll = history.findByHash(sender, (String) vote[0]);
            if (poll != null && poll.isPoll()) {
                poll.pollOtherVotes = idx;
                history.save();
            }
            return 2;
        }

        // Групповое сообщение (pmc grp) — в ленту группы, из ЛС прячем
        Object[] grp = PmWire.parseGroup(text);
        if (grp != null) {
            config.addModUser(sender);
            handleIncomingGroup(sender, (String) grp[0], (String[]) grp[1], (String) grp[2]);
            return 2;
        }

        // Структурированные сообщения (фото/голос/цитата) — точно от мода
        if (PmWire.isStructured(text)) {
            config.addModUser(sender);
        } else {
            // Обычный текст: представляемся один раз — вдруг у него тоже мод
            sendHi(sender);
        }
        typingUntil.remove(sender);

        String replyTo = null;
        int fragStart = -1, fragLen = 0;
        Object[] rf = PmWire.parseReplyFrag(text);
        if (rf != null) {
            replyTo = (String) rf[0];
            fragStart = (int) rf[1];
            fragLen = (int) rf[2];
            text = (String) rf[3];
        } else {
            String[] reply = PmWire.parseReply(text);
            if (reply != null) {
                replyTo = reply[0];
                text = reply[1];
            }
        }

        // Пересылка: pmc fwd <откуда> <содержимое>
        String forwardFrom = null;
        String[] fwd = PmWire.parseForward(text);
        if (fwd != null) {
            forwardFrom = fwd[0];
            text = fwd[1];
        }

        PmMessage msg = history.add(sender, false, text, 0);
        if (replyTo != null) {
            msg.replyTo = replyTo;
            if (fragStart >= 0) msg.replyFragment = sliceFragment(sender, replyTo, fragStart, fragLen);
        }
        if (forwardFrom != null) msg.forwardFrom = forwardFrom;
        applyPoll(msg, text);
        if (replyTo != null || forwardFrom != null || msg.isPoll()) history.save();

        MinecraftClient client = MinecraftClient.getInstance();
        boolean viewing = client.currentScreen instanceof PmScreen screen && screen.isViewing(sender);
        if (viewing) {
            sendSeen(sender);
        } else {
            history.markUnread(sender);
            if (!config.dnd) {
                client.getToastManager().add(new PmToast(sender, previewOf(text)));
                playNotifySound(client);
            }
        }
        return 1;
    }

    /** Звук уведомления с учётом выбора и громкости из настроек. */
    public static void playNotifySound(MinecraftClient client) {
        if (!config.soundEnabled || config.notifySound == 3) return;
        net.minecraft.sound.SoundEvent sound = switch (config.notifySound) {
            case 1 -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            case 2 -> SoundEvents.ENTITY_ITEM_PICKUP;
            default -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
        };
        float volume = Math.max(0.05f, config.notifyVolume / 100f);
        client.getSoundManager().play(PositionedSoundInstance.ui(sound, 1.4f, volume));
    }

    public static String previewOf(String text) {
        if (PmWire.parseImg(text) != null) {
            return Text.translatable("pmchat.image.label").getString();
        }
        if (PmWire.parseVoice(text) != null) {
            return Text.translatable("pmchat.voice.label").getString();
        }
        if (PmWire.parseVid(text) != null) {
            return Text.translatable("pmchat.video.label").getString();
        }
        String[] poll = PmWire.parsePoll(text);
        if (poll != null) {
            return "▤ " + poll[1];
        }
        return text;
    }

    /** Одноразовое рукопожатие «у меня мод» — тихо, без записи в историю. */
    private static void sendHi(String target) {
        if (!config.enableMeta || config.hiSentTo(target)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        config.markHiSent(target);
        client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + PmWire.HI);
    }

    /** Реакция на сообщение: локально + собеседнику с модом. */
    public static void sendReaction(String target, PmMessage msg, int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || msg.text == null) return;
        msg.reactMine = PmWire.REACTIONS[index];
        history.save();
        if (config.enableMeta && config.isModUser(target)) {
            String hash = PmHistory.msgHash(msg.text);
            client.player.networkHandler.sendChatCommand(
                    config.msgCommand + " " + target + " " + PmWire.reaction(hash, index));
        }
    }

    private static void sendPinMeta(String target, String wire) {
        if (config.enableMeta && config.isModUser(target) && !isLocalChat(target)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + wire);
            }
        }
    }

    /** Закрепить сообщение: локально + собеседнику с модом. */
    public static void addPin(String target, String hash, boolean forBoth) {
        config.addPin(target, hash);
        if (forBoth) sendPinMeta(target, PmWire.pin(hash));
    }

    /** Открепить одно сообщение. */
    public static void removePin(String target, String hash, boolean forBoth) {
        config.removePin(target, hash);
        if (forBoth) sendPinMeta(target, PmWire.unpinOne(hash));
    }

    /** Открепить все. */
    public static void clearPins(String target, boolean forBoth) {
        config.clearPins(target);
        if (forBoth) sendPinMeta(target, PmWire.pin(null));
    }

    /**
     * Редактировать своё исходящее сообщение. Локально меняем текст + метку
     * «изменено», а собеседнику с модом шлём pmc edit со старым хэшем.
     * Правка доступна только для модовых диалогов/групп (проверяется в UI).
     */
    public static void editMessage(String target, PmMessage msg, String newText) {
        if (msg == null || !msg.out || newText == null || newText.isBlank()) return;
        if (msg.text != null && msg.text.equals(newText)) return;
        String oldHash = PmHistory.msgHash(msg.text);
        // Обновляем закреп, если он ссылался на старый хэш
        if (config.isPinned(target, oldHash)) {
            config.removePin(target, oldHash);
            config.addPin(target, PmHistory.msgHash(newText));
        }
        msg.text = newText;
        msg.edited = true;
        msg.editTime = System.currentTimeMillis();
        history.save();

        if (isLocalChat(target)) return; // Избранное — только локально
        if (config.enableMeta && config.isModUser(target)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                String wire = PmWire.edit(oldHash, newText);
                client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + wire);
                synchronized (pendingEcho) {
                    pendingEcho.add(new String[]{target, wire, String.valueOf(System.currentTimeMillis() + 5000)});
                }
            }
        }
    }

    /**
     * Переслать сообщение в диалог target. Мод-получателю уходит структурно
     * (шапка «переслано от X»), обычному игроку — текстом «X » текст».
     */
    public static void forwardMessage(String target, String fromNick, PmMessage msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || target.isBlank()) return;
        String inner = msg.text != null ? msg.text : "";

        // Пересылка в Избранное — локальная копия, без сети
        if (isLocalChat(target)) {
            PmMessage local = history.add(target, true, inner, 0);
            applyPoll(local, inner);
            local.forwardFrom = fromNick;
            history.save();
            return;
        }

        if (config.isModUser(target)) {
            String wire = PmWire.forward(fromNick, inner);
            client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + wire);
            synchronized (pendingEcho) {
                pendingEcho.add(new String[]{target, wire, String.valueOf(System.currentTimeMillis() + 5000)});
            }
            PmMessage local = history.add(target, true, inner, 0);
            local.forwardFrom = fromNick;
            history.save();
        } else {
            // Без мода: медиа бесполезно, шлём человекочитаемо
            String body = previewOf(inner);
            String plain = fromNick + " » " + body;
            client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + plain);
            synchronized (pendingEcho) {
                pendingEcho.add(new String[]{target, plain, String.valueOf(System.currentTimeMillis() + 5000)});
            }
            PmMessage local = history.add(target, true, plain, 0);
            local.forwardFrom = fromNick;
            history.save();
        }
    }

    public static String selfNamePublic() {
        return selfName();
    }

    /**
     * Предупреждение игроку (6.1): сразу отправляет команду «/warn <ник>» на
     * сервер (без открытия игрового чата). Причина по умолчанию берётся из
     * настроек (warnReason), если задана.
     */
    public static void warnPlayer(String nick) {
        if (!config.staffFeatures || nick == null || nick.isBlank()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        String cmd = (config.warnCommand == null || config.warnCommand.isBlank() ? "warn" : config.warnCommand.trim());
        String reason = config.warnReason == null ? "" : config.warnReason.trim();
        String full = reason.isEmpty() ? cmd + " " + nick : cmd + " " + nick + " " + reason;
        client.player.networkHandler.sendChatCommand(full);
    }

    // ---------- Опросы (только личные чаты) ----------

    /** Разбирает pmc poll в поля сообщения. */
    static void applyPoll(PmMessage msg, String text) {
        String[] p = PmWire.parsePoll(text);
        if (p == null) return;
        msg.pollMulti = p[0].equals("1");
        msg.pollQuestion = p[1];
        msg.pollOptions = new java.util.ArrayList<>();
        for (int i = 2; i < p.length; i++) msg.pollOptions.add(p[i]);
        msg.pollMyVotes = new java.util.ArrayList<>();
        msg.pollOtherVotes = new java.util.ArrayList<>();
    }

    /** Создать опрос в личном диалоге (в глобал/каналы нельзя). */
    public static void sendPoll(String target, boolean multi, String question, java.util.List<String> options) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || target.isBlank() || question.isBlank() || options.size() < 2) return;
        String wire = PmWire.poll(multi, question, options);
        if (isLocalChat(target)) {
            PmMessage m = history.add(target, true, wire, 0);
            applyPoll(m, wire);
            history.save();
            return;
        }
        client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + wire);
        synchronized (pendingEcho) {
            pendingEcho.add(new String[]{target, wire, String.valueOf(System.currentTimeMillis() + 5000)});
        }
        PmMessage msg = history.add(target, true, wire, 0);
        applyPoll(msg, wire);
        history.save();
    }

    /** Проголосовать/переголосовать; собеседнику с модом уходит pmc pvote. */
    public static void castVote(String target, PmMessage poll, int optionIndex) {
        if (!poll.isPoll() || poll.text == null) return;
        if (poll.pollMyVotes == null) poll.pollMyVotes = new java.util.ArrayList<>();
        if (poll.pollMulti) {
            if (poll.pollMyVotes.contains(optionIndex)) poll.pollMyVotes.remove((Integer) optionIndex);
            else poll.pollMyVotes.add(optionIndex);
        } else {
            poll.pollMyVotes.clear();
            poll.pollMyVotes.add(optionIndex);
        }
        history.save();
        if (config.enableMeta && config.isModUser(target)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                String hash = PmHistory.msgHash(poll.text);
                client.player.networkHandler.sendChatCommand(
                        config.msgCommand + " " + target + " " + PmWire.pvote(hash, poll.pollMyVotes));
            }
        }
    }

    // ---------- NEW: секретные чаты (6.10) ----------

    private static PmSecretSession session(String target) {
        return secretSessions.computeIfAbsent(target.toLowerCase(Locale.ROOT), k -> new PmSecretSession());
    }

    public static PmSecretSession.State secretState(String target) {
        if (target == null) return PmSecretSession.State.NONE;
        PmSecretSession s = secretSessions.get(target.toLowerCase(Locale.ROOT));
        return s == null ? PmSecretSession.State.NONE : s.state;
    }

    public static boolean isSecretActive(String target) {
        return secretState(target) == PmSecretSession.State.ACTIVE;
    }

    /** Таймер самоуничтожения для следующих сообщений в этом секретном чате (сек, 0 — выкл). */
    public static int secretTtl(String target) {
        if (target == null) return 0;
        PmSecretSession s = secretSessions.get(target.toLowerCase(Locale.ROOT));
        return s == null ? 0 : s.ttlSeconds;
    }

    /** Варианты таймера по кругу: выкл → 10с → 30с → 1мин → 1ч → выкл. */
    private static final int[] TTL_OPTIONS = {0, 10, 30, 60, 3600};

    public static void cycleSecretTtl(String target) {
        PmSecretSession s = session(target);
        int idx = 0;
        for (int i = 0; i < TTL_OPTIONS.length; i++) {
            if (TTL_OPTIONS[i] == s.ttlSeconds) { idx = i; break; }
        }
        s.ttlSeconds = TTL_OPTIONS[(idx + 1) % TTL_OPTIONS.length];
    }

    /** Начать секретный чат: генерируем ключ, шлём запрос собеседнику. */
    public static void startSecretChat(String target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || target == null || target.isBlank()) return;
        PmSecretSession s = session(target);
        s.myKeyPair = PmCrypto.generateKeyPair();
        s.aesKey = null;
        s.state = PmSecretSession.State.PENDING;
        String pub = PmCrypto.hex(PmCrypto.rawPublicKey(s.myKeyPair));
        client.player.networkHandler.sendChatCommand(
                config.msgCommand + " " + target + " " + PmWire.secretRequest(pub));
    }

    /** Завершить секретный чат: собеседнику уходит метка, локально сессия стирается сразу. */
    public static void endSecretChat(String target) {
        MinecraftClient client = MinecraftClient.getInstance();
        secretSessions.remove(target.toLowerCase(Locale.ROOT));
        if (client.player == null || target == null || target.isBlank()) return;
        client.player.networkHandler.sendChatCommand(
                config.msgCommand + " " + target + " " + PmWire.secretEnd());
    }

    /**
     * Пришёл запрос секретного чата: генерируем свой ключ, считаем общий
     * секрет и сразу подтверждаем — без ручного «принять», чтобы не плодить
     * лишний UI поверх и без того сложного экрана.
     */
    private static void onSecretRequest(String sender, String peerPubHex) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        try {
            PmSecretSession s = session(sender);
            s.myKeyPair = PmCrypto.generateKeyPair();
            byte[] shared = PmCrypto.agree(s.myKeyPair, PmCrypto.unhex(peerPubHex));
            s.aesKey = PmCrypto.deriveAesKey(shared);
            s.state = PmSecretSession.State.ACTIVE;
            String pub = PmCrypto.hex(PmCrypto.rawPublicKey(s.myKeyPair));
            client.player.networkHandler.sendChatCommand(
                    config.msgCommand + " " + sender + " " + PmWire.secretAck(pub));
        } catch (Exception e) {
            LOGGER.warn("Secret chat handshake failed (request from {}): {}", sender, e.toString());
        }
    }

    /** Пришло подтверждение — досчитываем общий секрет со своей стороны, сессия активна. */
    private static void onSecretAck(String sender, String peerPubHex) {
        PmSecretSession s = secretSessions.get(sender.toLowerCase(Locale.ROOT));
        if (s == null || s.myKeyPair == null) return;
        try {
            byte[] shared = PmCrypto.agree(s.myKeyPair, PmCrypto.unhex(peerPubHex));
            s.aesKey = PmCrypto.deriveAesKey(shared);
            s.state = PmSecretSession.State.ACTIVE;
        } catch (Exception e) {
            LOGGER.warn("Secret chat handshake failed (ack from {}): {}", sender, e.toString());
        }
    }

    /** Пришло зашифрованное сообщение — расшифровываем и кладём в историю (в память, не на диск). */
    private static void onSecretMessage(String sender, int ttl, String nonceHex, String cipherHex) {
        PmSecretSession s = secretSessions.get(sender.toLowerCase(Locale.ROOT));
        String text = s != null && s.isActive() ? PmCrypto.decrypt(s.aesKey, nonceHex, cipherHex) : null;
        if (text == null) {
            text = Text.translatable("pmchat.secret.undecryptable").getString();
        }
        PmMessage msg = history.addSecret(sender, false, text, ttl);

        MinecraftClient client = MinecraftClient.getInstance();
        boolean viewing = client.currentScreen instanceof PmScreen screen && screen.isViewing(sender);
        if (viewing) {
            sendSeen(sender);
        } else {
            history.markUnread(sender);
            if (!config.dnd) {
                client.getToastManager().add(new PmToast(sender,
                        Text.translatable("pmchat.secret.toast").getString()));
                playNotifySound(client);
            }
        }
    }

    /** Отправка сообщения в активный секретный чат: шифруем и шлём вместо обычного /m текста. */
    public static PmMessage sendSecretMessage(String target, String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        PmSecretSession s = secretSessions.get(target.toLowerCase(Locale.ROOT));
        if (client.player == null || s == null || !s.isActive() || text == null || text.isBlank()) return null;
        try {
            String[] enc = PmCrypto.encrypt(s.aesKey, text);
            String wire = PmWire.secretMessage(s.ttlSeconds, enc[0], enc[1]);
            client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + wire);
            synchronized (pendingEcho) {
                pendingEcho.add(new String[]{target, wire, String.valueOf(System.currentTimeMillis() + 5000)});
            }
            return history.addSecret(target, true, text, s.ttlSeconds);
        } catch (Exception e) {
            LOGGER.warn("Secret message encryption failed: {}", e.toString());
            return null;
        }
    }

    /** Упоминают ли тебя: свой ник или доп. слова из настроек. */
    public static boolean mentionsMe(String text, String senderIfKnown) {
        if (!config.mentionEnabled || text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        String self = selfName().toLowerCase(Locale.ROOT);
        // Не считаем упоминанием свои же сообщения
        if (senderIfKnown != null && senderIfKnown.equalsIgnoreCase(selfName())) return false;
        if (!self.isBlank() && containsWord(lower, self)) return true;
        for (String w : config.mentionExtra.split(",")) {
            w = w.trim().toLowerCase(Locale.ROOT);
            if (!w.isEmpty() && containsWord(lower, w)) return true;
        }
        return false;
    }

    private static boolean containsWord(String haystack, String word) {
        int idx = haystack.indexOf(word);
        while (idx >= 0) {
            boolean lok = idx == 0 || !Character.isLetterOrDigit(haystack.charAt(idx - 1));
            int end = idx + word.length();
            boolean rok = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (lok && rok) return true;
            idx = haystack.indexOf(word, idx + 1);
        }
        return false;
    }

    private static void notifyMention(MinecraftClient client, String sender, String text) {
        client.getToastManager().add(new PmToast("@ " + sender, text));
        playNotifySound(client);
    }

    /** Эхо нашей отправки через мод пропускаем, чужие (набранные руками /m) — записываем. */
    private static int onOutgoingEcho(String target, String text) {
        if (PmWire.isTyping(text) || PmWire.isSeen(text) || PmWire.isHi(text)
                || PmWire.parseReaction(text) != null || PmWire.isPinMeta(text)
                || PmWire.isVoteMeta(text) || PmWire.isEditMeta(text) || PmWire.isUnpinMeta(text)
                || PmWire.isSecretMeta(text)) {
            return 2; // эхо собственной меты — прячем, в историю не пишем
        }
        long now = System.currentTimeMillis();
        synchronized (pendingEcho) {
            pendingEcho.removeIf(e -> Long.parseLong(e[2]) < now);
            for (String[] e : pendingEcho) {
                // Сверяем по ТЕКСТУ: Essentials мог фаззи-матчем доставить другому нику
                if (e[1].equals(text)) {
                    pendingEcho.remove(e);
                    if (!e[0].equalsIgnoreCase(target)) {
                        // Доставлено не тому, кого набирали — переносим сообщение в фактический диалог
                        Object[] repF = PmWire.parseReplyFrag(text);
                        String[] rep = repF == null ? PmWire.parseReply(text) : null;
                        String stored = repF != null ? (String) repF[3] : (rep != null ? rep[1] : text);
                        history.moveLastOutgoing(e[0], target, stored);
                    }
                    return 1;
                }
            }
        }
        String replyTo = null;
        Object[] repF = PmWire.parseReplyFrag(text);
        if (repF != null) {
            replyTo = (String) repF[0];
            text = (String) repF[3];
        } else {
            String[] reply = PmWire.parseReply(text);
            if (reply != null) {
                replyTo = reply[0];
                text = reply[1];
            }
        }
        PmMessage msg = history.add(target, true, text, 0);
        if (replyTo != null) {
            msg.replyTo = replyTo;
            history.save();
        }
        return 1;
    }

    /** Достаёт текст фрагмента [start, start+len) из оригинала (по хэшу) или null. */
    private static String sliceFragment(String conv, String hash, int start, int len) {
        PmMessage orig = history.findByHash(conv, hash);
        if (orig == null || orig.text == null) return null;
        String t = orig.text;
        if (start < 0 || len <= 0 || start >= t.length()) return null;
        return t.substring(start, Math.min(t.length(), start + len));
    }

    /** Отправка ЛС из мода: команда на сервер + локальная запись + ожидание эха. */
    public static PmMessage sendMessage(String target, String text) {
        return sendMessage(target, text, null);
    }

    public static PmMessage sendMessage(String target, String text, String replyToHash) {
        return sendMessage(target, text, replyToHash, -1, 0, null);
    }

    public static PmMessage sendMessage(String target, String text, String replyToHash,
                                        int fragStart, int fragLen, String fragText) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || target.isBlank() || text.isBlank()) return null;
        boolean hasFrag = replyToHash != null && fragStart >= 0 && fragText != null;

        // Локальный чат (Избранное) — только сохраняем, ничего не шлём на сервер
        if (isLocalChat(target)) {
            PmMessage m = history.add(target, true, text, 0);
            applyPoll(m, text);
            if (replyToHash != null) m.replyTo = replyToHash;
            if (hasFrag) m.replyFragment = fragText;
            history.save();
            return m;
        }

        // Маркер цитаты уходит по сети только модовым получателям
        String wire;
        if (replyToHash != null && config.isModUser(target)) {
            wire = hasFrag ? PmWire.replyFrag(replyToHash, fragStart, fragLen, text)
                    : PmWire.reply(replyToHash, text);
        } else {
            wire = text;
        }
        client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + wire);
        synchronized (pendingEcho) {
            pendingEcho.add(new String[]{target, wire, String.valueOf(System.currentTimeMillis() + 5000)});
        }
        PmMessage msg = history.add(target, true, text, 0);
        if (replyToHash != null) {
            msg.replyTo = replyToHash;
            if (hasFrag) msg.replyFragment = fragText;
            history.save();
        }
        return msg;
    }

    // ---------- Мета: печатает / прочитано (только между модами) ----------

    public static boolean isTyping(String player) {
        Long until = typingUntil.get(player);
        return until != null && until > System.currentTimeMillis();
    }

    public static void sendTyping(String target) {
        if (!config.enableMeta || !config.isModUser(target)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        Long last = lastTypingSent.get(target.toLowerCase(Locale.ROOT));
        if (last != null && now - last < 25000) return;
        lastTypingSent.put(target.toLowerCase(Locale.ROOT), now);
        client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + PmWire.TYPING);
    }

    public static void sendSeen(String target) {
        if (!config.enableMeta || !config.isModUser(target)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        Long last = lastSeenSent.get(target.toLowerCase(Locale.ROOT));
        if (last != null && now - last < 30000) return;
        lastSeenSent.put(target.toLowerCase(Locale.ROOT), now);
        client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + PmWire.SEEN);
    }

    /** Перевод денег из мессенджера: /pay + денежное сообщение в историю. */
    public static PmMessage sendMoney(String target, long amount) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || target.isBlank() || amount <= 0) return null;
        if (isLocalChat(target)) return null; // в Избранное деньги не переводим
        client.player.networkHandler.sendChatCommand(config.payCommand + " " + target + " " + amount);
        return history.add(target, true, "", amount);
    }

    public static String selfName() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null ? client.player.getGameProfile().name() : "";
    }

    public static boolean isRussian() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getLanguageManager().getLanguage().toLowerCase(Locale.ROOT).startsWith("ru");
    }

    /** NEW: открыть сайт документации в браузере — на английском, если у игрока не русский язык клиента. */
    public static void openDocs() {
        String url = isRussian()
                ? "https://yurosing.github.io/pocketchat/"
                : "https://yurosing.github.io/pocketchat/en/";
        try {
            net.minecraft.util.Util.getOperatingSystem().open(url);
        } catch (Exception e) {
            LOGGER.warn("Failed to open docs URL: {}", e.toString());
        }
    }

    public static PmConfig getConfig() {
        return config;
    }

    public static PmHistory getHistory() {
        return history;
    }

    public static KeyBinding getOpenKey() {
        return openKey;
    }
}
