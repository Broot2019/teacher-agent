package com.teacheragent.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 通用上传文件容器（替代 MultipartFile，后者不能跨线程传递给 @Async 方法）。
 * <p>语义优先级：persistedPath（磁盘路径）优于 bytes（内存字节）。</p>
 * <p>大文件场景应仅持有 persistedPath，bytes 为空数组以避免双倍堆内存占用。
 * 下游需要字节数据时调 {@link #toBytes()} 按需读盘。</p>
 */
public class UploadedFile {

    public final String name;
    /**
     * 内存字节。可能为空数组（推荐场景）：仅持有 persistedPath，需要时调 {@link #toBytes()}。
     * 仅在不便于落盘的小文件或纯内存场景下保留实际字节。
     */
    public final byte[] bytes;
    public final String persistedPath;

    public UploadedFile(String name, byte[] bytes) {
        this(name, bytes, null);
    }

    public UploadedFile(String name, byte[] bytes, String persistedPath) {
        this.name = name;
        this.bytes = bytes;
        this.persistedPath = persistedPath;
    }

    public File toFile(String dir) throws IOException {
        if (persistedPath != null && new File(persistedPath).exists()) {
            return new File(persistedPath);
        }
        if (bytes == null || bytes.length == 0) {
            throw new IOException("文件数据不可用: " + name);
        }
        File d = new File(dir);
        if (!d.exists()) d.mkdirs();
        String safe = name == null ? "tmp" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
        File f = new File(dir, "tmp_" + System.nanoTime() + "_" + safe);
        Files.write(f.toPath(), bytes);
        f.deleteOnExit();
        return f;
    }

    /**
     * 按需获取字节数据。优先返回 bytes，若 bytes 为空但 persistedPath 可用则从磁盘读取。
     */
    public byte[] toBytes() throws IOException {
        if (bytes != null && bytes.length > 0) {
            return bytes;
        }
        if (persistedPath != null) {
            File f = new File(persistedPath);
            if (f.exists()) {
                return Files.readAllBytes(f.toPath());
            }
        }
        throw new IOException("文件数据不可用: " + name);
    }
}
