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
        int dot = fileId.lastIndexOf('.');
        String name = dot > 0 ? fileId.substring(0, dot) : fileId;
        String ext = dot > 0 ? fileId.substring(dot + 1) : "png";
        return "pmc img " + hostCode + " " + name + " " + ext;
    }

    public static String voice(String hostCode, String fileId, int seconds) {
        int dot = fileId.lastIndexOf('.');
        String name = dot > 0 ? fileId.substring(0, dot) : fileId;
        String ext = dot > 0 ? fileId.substring(dot + 1) : "wav";
        return "pmc voice " + hostCode + " " + name + " " + ext + " " + seconds;
    }

    public static String vid(String hostCode, String fileId) {
        int dot = fileId.lastIndexOf('.');
        String name = dot > 0 ? fileId.substring(0, dot) : fileId;
        String ext = dot > 0 ? fileId.substring(dot + 1) : "mp4";
        return "pmc vid " + hostCode + " " + name + " " + ext;
    }

    /** {код хоста, id файла} или null. */
    public static String[] parseVid(String text) {
        if (text == null) return null;
        Matcher m = VID.matcher(text.trim());
        if (m.matches()) return new String[]{m.group(1), m.group(2) + "." + m.group(3)};
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

    // ---------- Разбор ----------

    /** {код хоста, id файла} или null. */
    public static String[] parseImg(String text) {
        if (text == null) return null;
        String t = text.trim();
        Matcher m = IMG_V3.matcher(t);
        if (m.matches()) return new String[]{m.group(1), m.group(2) + "." + m.group(3)};
        m = IMG_V2.matcher(t);
        if (m.matches()) return new String[]{"c", m.group(1) + "." + m.group(2)};
        m = IMG_V1.matcher(t);
        if (m.matches()) return new String[]{"c", m.group(1)};
        return null;
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
}
