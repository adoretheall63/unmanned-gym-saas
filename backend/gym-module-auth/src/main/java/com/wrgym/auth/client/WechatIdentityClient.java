package com.wrgym.auth.client;

/**
 * 微信身份兑换（docs/接口文档.md §2.1；§19 M1：可用测试替身，但用户唯一身份、本地 JWT、
 * 账号状态与 token_version 必须真实）。
 *
 * <p>真实实现需要真实 AppID/Secret 与企业侧确认，未接入时不得伪装成功。
 */
public interface WechatIdentityClient {

    /**
     * @param code wx.login 的临时 code
     * @return 后端换到的 appId + openid
     */
    WechatIdentity exchange(String code);

    record WechatIdentity(String appId, String openid) {
    }
}
