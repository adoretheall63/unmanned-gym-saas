package com.wrgym.common.port;

import java.util.Collection;
import java.util.Map;

/**
 * 商家信息只读端口（gym-common 定义，gym-module-merchant 实现）。
 *
 * <p>公开门店列表/详情需要商家名与启用状态；门店模块不直接读 merchant 表，避免跨模块写边界模糊。
 */
public interface MerchantPort {

    MerchantInfo get(long merchantId);

    Map<Long, MerchantInfo> getByIds(Collection<Long> merchantIds);

    record MerchantInfo(long id, String name, String status) {
    }
}
