package com.wrgym.common.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wrgym.common.audit.BusinessEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BusinessEventMapper extends BaseMapper<BusinessEventEntity> {
}
