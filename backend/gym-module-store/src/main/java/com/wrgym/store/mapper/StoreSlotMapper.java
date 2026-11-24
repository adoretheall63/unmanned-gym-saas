package com.wrgym.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wrgym.store.entity.StoreSlotEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /** 未来未售时段（规则变更只影响这些）。 */
    @Select("SELECT * FROM store_slot WHERE merchant_id = #{merchantId} AND store_id = #{storeId} "
            + "AND start_at > #{now} AND reserved_count = 0")
    List<StoreSlotEntity> listFutureUnsold(@Param("merchantId") long merchantId,
                                           @Param("storeId") long storeId, @Param("now") LocalDateTime now);

    /** 未来已承诺的最大占用量：规则容量不得低于它。 */
    @Select("SELECT COALESCE(MAX(reserved_count),0) FROM store_slot WHERE merchant_id = #{merchantId} "
            + "AND store_id = #{storeId} AND start_at > #{now}")
    int maxFutureReserved(@Param("merchantId") long merchantId, @Param("storeId") long storeId,
                          @Param("now") LocalDateTime now);

    /** 未来有承诺的时段（用于冲突判断）。 */
    @Select("SELECT * FROM store_slot WHERE merchant_id = #{merchantId} AND store_id = #{storeId} "
            + "AND start_at > #{now} AND reserved_count > 0 ORDER BY start_at")
    List<StoreSlotEntity> listFutureCommitted(@Param("merchantId") long merchantId,
                                              @Param("storeId") long storeId, @Param("now") LocalDateTime now);

    /** 只更新未来未售时段的价格/容量，已售时段与订单规则快照保持不变。 */
    @Update("UPDATE store_slot SET capacity = #{capacity}, price_cent = #{priceCent}, "
            + "updated_at = UTC_TIMESTAMP(3) WHERE merchant_id = #{merchantId} AND store_id = #{storeId} "
            + "AND start_at > #{now} AND reserved_count = 0")
    int updateUnsoldRules(@Param("merchantId") long merchantId, @Param("storeId") long storeId,
                          @Param("now") LocalDateTime now, @Param("capacity") int capacity,
                          @Param("priceCent") long priceCent);

    /** 时段生成：唯一键 (store_id, start_at, end_at) 防重，重复静默忽略。 */
    @Insert("INSERT IGNORE INTO store_slot (merchant_id, store_id, start_at, end_at, capacity, "
            + "reserved_count, price_cent, status) VALUES (#{merchantId}, #{storeId}, #{startAt}, #{endAt}, "
            + "#{capacity}, 0, #{priceCent}, 'OPEN')")
    int insertIgnore(@Param("merchantId") long merchantId, @Param("storeId") long storeId,
                     @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt,
                     @Param("capacity") int capacity, @Param("priceCent") long priceCent);
}
