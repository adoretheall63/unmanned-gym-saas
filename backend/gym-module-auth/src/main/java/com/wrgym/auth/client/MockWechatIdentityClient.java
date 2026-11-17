package com.wrgym.auth.client;

import com.wrgym.auth.config.AuthProperties;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 开发/测试用微信身份替身（M1）。
 *
 * <p>仅当 {@code gym.auth.wechat-provider=mock} 时生效；openid 由 code 确定性派生，
 * 保证同一 code 重复登录得到同一顾客。**不能**用于对外声称"真实微信登录已接入"。
 */
@Component
@ConditionalOnProperty(prefix = "gym.auth", name = "wechat-provider", havingValue = "mock")
public class MockWechatIdentityClient implements WechatIdentityClient {

    private final AuthProperties properties;

    public MockWechatIdentityClient(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public WechatIdentity exchange(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        return new WechatIdentity(properties.getWechatAppId(), "mock-" + digest(code));
    }

    private static String digest(String code) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(code.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
