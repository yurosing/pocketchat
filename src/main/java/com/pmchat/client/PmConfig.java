package com.pmchat.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PmConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("pmchat.json");

    /**
     * Регэкспы для перехвата строк ЛС из чата. Группа 1 — ник, группа 2 — текст.
     * По умолчанию — формат Essentials на vanilla-box: "(ЛС) я -> ник » текст".
     */
    public String incomingPattern = "\\(ЛС\\)\\s*\\W*([A-Za-z0-9_]{2,16})\\s*->\\s*я\\s*»\\s*(.+)";
    public String outgoingPattern = "\\(ЛС\\)\\s*я\\s*->\\s*\\W*([A-Za-z0-9_]{2,16})\\s*»\\s*(.+)";

    /** Глобальный чат: ник — последнее слово перед », дальше текст. */
    public String globalPattern = "([A-Za-z0-9_]{2,16})[^»A-Za-z0-9_]*»\\s*(.+)";

    /** Команды сервера. */
    public String msgCommand = "m";
    public String payCommand = "pay";

    /** Размер окна мессенджера: 0 — маленький, 1 — средний, 2 — большой. */
    public int uiScale = 0;

    /** Полноэкранный режим мессенджера (на весь экран, как в Telegram Desktop). */
    public boolean fullscreen = false;

    /** Прятать перехваченные строки ЛС из обычного чата. */
    public boolean hideChatLines = false;

    /** Проверять новые версии мода по релизам GitHub при заходе на сервер. */
    public boolean checkUpdates = true;
    /** Репозиторий для проверки обновлений (owner/repo, публичный). */
    public String updateRepo = "yurosing/pocketchat";

    /** Хостинг картинок: куда грузим и откуда качаем по id. */
    public String uploadUrl = "https://catbox.moe/user/api.php";
    public String imageHost = "https://files.catbox.moe/";

    /**
     * Порядок хостов загрузки (фолбэк): k=kappa.lol, x=x0.at,
     * q=qu.ax, c=catbox (заблокирован РКН — последним).
     */
    public String uploadOrder = "k,x,q,c";

    /** Переопределение URL скачивания по коду хоста. */
    public Map<String, String> hostOverrides = new HashMap<>();

    /** Тема интерфейса: 0 — тёмная, 1 — светлая. */
    public int theme = 0;

    /** Индекс цвета своих сообщений (палитра в моде). */
    public int outColor = 0;

    /** Индекс цвета входящих сообщений (есть тёмные — глаза не режет). */
    public int inColor = 0;

    /** Единый цвет ников вместо радуги + его индекс. */
    public boolean uniformNames = false;
    public int nameColor = 0;

    /** Размер текста сообщений в процентах (80–125). */
    public int textScalePct = 100;

    /** Индекс цвета символов текста сообщений (0 — авто). */
    public int msgTextColor = 0;

    /** «Не беспокоить»: без всплывающих уведомлений и звука. */
    public boolean dnd = false;

    /** Индекс цвета бейджа непрочитанных. */
    public int badgeColor = 0;

    /** Звук уведомления: 0 опыт, 1 колокольчик, 2 предмет, 3 выкл. */
    public int notifySound = 0;

    /** Громкость уведомления в процентах (25–100). */
    public int notifyVolume = 100;

    /** Озвучка сообщений общего чата системным голосом (TTS). */
    public boolean ttsGlobal = false;

    /**
     * Префикс для отправки в глобальный чат сервера (на многих RU-серверах
     * это "!"). Пусто — отправлять как есть (если глобал по умолчанию).
     */
    public String globalPrefix = "!";

    /** Обои фона чата: имя файла из config/pmchat-wallpapers/ или пусто. */
    public String wallpaper = "";

    /** Язык распознавания речи: 0 — русский, 1 — английский. */
    public int sttLang = 0;

    /**
     * Модели Vosk по языкам — список зеркал через запятую (пробуются по
     * порядку). GitHub-зеркало первым: alphacephei.com часто недоступен.
     */
    public String sttModelUrlRu =
            "https://github.com/yurosing/pocketchat/releases/download/models/vosk-model-small-ru-0.22.zip,"
            + "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip";
    public String sttModelUrlEn =
            "https://github.com/yurosing/pocketchat/releases/download/models/vosk-model-small-en-us-0.15.zip,"
            + "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";

    /** @deprecated Заменено на sttModelUrlRu/sttModelUrlEn. */
    @Deprecated
    public String sttModelUrl = null;

    /** Звук при входящем сообщении. */
    public boolean soundEnabled = true;

    /**
     * Staff-функции (6.1/6.8): кнопка /warn в ЛС и общем чате. По умолчанию
     * ВЫКЛ — не в общий доступ, только для хелперов+. Команда без слэша.
     */
    public boolean staffFeatures = false;
    public String warnCommand = "warn";
    /** Причина по умолчанию для /warn (пусто — не добавлять). */
    public String warnReason = "";

    /**
     * Отдельный чат логов CoreProtect (6.3). По умолчанию ВЫКЛ. Строки
     * показываются как есть, без перевода. Паттерн ловит вывод плагина.
     */
    public boolean coreProtectEnabled = false;
    /** держать полоску аудиоплеера видимой и когда открыт
     *  ванильный чат (при вводе сообщения), а не только на «голом» HUD. */
    public boolean mediaBarWhileTyping = false;
    public String coreProtectPattern = "(?i)(coreprotect|/co\\b|\\bco\\s+(lookup|inspect|rollback|restore)|\\d+(?:\\.\\d+)?\\s*/\\s*[hчd]\\s+(ago|назад))";

    // ---------- Фильтры чата («No Global Chat») ----------

    /** Полностью прятать глобальный чат из игрового чата (по паттерну global). */
    public boolean filterGlobal = false;
    /** Прятать сообщения из Discord (по паттерну discordPattern). */
    public boolean filterDiscord = false;

    /**
     * Паттерн Discord-строк. Группа 1 — ник автора в Discord. Пример строки:
     * "(Discord) 🔥 ENA_6543 » ⚚ Vahf, о привета".
     */
    public String discordPattern = "\\(Discord\\).*?([A-Za-z0-9_]{2,32})\\s*»\\s*(.+)";

    /** Игнор игроков в общем чате (как /ignoreplayer): ники. */
    public List<String> filterPlayers = new ArrayList<>();
    /** Игнор игроков в Discord: ники. */
    public List<String> filterDiscordPlayers = new ArrayList<>();

    /** Область действия текстового фильтра. */
    public static final int SCOPE_BOTH = 0, SCOPE_GLOBAL = 1, SCOPE_DISCORD = 2;

    /** Текстовый фильтр: прятать сообщения, содержащие подстроку, в выбранной области. */
    public static class FilterRule {
        public String text = "";
        public int scope = SCOPE_BOTH; // 0 везде, 1 глобал, 2 discord

        public FilterRule() {
        }

        public FilterRule(String text, int scope) {
            this.text = text;
            this.scope = scope;
        }
    }

    public List<FilterRule> filterRules = new ArrayList<>();

    private static boolean listContainsIgnoreCase(List<String> list, String name) {
        return name != null && list.stream().anyMatch(n -> n.equalsIgnoreCase(name.trim()));
    }

    public boolean isFilteredPlayer(String name) {
        return listContainsIgnoreCase(filterPlayers, name);
    }

    public boolean isFilteredDiscordPlayer(String name) {
        return listContainsIgnoreCase(filterDiscordPlayers, name);
    }

    public void addFilteredPlayer(String name) {
        if (name == null || name.isBlank()) return;
        if (!isFilteredPlayer(name)) filterPlayers.add(name.trim());
        save();
    }

    public void addFilteredDiscordPlayer(String name) {
        if (name == null || name.isBlank()) return;
        if (!isFilteredDiscordPlayer(name)) filterDiscordPlayers.add(name.trim());
        save();
    }

    // ---------- Чёрный список (5.5) ----------

    /**
     * Заблокированные игроки. У них скрыта аватарка (даже онлайн), а в шапке
     * диалога виден значок ЧС. Без серверного плагина блок дублируется в
     * серверный игнор Essentials командой {@link #ignoreCommand}.
     */
    public List<String> blocked = new ArrayList<>();

    /** Команда серверного игнора без плагина (Essentials переключает ей же). */
    public String ignoreCommand = "ignore";

    // ---------- Профиль (4.2 / 4.5) ----------

    /** Свой профиль: день рождения (свободный текст, напр. «14.02»). */
    public String profileBirthday = "";
    /** Свой профиль: описание «о себе». */
    public String profileDescription = "";
    /**
     * Код своей роли-должности. Пусто — без роли. Коды: C — контент-мейкер,
     * H — хелпер, M — модератор, E — ивент-мейкер, D — разработчик.
     */
    public String profileRole = "";

    /** Локально назначенные роли других игроков (ник → код роли). «Настраивать самому». */
    public Map<String, String> playerRoles = new HashMap<>();

    public String roleOf(String name) {
        if (name == null) return "";
        String r = playerRoles.get(name.trim());
        return r == null ? "" : r;
    }

    public void setRole(String name, String code) {
        if (name == null || name.isBlank()) return;
        if (code == null || code.isEmpty()) playerRoles.remove(name.trim());
        else playerRoles.put(name.trim(), code);
        save();
    }

    public boolean isBlocked(String name) {
        return listContainsIgnoreCase(blocked, name);
    }

    public void addBlocked(String name) {
        if (name == null || name.isBlank()) return;
        if (!isBlocked(name)) blocked.add(name.trim());
        save();
    }

    public void removeBlocked(String name) {
        if (name == null) return;
        blocked.removeIf(n -> n.equalsIgnoreCase(name.trim()));
        save();
    }

    /** Канал серверного чата (клан/альянс/группа): вкладка в мессенджере. */
    public static class PmChannel {
        public String id;
        public String label;
        public String command;
        public String pattern;

        public PmChannel() {
        }

        public PmChannel(String id, String label, String command, String pattern) {
            this.id = id;
            this.label = label;
            this.command = command;
            this.pattern = pattern;
        }
    }

    /**
     * Каналы: вкладка создаётся при первом пойманном сообщении.
     * В паттерне именованные группы: (?<name>ник), (?<text>текст),
     * опционально (?<clan>клан) — показывается рядом с ником.
     */
    public List<PmChannel> channels = defaultChannels();

    /**
     * Групповой чат (6.9): локальная беседа с несколькими игроками. Мод шлёт
     * обычные /m каждому участнику, входящие собираются в одну ленту. id —
     * детерминированный (из состава), чтобы у всех участников с модом совпадал.
     */
    public static class PmGroup {
        public String id;
        public String name;
        public List<String> members = new ArrayList<>();
        /** Имя файла аватарки группы из config/pmchat-avatars/ (3.1). Пусто — иконка по умолчанию. */
        public String avatar = "";

        public PmGroup() {
        }

        public PmGroup(String id, String name, List<String> members) {
            this.id = id;
            this.name = name;
            this.members = members;
        }
    }

    public List<PmGroup> groups = new ArrayList<>();

    public PmGroup findGroup(String id) {
        if (id == null) return null;
        for (PmGroup g : groups) {
            if (id.equals(g.id)) return g;
        }
        return null;
    }

    private static List<PmChannel> defaultChannels() {
        List<PmChannel> list = new ArrayList<>();
        list.add(new PmChannel("clan", "Клан", ".",
                "^\\W{0,6}(?:клан|clan)\\W+.*?(?<name>[A-Za-z0-9_]{2,16})\\s*[»:>]\\s*(?<text>.+)"));
        list.add(new PmChannel("ally", "Альянс", "ally",
                "^\\W{0,6}(?:альянс|союз|ally)\\W+(?:\\[?(?<clan>[A-Za-zА-Яа-я0-9_]{2,16})\\]?\\W+)?.*?(?<name>[A-Za-z0-9_]{2,16})\\s*[»:>]\\s*(?<text>.+)"));
        list.add(new PmChannel("gc", "Группа", "gc",
                "^\\W{0,6}(?:gc|группа|войс|voice)\\W+.*?(?<name>[A-Za-z0-9_]{2,16})\\s*[»:>]\\s*(?<text>.+)"));
        return list;
    }

    /**
     * Служебные метки ([typ]/[seen]) шлём только игрокам из modUsers —
     * тем, от кого мы уже получали структурированные сообщения мода.
     */
    public boolean enableMeta = true;
    public List<String> modUsers = new ArrayList<>();

    /** Кэш загруженных стикеров: имя файла -> id на хостинге. */
    public Map<String, String> stickerCache = new HashMap<>();

    /** Недавно использованные стикеры/гифки (абсолютные пути), свежие в начале. */
    public List<String> recentStickers = new ArrayList<>();

    public void pushRecentSticker(String path) {
        recentStickers.remove(path);
        recentStickers.add(0, path);
        while (recentStickers.size() > 20) recentStickers.remove(recentStickers.size() - 1);
        save();
    }

    /** @deprecated Одиночный закреп. Мигрирует в {@link #pinned} при загрузке. */
    @Deprecated
    public Map<String, String> pins = new HashMap<>();

    /** Закреплённые сообщения в диалоге: ник -> список хэшей текста (как в Telegram). */
    public Map<String, List<String>> pinned = new HashMap<>();

    public List<String> pinnedList(String conv) {
        return pinned.getOrDefault(conv, java.util.Collections.emptyList());
    }

    public boolean isPinned(String conv, String hash) {
        List<String> list = pinned.get(conv);
        return list != null && list.contains(hash);
    }

    /** Добавить закреп (без дублей, свежий — в конец). */
    public void addPin(String conv, String hash) {
        if (hash == null || hash.isEmpty()) return;
        List<String> list = pinned.computeIfAbsent(conv, k -> new ArrayList<>());
        list.remove(hash);
        list.add(hash);
        while (list.size() > 30) list.remove(0);
        save();
    }

    public void removePin(String conv, String hash) {
        List<String> list = pinned.get(conv);
        if (list != null) {
            list.remove(hash);
            if (list.isEmpty()) pinned.remove(conv);
            save();
        }
    }

    public void clearPins(String conv) {
        if (pinned.remove(conv) != null) save();
    }

    /** Контакты (избранные собеседники) — закрепляются вверху списка. */
    public List<String> contacts = new ArrayList<>();

    /**
     * Локальное переименование игроков: реальный ник (в нижнем регистре) →
     * отображаемое имя. Задаётся через профиль (добавление в контакты). На
     * отправку /m не влияет — маршрутизация всегда по реальному нику.
     */
    public Map<String, String> aliases = new HashMap<>();

    /** Отображаемое имя игрока: локальный псевдоним, если задан, иначе сам ник. */
    public String aliasOf(String name) {
        if (name == null) return "";
        String a = aliases.get(name.toLowerCase(Locale.ROOT));
        return (a == null || a.isBlank()) ? name : a;
    }

    public boolean hasAlias(String name) {
        if (name == null) return false;
        String a = aliases.get(name.toLowerCase(Locale.ROOT));
        return a != null && !a.isBlank();
    }

    /**
     * Переименовать игрока локально. Пустое имя — снять переименование.
     * Ненулевое переименование добавляет игрока в контакты (как и задумано).
     */
    public void setAlias(String name, String alias) {
        if (name == null || name.isBlank()) return;
        String key = name.toLowerCase(Locale.ROOT);
        if (alias == null || alias.isBlank()) {
            aliases.remove(key);
        } else {
            aliases.put(key, alias.trim());
            if (!isContact(name)) contacts.add(name.trim());
        }
        save();
    }

    /** Упоминания: подсветка+пинг, когда в чате звучит твой ник. */
    public boolean mentionEnabled = true;
    /** Доп. слова-триггеры через запятую (помимо своего ника). */
    public String mentionExtra = "";

    /** Закрывать меню при получении урона. */
    public boolean closeOnDamage = false;

    /**
     * Тип голосовой группы при звонке через Simple Voice Chat:
     * 0 — обычная (NORMAL), 1 — открытая (OPEN), 2 — изолированная (ISOLATED).
     */
    public int voiceGroupType = 0;

    /** Защищать войс-группу случайным паролем (звонящий делится им сам). */
    public boolean voiceGroupPassword = true;

    /** При копировании текста добавлять «ник: » в начало. */
    public boolean mentionOnCopy = false;

    public boolean isContact(String name) {
        return contacts.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    public void toggleContact(String name) {
        if (isContact(name)) contacts.removeIf(n -> n.equalsIgnoreCase(name));
        else contacts.add(name);
        save();
    }

    public boolean isModUser(String name) {
        return modUsers.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    public void addModUser(String name) {
        if (!isModUser(name)) {
            modUsers.add(name);
            save();
        }
    }

    /** Кому уже отправляли рукопожатие pmc hi (один раз на контакт). */
    public List<String> hiSent = new ArrayList<>();

    public boolean hiSentTo(String name) {
        return hiSent.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    public void markHiSent(String name) {
        if (!hiSentTo(name)) {
            hiSent.add(name);
            save();
        }
    }

    public static PmConfig load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                PmConfig cfg = GSON.fromJson(reader, PmConfig.class);
                if (cfg != null) {
                    if (cfg.modUsers == null) cfg.modUsers = new ArrayList<>();
                    if (cfg.hiSent == null) cfg.hiSent = new ArrayList<>();
                    if (cfg.stickerCache == null) cfg.stickerCache = new HashMap<>();
                    if (cfg.pins == null) cfg.pins = new HashMap<>();
                    if (cfg.pinned == null) cfg.pinned = new HashMap<>();
                    // Миграция одиночных закрепов в список
                    if (!cfg.pins.isEmpty()) {
                        for (Map.Entry<String, String> e : cfg.pins.entrySet()) {
                            if (e.getValue() != null && !e.getValue().isEmpty()
                                    && !cfg.isPinned(e.getKey(), e.getValue())) {
                                cfg.pinned.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
                            }
                        }
                        cfg.pins.clear();
                    }
                    // Миграция старого паттерна CoreProtect на блочный
                    if (cfg.coreProtectPattern == null || cfg.coreProtectPattern.isBlank()
                            || cfg.coreProtectPattern.contains("/h\\s+ago")) {
                        cfg.coreProtectPattern = new PmConfig().coreProtectPattern;
                    }
                    if (cfg.contacts == null) cfg.contacts = new ArrayList<>();
                    if (cfg.aliases == null) cfg.aliases = new HashMap<>();
                    if (cfg.recentStickers == null) cfg.recentStickers = new ArrayList<>();
                    if (cfg.hostOverrides == null) cfg.hostOverrides = new HashMap<>();
                    if (cfg.uploadOrder == null || cfg.uploadOrder.isBlank()
                            || cfg.uploadOrder.equals("q,l,x,c")
                            || cfg.uploadOrder.equals("x,q,c")) {
                        cfg.uploadOrder = "k,x,q,c"; // миграция со старого порядка
                    }
                    if (cfg.channels == null || cfg.channels.isEmpty()) cfg.channels = defaultChannels();
                    if (cfg.groups == null) cfg.groups = new ArrayList<>();
                    for (PmGroup g : cfg.groups) {
                        if (g.members == null) g.members = new ArrayList<>();
                        if (g.avatar == null) g.avatar = "";
                    }
                    if (cfg.warnReason == null) cfg.warnReason = "";
                    if (cfg.filterPlayers == null) cfg.filterPlayers = new ArrayList<>();
                    if (cfg.filterDiscordPlayers == null) cfg.filterDiscordPlayers = new ArrayList<>();
                    if (cfg.blocked == null) cfg.blocked = new ArrayList<>();
                    if (cfg.ignoreCommand == null || cfg.ignoreCommand.isBlank()) cfg.ignoreCommand = "ignore";
                    if (cfg.playerRoles == null) cfg.playerRoles = new HashMap<>();
                    if (cfg.profileBirthday == null) cfg.profileBirthday = "";
                    if (cfg.profileDescription == null) cfg.profileDescription = "";
                    if (cfg.profileRole == null) cfg.profileRole = "";
                    if (cfg.filterRules == null) cfg.filterRules = new ArrayList<>();
                    if (cfg.discordPattern == null || cfg.discordPattern.isBlank()) {
                        cfg.discordPattern = new PmConfig().discordPattern;
                    }
                    // Миграция: одиночный alphacephei-URL -> список зеркал с GitHub
                    if (cfg.sttModelUrlRu == null || !cfg.sttModelUrlRu.contains(",")) {
                        cfg.sttModelUrlRu = new PmConfig().sttModelUrlRu;
                    }
                    if (cfg.sttModelUrlEn == null || !cfg.sttModelUrlEn.contains(",")) {
                        cfg.sttModelUrlEn = new PmConfig().sttModelUrlEn;
                    }
                    if (cfg.textScalePct < 60 || cfg.textScalePct > 150) cfg.textScalePct = 100;
                    if (cfg.notifyVolume < 5 || cfg.notifyVolume > 100) cfg.notifyVolume = 100;
                    return cfg;
                }
            } catch (IOException ignored) {
            }
        }
        PmConfig cfg = new PmConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException ignored) {
        }
    }
}
