package com.pmchat.plugin;

import com.pmchat.api.MediaService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Public {@link MediaService} view over the plugin's internal {@link MediaStore}. */
record MediaServiceImpl(MediaStore store, int maxFileBytes) implements MediaService {

    @Override
    public String store(byte[] data, String ext) throws IOException {
        Path temp = store.newTemp();
        try {
            Files.write(temp, data == null ? new byte[0] : data);
            return store.commit(temp, ext == null ? "bin" : ext);
        } catch (IOException e) {
            store.deleteQuiet(temp);
            throw e;
        }
    }

    @Override
    public byte[] read(String fileId) throws IOException {
        Path p = store.resolve(fileId);
        return p == null ? null : Files.readAllBytes(p);
    }

    @Override
    public Path path(String fileId) {
        return store.resolve(fileId);
    }

    @Override
    public boolean exists(String fileId) {
        return store.resolve(fileId) != null;
    }

    @Override
    public boolean delete(String fileId) {
        Path p = store.resolve(fileId);
        if (p == null) return false;
        store.deleteQuiet(p);
        return !Files.exists(p);
    }

    @Override
    public int maxFileBytes() {
        return maxFileBytes;
    }
}
