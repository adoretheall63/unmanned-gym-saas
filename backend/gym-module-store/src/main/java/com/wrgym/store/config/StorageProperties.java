package com.wrgym.store.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 门店展示图片存储配置（M5：开发环境用本地目录，生产替换为对象存储实现）。
 *
 * <p>只存公开展示图，不存人脸或敏感资料；objectKey 仅本商家可用，url 为公开展示地址。
 */
@ConfigurationProperties(prefix = "gym.storage")
public class StorageProperties {

    /** 本地存储根目录（dev）。 */
    private String localRoot = "data/store-images";
    /** 公开访问前缀（由静态资源/Nginx/对象存储 CDN 提供）。 */
    private String publicBaseUrl = "/media/store-images";
    /** 单文件大小上限（字节），契约 5MiB。 */
    private long maxImageBytes = 5L * 1024 * 1024;

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }
}
