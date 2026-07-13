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

    /** Прятать перехваченные строки ЛС из обычного чата. */
    public boolean hideChatLines = false;

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

    /** Закреплённое сообщение в диалоге: ник собеседника -> хэш текста. */
    public Map<String, String> pins = new HashMap<>();

    /** Контакты (избранные собеседники) — закрепляются вверху списка. */
    public List<String> contacts = new ArrayList<>();

    /** Упоминания: подсветка+пинг, когда в чате звучит твой ник. */
    public boolean mentionEnabled = true;
    /** Доп. слова-триггеры через запятую (помимо своего ника). */
    public String mentionExtra = "";

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
                    if (cfg.contacts == null) cfg.contacts = new ArrayList<>();
                    if (cfg.hostOverrides == null) cfg.hostOverrides = new HashMap<>();
                    if (cfg.uploadOrder == null || cfg.uploadOrder.isBlank()
                            || cfg.uploadOrder.equals("q,l,x,c")
                            || cfg.uploadOrder.equals("x,q,c")) {
                        cfg.uploadOrder = "k,x,q,c"; // миграция со старого порядка
                    }
                    if (cfg.channels == null || cfg.channels.isEmpty()) cfg.channels = defaultChannels();
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
