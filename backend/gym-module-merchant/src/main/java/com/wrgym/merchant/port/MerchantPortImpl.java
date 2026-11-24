package com.wrgym.merchant.port;

import com.wrgym.common.port.MerchantPort;
import com.wrgym.merchant.entity.MerchantEntity;
import com.wrgym.merchant.mapper.MerchantProfileMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MerchantPort 实现：供其它模块读取商家名/状态（不暴露写能力）。
 */
@Service
public class MerchantPortImpl implements MerchantPort {

    private final MerchantProfileMapper merchantProfileMapper;

    public MerchantPortImpl(MerchantProfileMapper merchantProfileMapper) {
        this.merchantProfileMapper = merchantProfileMapper;
    }

    @Override
    public MerchantInfo get(long merchantId) {
        MerchantEntity merchant = merchantProfileMapper.selectById(merchantId);
        return merchant == null ? null
                : new MerchantInfo(merchant.getId(), merchant.getName(), merchant.getStatus());
    }

    @Override
    public Map<Long, MerchantInfo> getByIds(Collection<Long> merchantIds) {
        Map<Long, MerchantInfo> result = new HashMap<>();
        if (merchantIds == null || merchantIds.isEmpty()) {
            return result;
        }
        List<MerchantEntity> merchants = merchantProfileMapper.selectBatchIds(merchantIds);
        for (MerchantEntity merchant : merchants) {
            result.put(merchant.getId(), new MerchantInfo(merchant.getId(), merchant.getName(), merchant.getStatus()));
        }
        return result;
    }
}
