package com.hiking.gymsaas.account;

import static org.junit.jupiter.api.Assertions.*;

import com.hiking.gymsaas.account.service.StaffRules;
import com.hiking.gymsaas.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 员工规则的纯函数测试：权限白名单、ADMIN 空权限、配置 ETag 稳定性。 */
class StaffRulesTest {

    @Test
    void adminMustNotCarryOperatorPermissions() {
        assertThrows(BusinessException.class, () -> StaffRules.validatePermissions("ADMIN", List.of("ORDER_READ")));
        assertDoesNotThrow(() -> StaffRules.validatePermissions("ADMIN", List.of()));
    }

    @Test
    void operatorPermissionsMustBeWhitelistedAndUnique() {
        assertDoesNotThrow(() -> StaffRules.validatePermissions("OPERATOR", List.of("ORDER_READ", "STORE_READ")));
        assertThrows(BusinessException.class,
                () -> StaffRules.validatePermissions("OPERATOR", List.of("NOT_A_PERMISSION")));
        assertThrows(BusinessException.class,
                () -> StaffRules.validatePermissions("OPERATOR", List.of("ORDER_READ", "ORDER_READ")));
        assertThrows(BusinessException.class, () -> StaffRules.validatePermissions("OPERATOR", null));
    }

    @Test
    void unknownRolesAreRejected() {
        assertThrows(BusinessException.class, () -> StaffRules.validatePermissions("OWNER", List.of()));
    }

    @Test
    void etagDependsOnWritableConfigurationOnlyAndIgnoresPermissionOrder() {
        String a = StaffRules.etag("OPERATOR", List.of("ORDER_READ", "STORE_READ"), "ACTIVE");
        String b = StaffRules.etag("operator", List.of("STORE_READ", "ORDER_READ"), "active");
        String c = StaffRules.etag("OPERATOR", List.of("ORDER_READ"), "ACTIVE");
        String d = StaffRules.etag("OPERATOR", List.of("ORDER_READ", "STORE_READ"), "DISABLED");
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertTrue(a.startsWith("\"") && a.endsWith("\""));
    }
}
