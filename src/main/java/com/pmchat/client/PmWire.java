package com.pmchat.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Формат служебных сообщений в /m. Только буквы, цифры и пробелы —
 * анти-рекламные фильтры серверов режут точки/двоеточия/скобки.
 * Все старые форматы распознаются при приёме для совместимости.
 */
public final class PmWire {

    // v3: pmc img <host> <name> <ext> / pmc voice <host> <name> <ext> <secs>
    private static final Pattern IMG_V3 = Pattern.compile("^pmc img ([a-z]) ([A-Za-z0-9_-]+) ([A-Za-z0-9]+)$");
    private static final Pattern VOICE_V3 = Pattern.compile("^pmc voice ([a-z]) ([A-Za-z0-9_-]+) ([A-Za-z0-9]+) (\\d+)$");
    // Видео: pmc vid <host> <name> <ext>
    private static final Pattern VID = Pattern.compile("^pmc vid ([a-z]) ([A-Za-z0-9_-]+) ([A-Za-z0-9]+)$");
    // спойлер — то же самое + " s" в конце. pmc img/vid ... s
    private static final Pattern IMG_SPOILER = Pattern.compile("^pmc img ([a-z]) ([A-Za-z0-9_-]+) ([A-Za-z0-9]+) s$");
    private static final Pattern VID_SPOILER = Pattern.compile("^pmc vid ([a-z]) ([A-Za-z0-9_-]+) ([A-Za-z0-9]+) s$");
    // v2 (без хоста — catbox)
    private static final Pattern IMG_V2 = Pattern.compile("^pmc img ([A-Za-z0-9_-]+) ([A-Za-z0-9]+)$");
    private static final Pattern VOICE_V2 = Pattern.compile("^pmc voice ([A-Za-z0-9_-]+) ([A-Za-z0-9]+) (\\d+)$");
    // v1 (скобки)
    private static final Pattern IMG_V1 = Pattern.compile("^\\[img:([A-Za-z0-9._-]+)\\]$");
    private static final Pattern VOICE_V1 = Pattern.compile("^\\[voice:([A-Za-z0-9._-]+):(\\d+)\\]$");

    private static final Pattern RE_NEW = Pattern.compile("^pmc re ([0-9a-fA-F]{1,8}) (.+)$");
    private static final Pattern RE_OLD = Pattern.compile("^\\[re:([0-9a-fA-F]{1,8})\\](.+)$");
    // Ответ на фрагмент: pmc ref <hash> <start> <len> <текст>
    private static final Pattern RE_FRAG = Pattern.compile("^pmc ref ([0-9a-fA-F]{1,8}) (\\d+) (\\d+) (.+)$");

    // Реакция: pmc rx <hash> <index>
    private static final Pattern RX = Pattern.compile("^pmc rx ([0-9a-fA-F]{1,8}) (\\d{1,2})$");
    // Правка: pmc edit <hash> <новый текст>
    private static final Pattern EDIT = Pattern.compile("^pmc edit ([0-9a-fA-F]{1,8}) (.+)$", Pattern.DOTALL);
    // Пересылка: pmc fwd <откуда> <исходное содержимое>
    private static final Pattern FWD = Pattern.compile("^pmc fwd (\\S{1,20}) (.+)$", Pattern.DOTALL);
    // Закреп: pmc pin <hash> (добавить), pmc pin - (открепить все)
    private static final Pattern PIN = Pattern.compile("^pmc pin ([0-9a-fA-F]{1,8}|-)$");
    // Открепить одно: pmc unpin <hash>
    private static final Pattern UNPIN = Pattern.compile("^pmc unpin ([0-9a-fA-F]{1,8})$");
    // Опрос: pmc poll <multi> вопрос // вариант1 // вариант2 ...
    private static final Pattern POLL = Pattern.compile("^pmc poll ([01]) (.+)$", Pattern.DOTALL);
    // Голос: pmc pvote <pollHash> <индексы через запятую или ->
    private static final Pattern PVOTE = Pattern.compile("^pmc pvote ([0-9a-fA-F]{1,8}) ([-0-9,]+)$");
    // Групповое сообщение: pmc grp <hexИмя> <составЧерезДефис> <текст>
    private static final Pattern GRP = Pattern.compile("^pmc grp ([0-9a-f]*) ([A-Za-z0-9_-]+) (.+)$", Pattern.DOTALL);

