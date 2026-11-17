package com.wrgym.common.idempotency.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wrgym.common.idempotency.ApiRequestEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * api_request 访问入口。注意：本包在 {@code com.wrgym.**.mapper} 扫描范围内。
 */
@Mapper
public interface ApiRequestMapper extends BaseMapper<ApiRequestEntity> {
}
