package com.pmchat.api;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Read and write access to the server's media store — the photos, voice notes and
 * videos PocketChat clients have relayed through the server.
 *
 * <p>Files are named {@code <token>.<ext>} where the token is a 16-character random
 * string. That id is only ever handed to the sender and the recipient, so it doubles
 * as a capability token: <b>anyone who knows a file id can download the file</b>. Treat
 * ids as secrets, and use {@link com.pmchat.api.event.PocketChatMediaDownloadEvent} if
 * you need stricter access control.
 *
 * <p>All methods here touch the disk. Call them off the main server thread.
 *
 * @since 1.0.0
 */
public interface MediaService {

    /**
     * Stores bytes as a new media file.
     *
     * @param data the file contents
     * @param ext  file extension without the dot ({@code png}, {@code ogg}, {@code mp4}…);
     *             anything that is not 1–8 alphanumerics becomes {@code bin}
     * @return the new file id, e.g. {@code "a1B2c3D4e5F6g7H8.png"}
     * @throws IOException when the file could not be written
     */
    String store(byte[] data, String ext) throws IOException;

    /**
     * Reads a stored file into memory. Prefer {@link #path(String)} for large files.
     *
     * @param fileId a file id
     * @return the contents, or {@code null} when the id is unknown or malformed
     * @throws IOException when the file exists but could not be read
     */
    byte[] read(String fileId) throws IOException;

    /**
     * The on-disk location of a stored file, for streaming reads.
     *
     * @param fileId a file id
     * @return a validated path, or {@code null} when the id is unknown or malformed
     */
    Path path(String fileId);

    /**
     * @param fileId a file id
     * @return {@code true} when a file with this id is stored
     */
    boolean exists(String fileId);

    /**
     * Deletes a stored file. Media is also swept automatically by the plugin's
     * retention and total-size limits.
     *
     * @param fileId a file id
     * @return {@code true} when a file was deleted
     */
    boolean delete(String fileId);

    /**
     * The largest single upload the server accepts, in bytes ({@code max-file-mb}
     * in {@code config.yml}). Clients that exceed it fall back to external file hosts.
     *
     * @return the per-file limit in bytes
     */
    int maxFileBytes();
}
