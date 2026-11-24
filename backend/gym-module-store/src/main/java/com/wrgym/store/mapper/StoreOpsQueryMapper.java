package com.wrgym.store.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 架构例外：门店规则变更/停用前的**只读**跨模块检查（到店、订单、设备命令）。
 *
 * <p>这些事实分别属于 visit / booking / device 模块；在对应模块提供端口之前，这里只做
 * SELECT 计数与 ID 列表，不写任何跨模块表。待模块就绪后应替换为 common.port 端口调用。
 */
@Mapper
public interface StoreOpsQueryMapper {

    @Select("SELECT COUNT(*) FROM visit_session WHERE store_id = #{storeId} AND status = 'IN_STORE'")
    long countUnfinishedVisits(@Param("storeId") long storeId);

    @Select("SELECT COUNT(*) FROM booking_order WHERE store_id = #{storeId} "
            + "AND status IN ('PENDING_PAYMENT','RESERVED','IN_USE','CANCEL_PENDING','NO_SHOW_PENDING') "
            + "AND end_at > #{now}")
    long countUnfulfilledOrders(@Param("storeId") long storeId, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM device_operation WHERE store_id = #{storeId} "
            + "AND status IN ('PENDING','RUNNING','UNKNOWN')")
    long countPendingOperations(@Param("storeId") long storeId);

    @Select("SELECT id FROM booking_order WHERE store_id = #{storeId} "
            + "AND status IN ('PENDING_PAYMENT','RESERVED','IN_USE','CANCEL_PENDING','NO_SHOW_PENDING') "
            + "AND end_at > #{now} ORDER BY id LIMIT 50")
    List<Long> listUnfulfilledOrderIds(@Param("storeId") long storeId, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM booking_order WHERE store_id = #{storeId} AND status IN ('RESERVED','IN_USE') "
            + "AND start_at < #{end} AND end_at > #{start}")
    long countOrdersOverlapping(@Param("storeId") long storeId, @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);
}
