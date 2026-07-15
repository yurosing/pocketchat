package com.pmchat.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Файловые хостинги для фото/голосовых. catbox.moe заблокирован РКН,
 * поэтому загрузка идёт с фолбэком по списку, а в метке сообщения
 * передаётся код хоста — получатель качает с того же места.
 */
public final class PmHosts {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private PmHosts() {
    }

    /** Базовый URL скачивания по коду хоста. */
    public static String baseUrl(String code) {
        String override = PmChatClient.getConfig().hostOverrides.get(code);
        if (override != null && !override.isBlank()) return override;
        return switch (code) {
            case "k" -> "https://kappa.lol/";
            case "q" -> "https://qu.ax/";
            case "l" -> "https://pomf2.lain.la/f/";
            case "x" -> "https://x0.at/";
            default -> PmChatClient.getConfig().imageHost; // "c" — catbox
        };
    }

    /**
     * Грузит файл на первый доступный хост из configured-списка.
     * @return {код хоста, id файла} или исключение, если не вышло нигде
     */
    public static String[] upload(byte[] data, String filename) throws Exception {
        Exception last = null;
        for (String code : PmChatClient.getConfig().uploadOrder.split(",")) {
            code = code.trim().toLowerCase(Locale.ROOT);
            try {
                String id = switch (code) {
                    case "k" -> uploadKappa(data, filename);
                    case "q" -> uploadPomf("https://qu.ax/upload.php", data, filename);
                    case "l" -> uploadPomf("https://pomf.lain.la/upload.php", data, filename);
                    case "x" -> uploadX0(data, filename);
                    case "c" -> uploadCatbox(data, filename);
                    default -> null;
                };
                if (id != null && !id.isBlank()) {
                    PmChatClient.LOGGER.info("Uploaded to '{}': {}", code, id);
                    return new String[]{code, id};
                }
            } catch (Exception e) {
                PmChatClient.LOGGER.warn("Host '{}' failed: {}", code, e.toString());
                last = e;
            }
        }
        throw last != null ? last : new IllegalStateException("no upload hosts configured");
    }

    // ---------- Реализации ----------

    private static byte[] multipart(String boundary, String field, String filename, String mime, byte[] data)
            throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mime + "\r\n\r\n";
        body.write(head.getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private static String mimeOf(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/png";
    }

    private static HttpResponse<String> post(String url, String contentType, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60)) // большие аудио/видео не успевали за 14с
                .header("Content-Type", contentType)
                .header("User-Agent", "pmchat-mod/1.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** kappa.lol: file= → JSON {id, ext}. */
    private static String uploadKappa(byte[] data, String filename) throws Exception {
        String boundary = "----pmchat" + System.nanoTime();
        HttpResponse<String> resp = post("https://kappa.lol/api/upload",
                "multipart/form-data; boundary=" + boundary,
                multipart(boundary, "file", filename, mimeOf(filename), data));
        if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        String id = json.get("id").getAsString();
        String ext = json.has("ext") ? json.get("ext").getAsString() : "";
        return ext.startsWith(".") ? id + ext : id + "." + ext;
    }

    /** Pomf-клоны (qu.ax, pomf.lain.la): files[] → JSON с url. */
    private static String uploadPomf(String endpoint, byte[] data, String filename) throws Exception {
        String boundary = "----pmchat" + System.nanoTime();
        HttpResponse<String> resp = post(endpoint, "multipart/form-data; boundary=" + boundary,
                multipart(boundary, "files[]", filename, mimeOf(filename), data));
        if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!json.get("success").getAsBoolean()) throw new IllegalStateException(resp.body());
        JsonArray files = json.getAsJsonArray("files");
        String url = files.get(0).getAsJsonObject().get("url").getAsString();
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /** x0.at: file= → URL простым текстом. */
    private static String uploadX0(byte[] data, String filename) throws Exception {
        String boundary = "----pmchat" + System.nanoTime();
        HttpResponse<String> resp = post("https://x0.at", "multipart/form-data; boundary=" + boundary,
                multipart(boundary, "file", filename, mimeOf(filename), data));
        String url = resp.body().trim();
        if (resp.statusCode() != 200 || !url.startsWith("http")) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " " + url);
        }
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /** catbox.moe: reqtype=fileupload → URL текстом. */
    private static String uploadCatbox(byte[] data, String filename) throws Exception {
        String boundary = "----pmchat" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mimeOf(filename) + "\r\n\r\n";
        body.write(head.getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> resp = post(PmChatClient.getConfig().uploadUrl,
                "multipart/form-data; boundary=" + boundary, body.toByteArray());
        String url = resp.body().trim();
        if (resp.statusCode() != 200 || !url.startsWith("http")) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " " + url);
        }
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
