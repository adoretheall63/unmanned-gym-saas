package com.hiking.gymsaas.account.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.hiking.gymsaas.account.model.entity.AdminUser;
import com.hiking.gymsaas.account.model.entity.CustomerUser;
import com.hiking.gymsaas.account.model.entity.Merchant;
import org.apache.ibatis.annotations.*;

// Authentication precedes tenant context; every lookup below binds a verified identity or login credential.
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface IdentityMapper {
    String ADMIN_COLUMNS = "id, merchant_id, username, password_hash, role, permissions, status, token_version";
    String CUSTOMER_COLUMNS = "id, nickname, phone_verified_at, status, token_version";

    @Select("SELECT " + ADMIN_COLUMNS + " FROM admin_user WHERE username = #{username}")
    @Results(id = "adminIdentity", value = @Result(column = "permissions", property = "permissions",
            typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class))
    AdminUser adminByUsername(String username);

    @Select("SELECT " + ADMIN_COLUMNS + " FROM admin_user WHERE id = #{id}")
    @ResultMap("adminIdentity")
    AdminUser admin(long id);

    @Select("SELECT " + ADMIN_COLUMNS + " FROM admin_user WHERE id = #{id} FOR UPDATE")
    @ResultMap("adminIdentity")
    AdminUser lockAdmin(long id);

    @Select("SELECT " + CUSTOMER_COLUMNS + " FROM customer_user WHERE id = #{id}")
    CustomerUser customer(long id);

    @Select("SELECT " + CUSTOMER_COLUMNS + " FROM customer_user WHERE id = #{id} FOR UPDATE")
    CustomerUser lockCustomer(long id);

    @Select("SELECT " + CUSTOMER_COLUMNS + " FROM customer_user WHERE wx_app_id = #{appId} AND openid = #{openid}")
    CustomerUser customerByWechat(@Param("appId") String appId, @Param("openid") String openid);

    @Insert("INSERT INTO customer_user(wx_app_id, openid) VALUES(#{appId}, #{openid})")
    int createCustomer(@Param("appId") String appId, @Param("openid") String openid);

    @Select("SELECT id, name, status FROM merchant WHERE id = #{id}")
    Merchant merchant(long id);

    @Select("SELECT id, name, status FROM merchant WHERE id = #{id} FOR SHARE")
    Merchant lockMerchant(long id);

    @Update("UPDATE admin_user SET token_version = token_version + 1 WHERE id = #{id} AND token_version = #{version} AND status = 'ACTIVE'")
    int logoutAdmin(@Param("id") long id, @Param("version") long version);

    @Update("UPDATE customer_user SET token_version = token_version + 1 WHERE id = #{id} AND token_version = #{version} AND status = 'ACTIVE'")
    int logoutCustomer(@Param("id") long id, @Param("version") long version);
}
