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
    private static final int GLOBAL_LIMIT = 300;
    private static final java.util.List<PmMessage> globalChat =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /** Ожидаемые эхо-строки наших собственных отправок: "ник|текст" -> срок годности. */
    private static final Deque<String[]> pendingEcho = new ArrayDeque<>();


    /** Кто сейчас печатает: ник -> активно до (мс). */
    private static final java.util.Map<String, Long> typingUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> lastTypingSent = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> lastSeenSent = new java.util.HashMap<>();

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
            if (pinHash.equals("-")) config.pins.remove(sender);
            else config.pins.put(sender, pinHash);
            config.save();
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

        // Структурированные сообщения (фото/голос/цитата) — точно от мода
        if (PmWire.isStructured(text)) {
            config.addModUser(sender);
        } else {
            // Обычный текст: представляемся один раз — вдруг у него тоже мод
            sendHi(sender);
        }
        typingUntil.remove(sender);

        String replyTo = null;
        String[] reply = PmWire.parseReply(text);
        if (reply != null) {
            replyTo = reply[0];
            text = reply[1];
        }

        // Пересылка: pmc fwd <откуда> <содержимое>
        String forwardFrom = null;
        String[] fwd = PmWire.parseForward(text);
        if (fwd != null) {
            forwardFrom = fwd[0];
            text = fwd[1];
        }

        PmMessage msg = history.add(sender, false, text, 0);
        if (replyTo != null) msg.replyTo = replyTo;
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

    /** Закрепить/открепить сообщение: локально + собеседнику с модом. */
    public static void setPin(String target, String hashOrNull, boolean forBoth) {
        if (hashOrNull == null) config.pins.remove(target);
        else config.pins.put(target, hashOrNull);
        config.save();
        if (forBoth && config.enableMeta && config.isModUser(target)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.networkHandler.sendChatCommand(
                        config.msgCommand + " " + target + " " + PmWire.pin(hashOrNull));
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
                || PmWire.isVoteMeta(text)) {
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
                        String[] rep = PmWire.parseReply(text);
                        String stored = rep != null ? rep[1] : text;
                        history.moveLastOutgoing(e[0], target, stored);
                    }
                    return 1;
                }
            }
        }
        String[] reply = PmWire.parseReply(text);
        String replyTo = null;
        if (reply != null) {
            replyTo = reply[0];
            text = reply[1];
        }
        PmMessage msg = history.add(target, true, text, 0);
        if (replyTo != null) {
            msg.replyTo = replyTo;
            history.save();
        }
        return 1;
    }

    /** Отправка ЛС из мода: команда на сервер + локальная запись + ожидание эха. */
    public static PmMessage sendMessage(String target, String text) {
        return sendMessage(target, text, null);
    }

    public static PmMessage sendMessage(String target, String text, String replyToHash) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || target.isBlank() || text.isBlank()) return null;

        // Маркер цитаты уходит по сети только модовым получателям
        String wire = (replyToHash != null && config.isModUser(target))
                ? PmWire.reply(replyToHash, text)
                : text;
        client.player.networkHandler.sendChatCommand(config.msgCommand + " " + target + " " + wire);
        synchronized (pendingEcho) {
            pendingEcho.add(new String[]{target, wire, String.valueOf(System.currentTimeMillis() + 5000)});
        }
        PmMessage msg = history.add(target, true, text, 0);
        if (replyToHash != null) {
            msg.replyTo = replyToHash;
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
