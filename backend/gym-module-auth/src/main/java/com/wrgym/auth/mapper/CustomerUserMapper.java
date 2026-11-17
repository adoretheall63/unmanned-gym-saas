package com.wrgym.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wrgym.auth.entity.CustomerUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 顾客账号访问。所有条件更新都带状态/版本条件，配合当前读保证并发安全。
 */
@Mapper
public interface CustomerUserMapper extends BaseMapper<CustomerUser> {

    /** 退出全部登录：原子递增 token_version（旧 JWT 全部失效）。 */
    @Update("UPDATE customer_user SET token_version = token_version + 1, updated_at = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'ACTIVE'")
    int bumpTokenVersion(@Param("id") long id);
}
