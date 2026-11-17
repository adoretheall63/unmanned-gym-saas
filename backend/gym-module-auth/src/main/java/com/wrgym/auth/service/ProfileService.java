package com.wrgym.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wrgym.auth.client.PhoneBindingClient;
import com.wrgym.auth.crypto.PhoneCipher;
import com.wrgym.auth.dto.AuthDtos;
import com.wrgym.auth.entity.CustomerUser;
import com.wrgym.auth.mapper.CustomerUserMapper;
import com.wrgym.common.api.ErrorCode;
import com.wrgym.common.exception.BizException;
import com.wrgym.common.idempotency.IdempotencyService;
import com.wrgym.common.time.UtcTimes;
import com.wrgym.security.auth.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人资料（docs/接口文档.md §2.5–§2.8）。
 *
 * <p>出参白名单：id / nickname / phoneMasked / phoneVerified；
 * **不返回 openid、手机号密文、人脸原图**。2.6/2.7/2.8 必须带 Idempotency-Key。
 */
@Service
public class ProfileService {

    private final CustomerUserMapper customerUserMapper;
    private final PhoneBindingClient phoneBindingClient;
    private final PhoneCipher phoneCipher;
    private final IdempotencyService idempotencyService;

    public ProfileService(CustomerUserMapper customerUserMapper, PhoneBindingClient phoneBindingClient,
                          PhoneCipher phoneCipher, IdempotencyService idempotencyService) {
        this.customerUserMapper = customerUserMapper;
        this.phoneBindingClient = phoneBindingClient;
        this.phoneCipher = phoneCipher;
        this.idempotencyService = idempotencyService;
    }

    /** 2.5 读取本人资料。 */
    public AuthDtos.CustomerProfile getProfile() {
        return toProfile(loadCurrentCustomer());
    }

    /** 2.6 修改本人昵称：只接受 nickname，不能修改身份/角色/商家归属。 */
    public AuthDtos.CustomerProfile updateNickname(String idempotencyKey, String nickname) {
        long customerId = SecurityUtils.currentCustomerId();
        String key = idempotencyService.requireKey(idempotencyKey);
        return idempotencyService.execute("app.me.nickname", key, nickname,
                AuthDtos.CustomerProfile.class, () -> {
                    CustomerUser update = new CustomerUser();
                    update.setNickname(nickname);
                    int affected = customerUserMapper.update(update,
                            new LambdaUpdateWrapper<CustomerUser>()
                                    .eq(CustomerUser::getId, customerId)
                                    .eq(CustomerUser::getStatus, "ACTIVE"));
                    if (affected != 1) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    return toProfile(loadById(customerId));
                });
    }

    /** 2.7 绑定已验证手机号：code 一次性兑换，只保存密文与验证时间。 */
    @Transactional(rollbackFor = Exception.class)
    public AuthDtos.CustomerProfile bindPhone(String idempotencyKey, String phoneCode) {
        long customerId = SecurityUtils.currentCustomerId();
        String key = idempotencyService.requireKey(idempotencyKey);
        if (!phoneCipher.available()) {
            throw new BizException(ErrorCode.PHONE_BINDING_UNAVAILABLE, "手机号绑定能力未开通");
        }
        return idempotencyService.execute("app.me.phone-binding.bind", key, phoneCode,
                AuthDtos.CustomerProfile.class, () -> {
                    String phone = phoneBindingClient.exchange(phoneCode);
                    String ciphertext = phoneCipher.encrypt(phone);
                    CustomerUser update = new CustomerUser();
                    update.setPhoneCiphertext(ciphertext);
                    update.setPhoneVerifiedAt(UtcTimes.now());
                    int affected = customerUserMapper.update(update,
                            new LambdaUpdateWrapper<CustomerUser>()
                                    .eq(CustomerUser::getId, customerId)
                                    .eq(CustomerUser::getStatus, "ACTIVE"));
                    if (affected != 1) {
                        throw new BizException(ErrorCode.NOT_FOUND);
                    }
                    return toProfile(loadById(customerId));
                });
    }

    /** 2.8 解除手机号绑定：仅清理本人密文与验证时间，不动微信身份与历史业务。 */
    @Transactional(rollbackFor = Exception.class)
    public AuthDtos.CustomerProfile unbindPhone(String idempotencyKey) {
        long customerId = SecurityUtils.currentCustomerId();
        String key = idempotencyService.requireKey(idempotencyKey);
        return idempotencyService.execute("app.me.phone-binding.unbind", key, "-",
                AuthDtos.CustomerProfile.class, () -> {
                    CustomerUser update = new CustomerUser();
                    update.setPhoneCiphertext(null);
                    update.setPhoneVerifiedAt(null);
                    customerUserMapper.update(update, new LambdaUpdateWrapper<CustomerUser>()
                            .eq(CustomerUser::getId, customerId)
                            .eq(CustomerUser::getStatus, "ACTIVE")
                            .set(CustomerUser::getPhoneCiphertext, null)
                            .set(CustomerUser::getPhoneVerifiedAt, null));
                    return toProfile(loadById(customerId));
                });
    }

    private CustomerUser loadCurrentCustomer() {
        return loadById(SecurityUtils.currentCustomerId());
    }

    private CustomerUser loadById(long customerId) {
        CustomerUser customer = customerUserMapper.selectById(customerId);
        if (customer == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return customer;
    }

    private AuthDtos.CustomerProfile toProfile(CustomerUser customer) {
        String phone = phoneCipher.decrypt(customer.getPhoneCiphertext());
        return new AuthDtos.CustomerProfile(String.valueOf(customer.getId()), customer.getNickname(),
                PhoneCipher.mask(phone), customer.getPhoneVerifiedAt() != null);
    }
}
