package com.wrgym.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证模块配置。
 *
 * <p>密钥不落仓库：生产通过环境变量注入。wechat/phone 的 provider 决定使用真实实现还是测试替身；
 * 默认（未设置）使用"能力不可用"实现，明确返回 503/422，不伪装成功。
 */
@ConfigurationProperties(prefix = "gym.auth")
public class AuthProperties {

    /** 微信 AppID：本地 mock 时仅作为身份命名空间。 */
    private String wechatAppId = "wx-dev-unconfigured";
    /** 微信身份兑换实现：mock（仅开发）或留空（未接入 → 503）。 */
    private String wechatProvider = "";
    /** 手机号兑换实现：mock（仅开发）或留空（未开通 → 422）。 */
    private String phoneProvider = "";
    /** 手机号 AES-256-GCM 密钥（Base64，32 字节），生产由环境变量注入。 */
    private String phoneCipherKey = "";

    public String getWechatAppId() {
        return wechatAppId;
    }

    public void setWechatAppId(String wechatAppId) {
        this.wechatAppId = wechatAppId;
    }

    public String getWechatProvider() {
        return wechatProvider;
    }

    public void setWechatProvider(String wechatProvider) {
        this.wechatProvider = wechatProvider;
    }

    public String getPhoneProvider() {
        return phoneProvider;
    }

    public void setPhoneProvider(String phoneProvider) {
        this.phoneProvider = phoneProvider;
    }

    public String getPhoneCipherKey() {
        return phoneCipherKey;
    }

    public void setPhoneCipherKey(String phoneCipherKey) {
        this.phoneCipherKey = phoneCipherKey;
    }
}