    // ---------- Публичные каналы (аналог Telegram-каналов, 3.2) ----------
    // Пост: pmc bc <id> <владелецHex> <имяHex> <описаниеHex> <числоПодписчиков> <текст>
    private static final Pattern BC_POST = Pattern.compile(
            "^pmc bc ([0-9a-f]{1,8}) ([0-9a-f]*) ([0-9a-f]*) ([0-9a-f]*) (\\d+) (.+)$", Pattern.DOTALL);
    // «Добро пожаловать» (ответ на заявку, без поста): pmc bcw <id> <имяHex> <описаниеHex> <числоПодписчиков>
    private static final Pattern BC_WELCOME = Pattern.compile(
            "^pmc bcw ([0-9a-f]{1,8}) ([0-9a-f]*) ([0-9a-f]*) (\\d+)$");
    // Заявка на подписку (получателю — владельцу канала): pmc bcj <id>
    private static final Pattern BC_JOIN = Pattern.compile("^pmc bcj ([0-9a-f]{1,8})$");
    // Отписка: pmc bcl <id>
    private static final Pattern BC_LEAVE = Pattern.compile("^pmc bcl ([0-9a-f]{1,8})$");
    // Выдача прав админа + копия состава подписчиков: pmc bcg <id> [составЧерезДефис]
    // (состав необязателен — у свежесозданного канала подписчиков может ещё не быть)
    private static final Pattern BC_GRANT = Pattern.compile("^pmc bcg ([0-9a-f]{1,8})(?: ([A-Za-z0-9_-]+))?$");
    // Снятие прав админа: pmc bcr <id>
    private static final Pattern BC_REVOKE = Pattern.compile("^pmc bcr ([0-9a-f]{1,8})$");
    // Отметка «просмотрено» (владельцу, для счётчика просмотров): pmc bcv <id> <hash>
    private static final Pattern BC_VIEW = Pattern.compile("^pmc bcv ([0-9a-f]{1,8}) ([0-9a-fA-F]{1,8})$");
    // Закреп/откреп поста канала: pmc bcp <id> <hash|-> / pmc bcu <id> <hash>
    private static final Pattern BC_PIN = Pattern.compile("^pmc bcp ([0-9a-f]{1,8}) ([0-9a-fA-F]{1,8}|-)$");
    private static final Pattern BC_UNPIN = Pattern.compile("^pmc bcu ([0-9a-f]{1,8}) ([0-9a-fA-F]{1,8})$");

    // секретные чаты — сквозное шифрование.
    // pmc sec req <pubHex 64> — запрос сессии со своим публичным ключом X25519
    private static final Pattern SEC_REQ = Pattern.compile("^pmc sec req ([0-9a-f]{64})$");
    // pmc sec ack <pubHex 64> — подтверждение с ответным ключом
    private static final Pattern SEC_ACK = Pattern.compile("^pmc sec ack ([0-9a-f]{64})$");
    // pmc sec end — закрыть секретный чат
    private static final String SEC_END = "pmc sec end";
    // pmc sec msg <ttlSeconds> <nonceHex 24> <cipherHex>
    private static final Pattern SEC_MSG = Pattern.compile("^pmc sec msg (\\d+) ([0-9a-f]{24}) ([0-9a-f]+)$");

    // pmc call — просто уведомление «зову в войсчат» (сам звонок
    // делает команда сервера /voicechat invite, у неё нет параметров группы/пароля)
    private static final String CALL = "pmc call";

    public static final String POLL_DELIM = " // ";

    public static final String TYPING = "pmc typ";
    public static final String SEEN = "pmc seen";
    public static final String HI = "pmc hi";

    /** Набор реакций (BMP-символы, есть в шрифте игры). */
    public static final String[] REACTIONS = {"❤", "★", "☺", "☹", "⚡", "✔"};
    /** Цвета реакций (в тон символам). */
    public static final int[] REACTION_COLORS = {
            0xFFE8698B, 0xFFF0C34E, 0xFF6FBF8B, 0xFF7E9AAB, 0xFF5AB0E0, 0xFF8FD8A8
    };

    public static int reactionColor(String symbol) {
        for (int i = 0; i < REACTIONS.length; i++) {
            if (REACTIONS[i].equals(symbol)) return REACTION_COLORS[i];
        }
        return 0xFFEDF3F0;
    }

    private PmWire() {
    }

    // ---------- Сборка ----------

