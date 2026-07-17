package com.pmchat.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * NEW (5.0): мост к Simple Voice Chat (клиентская часть, через рефлексию).
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

    /**
     * Принудительно создать голосовую группу с именем name (как это делает
     * родное GUI SVC — пакетом на сервер). true — пакет ушёл; membership
     * подтвердится сервером чуть позже, проверяйте {@link #isInGroup()}.
     */
    public static boolean createGroup(String name) {
        try {
            String safe = sanitizeName(name);
            Class<?> pktClass = Class.forName("de.maxhenkel.voicechat.net.CreateGroupPacket");

            Object packet = null;
            try {
                // Новые версии: (String name, String password, Group.Type type)
                Class<?> typeClass = Class.forName("de.maxhenkel.voicechat.api.Group$Type");
                Object normal = enumConstant(typeClass, "NORMAL");
                Constructor<?> c3 = pktClass.getConstructor(String.class, String.class, typeClass);
                packet = c3.newInstance(safe, null, normal);
            } catch (NoSuchMethodException | ClassNotFoundException e) {
                // Старые версии: (String name, String password)
                Constructor<?> c2 = pktClass.getConstructor(String.class, String.class);
                packet = c2.newInstance(safe, null);
            }

            // Отправка: имя класса-отправителя менялось между версиями
            for (String sender : new String[]{
                    "de.maxhenkel.voicechat.net.ClientServerNetManager",
                    "de.maxhenkel.voicechat.net.NetManager"}) {
                try {
                    Class<?> net = Class.forName(sender);
                    for (Method m : net.getMethods()) {
                        if (m.getName().equals("sendToServer") && m.getParameterCount() == 1
                                && m.getParameterTypes()[0].isInstance(packet)) {
                            m.invoke(null, packet);
                            LOGGER.info("Voice group '{}' create packet sent via {}", safe, sender);
                            return true;
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
            LOGGER.warn("SVC found but no sendToServer method matched — cannot create group");
        } catch (Throwable t) {
            LOGGER.warn("SVC create group failed: {}", t.toString());
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
