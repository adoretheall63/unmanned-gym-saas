package com.wrgym.auth.client;

import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 未接入真实微信时的默认实现：明确返回 503 DEPENDENCY_UNAVAILABLE，
 * 不伪造登录成功（docs/接口文档.md §1.4、§17.3）。
 */
@Component
@ConditionalOnMissingBean(WechatIdentityClient.class)
public class UnavailableWechatIdentityClient implements WechatIdentityClient {

    @Override
    public WechatIdentity exchange(String code) {
        throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "微信登录尚未接入真实服务");
    }
}
