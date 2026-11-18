package com.hiking.gymsaas.common.idempotency.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.hiking.gymsaas.common.idempotency.model.entity.ApiRequest;
import org.apache.ibatis.annotations.*;

// Global table: every read is scoped by the server-verified principal, operation and key.
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface IdempotencyMapper {
    @Insert("INSERT INTO api_request(principal_type,principal_id,operation,idempotency_key,request_hash) "
            + "VALUES(#{principalType},#{principalId},#{operation},#{idempotencyKey},#{requestHash})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int claim(ApiRequest request);

    @Update("UPDATE api_request SET http_status=#{httpStatus},result_snapshot=#{resultSnapshot,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} "
            + "WHERE id=#{id} AND principal_type=#{principalType} AND principal_id=#{principalId} AND http_status IS NULL")
    int complete(ApiRequest request);

    @Select("SELECT request_hash,http_status,result_snapshot FROM api_request WHERE principal_type=#{principalType} "
            + "AND principal_id=#{principalId} AND operation=#{operation} AND idempotency_key=#{idempotencyKey}")
    @Results(@Result(column = "result_snapshot", property = "resultSnapshot", javaType = com.fasterxml.jackson.databind.JsonNode.class,
            typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class))
    ApiRequest committed(ApiRequest request);
}
