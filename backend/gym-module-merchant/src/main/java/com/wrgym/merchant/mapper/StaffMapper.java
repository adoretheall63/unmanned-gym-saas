package com.wrgym.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wrgym.merchant.entity.StaffEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 员工访问。所有写操作都带商家条件，且先锁"本商家启用中的管理员集合"以串行化最后管理员保护。
 *
 * <p>注解 {@code @Select} 不会自动套用实体的 autoResultMap，因此显式声明
 * {@code permissions} 的 JSON TypeHandler；否则读出的 permissions 为 null，
 * 会导致 ETag 计算不一致（412）。
 */
@Mapper
public interface StaffMapper extends BaseMapper<StaffEntity> {

    /** 本商家启用中的管理员：用于串行锁定管理员集合（禁止撤销/停用最后一位）。 */
    @Results(id = "staffWithJson", value = {
            @Result(column = "permissions", property = "permissions", typeHandler = JacksonTypeHandler.class)
    })
    @Select("SELECT * FROM admin_user WHERE merchant_id = #{merchantId} AND status = 'ACTIVE' AND role = 'ADMIN' FOR UPDATE")
    List<StaffEntity> lockActiveAdmins(@Param("merchantId") long merchantId);

    /** 租户内目标行锁：同时校验归属，避免跨商家操作。 */
    @ResultMap("staffWithJson")
    @Select("SELECT * FROM admin_user WHERE id = #{staffId} AND merchant_id = #{merchantId} FOR UPDATE")
    StaffEntity selectTenantForUpdate(@Param("staffId") long staffId, @Param("merchantId") long merchantId);

    @Update("UPDATE admin_user SET role = #{role}, permissions = CAST(#{permissions} AS JSON), "
            + "token_version = token_version + 1, updated_at = UTC_TIMESTAMP(3) "
            + "WHERE id = #{staffId} AND merchant_id = #{merchantId}")
    int updateRolePermissions(@Param("staffId") long staffId, @Param("merchantId") long merchantId,
                              @Param("role") String role, @Param("permissions") String permissionsJson);

    /** 启停：任何状态变更都使旧 JWT 失效（token_version 递增）。 */
    @Update("UPDATE admin_user SET status = #{status}, token_version = token_version + 1, "
            + "updated_at = UTC_TIMESTAMP(3) WHERE id = #{staffId} AND merchant_id = #{merchantId}")
    int updateStatus(@Param("staffId") long staffId, @Param("merchantId") long merchantId,
                     @Param("status") String status);

    /** 管理员重置密码：只写 BCrypt 哈希，并递增 token_version。 */
    @Update("UPDATE admin_user SET password_hash = #{passwordHash}, token_version = token_version + 1, "
            + "updated_at = UTC_TIMESTAMP(3) WHERE id = #{staffId} AND merchant_id = #{merchantId}")
    int resetPassword(@Param("staffId") long staffId, @Param("merchantId") long merchantId,
                      @Param("passwordHash") String passwordHash);
}
