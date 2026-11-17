package com.wrgym.auth.client;

/**
 * 手机号一次性 code 兑换（docs/接口文档.md §2.7）。
 *
 * <p>失败语义固定：能力未开通/未接入 → 422 PHONE_BINDING_UNAVAILABLE，且不影响预约流程。
 */
public interface PhoneBindingClient {

    /** @param phoneCode 微信授权产生的一次性手机号兑换 code（不得直接提交手机号） */
    String exchange(String phoneCode);
}
