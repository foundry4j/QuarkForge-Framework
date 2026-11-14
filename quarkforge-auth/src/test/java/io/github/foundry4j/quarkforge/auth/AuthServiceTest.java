package io.github.foundry4j.quarkforge.auth;

import io.github.foundry4j.quarkforge.auth.entity.RefreshTokenRepository;
import io.github.foundry4j.quarkforge.auth.service.AuthService;
import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// ## AuthServiceTest
/// 登录、刷新轮换、复用检测与注销的端到端验证（H2 内存库）。
@QuarkusTest
class AuthServiceTest {

    @Inject
    AuthService service;

    @Inject
    RefreshTokenRepository tokens;

    @BeforeEach
    @Transactional
    void clean() {
        tokens.deleteAll();
    }

    @Test
    void loginIssuesTokenPair() {
        AuthService.TokenPair pair = service.login("alice", "secret");

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        assertEquals(120 * 60, pair.expiresIn());
        assertEquals(1, tokens.count());
    }

    @Test
    void rejectsWrongPassword() {
        BizException error = assertThrows(BizException.class, () -> service.login("alice", "wrong"));

        assertEquals(CommonErrorCode.UNAUTHORIZED.code(), error.code());
        assertEquals("用户名或密码错误", error.getMessage());
    }

    @Test
    void rejectsUnknownUser() {
        BizException error = assertThrows(BizException.class, () -> service.login("nobody", "secret"));

        assertEquals(CommonErrorCode.UNAUTHORIZED.code(), error.code());
    }

    @Test
    void rejectsDisabledAccount() {
        BizException error = assertThrows(BizException.class, () -> service.login("bob", "secret"));

        assertEquals(CommonErrorCode.UNAUTHORIZED.code(), error.code());
        assertEquals("账号已禁用", error.getMessage());
    }

    @Test
    void rejectsBlankCredentials() {
        BizException error = assertThrows(BizException.class, () -> service.login(" ", ""));

        assertEquals(CommonErrorCode.PARAM_ERROR.code(), error.code());
    }

    @Test
    void refreshRotatesToken() {
        AuthService.TokenPair first = service.login("alice", "secret");

        AuthService.TokenPair second = service.refresh(first.refreshToken());

        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertNotEquals(first.accessToken(), second.accessToken());
        // 旧令牌已吊销，再次使用触发复用检测并吊销该用户全部令牌
        assertThrows(BizException.class, () -> service.refresh(first.refreshToken()));
        assertThrows(BizException.class, () -> service.refresh(second.refreshToken()));
    }

    @Test
    void rejectsUnknownRefreshToken() {
        BizException error = assertThrows(BizException.class, () -> service.refresh("unknown"));

        assertEquals(CommonErrorCode.UNAUTHORIZED.code(), error.code());
    }

    @Test
    void logoutRevokesToken() {
        AuthService.TokenPair pair = service.login("alice", "secret");

        service.logout(pair.refreshToken());

        assertThrows(BizException.class, () -> service.refresh(pair.refreshToken()));
    }

    @Test
    void logoutIsIdempotentForUnknownToken() {
        service.logout("unknown");
        service.logout(null);
    }
}
