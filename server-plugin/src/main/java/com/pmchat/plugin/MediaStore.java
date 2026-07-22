package com.pmchat.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * On-disk media storage for the relay. Files live under {@code <dataFolder>/media}
 * named {@code <id>.<ext>}, where {@code id} is a random unguessable token — the id
 * is only ever shared with the recipient through the private message, so random ids
 * act as capability tokens.
 *
 * Nothing is held in memory here: uploads are streamed into a temp file and then
 * committed; downloads hand back a validated {@link Path} that the caller streams
 * off disk chunk by chunk. All methods are safe to call off the main server thread.
 */
final class MediaStore {

    /** Accepts exactly {@code <token>.<ext>} with filesystem-safe characters (guards path traversal). */
    private static final Pattern FILE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}\\.[A-Za-z0-9]{1,8}");
    private static final Pattern EXT = Pattern.compile("[A-Za-z0-9]{1,8}");
    private static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path dir;
    private final Path tmpDir;
    private final long retentionMillis;
    private final long maxTotalBytes;
    private final Logger logger;

    MediaStore(Path dir, long retentionHours, long maxTotalMb, Logger logger) {
        this.dir = dir;
        this.tmpDir = dir.resolve("tmp");
        this.retentionMillis = retentionHours * 3600_000L;
        this.maxTotalBytes = maxTotalMb * 1024L * 1024L;
        this.logger = logger;
        try {
            Files.createDirectories(dir);
            Files.createDirectories(tmpDir);
            // Clear leftover temp files from a previous unclean shutdown.
            try (Stream<Path> s = Files.list(tmpDir)) {
                s.forEach(this::deleteQuiet);
            }
        } catch (IOException e) {
            logger.warning("Could not prepare media dir " + dir + ": " + e);
        }
    }

    /** Fresh temp file for streaming an incoming upload into. */
    Path newTemp() throws IOException {
        return Files.createTempFile(tmpDir, "up-", ".part");
    }

    /** Moves a finished temp upload into place under a fresh id; returns the file id. */
    String commit(Path temp, String ext) throws IOException {
        String safeExt = EXT.matcher(ext).matches() ? ext.toLowerCase(Locale.ROOT) : "bin";
        String fileId;
        Path target;
        do {
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 16; i++) {
                sb.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
            }
            fileId = sb + "." + safeExt;
            target = dir.resolve(fileId);
        } while (Files.exists(target));
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return fileId;
    }

    /** Validated path of a stored file for streaming reads, or null if the id is bad/missing. */
    Path resolve(String fileId) {
        if (fileId == null || !FILE_ID.matcher(fileId).matches()) return null;
        Path f = dir.resolve(fileId).normalize();
        if (!f.startsWith(dir)) return null; // defence in depth against traversal
        return Files.isRegularFile(f) ? f : null;
    }

    void deleteQuiet(Path f) {
        try {
            Files.deleteIfExists(f);
        } catch (IOException ignored) {
        }
    }

    /** Deletes files older than the retention window, then trims oldest-first if over the size cap. */
    void cleanup() {
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>(stream.filter(Files::isRegularFile).toList());
            long now = System.currentTimeMillis();
            long total = 0;
            List<Path> survivors = new ArrayList<>();
            for (Path f : files) {
                long age = now - lastModified(f);
                if (retentionMillis > 0 && age > retentionMillis) {
                    deleteQuiet(f);
                } else {
                    survivors.add(f);
                    total += sizeQuiet(f);
                }
            }
            if (maxTotalBytes > 0 && total > maxTotalBytes) {
                survivors.sort(Comparator.comparingLong(this::lastModified)); // oldest first
                for (Path f : survivors) {
                    if (total <= maxTotalBytes) break;
                    total -= sizeQuiet(f);
                    deleteQuiet(f);
                }
            }
        } catch (IOException e) {
            logger.warning("Media cleanup failed: " + e);
        }
    }

    private long lastModified(Path f) {
        try {
            return Files.getLastModifiedTime(f).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private long sizeQuiet(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            return 0L;
        }
    }
}