    public static String img(String hostCode, String fileId) {
        return img(hostCode, fileId, false);
    }

    /** фото со спойлером — скрыто размытием, пока получатель не кликнет. */
    public static String img(String hostCode, String fileId, boolean spoiler) {
        int dot = fileId.lastIndexOf('.');
        String name = dot > 0 ? fileId.substring(0, dot) : fileId;
        String ext = dot > 0 ? fileId.substring(dot + 1) : "png";
        String base = "pmc img " + hostCode + " " + name + " " + ext;
        return spoiler ? base + " s" : base;
    }

    public static String voice(String hostCode, String fileId, int seconds) {
        int dot = fileId.lastIndexOf('.');
        String name = dot > 0 ? fileId.substring(0, dot) : fileId;
        String ext = dot > 0 ? fileId.substring(dot + 1) : "wav";
        return "pmc voice " + hostCode + " " + name + " " + ext + " " + seconds;
    }

    public static String vid(String hostCode, String fileId) {
        return vid(hostCode, fileId, false);
    }

    /** видео со спойлером — скрыто размытием, пока получатель не кликнет. */
    public static String vid(String hostCode, String fileId, boolean spoiler) {
        int dot = fileId.lastIndexOf('.');
        String name = dot > 0 ? fileId.substring(0, dot) : fileId;
        String ext = dot > 0 ? fileId.substring(dot + 1) : "mp4";
        String base = "pmc vid " + hostCode + " " + name + " " + ext;
        return spoiler ? base + " s" : base;
    }

    /** {код хоста, id файла, "1"/"0" — спойлер} или null. */
    public static String[] parseVid(String text) {
        if (text == null) return null;
        String t = text.trim();
        Matcher m = VID_SPOILER.matcher(t);
        if (m.matches()) return new String[]{m.group(1), m.group(2) + "." + m.group(3), "1"};
        m = VID.matcher(t);
        if (m.matches()) return new String[]{m.group(1), m.group(2) + "." + m.group(3), "0"};
        return null;
    }

    public static String reply(String hash, String text) {
        return "pmc re " + hash + " " + text;
    }

    /** Ответ на фрагмент цитируемого сообщения (start/len — смещение в оригинале). */
    public static String replyFrag(String hash, int start, int len, String text) {
        return "pmc ref " + hash + " " + start + " " + len + " " + text;
    }

