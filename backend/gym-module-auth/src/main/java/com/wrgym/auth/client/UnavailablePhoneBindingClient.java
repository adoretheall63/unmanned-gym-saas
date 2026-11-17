package com.wrgym.auth.client;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 手机号绑定能力未开通时的默认实现：422 PHONE_BINDING_UNAVAILABLE（docs/接口文档.md §2.7），
 * 明确"不影响预约"。
 */
@Component
@ConditionalOnMissingBean(PhoneBindingClient.class)
public class UnavailablePhoneBindingClient implements PhoneBindingClient {

    @Override
    public String exchange(String phoneCode) {
        throw new BizException(ErrorCode.PHONE_BINDING_UNAVAILABLE, "手机号绑定能力未开通");
    }
}
