package com.hiking.gymsaas.booking.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hiking.gymsaas.booking.model.entity.BookingOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/** 顾客侧订单写/读：显式忽略租户改写，由 Service 校验 user_id 归属。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BookingOrderMapper extends BaseMapper<BookingOrder> {

    // 自定义查询不套用实体 autoResultMap：rule_snapshot JSON 列必须显式声明 TypeHandler。
    @Select("SELECT * FROM booking_order WHERE id = #{id} FOR UPDATE")
    @Results(id = "bookingOrderRow", value = @Result(column = "rule_snapshot", property = "ruleSnapshot",
            typeHandler = JacksonTypeHandler.class))
    BookingOrder lockOrder(long id);
}
