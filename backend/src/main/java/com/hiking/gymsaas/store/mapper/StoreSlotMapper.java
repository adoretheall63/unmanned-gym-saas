package com.hiking.gymsaas.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiking.gymsaas.store.model.entity.StoreSlot;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 后台时段访问；商家隔离由拦截器注入。时段唯一键 (store_id,start_at,end_at) 防重复生成。 */
@Mapper
public interface StoreSlotMapper extends BaseMapper<StoreSlot> {

    @Select("SELECT * FROM store_slot WHERE id = #{id} FOR UPDATE")
    StoreSlot lock(long id);

    @Select("SELECT * FROM store_slot WHERE start_at > #{now} AND reserved_count = 0")
    List<StoreSlot> futureUnsold(@Param("now") LocalDateTime now);

    @Select("SELECT * FROM store_slot WHERE start_at > #{now} AND reserved_count > 0 ORDER BY start_at")
    List<StoreSlot> futureCommitted(@Param("now") LocalDateTime now);

    @Select("SELECT COALESCE(MAX(capacity - reserved_count),0) FROM store_slot WHERE start_at > #{now}")
    int maxFutureRemainingDemand(@Param("now") LocalDateTime now);

    @Select("SELECT COALESCE(MAX(reserved_count),0) FROM store_slot WHERE start_at > #{now}")
    int maxFutureReserved(@Param("now") LocalDateTime now);

    /** 规则应用：只更新未来未售时段的容量与价格，已售时段的订单规则快照不变。 */
    @Update("UPDATE store_slot SET capacity = #{capacity}, price_cent = #{priceCent} "
            + "WHERE start_at > #{now} AND reserved_count = 0")
    int updateUnsoldRules(@Param("now") LocalDateTime now, @Param("capacity") int capacity,
                          @Param("priceCent") long priceCent);

    /** 时段生成：唯一键防重，重复静默忽略；只补完整小时段。 */
    @Insert("INSERT IGNORE INTO store_slot(merchant_id, store_id, start_at, end_at, capacity, reserved_count, "
            + "price_cent, status) VALUES(#{merchantId}, #{storeId}, #{startAt}, #{endAt}, #{capacity}, 0, "
            + "#{priceCent}, 'OPEN')")
    int insertIgnore(@Param("merchantId") long merchantId, @Param("storeId") long storeId,
                     @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt,
                     @Param("capacity") int capacity, @Param("priceCent") long priceCent);

    /** 已售时段（含未来）按开始时间排序，用于冲突判断。 */
    @Select("SELECT * FROM store_slot WHERE start_at > #{now} AND reserved_count > 0 ORDER BY start_at")
    List<StoreSlot> committedAfter(@Param("now") LocalDateTime now);
}
