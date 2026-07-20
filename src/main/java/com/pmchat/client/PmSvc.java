package com.pmchat.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * мост к Simple Voice Chat (клиентская часть, через рефлексию).
 *
 * Зачем: «/voicechat invite ник» работает ТОЛЬКО если ты уже состоишь в
 * голосовой группе — сама по себе группа при инвайте не создаётся. Поэтому
 * перед звонком проверяем членство и, если группы нет, принудительно создаём
 * её тем же пакетом CreateGroupPacket, который шлёт родной GUI мода.
 *
 * Всё через рефлексию: SVC не подключён как зависимость сборки, а его
 * версии слегка меняют внутренности. Каждый вызов обёрнут в try — если SVC
 * нет или классы уехали, просто возвращаем «не знаю»/false, и звонок идёт
 * по старому пути (голый invite).
 */
public final class PmSvc {

    private static final Logger LOGGER = LoggerFactory.getLogger("pmchat-svc");

    private PmSvc() {
    }

    /** Установлен ли Simple Voice Chat (клиентские классы доступны). */
    public static boolean isLoaded() {
        try {
            Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Состоим ли мы сейчас в голосовой группе.
     * TRUE/FALSE — уверенный ответ, null — SVC нет или API не читается.
     */
    public static Boolean isInGroup() {
        try {
            Class<?> cm = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            Object psm = cm.getMethod("getPlayerStateManager").invoke(null);
            if (psm == null) return null;
            // Новые версии: getGroupID()/getGroup() без аргументов; старые — isInGroup()
            for (String probe : new String[]{"getGroupID", "getGroup", "isInGroup"}) {
                try {
                    Method m = psm.getClass().getMethod(probe);
                    Object r = m.invoke(psm);
                    if (r instanceof Boolean b) return b;
                    return r != null;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("SVC group check failed: {}", t.toString());
        }
        return null;
    }

    /** Имена типов групп SVC по индексу: 0 — обычная, 1 — открытая, 2 — изолированная. */
    private static final String[] TYPE_NAMES = {"NORMAL", "OPEN", "ISOLATED"};

    /** Название типа группы для показа в интерфейсе (по индексу конфига). */
    public static String typeName(int typeIndex) {
        return TYPE_NAMES[Math.max(0, Math.min(TYPE_NAMES.length - 1, typeIndex))];
    }

    /** @deprecated Используйте {@link #createGroup(String, String, int)}. */
    @Deprecated
    public static boolean createGroup(String name) {
        return createGroup(name, null, 0);
    }

    /**
     * Принудительно создать голосовую группу (как это делает родное GUI SVC —
     * пакетом на сервер). true — пакет ушёл; membership подтвердится сервером
     * чуть позже, проверяйте {@link #isInGroup()}.
     *
     * @param name      имя группы
     * @param password  пароль (null/пусто — без пароля)
     * @param typeIndex тип: 0 NORMAL, 1 OPEN, 2 ISOLATED (для старых версий SVC
     *                  без типов игнорируется)
     */
    public static boolean createGroup(String name, String password, int typeIndex) {
        try {
            String safe = sanitizeName(name);
            String pass = (password == null || password.isBlank()) ? null : password;
            Class<?> pktClass = Class.forName("de.maxhenkel.voicechat.net.CreateGroupPacket");

            Object packet = null;
            try {
                // Новые версии: (String name, String password, Group.Type type)
                Class<?> typeClass = Class.forName("de.maxhenkel.voicechat.api.Group$Type");
                Object type = enumConstant(typeClass, typeName(typeIndex));
                Constructor<?> c3 = pktClass.getConstructor(String.class, String.class, typeClass);
                packet = c3.newInstance(safe, pass, type);
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                // Старые версии: (String name, String password)
                Constructor<?> c2 = pktClass.getConstructor(String.class, String.class);
                packet = c2.newInstance(safe, pass);
            }

            if (sendToServer(packet)) {
                LOGGER.info("Voice group '{}' create packet sent (type {}, {})",
                        safe, typeName(typeIndex), pass == null ? "no pass" : "with pass");
                return true;
            }
            LOGGER.warn("SVC found but no sendToServer method matched — cannot create group");
        } catch (Throwable t) {
            LOGGER.warn("SVC create group failed: {}", t.toString());
        }
        return false;
    }

    /** Выйти из текущей голосовой группы (положить трубку). true — пакет ушёл. */
    public static boolean leaveGroup() {
        try {
            Class<?> pktClass = Class.forName("de.maxhenkel.voicechat.net.LeaveGroupPacket");
            Object packet = pktClass.getConstructor().newInstance();
            if (sendToServer(packet)) {
                LOGGER.info("Voice group leave packet sent");
                return true;
            }
        } catch (Throwable t) {
            LOGGER.warn("SVC leave group failed: {}", t.toString());
        }
        return false;
    }

    /**
     * Сколько игроков сейчас в НАШЕЙ голосовой группе (включая нас), по данным
     * SVC. 0 — SVC нет, мы вне группы или API не читается.
     */
    public static int participantCount() {
        java.util.UUID g = ownGroupId();
        if (g == null) return 0;
        int count = 1; // себя (getPlayerStates нас не включает)
        for (Object st : playerStates()) {
            java.util.UUID sg = (java.util.UUID) invokeFirst(st, "getGroup");
            if (g.equals(sg)) count++;
        }
        return count;
    }

    /** UUID нашей голосовой группы (SVC ClientPlayerStateManager.getGroupID), или null. */
    private static java.util.UUID ownGroupId() {
        try {
            Class<?> cm = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            Object psm = cm.getMethod("getPlayerStateManager").invoke(null);
            if (psm == null) return null;
            Object id = psm.getClass().getMethod("getGroupID").invoke(psm);
            return id instanceof java.util.UUID u ? u : null;
        } catch (Throwable t) {
            LOGGER.debug("SVC group id failed: {}", t.toString());
            return null;
        }
    }

    /** Список PlayerState всех известных игроков (SVC getPlayerStates(boolean)). */
    private static java.util.List<?> playerStates() {
        try {
            Class<?> cm = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            Object psm = cm.getMethod("getPlayerStateManager").invoke(null);
            if (psm == null) return java.util.Collections.emptyList();
            Object states = psm.getClass().getMethod("getPlayerStates", boolean.class).invoke(psm, false);
            return states instanceof java.util.List<?> l ? l : java.util.Collections.emptyList();
        } catch (Throwable t) {
            LOGGER.debug("SVC player states failed: {}", t.toString());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * UUID игроков в нашей голосовой группе (без нас — своё состояние SVC часто
     * не кладёт в общий список; себя UI добавляет отдельно). Пусто — SVC нет/вне
     * группы/не читается.
     */
    public static java.util.List<java.util.UUID> groupMemberIds() {
        java.util.List<java.util.UUID> out = new java.util.ArrayList<>();
        java.util.UUID g = ownGroupId();
        if (g == null) return out;
        for (Object st : playerStates()) {
            java.util.UUID sg = (java.util.UUID) invokeFirst(st, "getGroup");
            if (!g.equals(sg)) continue;
            Object id = invokeFirst(st, "getUuid");
            if (id instanceof java.util.UUID u) out.add(u);
        }
        return out;
    }

    /** ClientVoicechat (SVC ClientManager.getClient), или null если не подключён. */
    private static Object client() {
        try {
            Class<?> cm = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            return cm.getMethod("getClient").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Говорит ли сейчас игрок — TalkCache.isTalking(UUID). false — недоступно/молчит. */
    public static boolean isSpeaking(java.util.UUID id) {
        if (id == null) return false;
        try {
            Object c = client();
            if (c == null) return false;
            Object cache = c.getClass().getMethod("getTalkCache").invoke(c);
            if (cache == null) return false;
            Object r = cache.getClass().getMethod("isTalking", java.util.UUID.class).invoke(cache, id);
            return r instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Говорим ли МЫ — MicThread.isTalking(). false — микрофон выкл/молчим. */
    public static boolean isSelfSpeaking() {
        try {
            Object c = client();
            if (c == null) return false;
            Object mic = c.getClass().getMethod("getMicThread").invoke(c);
            if (mic == null) return false;
            Object r = mic.getClass().getMethod("isTalking").invoke(mic);
            return r instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object invokeStaticFirst(Class<?> cls, String... methods) {
        for (String m : methods) {
            try {
                return cls.getMethod(m).invoke(null);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean groupsEqual(Object a, Object b) {
        if (a == null || b == null) return false;
        Object ka = groupKey(a), kb = groupKey(b);
        return ka != null && ka.equals(kb);
    }

    /** ID группы для сравнения: сам объект — это уже UUID/строка, либо у него есть getId(). */
    private static Object groupKey(Object group) {
        for (String probe : new String[]{"getId", "getID", "getGroupId", "getGroupID"}) {
            try {
                return group.getClass().getMethod(probe).invoke(group);
            } catch (Throwable ignored) {
            }
        }
        return group; // UUID/String — сравниваем напрямую
    }

    private static Object invokeFirst(Object target, String... methods) {
        for (String m : methods) {
            try {
                return target.getClass().getMethod(m).invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static java.util.Collection<?> asCollection(Object o) {
        if (o instanceof java.util.Collection<?> c) return c;
        if (o instanceof java.util.Map<?, ?> m) return m.values();
        return null;
    }

    /** Отправить пакет на сервер (имя класса-отправителя менялось между версиями). */
    private static boolean sendToServer(Object packet) {
        for (String sender : new String[]{
                "de.maxhenkel.voicechat.net.ClientServerNetManager",
                "de.maxhenkel.voicechat.net.NetManager"}) {
            try {
                Class<?> net = Class.forName(sender);
                for (Method m : net.getMethods()) {
                    if (m.getName().equals("sendToServer") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isInstance(packet)) {
                        m.invoke(null, packet);
                        return true;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                LOGGER.debug("sendToServer via {} failed: {}", sender, t.toString());
            }
        }
        return false;
    }

    /** Имя группы: без переводов строк/табов, непустое, не длиннее 16 символов. */
    private static String sanitizeName(String name) {
        String s = name == null ? "" : name.replaceAll("[\\r\\n\\t]", " ").trim();
        if (s.isEmpty()) s = "Call";
        return s.length() > 16 ? s.substring(0, 16) : s;
    }

    private static Object enumConstant(Class<?> enumClass, String preferred) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null || constants.length == 0) return null;
        for (Object c : constants) {
            if (((Enum<?>) c).name().equals(preferred)) return c;
        }
        return constants[0];
    }
}
