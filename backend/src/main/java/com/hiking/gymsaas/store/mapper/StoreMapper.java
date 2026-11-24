package com.hiking.gymsaas.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiking.gymsaas.store.model.entity.Store;
import com.hiking.gymsaas.store.model.entity.StoreSlot;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 后台门店/时段访问：商家隔离由 MerchantIsolationInterceptor 注入，所有查询自动限定当前商家。
 */
@Mapper
public interface StoreMapper extends BaseMapper<Store> {

    @Select("SELECT * FROM store WHERE id = #{id} FOR UPDATE")
    Store lock(long id);

    @Select("SELECT * FROM store_slot WHERE store_id = #{storeId} AND start_at >= #{from} AND start_at < #{to} "
            + "ORDER BY start_at")
    List<StoreSlot> slots(@Param("storeId") long storeId, @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);
}
