package io.github.foundry4j.quarkforge.security;

import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// ## JwtTokenService
/// JWT 签发服务。签名密钥等配置沿用 SmallRye JWT 标准配置项：
///
/// ```properties
/// # 签发密钥：PEM 私钥或 JWK（HS256 用 oct JWK），也可用 smallrye.jwt.sign.key-location 指向文件
/// smallrye.jwt.sign.key=${JWT_SIGN_KEY}
/// # 验签密钥：对称算法使用同一份内容，非对称算法配置公钥
/// smallrye.jwt.verify.key=${JWT_SIGN_KEY}
/// mp.jwt.verify.issuer=quarkforge
/// ```
///
/// 框架扩展配置：
///
/// | 配置 | 默认值 | 说明 |
/// |------|--------|------|
/// | `quarkforge.jwt.issuer` | `quarkforge` | 签发方（iss claim） |
/// | `quarkforge.jwt.ttl-minutes` | `120` | 令牌有效期（分钟） |
@ApplicationScoped
public class JwtTokenService {

    /// 权限集合的自定义 claim 名（配合 `@RequiresPermission` 使用）。
    public static final String PERMISSIONS_CLAIM = "perms";

    @ConfigProperty(name = "quarkforge.jwt.issuer", defaultValue = "quarkforge")
    String issuer;

    @ConfigProperty(name = "quarkforge.jwt.ttl-minutes", defaultValue = "120")
    long ttlMinutes;

    /// 签发令牌，`subject` 作为用户 ID（sub claim），`username` 写入 upn claim。
    public String issue(String subject, String username, Set<String> roles) {
        return issue(subject, username, roles, Set.of(), Map.of());
    }

    /// 签发令牌并写入权限集合（`perms` claim）。
    ///
    /// 角色到权限的映射是业务规则，框架不内置：业务方在登录时根据用户角色
    /// 展开出权限集合传入即可，`@RequiresPermission` 校验时直接读取令牌。
    public String issue(String subject, String username, Set<String> roles, Set<String> permissions) {
        return issue(subject, username, roles, permissions, Map.of());
    }

    /// 签发令牌并附加自定义 claims。
    public String issue(String subject, String username, Set<String> roles, Map<String, Object> extraClaims) {
        return issue(subject, username, roles, Set.of(), extraClaims);
    }

    /// 签发令牌，写入权限集合并附加自定义 claims。
    public String issue(String subject, String username, Set<String> roles, Set<String> permissions,
                        Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        JwtClaimsBuilder builder = Jwt.issuer(issuer)
                .subject(subject)
                .upn(username)
                .claim("jti", UUID.randomUUID().toString())
                .groups(roles == null ? Set.of() : roles)
                .claim(PERMISSIONS_CLAIM, permissions == null ? Set.of() : permissions)
                .issuedAt(now.getEpochSecond())
                .expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES).getEpochSecond());
        extraClaims.forEach(builder::claim);
        return builder.sign();
    }
}
