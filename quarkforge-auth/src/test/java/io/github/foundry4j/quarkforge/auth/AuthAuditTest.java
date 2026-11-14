package io.github.foundry4j.quarkforge.auth;

import io.github.foundry4j.quarkforge.auth.service.AuthService;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.github.foundry4j.quarkforge.security.SecurityAuditEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## AuthAuditTest
/// 登录成功/失败、登出、批量吊销令牌都会发布安全审计事件。
@QuarkusTest
class AuthAuditTest {

    @Inject
    AuthService service;

    @BeforeEach
    void clearEvents() {
        SecurityAuditCollector.clear();
    }

    @Test
    void loginSuccessIsAudited() {
        service.login("alice", "secret");

        assertTrue(SecurityAuditCollector.events().stream().anyMatch(event ->
                SecurityAuditEvent.LOGIN_SUCCESS.equals(event.action())
                        && "alice".equals(event.actor())
                        && "1".equals(event.target())));
    }

    @Test
    void loginFailureIsAudited() {
        assertThrows(BizException.class, () -> service.login("alice", "wrong"));

        assertTrue(SecurityAuditCollector.events().stream().anyMatch(event ->
                SecurityAuditEvent.LOGIN_FAILURE.equals(event.action())
                        && "alice".equals(event.actor())
                        && event.detail().contains("用户名或密码错误")));
    }

    @Test
    void logoutAndBulkRevocationAreAudited() {
        AuthService.TokenPair pair = service.login("alice", "secret");
        SecurityAuditCollector.clear();

        service.logout(pair.refreshToken());
        service.revokeAllTokens("1");

        assertTrue(SecurityAuditCollector.events().stream().anyMatch(event ->
                SecurityAuditEvent.LOGOUT.equals(event.action()) && "1".equals(event.target())));
        assertTrue(SecurityAuditCollector.events().stream().anyMatch(event ->
                SecurityAuditEvent.TOKEN_REVOKED.equals(event.action()) && "1".equals(event.target())));
    }
}