    /** {хэш, start, len, текст} или null. */
    public static Object[] parseReplyFrag(String text) {
        if (text == null) return null;
        Matcher m = RE_FRAG.matcher(text.trim());
        if (!m.matches()) return null;
        try {
            return new Object[]{m.group(1), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), m.group(4).trim()};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String reaction(String hash, int index) {
        return "pmc rx " + hash + " " + index;
    }

    /** Правка сообщения: старый хэш + новый текст. */
    public static String edit(String hash, String text) {
        return "pmc edit " + hash + " " + text;
    }

    /** {хэш, новый текст} или null. */
    public static String[] parseEdit(String text) {
        if (text == null) return null;
        Matcher m = EDIT.matcher(text.trim());
        return m.matches() ? new String[]{m.group(1), m.group(2).trim()} : null;
    }

    public static boolean isEditMeta(String text) {
        return parseEdit(text) != null;
    }

    public static String forward(String from, String innerContent) {
        return "pmc fwd " + from + " " + innerContent;
    }

    public static String pin(String hash) {
        return "pmc pin " + (hash == null || hash.isEmpty() ? "-" : hash);
    }

    public static String unpinOne(String hash) {
        return "pmc unpin " + hash;
    }

    /** Хэш откреплённого сообщения (pmc unpin) или null. */
    public static String parseUnpin(String text) {
        if (text == null) return null;
        Matcher m = UNPIN.matcher(text.trim());
        return m.matches() ? m.group(1) : null;
    }

    public static boolean isUnpinMeta(String text) {
        return parseUnpin(text) != null;
    }

    public static String poll(boolean multi, String question, java.util.List<String> options) {
        StringBuilder sb = new StringBuilder("pmc poll ").append(multi ? "1" : "0").append(" ");
        sb.append(question.replace(POLL_DELIM, " / "));
        for (String o : options) sb.append(POLL_DELIM).append(o.replace(POLL_DELIM, " / "));
        return sb.toString();
    }

    public static String pvote(String pollHash, java.util.List<Integer> indices) {
        String csv = indices.isEmpty() ? "-" : indices.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        return "pmc pvote " + pollHash + " " + csv;
    }

    /** {multi("0"/"1"), вопрос, вариант1, вариант2, ...} или null. */
    public static String[] parsePoll(String text) {
        if (text == null) return null;
        Matcher m = POLL.matcher(text.trim());
        if (!m.matches()) return null;
        String[] parts = m.group(2).split(java.util.regex.Pattern.quote(POLL_DELIM), -1);
        if (parts.length < 3) return null; // вопрос + минимум 2 варианта
        String[] out = new String[parts.length + 1];
        out[0] = m.group(1);
        System.arraycopy(parts, 0, out, 1, parts.length);
        return out;
    }

    /** {pollHash, список индексов} или null. */
    public static Object[] parseVote(String text) {
        if (text == null) return null;
        Matcher m = PVOTE.matcher(text.trim());
        if (!m.matches()) return null;
        java.util.List<Integer> idx = new java.util.ArrayList<>();
        if (!m.group(2).equals("-")) {
            for (String s : m.group(2).split(",")) {
                try { idx.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        return new Object[]{m.group(1), idx};
    }

    /** {откуда, исходное содержимое} или null. */
    public static String[] parseForward(String text) {
        if (text == null) return null;
        Matcher m = FWD.matcher(text.trim());
        return m.matches() ? new String[]{m.group(1), m.group(2).trim()} : null;
    }

    /** hash закрепа, "-" = открепить, null = не pin-метка. */
    public static String parsePin(String text) {
        if (text == null) return null;
        Matcher m = PIN.matcher(text.trim());
        return m.matches() ? m.group(1) : null;
    }

    // ---------- Группы ----------

    /** UTF-8 -> hex (только 0-9a-f, безопасно для анти-рекламных фильтров). */
    public static String hex(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** hex -> UTF-8 (обратно к {@link #hex}). */
    public static String unhex(String h) {
        if (h == null || h.isEmpty() || (h.length() & 1) != 0) return "";
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(h.charAt(i * 2), 16);
            int lo = Character.digit(h.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return "";
            out[i] = (byte) ((hi << 4) | lo);
        }
        return new String(out, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Групповое сообщение: имя (hex) + полный состав + текст. */
    public static String group(String name, java.util.List<String> roster, String text) {
        return "pmc grp " + hex(name) + " " + String.join("-", roster) + " " + text;
    }

    /** {имя, состав(String[]), текст} или null. */
    public static Object[] parseGroup(String text) {
        if (text == null) return null;
        Matcher m = GRP.matcher(text.trim());
        if (!m.matches()) return null;
        String name = unhex(m.group(1));
        String[] roster = m.group(2).split("-");
        return new Object[]{name, roster, m.group(3).trim()};
    }

    public static boolean isGroupMeta(String text) {
        return parseGroup(text) != null;
    }

    // ---------- Публичные каналы (аналог Telegram-каналов, 3.2) ----------

    /**
     * Пост в канале. Владелец шлётся отдельным полем (не только sender), т.к. пост
     * может прийти от админа, а не от владельца — получателю нужно знать, кому
     * писать заявку/отписку/и т.п.
     */
    public static String bcPost(String id, String owner, String name, String description, int count, String text) {
        return "pmc bc " + id + " " + hex(owner) + " " + hex(name) + " " + hex(description) + " " + count + " " + text;
    }

    /** {id, владелец, имя, описание, число подписчиков(Integer), текст} или null. */
    public static Object[] parseBcPost(String text) {
        if (text == null) return null;
        Matcher m = BC_POST.matcher(text.trim());
        if (!m.matches()) return null;
        try {
            return new Object[]{m.group(1), unhex(m.group(2)), unhex(m.group(3)), unhex(m.group(4)),
                    Integer.parseInt(m.group(5)), m.group(6).trim()};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Ответ владельца на заявку на подписку — заводит вкладку канала даже без единого поста. */
    public static String bcWelcome(String id, String name, String description, int count) {
        return "pmc bcw " + id + " " + hex(name) + " " + hex(description) + " " + count;
    }

    /** {id, имя, описание, число подписчиков(Integer)} или null. */
    public static Object[] parseBcWelcome(String text) {
        if (text == null) return null;
        Matcher m = BC_WELCOME.matcher(text.trim());
        if (!m.matches()) return null;
        try {
            return new Object[]{m.group(1), unhex(m.group(2)), unhex(m.group(3)), Integer.parseInt(m.group(4))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String bcJoin(String id) {
        return "pmc bcj " + id;
    }

    /** id канала из заявки на подписку или null. */
    public static String parseBcJoin(String text) {
        if (text == null) return null;
        Matcher m = BC_JOIN.matcher(text.trim());
        return m.matches() ? m.group(1) : null;
    }

    public static String bcLeave(String id) {
        return "pmc bcl " + id;
    }

    /** id канала из отписки или null. */
    public static String parseBcLeave(String text) {
        if (text == null) return null;
        Matcher m = BC_LEAVE.matcher(text.trim());
        return m.matches() ? m.group(1) : null;
    }

    public static String bcGrant(String id, java.util.List<String> roster) {
        return roster.isEmpty() ? "pmc bcg " + id : "pmc bcg " + id + " " + String.join("-", roster);
    }

    /** {id, состав(String[])} или null. */
    public static Object[] parseBcGrant(String text) {
        if (text == null) return null;
        Matcher m = BC_GRANT.matcher(text.trim());
        if (!m.matches()) return null;
        String rosterRaw = m.group(2);
        String[] roster = rosterRaw == null || rosterRaw.isEmpty() ? new String[0] : rosterRaw.split("-");
        return new Object[]{m.group(1), roster};
    }

    public static String bcRevoke(String id) {
        return "pmc bcr " + id;
    }

    /** id канала из снятия прав админа или null. */
    public static String parseBcRevoke(String text) {
        if (text == null) return null;
        Matcher m = BC_REVOKE.matcher(text.trim());
        return m.matches() ? m.group(1) : null;
    }

    public static String bcView(String id, String hash) {
        return "pmc bcv " + id + " " + hash;
    }

    /** {id, hash} или null. */
    public static String[] parseBcView(String text) {
        if (text == null) return null;
        Matcher m = BC_VIEW.matcher(text.trim());
        return m.matches() ? new String[]{m.group(1), m.group(2)} : null;
    }

    public static String bcPin(String id, String hash) {
        return "pmc bcp " + id + " " + (hash == null || hash.isEmpty() ? "-" : hash);
    }

    /** {id, hash или "-" (открепить все)} или null. */
    public static String[] parseBcPin(String text) {
        if (text == null) return null;
        Matcher m = BC_PIN.matcher(text.trim());
        return m.matches() ? new String[]{m.group(1), m.group(2)} : null;
    }

    public static String bcUnpin(String id, String hash) {
        return "pmc bcu " + id + " " + hash;
    }

    /** {id, hash} или null. */
    public static String[] parseBcUnpin(String text) {
        if (text == null) return null;
        Matcher m = BC_UNPIN.matcher(text.trim());
        return m.matches() ? new String[]{m.group(1), m.group(2)} : null;
    }

    /** Служебные (не-постовые) строки канала — их прячем как обычную мету, без пометки в истории. */
    public static boolean isBcControlMeta(String text) {
        return parseBcWelcome(text) != null || parseBcJoin(text) != null || parseBcLeave(text) != null
                || parseBcGrant(text) != null || parseBcRevoke(text) != null || parseBcView(text) != null
                || parseBcPin(text) != null || parseBcUnpin(text) != null;
    }

    public static boolean isBroadcastMeta(String text) {
        return parseBcPost(text) != null || isBcControlMeta(text);
    }

    // ---------- секретные чаты (6.10) ----------

    public static String secretRequest(String pubHex) {
        return "pmc sec req " + pubHex;
    }

    public static String secretAck(String pubHex) {
        return "pmc sec ack " + pubHex;
    }

    public static String secretEnd() {
        return SEC_END;
    }

    public static String secretMessage(int ttlSeconds, String nonceHex, String cipherHex) {
        return "pmc sec msg " + ttlSeconds + " " + nonceHex + " " + cipherHex;
    }

    /** Публичный ключ (hex) из запроса или null. */
    public static String parseSecretRequest(String text) {
        if (text == null) return null;
        Matcher m = SEC_REQ.matcher(text.trim());
        return m.matches() ? m.group(1) : null;
    }

    /** Публичный ключ (hex) из подтверждения или null. */
    public static String parseSecretAck(String text) {
        if (text == null) return null;
        Matcher m = SEC_ACK.matcher(text.trim());
        return m.matches() ? m.group(1) : null;
    }

    public static boolean isSecretEnd(String text) {
        return text != null && text.trim().equals(SEC_END);
    }

    /** {ttlSeconds, nonceHex, cipherHex} или null. */
    public static String[] parseSecretMessage(String text) {
        if (text == null) return null;
        Matcher m = SEC_MSG.matcher(text.trim());
        return m.matches() ? new String[]{m.group(1), m.group(2), m.group(3)} : null;
    }

    public static boolean isSecretMeta(String text) {
        if (text == null) return false;
        String t = text.trim();
        return parseSecretRequest(t) != null || parseSecretAck(t) != null || isSecretEnd(t);
    }

    // ---------- звонки через Simple Voice Chat (/voicechat invite) ----------

    public static String call() {
        return CALL;
    }

    public static boolean isCall(String text) {
        return text != null && text.trim().equals(CALL);
    }

    public static boolean isCallMeta(String text) {
        return isCall(text);
    }

    // ---------- Разбор ----------

    /** {код хоста, id файла, "1"/"0" — спойлер} или null. */
    public static String[] parseImg(String text) {
        if (text == null) return null;
        String t = text.trim();
        Matcher m = IMG_SPOILER.matcher(t);
        if (m.matches()) return new String[]{m.group(1), m.group(2) + "." + m.group(3), "1"};
        m = IMG_V3.matcher(t);
        if (m.matches()) return new String[]{m.group(1), m.group(2) + "." + m.group(3), "0"};
        m = IMG_V2.matcher(t);
        if (m.matches()) return new String[]{"c", m.group(1) + "." + m.group(2), "0"};
        m = IMG_V1.matcher(t);
        if (m.matches()) return new String[]{"c", m.group(1), "0"};
        return null;
    }

    public static boolean isSpoiler(String[] imgOrVidRef) {
        return imgOrVidRef != null && imgOrVidRef.length > 2 && "1".equals(imgOrVidRef[2]);
    }

    /** {код хоста, id файла, секунды} или null. */
    public static String[] parseVoice(String text) {
        if (text == null) return null;
        String t = text.trim();
        Matcher m = VOICE_V3.matcher(t);
        if (m.matches()) return new String[]{m.group(1), m.group(2) + "." + m.group(3), m.group(4)};
        m = VOICE_V2.matcher(t);
        if (m.matches()) return new String[]{"c", m.group(1) + "." + m.group(2), m.group(3)};
        m = VOICE_V1.matcher(t);
        if (m.matches()) return new String[]{"c", m.group(1), m.group(2)};
        return null;
    }

    /** {хэш, текст} или null. */
    public static String[] parseReply(String text) {
        if (text == null) return null;
        String t = text.trim();
        Matcher m = RE_NEW.matcher(t);
        if (m.matches()) return new String[]{m.group(1), m.group(2).trim()};
        m = RE_OLD.matcher(t);
        if (m.matches()) return new String[]{m.group(1), m.group(2).trim()};
        return null;
    }

    /** {хэш, символ реакции} или null. */
    public static String[] parseReaction(String text) {
        if (text == null) return null;
        Matcher m = RX.matcher(text.trim());
        if (!m.matches()) return null;
        int idx = Integer.parseInt(m.group(2));
        if (idx < 0 || idx >= REACTIONS.length) return null;
        return new String[]{m.group(1), REACTIONS[idx]};
    }

    public static boolean isTyping(String text) {
        String t = text.trim();
        return t.equals(TYPING) || t.equals("[typ]");
    }

    public static boolean isSeen(String text) {
        String t = text.trim();
        return t.equals(SEEN) || t.equals("[seen]");
    }

    /** Рукопожатие «у меня стоит мод» — шлётся один раз на контакт. */
    public static boolean isHi(String text) {
        return text.trim().equals(HI);
    }

    public static boolean isPinMeta(String text) {
        return parsePin(text) != null;
    }

    public static boolean isVoteMeta(String text) {
        return parseVote(text) != null;
    }

    /** Любое структурированное сообщение мода — признак, что у отправителя стоит мод. */
    public static boolean isStructured(String text) {
        String t = text.trim();
        return t.startsWith("pmc ") || t.startsWith("[img:") || t.startsWith("[voice:")
                || t.startsWith("[re:") || t.equals("[typ]") || t.equals("[seen]");
    }

    /** Максимум символов текста для секретного сообщения (ограничение из-за шифрования, см. PmCrypto). */
    public static final int SECRET_MAX_CHARS = 40;
}
