package io.github.foundry4j.quarkforge.security;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// ## RbacInterceptorTest
/// 通过 `@InjectMock SecurityIdentity` 模拟不同登录身份，
/// 验证角色/权限注解的组合语义与 401/403 错误码。
@QuarkusTest
class RbacInterceptorTest {

    @InjectMock
    SecurityIdentity identity;

    @Inject
    DemoService demo;

    @Inject
    StaffService staff;

    @Inject
    SecurityAuditCollector auditor;

    @BeforeEach
    void clearAuditEvents() {
        SecurityAuditCollector.clear();
    }

    // ---------- 被测 Bean ----------

    @ApplicationScoped
    public static class DemoService {

        public String open() {
            return "open";
        }

        @RequiresRole("admin")
        public String adminOnly() {
            return "admin";
        }

        @RequiresRole({"admin", "ops"})
        public String adminOrOps() {
            return "adminOrOps";
        }

        @RequiresRole(value = {"admin", "ops"}, logical = Logical.ALL)
        public String adminAndOps() {
            return "adminAndOps";
        }

        @RequiresPermission("user:create")
        public String createUser() {
            return "create";
        }

        @RequiresPermission(value = {"user:create", "user:delete"}, logical = Logical.ALL)
        public String createAndDelete() {
            return "createAndDelete";
        }
    }

    @ApplicationScoped
    @RequiresRole("staff")
    public static class StaffService {

        public String classLevel() {
            return "staff";
        }

        @RequiresPermission("report:view")
        public String report() {
            return "report";
        }
    }

    // ---------- 无注解与未认证 ----------

    @Test
    void methodWithoutAnnotationIsOpenToAnonymous() {
        anonymous();
        assertEquals("open", demo.open());
    }

    @Test
    void anonymousGets401() {
        anonymous();
        BizException ex = assertThrows(BizException.class, () -> demo.adminOnly());
        assertEquals(CommonErrorCode.UNAUTHORIZED.code(), ex.code());
    }

    // ---------- 角色校验 ----------

    @Test
    void singleRequiredRolePasses() {
        authenticate(Set.of("admin"), List.of());
        assertEquals("admin", demo.adminOnly());
    }

    @Test
    void anyOfRolesPasses() {
        authenticate(Set.of("ops"), List.of());
        assertEquals("adminOrOps", demo.adminOrOps());
    }

    @Test
    void missingRoleGets403() {
        authenticate(Set.of("user"), List.of());
        BizException ex = assertThrows(BizException.class, () -> demo.adminOnly());
        assertEquals(CommonErrorCode.FORBIDDEN.code(), ex.code());
        assertTrue(ex.getMessage().contains("admin"));
    }

    @Test
    void allRolesRequiresEveryOne() {
        authenticate(Set.of("admin"), List.of());
        assertThrows(BizException.class, () -> demo.adminAndOps());

        authenticate(Set.of("admin", "ops"), List.of());
        assertEquals("adminAndOps", demo.adminAndOps());
    }

    // ---------- 权限校验 ----------

    @Test
    void permissionPasses() {
        authenticate(Set.of(), List.of("user:create"));
        assertEquals("create", demo.createUser());
    }

    @Test
    void missingPermissionGets403() {
        authenticate(Set.of(), List.of("user:create"));
        BizException ex = assertThrows(BizException.class, () -> demo.createAndDelete());
        assertEquals(CommonErrorCode.FORBIDDEN.code(), ex.code());
        assertTrue(ex.getMessage().contains("user:delete"));
    }

    @Test
    void allPermissionsPass() {
        authenticate(Set.of(), List.of("user:create", "user:delete"));
        assertEquals("createAndDelete", demo.createAndDelete());
    }

    // ---------- 类级注解 ----------

    @Test
    void classLevelRoleApplies() {
        authenticate(Set.of("staff"), List.of());
        assertEquals("staff", staff.classLevel());
    }

    @Test
    void classLevelRoleForbiddenForOthers() {
        authenticate(Set.of("user"), List.of());
        assertThrows(BizException.class, () -> staff.classLevel());
    }

    @Test
    void classRoleAndMethodPermissionAreBothChecked() {
        // 类级角色满足但方法级权限缺失 → 403
        authenticate(Set.of("staff"), List.of());
        assertThrows(BizException.class, () -> staff.report());

        // 两者都满足 → 放行
        authenticate(Set.of("staff"), List.of("report:view"));
        assertEquals("report", staff.report());
    }

    // ---------- 安全审计 ----------

    @Test
    void deniedRoleEmitsSecurityAuditEvent() {
        authenticate(Set.of("user"), List.of());
        assertThrows(BizException.class, () -> demo.adminOnly());

        assertTrue(SecurityAuditCollector.events().stream().anyMatch(event ->
                SecurityAuditEvent.ACCESS_DENIED.equals(event.action())
                        && "tom".equals(event.actor())
                        && event.target().contains("DemoService#adminOnly")
                        && event.detail().contains("admin")));
    }

    @Test
    void deniedPermissionEmitsSecurityAuditEvent() {
        authenticate(Set.of(), List.of("user:create"));
        assertThrows(BizException.class, () -> demo.createAndDelete());

        assertTrue(SecurityAuditCollector.events().stream().anyMatch(event ->
                SecurityAuditEvent.ACCESS_DENIED.equals(event.action())
                        && event.detail().contains("user:delete")));
    }

    // ---------- 身份模拟 ----------

    private void anonymous() {
        when(identity.isAnonymous()).thenReturn(true);
    }

    private void authenticate(Set<String> roles, List<String> permissions) {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getName()).thenReturn("tom");
        when(jwt.getSubject()).thenReturn("u1");
        doReturn(permissions).when(jwt).getClaim(JwtTokenService.PERMISSIONS_CLAIM);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(jwt);
        when(identity.getRoles()).thenReturn(roles);
    }
}
