package com.wrgym.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wrgym.auth.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 后台员工账号访问。密码与 token_version 修改均为条件更新，和状态校验一致。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    /** 退出全部登录 / 改密后失效：原子递增 token_version。 */
    @Update("UPDATE admin_user SET token_version = token_version + 1, updated_at = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'ACTIVE'")
    int bumpTokenVersion(@Param("id") long id);

    /** 改密：只在旧哈希仍匹配时更新，避免并发改密互相覆盖。 */
    @Update("UPDATE admin_user SET password_hash = #{newHash}, token_version = token_version + 1, "
            + "updated_at = UTC_TIMESTAMP(3) WHERE id = #{id} AND password_hash = #{oldHash}")
    int changePassword(@Param("id") long id, @Param("oldHash") String oldHash,
                       @Param("newHash") String newHash);
}
