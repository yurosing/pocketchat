package com.pmchat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Локальная история переписки: config/pmchat-history.json.
 * Ключ — ник собеседника, значение — сообщения по времени.
 */
public class PmHistory {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pmchat-history.json");
    // ключ шифрования истории — 32 случайных байта, лежат рядом
    // с файлом. Даёт шифрование «на диске»: скопировав только историю, её не
    // прочитать. Магия в начале файла отличает зашифрованный формат от старого
    // открытого JSON (для миграции).
    private static final Path KEY_FILE = FabricLoader.getInstance().getConfigDir().resolve("pmchat-history.key");
    private static final byte[] MAGIC = "PMHENC1\n".getBytes(StandardCharsets.US_ASCII);
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, List<PmMessage>>>() {}.getType();

    private LinkedHashMap<String, List<PmMessage>> conversations = new LinkedHashMap<>();

    /** Непрочитанные (не сохраняются между сессиями). */
    private final Map<String, Integer> unread = new HashMap<>();

    public static PmHistory load() {
        PmHistory history = new PmHistory();
        if (Files.exists(FILE)) {
            try {
                byte[] raw = Files.readAllBytes(FILE);
                String json = decodeFile(raw);
                if (json != null) {
                    LinkedHashMap<String, List<PmMessage>> data = GSON.fromJson(json, TYPE);
                    if (data != null) {
                        history.conversations = data;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return history;
    }

    /** Достаём JSON: если файл начинается с магии — расшифровываем, иначе это
     *  старый открытый JSON (мигрируем при следующем save). */
    private static String decodeFile(byte[] raw) {
        if (startsWithMagic(raw)) {
            try {
                byte[] blob = new byte[raw.length - MAGIC.length];
                System.arraycopy(raw, MAGIC.length, blob, 0, blob.length);
                byte[] plain = PmCrypto.open(PmCrypto.aesKey(loadOrCreateKey()), blob);
                return new String(plain, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return null; // ключ потерян/файл повреждён — начинаем с пустой истории
            }
        }
        return new String(raw, StandardCharsets.UTF_8); // старый формат
    }

    private static boolean startsWithMagic(byte[] raw) {
        if (raw.length < MAGIC.length) return false;
        for (int i = 0; i < MAGIC.length; i++) if (raw[i] != MAGIC[i]) return false;
        return true;
    }

    /** 32-байтный ключ рядом с историей; создаём при первом запуске. */
    private static byte[] loadOrCreateKey() throws IOException {
        if (Files.exists(KEY_FILE)) {
            byte[] k = Files.readAllBytes(KEY_FILE);
            if (k.length == 32) return k;
        }
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        Files.write(KEY_FILE, k);
        return k;
    }

    /**
     * секретные сообщения (6.10) на диск не пишутся — ни текст, ни сам
     * факт их существования. Перед сохранением строим копию без них.
     */
    public void save() {
        LinkedHashMap<String, List<PmMessage>> toWrite = conversations;
        boolean hasSecret = conversations.values().stream()
                .anyMatch(list -> list.stream().anyMatch(m -> m.secret));
        if (hasSecret) {
            toWrite = new LinkedHashMap<>();
            for (Map.Entry<String, List<PmMessage>> e : conversations.entrySet()) {
                List<PmMessage> filtered = new ArrayList<>();
                for (PmMessage m : e.getValue()) {
                    if (!m.secret) filtered.add(m);
                }
                if (!filtered.isEmpty()) toWrite.put(e.getKey(), filtered);
            }
        }
        try {
            String json = GSON.toJson(toWrite, TYPE);
            SecretKey key = PmCrypto.aesKey(loadOrCreateKey());
            byte[] blob = PmCrypto.seal(key, json.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[MAGIC.length + blob.length];
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            System.arraycopy(blob, 0, out, MAGIC.length, blob.length);
            Files.write(FILE, out);
        } catch (Exception ignored) {
        }
    }

    /** снести просроченные секретные сообщения (самоуничтожение). true, если что-то удалили. */
    public boolean sweepExpiredSecrets(long now) {
        boolean changed = false;
        for (List<PmMessage> list : conversations.values()) {
            changed |= list.removeIf(m -> m.secret && m.destructAt > 0 && m.destructAt <= now);
        }
        return changed;
    }

    public PmMessage add(String player, boolean out, String text, long money) {
        PmMessage msg = new PmMessage(out, text, System.currentTimeMillis(), money);
        conversations.computeIfAbsent(player, k -> new ArrayList<>()).add(msg);
        save();
        return msg;
    }

    /**
     * добавить секретное сообщение (6.10) — помечено секретным ДО первого
     * save(), чтобы текст ни на миг не попал в файл на диске.
     */
    public PmMessage addSecret(String player, boolean out, String text, int ttlSeconds) {
        PmMessage msg = new PmMessage(out, text, System.currentTimeMillis(), 0);
        msg.secret = true;
        if (ttlSeconds > 0) msg.destructAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        conversations.computeIfAbsent(player, k -> new ArrayList<>()).add(msg);
        save();
        return msg;
    }

    public List<PmMessage> messages(String player) {
        return conversations.getOrDefault(player, List.of());
    }

    /** Ники диалогов, свежие сверху. */
    public List<String> conversationNames() {
        List<String> names = new ArrayList<>(conversations.keySet());
        names.sort(Comparator.comparingLong(this::lastTime).reversed());
        return names;
    }

    public long lastTime(String player) {
        List<PmMessage> list = conversations.get(player);
        return list == null || list.isEmpty() ? 0 : list.get(list.size() - 1).time;
    }

    public PmMessage lastMessage(String player) {
        List<PmMessage> list = conversations.get(player);
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    /** Короткий хэш текста — общий у обеих сторон, служит id для цитат. */
    public static String msgHash(String text) {
        return Integer.toHexString(text == null ? 0 : text.hashCode());
    }

    /** Последнее сообщение диалога с данным хэшем текста (для цитаты). */
    public PmMessage findByHash(String player, String hash) {
        List<PmMessage> list = messages(player);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (msgHash(list.get(i).text).equals(hash)) {
                return list.get(i);
            }
        }
        return null;
    }

    /**
     * Essentials доставил другому игроку (фаззи-матч ника): переносим
     * последнее наше сообщение из набранного диалога в фактический.
     */
    public PmMessage moveLastOutgoing(String from, String to, String text) {
        List<PmMessage> src = conversations.get(from);
        if (src == null) return null;
        for (int i = src.size() - 1; i >= 0; i--) {
            PmMessage msg = src.get(i);
            if (msg.out && text.equals(msg.text)) {
                src.remove(i);
                if (src.isEmpty()) {
                    conversations.remove(from);
                }
                conversations.computeIfAbsent(to, k -> new ArrayList<>()).add(msg);
                save();
                return msg;
            }
        }
        return null;
    }

    /** Пометить все наши сообщения в диалоге прочитанными (пришло [seen]). */
    public void markAllOutgoingRead(String player) {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (PmMessage msg : messages(player)) {
            if (msg.out && !msg.read) {
                msg.read = true;
                msg.readTime = now;
                changed = true;
            }
        }
        if (changed) save();
    }

    /** Удалить одно сообщение из диалога (локально). */
    public void deleteMessage(String player, PmMessage msg) {
        List<PmMessage> list = conversations.get(player);
        if (list != null && list.remove(msg)) {
            if (list.isEmpty()) {
                conversations.remove(player);
            }
            save();
        }
    }

    /** Полностью удалить диалог с игроком. */
    public void clearConversation(String player) {
        conversations.remove(player);
        unread.remove(player);
        save();
    }

    // ---------- Непрочитанные ----------

    public void markUnread(String player) {
        unread.merge(player, 1, Integer::sum);
    }

    public void clearUnread(String player) {
        unread.remove(player);
    }

    public int unreadCount(String player) {
        return unread.getOrDefault(player, 0);
    }

    public int totalUnread() {
        return unread.values().stream().mapToInt(Integer::intValue).sum();
    }

    // ---------- Статистика ----------

    public int totalMessages() {
        return conversations.values().stream().mapToInt(List::size).sum();
    }

    public int countIn(String player, boolean out) {
        return (int) messages(player).stream().filter(m -> m.out == out).count();
    }

    public long moneySent(String player) {
        return messages(player).stream().filter(m -> m.out && m.money > 0).mapToLong(m -> m.money).sum();
    }

    public long firstTime(String player) {
        List<PmMessage> list = conversations.get(player);
        return list == null || list.isEmpty() ? 0 : list.get(0).time;
    }

    /** Топ собеседников по числу сообщений. */
    public List<Map.Entry<String, Integer>> topContacts(int limit) {
        List<Map.Entry<String, Integer>> top = new ArrayList<>();
        for (Map.Entry<String, List<PmMessage>> e : conversations.entrySet()) {
            top.add(Map.entry(e.getKey(), e.getValue().size()));
        }
        top.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return top.subList(0, Math.min(limit, top.size()));
    }

    /** Поиск: есть ли совпадение в нике или сообщениях диалога. */
    public boolean matches(String player, String queryLower) {
        if (player.toLowerCase(Locale.ROOT).contains(queryLower)) return true;
        for (PmMessage msg : messages(player)) {
            if (msg.text != null && msg.text.toLowerCase(Locale.ROOT).contains(queryLower)) {
                return true;
            }
        }
        return false;
    }
}
