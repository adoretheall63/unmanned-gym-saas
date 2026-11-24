package com.wrgym.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wrgym.merchant.entity.MerchantEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 商家资料访问（本模块视角）。改名必须锁行后重算 ETag 再比对（docs/接口文档.md §1.2）。
 *
 * <p>类名与 gym-module-auth 的 MerchantMapper 区分，避免 MyBatis 的默认 bean 名（merchantMapper）冲突。
 */
@Mapper
public interface MerchantProfileMapper extends BaseMapper<MerchantEntity> {

    @Select("SELECT * FROM merchant WHERE id = #{merchantId} FOR UPDATE")
    MerchantEntity selectForUpdate(@Param("merchantId") long merchantId);

    @Update("UPDATE merchant SET name = #{name}, updated_at = UTC_TIMESTAMP(3) WHERE id = #{merchantId}")
    int updateName(@Param("merchantId") long merchantId, @Param("name") String name);
}
