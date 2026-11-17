package com.wrgym.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（docs/接口文档.md §1.1：顾客与后台各自 Bearer JWT，有效期 7200 秒）。
 *
 * <p>secret 不写入仓库；dev 环境用 application-dev.yml 的占位值，生产从环境变量注入。
 */
@ConfigurationProperties(prefix = "gym.jwt")
public class JwtProperties {

    /** HS256 密钥，长度至少 32 字节。 */
    private String secret;
    /** 有效期（秒），契约默认 7200。 */
    private long expireSeconds = 7200L;
    /** 签发者标识。 */
    private String issuer = "gym-saas";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(long expireSeconds) {
        this.expireSeconds = expireSeconds;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
