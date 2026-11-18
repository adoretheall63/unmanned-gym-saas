package com.hiking.gymsaas.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiking.gymsaas.account.model.entity.Merchant;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {
    @Select("SELECT id,name,status FROM merchant WHERE id=#{id} FOR UPDATE")
    Merchant lock(long id);

    @Update("UPDATE merchant SET name=#{name} WHERE id=#{id}")
    int rename(@Param("id") long id, @Param("name") String name);
}
