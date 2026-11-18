package com.hiking.gymsaas.operation.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiking.gymsaas.operation.model.entity.BusinessEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditMapper extends BaseMapper<BusinessEvent> {
    // Login has no SecurityContext yet; AuditService supplies an identity freshly verified under the transaction locks.
    @InterceptorIgnore(tenantLine = "true")
    @Insert("INSERT INTO business_event(merchant_id,event_type,target_type,target_id,admin_id,user_id,actor_type,request_id,reason,detail,occurred_at) "
            + "VALUES(#{merchantId},#{eventType},#{targetType},#{targetId},#{adminId},#{userId},#{actorType},#{requestId},#{reason},"
            + "#{detail,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},#{occurredAt})")
    int appendVerifiedIdentity(BusinessEvent event);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id FROM merchant_customer WHERE merchant_id=#{merchantId} AND user_id=#{userId} FOR SHARE")
    Long lockCustomerMembership(@Param("merchantId") long merchantId, @Param("userId") long userId);
}
