package com.pmchat.client;

import net.minecraft.client.Minecraft;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * «Общий вайб» (5.8): фоновый эмбиент, зацикленный одновременно у обоих
 * собеседников, пока идёт переписка — как будто вы в одной комнате. Свой файл
 * (config/pmchat-vibe, WAV/AU/AIFF — как и голосовые) грузится на тот же
 * хостинг, что фото/голос/видео ({@link PmImages#upload}); собеседник просто
 * скачивает тот же файл и зацикливает у себя. Один активный вайб за раз.
 */
public final class PmVibe {

    private PmVibe() {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    private static volatile Clip clip;
    private static volatile String activeConversation;
    private static volatile String activeTrackName;

    /** Ждущее решения игрока приглашение от собеседника (5.8) — не играет, пока не принято. */
    public static final class Invite {
        public final String sender;
        public final String hostCode;
        public final String fileId;
        public final long at;

        Invite(String sender, String hostCode, String fileId, long at) {
            this.sender = sender;
            this.hostCode = hostCode;
            this.fileId = fileId;
            this.at = at;
        }
    }

    private static volatile Invite pendingInvite;

    public static Path dir() {
        Path d = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("pmchat-vibe");
        try {
            Files.createDirectories(d);
        } catch (Exception ignored) {
        }
        return d;
    }

    /** Локальные файлы-эмбиенты, которые можно включить (сам кладёшь в config/pmchat-vibe). */
    public static List<Path> listTracks() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".wav") || n.endsWith(".au") || n.endsWith(".aif") || n.endsWith(".aiff");
            }).sorted().forEach(out::add);
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean isActive() {
        return clip != null;
    }

    public static boolean isActiveFor(String conversation) {
        return clip != null && activeConversation != null && conversation != null
                && activeConversation.equalsIgnoreCase(conversation);
    }

    /** Имя проигрываемого сейчас трека (для маленького индикатора в шапке чата) или null. */
    public static String activeTrackLabel() {
        return activeTrackName;
    }

    /** Грузит выбранный локальный файл, запускает у себя и зовёт собеседника включить тот же. */
    public static void startForConversation(String conversation, Path file) {
        if (conversation == null || file == null) return;
        PmImages.upload(file).whenComplete((res, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null || res == null) return;
            try {
                byte[] bytes = Files.readAllBytes(file);
                PmImages.saveToDisk(res[0], res[1], bytes);
                playBytes(bytes, conversation, file.getFileName().toString());
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("Vibe start failed: {}", e.toString());
            }
            PmChatClient.sendMessage(conversation, PmWire.vibe(res[0], res[1], true));
        }));
    }

    /**
     * Собеседник зовёт включить вайб у себя — НЕ скачиваем и не играем сразу
     * (чужой звук без спроса — плохо), а откладываем приглашение: игрок сам
     * решает через {@link #acceptInvite} / {@link #declineInvite} (см. полоску
     * над списком сообщений в PmScreen, и тост — если мессенджер сейчас закрыт).
     */
    public static void onIncoming(String sender, String hostCode, String fileId) {
        if (sender == null) return;
        pendingInvite = new Invite(sender, hostCode, fileId, System.currentTimeMillis());
        Minecraft client = Minecraft.getInstance();
        if (!PmChatClient.getConfig().dnd) {
            client.gui.toastManager().addToast(new PmToast(sender,
                    net.minecraft.network.chat.Component.translatable("pmchat.vibe.invite.toast").getString()));
        }
    }

    public static Invite pendingInviteFor(String conversation) {
        Invite inv = pendingInvite;
        return inv != null && conversation != null && inv.sender.equalsIgnoreCase(conversation) ? inv : null;
    }

    /** Игрок принял приглашение — только теперь качаем (или берём из кэша) файл и зацикливаем. */
    public static void acceptInvite(String conversation) {
        Invite inv = pendingInviteFor(conversation);
        if (inv == null) return;
        pendingInvite = null;
        CompletableFuture.runAsync(() -> {
            try {
                Path cached = PmImages.mediaFile(inv.hostCode, inv.fileId);
                byte[] bytes;
                if (Files.exists(cached)) {
                    bytes = Files.readAllBytes(cached);
                } else {
                    HttpResponse<byte[]> resp = HTTP.send(HttpRequest.newBuilder()
                                    .uri(URI.create(PmHosts.baseUrl(inv.hostCode) + inv.fileId))
                                    .timeout(Duration.ofSeconds(15))
                                    .header("User-Agent", "pmchat-mod/1.0")
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() != 200) return;
                    bytes = resp.body();
                    PmImages.saveToDisk(inv.hostCode, inv.fileId, bytes);
                }
                byte[] fBytes = bytes;
                Minecraft.getInstance().execute(() -> playBytes(fBytes, inv.sender, inv.fileId));
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("Vibe fetch failed: {}", e.toString());
            }
        });
    }

    /** Игрок отклонил приглашение — просто гасим его, собеседнику специально не сообщаем. */
    public static void declineInvite(String conversation) {
        Invite inv = pendingInviteFor(conversation);
        if (inv != null) pendingInvite = null;
    }

    /** Собеседник выключил у себя — гасим (активный вайб или неотвеченное приглашение) у нас. */
    public static void onIncomingStop(String sender) {
        if (sender == null) return;
        if (activeConversation != null && activeConversation.equalsIgnoreCase(sender)) stop();
        Invite inv = pendingInvite;
        if (inv != null && inv.sender.equalsIgnoreCase(sender)) pendingInvite = null;
    }

    private static void playBytes(byte[] bytes, String conversation, String trackName) {
        stopClipOnly();
        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
            Clip c = AudioSystem.getClip();
            c.open(stream);
            c.loop(Clip.LOOP_CONTINUOUSLY);
            clip = c;
            activeConversation = conversation;
            activeTrackName = trackName;
        } catch (Exception e) {
            PmChatClient.LOGGER.warn("Vibe playback failed: {}", e.toString());
        }
    }

    private static void stopClipOnly() {
        Clip c = clip;
        clip = null;
        if (c != null) {
            try {
                c.stop();
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** Гасит локально без уведомления собеседника (выход с сервера и т.п.). */
    public static void stop() {
        stopClipOnly();
        activeConversation = null;
        activeTrackName = null;
        pendingInvite = null;
    }

    /** Явная остановка игроком — гасит у себя и шлёт «стоп» собеседнику. */
    public static void stopAndNotify() {
        String conv = activeConversation;
        stop();
        if (conv != null) PmChatClient.sendMessage(conv, PmWire.vibeStop());
    }
}
