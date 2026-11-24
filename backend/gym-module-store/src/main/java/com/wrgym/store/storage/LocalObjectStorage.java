package com.wrgym.store.storage;

import com.wrgym.store.config.StorageProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地对象存储（M5 开发实现）。
 *
 * <p>生产应替换为受控对象存储；本实现的 objectKey 与公开 URL 约定保持一致，
 * 便于后续替换而不改业务代码。路径按 objectKey 拼接，写入前校验不越出根目录。
 */
@Service
public class LocalObjectStorage {

    private final Path root;
    private final String publicBaseUrl;

    public LocalObjectStorage(StorageProperties properties) {
        this.root = Paths.get(properties.getLocalRoot()).toAbsolutePath().normalize();
        this.publicBaseUrl = properties.getPublicBaseUrl();
    }

    public void save(String objectKey, byte[] bytes) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法对象键: " + objectKey);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("写入图片文件失败", ex);
        }
    }

    public String publicUrl(String objectKey) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/" + objectKey;
    }
}
