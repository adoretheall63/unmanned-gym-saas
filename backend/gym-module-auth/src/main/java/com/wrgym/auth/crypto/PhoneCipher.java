package com.wrgym.auth.crypto;

import com.wrgym.auth.config.AuthProperties;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 手机号加密存储（docs/接口文档.md §2.7：加密保存；docs/数据库设计.md：JSON/密文只存必要数据）。
 *
 * <p>格式 {@code v1:<base64(iv||ciphertext)>}，AES-256-GCM；密钥来自配置，禁止写死或提交仓库。
 * 未配置密钥时不允许绑定（422 PHONE_BINDING_UNAVAILABLE），避免明文/弱加密落库。
 */
@Component
public class PhoneCipher {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;

    public PhoneCipher(AuthProperties properties) {
        String configured = properties.getPhoneCipherKey();
        if (configured == null || configured.isBlank()) {
            this.key = null;
        } else {
            byte[] raw = Base64.getDecoder().decode(configured);
            if (raw.length != 32) {
                throw new IllegalStateException("gym.auth.phone-cipher-key 必须是 Base64 编码的 32 字节密钥");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    public boolean available() {
        return key != null;
    }

    public String encrypt(String phone) {
        if (key == null) {
            throw new BizException(ErrorCode.PHONE_BINDING_UNAVAILABLE, "手机号加密密钥未配置");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(phone.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return "v1:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("手机号加密失败", ex);
        }
    }

    public String decrypt(String stored) {
        if (key == null || stored == null || !stored.startsWith("v1:")) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(3));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    /** 脱敏展示：保留前 3 位与后 4 位。 */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 8) {
            return null;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
