package io.github.foundry4j.quarkforge.auth.resource;

import io.github.foundry4j.quarkforge.auth.service.AuthService;
import io.github.foundry4j.quarkforge.core.api.Result;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;

/// ## AuthResource
/// 登录相关 REST 端点：
///
/// | Method | Path | Description |
/// |--------|------|-------------|
/// | POST | `/auth/login` | 用户名密码登录，返回访问令牌 + 刷新令牌 |
/// | POST | `/auth/refresh` | 刷新令牌换新令牌对（旧刷新令牌立即失效） |
/// | POST | `/auth/logout` | 注销并吊销刷新令牌 |
///
/// 端点标注 `@PermitAll`，避免全局鉴权规则误挡登录请求。
@Path("/auth")
@PermitAll
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService service;

    @Inject
    SecurityIdentity identity;

    @POST
    @Path("/login")
    public Result<TokenResponse> login(LoginRequest request) {
        AuthService.TokenPair pair = service.login(request.username(), request.password());
        return Result.ok(TokenResponse.of(pair));
    }

    @POST
    @Path("/refresh")
    public Result<TokenResponse> refresh(RefreshRequest request) {
        AuthService.TokenPair pair = service.refresh(request.refreshToken());
        return Result.ok(TokenResponse.of(pair));
    }

    @POST
    @Path("/logout")
    public Result<Void> logout(LogoutRequest request) {
        JsonWebToken jwt = identity.getPrincipal() instanceof JsonWebToken token ? token : null;
        String jti = jwt == null ? null : jwt.getTokenID();
        Instant expiresAt = jwt == null || jwt.getExpirationTime() <= 0
                ? null : Instant.ofEpochSecond(jwt.getExpirationTime());
        service.logout(request.refreshToken(), jti, expiresAt);
        return Result.ok();
    }

    /// 登录请求。
    public record LoginRequest(String username, String password) {
    }

    /// 刷新请求。
    public record RefreshRequest(String refreshToken) {
    }

    /// 注销请求。
    public record LogoutRequest(String refreshToken) {
    }

    /// 令牌响应。
    ///
    /// @param accessToken  访问令牌（JWT）
    /// @param refreshToken 刷新令牌明文，客户端需安全保存
    /// @param tokenType    固定为 `Bearer`
    /// @param expiresIn    访问令牌有效期（秒）
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

        static TokenResponse of(AuthService.TokenPair pair) {
            return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresIn());
        }
    }
}
