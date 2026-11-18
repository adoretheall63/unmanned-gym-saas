package com.hiking.gymsaas.account.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!bootstrap")
public class WechatLoginClient {
    private final RestClient client;
    private final String appId;
    private final String secret;

    public WechatLoginClient(RestClient.Builder builder, @Value("${app.wechat.app-id:}") String appId,
                             @Value("${app.wechat.app-secret:}") String secret) {
        this.client = builder.build();
        this.appId = appId;
        this.secret = secret;
    }

    public record WechatIdentity(String appId, String openid) {
        @Override public String toString() { return "WechatIdentity[credentials=REDACTED]"; }
    }

    public WechatIdentity exchange(String code) {
        if (appId.isBlank() || secret.isBlank()) throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        try {
            JsonNode result = client.get().uri(b -> b.scheme("https").host("api.weixin.qq.com")
                    .path("/sns/jscode2session").queryParam("appid", "{appid}").queryParam("secret", "{secret}")
                    .queryParam("js_code", "{code}").queryParam("grant_type", "authorization_code")
                    .build(appId, secret, code)).retrieve().body(JsonNode.class);
            if (result == null || !result.isObject()) throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            int error = result.path("errcode").asInt(0);
            if (error == 40029 || error == 40163) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
            String openid = result.path("openid").asText("");
            if (error != 0 || openid.isBlank() || openid.length() > 128) {
                throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            }
            return new WechatIdentity(appId, openid);
        } catch (RestClientException ex) {
            // Upstream exception text can contain the code and app secret in its URL.
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }
}
