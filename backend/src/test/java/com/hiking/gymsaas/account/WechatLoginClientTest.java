package com.hiking.gymsaas.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.hiking.gymsaas.account.service.WechatLoginClient;
import com.hiking.gymsaas.common.exception.BusinessException;
import com.hiking.gymsaas.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WechatLoginClientTest {
    @Test
    void onlyServerExchangedOpenidBecomesTheIdentityAndSessionKeysAreDiscarded() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andExpect(queryParam("appid", "wx-test"))
                .andExpect(queryParam("js_code", "one-use-code"))
                .andRespond(withSuccess("{\"openid\":\"trusted-openid\",\"session_key\":\"private-session\"}", MediaType.APPLICATION_JSON));
        var result = new WechatLoginClient(builder, "wx-test", "private-secret").exchange("one-use-code");
        assertEquals("trusted-openid", result.openid());
        assertEquals("wx-test", result.appId());
        assertFalse(result.toString().contains("trusted-openid"));
        server.verify();
    }

    @Test
    void invalidCodesAre401AndMissingCredentialsOrUpstreamErrorsAre503() {
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> new WechatLoginClient(RestClient.builder(), "", "").exchange("code")).code());
        for (int code : new int[]{40029, 40163, -1}) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(anything()).andRespond(withSuccess("{\"errcode\":" + code + "}", MediaType.APPLICATION_JSON));
            var error = assertThrows(BusinessException.class, () -> new WechatLoginClient(builder, "wx-test", "secret").exchange("code"));
            assertEquals(code == -1 ? ErrorCode.DEPENDENCY_UNAVAILABLE : ErrorCode.UNAUTHENTICATED, error.code());
            server.verify();
        }
    }
}
