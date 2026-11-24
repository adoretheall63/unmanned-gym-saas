package com.wrgym.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wrgym.store.entity.StoreSlotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 可预约时段访问。时段由门店规则生成；同一门店同一时间段唯一（uk_slot_time）。
 */
@Mapper
public interface StoreSlotMapper extends BaseMapper<StoreSlotEntity> {

    @Select("SELECT * FROM store_slot WHERE store_id = #{storeId} AND merchant_id = #{merchantId} "
            + "AND start_at >= #{from} AND start_at < #{to} ORDER BY start_at")
    List<StoreSlotEntity> listByRange(@Param("merchantId") long merchantId, @Param("storeId") long storeId,
                                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Select("SELECT * FROM store_slot WHERE id = #{slotId} AND store_id = #{storeId} "
            + "AND merchant_id = #{merchantId} FOR UPDATE")
    StoreSlotEntity selectTenantForUpdate(@Param("slotId") long slotId, @Param("storeId") long storeId,
                                          @Param("merchantId") long merchantId);
}
