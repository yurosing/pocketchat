package com.pmchat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Проверка новых версий мода по публичным релизам GitHub (без токена).
 * При заходе на сервер один раз за сессию дёргает {@code releases/latest},
 * сравнивает тег с текущей версией мода и, если релиз новее, показывает в
 * чате уведомление с кликабельной ссылкой на страницу релиза. Ничего не
 * скачивает — игрок сам решает, обновляться ли.
 */
public final class PmUpdate {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile boolean checkedThisSession = false;

    private PmUpdate() {
    }

    /** Сбрасывается при выходе с сервера — чтобы проверить снова при следующем заходе. */
    public static void resetSession() {
        checkedThisSession = false;
    }

    /** Запускает фоновую проверку обновлений (не чаще одного раза за сессию). */
    public static void checkAsync() {
        PmConfig cfg = PmChatClient.getConfig();
        if (!cfg.checkUpdates || checkedThisSession) return;
        String repo = cfg.updateRepo;
        if (repo == null || !repo.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+")) return;
        checkedThisSession = true;

        Thread t = new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                        .timeout(Duration.ofSeconds(12))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "pmchat-mod")
                        .GET().build();
                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) return;
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (json.has("draft") && json.get("draft").getAsBoolean()) return;
                if (json.has("prerelease") && json.get("prerelease").getAsBoolean()) return;
                String tag = json.has("tag_name") && !json.get("tag_name").isJsonNull()
                        ? json.get("tag_name").getAsString() : null;
                if (tag == null) return;
                String url = json.has("html_url") ? json.get("html_url").getAsString()
                        : "https://github.com/" + repo + "/releases";

                if (isNewer(tag, currentVersion())) {
                    Minecraft.getInstance().execute(() -> notifyUpdate(tag, url));
                }
            } catch (Exception e) {
                PmChatClient.LOGGER.debug("Update check failed: {}", e.toString());
            }
        }, "pmchat-update-check");
        t.setDaemon(true);
        t.start();
    }

    private static void notifyUpdate(String tag, String url) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null) return;
        MutableComponent msg = Component.literal("[pmchat] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("Доступна новая версия " + tag + ". ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Открыть страницу релиза →")
                        .withStyle(s -> s.applyFormats(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)))));
        client.gui.hud.getChat().addMessage(msg);

        // Заодно приглашение в Discord — если задано (pmchat.json: discordUrl).
        String discord = PmChatClient.getConfig().discordUrl;
        if (discord != null && !discord.isBlank()) {
            try {
                URI discordUri = URI.create(discord);
                MutableComponent dmsg = Component.literal("[pmchat] ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal("Наш Discord: ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(discord)
                                .withStyle(s -> s.applyFormats(ChatFormatting.BLUE, ChatFormatting.UNDERLINE)
                                        .withClickEvent(new ClickEvent.OpenUrl(discordUri))
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(discord)))));
                client.gui.hud.getChat().addMessage(dmsg);
            } catch (IllegalArgumentException ignored) {
                // некорректная ссылка в конфиге — молча пропускаем
            }
        }
    }

    /** Текущая версия мода из его метаданных (например «1.10.0»). */
    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer(PmChatClient.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0");
    }

    /**
     * true, если {@code latestTag} новее текущей версии. Сравниваем только
     * числовые части (1.10.0 &gt; 1.8.1), игнорируя ведущий «v» и суффиксы вроде
     * «-secret»/«+build». Некорректные версии → «не новее» (не спамим).
     */
    static boolean isNewer(String latestTag, String current) {
        int[] a = numericParts(latestTag);
        int[] b = numericParts(current);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] numericParts(String v) {
        if (v == null) return new int[0];
        String s = v.strip();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        // отрезаем суффикс после первого не-версионного символа (-, +, пробел, буква)
        int cut = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isDigit(c) || c == '.')) {
                cut = i;
                break;
            }
        }
        s = s.substring(0, cut);
        if (s.isEmpty()) return new int[0];
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
