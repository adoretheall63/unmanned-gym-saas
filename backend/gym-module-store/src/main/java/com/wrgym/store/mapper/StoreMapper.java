package com.wrgym.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wrgym.store.entity.StoreEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 门店访问。
 *
 * <p>注解 {@code @Select} 不会套用实体 autoResultMap，因此显式声明 images 的 JSON TypeHandler，
 * 否则读出的图片列表为 null，导致 ETag/响应不一致。
 * <p>公开列表用 JOIN 过滤"启用商家 + OPEN 门店"，保证分页 total 与过滤条件一致。
 */
@Mapper
public interface StoreMapper extends BaseMapper<StoreEntity> {

    @Results(id = "storeWithJson", value = {
            @Result(column = "images", property = "images", typeHandler = JacksonTypeHandler.class)
    })
    @Select("SELECT * FROM store WHERE id = #{storeId} AND merchant_id = #{merchantId}")
    StoreEntity selectTenant(@Param("storeId") long storeId, @Param("merchantId") long merchantId);

    @ResultMap("storeWithJson")
    @Select("SELECT * FROM store WHERE id = #{storeId} AND merchant_id = #{merchantId} FOR UPDATE")
    StoreEntity selectTenantForUpdate(@Param("storeId") long storeId, @Param("merchantId") long merchantId);

    @ResultMap("storeWithJson")
    @Select("SELECT s.* FROM store s JOIN merchant m ON m.id = s.merchant_id "
            + "WHERE m.status = 'ACTIVE' AND s.status = 'OPEN' ORDER BY s.id LIMIT #{offset}, #{size}")
    List<StoreEntity> listPublic(@Param("offset") long offset, @Param("size") long size);

    @Select("SELECT COUNT(*) FROM store s JOIN merchant m ON m.id = s.merchant_id "
            + "WHERE m.status = 'ACTIVE' AND s.status = 'OPEN'")
    long countPublic();
}
