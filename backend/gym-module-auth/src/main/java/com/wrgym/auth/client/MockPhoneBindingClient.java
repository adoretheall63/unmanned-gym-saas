package com.wrgym.auth.client;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发/测试用手机号兑换替身（M1）。仅 {@code gym.auth.phone-provider=mock} 时生效。
 * 兑换结果为确定性假号码，仅供本地联调，不得写入真实身份数据。
 */
@Component
@ConditionalOnProperty(prefix = "gym.auth", name = "phone-provider", havingValue = "mock")
public class MockPhoneBindingClient implements PhoneBindingClient {

    @Override
    public String exchange(String phoneCode) {
        int suffix = Math.abs(phoneCode.hashCode()) % 100_000_000;
        return String.format("138%08d", suffix);
    }
}
