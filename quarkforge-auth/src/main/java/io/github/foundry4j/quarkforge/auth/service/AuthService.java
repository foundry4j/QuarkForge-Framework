package io.github.foundry4j.quarkforge.auth.service;

import io.github.foundry4j.quarkforge.auth.account.UserAccount;
import io.github.foundry4j.quarkforge.auth.account.UserAccountProvider;
import io.github.foundry4j.quarkforge.auth.entity.RefreshToken;
import io.github.foundry4j.quarkforge.auth.entity.RefreshTokenRepository;
import io.github.foundry4j.quarkforge.auth.password.PasswordEncoder;
import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.github.foundry4j.quarkforge.security.JwtTokenService;
import io.github.foundry4j.quarkforge.security.TokenBlacklist;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/// ## AuthService
/// 登录、刷新与注销的核心流程。
///
/// 约定：
///
/// - 访问令牌沿用 [JwtTokenService] 的签发规则（`sub`/`upn`/`groups`/`perms`）；
/// - 刷新令牌为 32 字节随机值，数据库只保存 SHA-256 哈希；
/// - 刷新即轮换：旧令牌吊销、签发新令牌；已吊销令牌被再次使用会吊销该用户全部令牌；
/// - 注销为幂等操作，未知令牌不报错。
///
/// | 配置 | 默认值 | 说明 |
/// |------|--------|------|
/// | `quarkforge.auth.refresh-token.ttl-days` | `30` | 刷新令牌有效期（天） |
/// | `quarkforge.jwt.ttl-minutes` | `120` | 访问令牌有效期（分钟） |
@ApplicationScoped
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    RefreshTokenRepository tokens;

    @Inject
    JwtTokenService jwtTokenService;

    @Inject
    PasswordEncoder passwordEncoder;

    @Inject
    TokenBlacklist tokenBlacklist;

    @Inject
    Instance<UserAccountProvider> providers;

    @ConfigProperty(name = "quarkforge.auth.refresh-token.ttl-days", defaultValue = "30")
    long refreshTtlDays;

    @ConfigProperty(name = "quarkforge.jwt.ttl-minutes", defaultValue = "120")
    long accessTtlMinutes;

    /// 用户名密码登录，成功返回新的访问令牌与刷新令牌。
    @Transactional
    public TokenPair login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new BizException(CommonErrorCode.PARAM_ERROR, "用户名和密码不能为空");
        }
        UserAccount account = accounts().findByUsername(username)
                .orElseThrow(this::badCredentials);
        if (!passwordEncoder.matches(password, account.encodedPassword())) {
            throw badCredentials();
        }
        if (!account.enabled()) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "账号已禁用");
        }
        return issue(account);
    }

    /// 使用刷新令牌换取新的令牌对，旧刷新令牌立即失效。
    ///
    /// 复用检测会在抛错前吊销该用户全部令牌，因此需要 `dontRollbackOn`，
    /// 保证吊销动作随事务提交而不是被异常回滚。
    @Transactional(dontRollbackOn = BizException.class)
    public TokenPair refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        RefreshToken stored = tokens.findByHash(hash(refreshToken)).orElseThrow(this::invalidRefreshToken);
        Instant now = Instant.now();
        if (stored.revokedAt != null) {
            // 已吊销令牌被再次使用，判定为令牌泄露，吊销该用户全部令牌
            tokens.revokeAll(stored.userId, now);
            throw invalidRefreshToken();
        }
        if (!stored.expiresAt.isAfter(now)) {
            throw invalidRefreshToken();
        }
        UserAccount account = accounts().findById(stored.userId)
                .orElseThrow(this::invalidRefreshToken);
        if (!account.enabled()) {
            throw new BizException(CommonErrorCode.UNAUTHORIZED, "账号已禁用");
        }
        stored.revokedAt = now;
        stored.persist();
        return issue(account);
    }

    /// 注销：吊销给定的刷新令牌，未知令牌视为已注销。
    @Transactional
    public void logout(String refreshToken) {
        logout(refreshToken, null, null);
    }

    /// 注销并吊销当前访问令牌：`jti` 与过期时间来自请求携带的 JWT，
    /// 未携带访问令牌时只吊销刷新令牌。
    @Transactional
    public void logout(String refreshToken, String accessTokenJti, Instant accessTokenExpiresAt) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokens.findByHash(hash(refreshToken)).ifPresent(token -> {
                if (token.revokedAt == null) {
                    token.revokedAt = Instant.now();
                    token.persist();
                }
            });
        }
        tokenBlacklist.revoke(accessTokenJti, accessTokenExpiresAt);
    }

    /// 吊销指定用户的全部令牌，用于改密、封号等需要立即失效的场景：
    /// 访问令牌按“签发时间早于当前时刻”批量失效，刷新令牌直接从库中吊销。
    @Transactional
    public void revokeAllTokens(String userId) {
        Instant now = Instant.now();
        tokenBlacklist.revokeIssuedBefore(userId, now);
        tokens.revokeAll(userId, now);
    }

    /// 签发访问令牌并落库一条刷新令牌记录。
    private TokenPair issue(UserAccount account) {
        String accessToken = jwtTokenService.issue(account.id(), account.username(),
                account.roles(), account.permissions());
        String rawToken = randomToken();
        RefreshToken token = new RefreshToken();
        token.tokenHash = hash(rawToken);
        token.userId = account.id();
        token.expiresAt = Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS);
        token.persist();
        return new TokenPair(accessToken, rawToken, accessTtlMinutes * 60);
    }

    private UserAccountProvider accounts() {
        if (providers.isUnsatisfied()) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR,
                    "未提供 UserAccountProvider 实现，无法完成认证");
        }
        return providers.get();
    }

    private BizException badCredentials() {
        return new BizException(CommonErrorCode.UNAUTHORIZED, "用户名或密码错误");
    }

    private BizException invalidRefreshToken() {
        return new BizException(CommonErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期");
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256 实现", e);
        }
    }

    /// 登录/刷新成功后的令牌对。
    ///
    /// @param accessToken  访问令牌（JWT）
    /// @param refreshToken 刷新令牌明文，仅本次响应可见
    /// @param expiresIn    访问令牌有效期（秒）
    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
    }
}
