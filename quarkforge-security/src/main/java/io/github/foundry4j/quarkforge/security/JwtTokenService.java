package io.github.foundry4j.quarkforge.security;

import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// ## JwtTokenService
/// JWT 签发服务。签名密钥优先使用框架的密钥环配置（支持 RS256/JWKS + `kid` 轮换，
/// 见 [JwtSigningConfig]），未配置时沿用 SmallRye JWT 标准配置项：
///
/// ```properties
/// # 签发密钥：PEM 私钥或 JWK（HS256 用 oct JWK），也可用 smallrye.jwt.sign.key-location 指向文件
/// smallrye.jwt.sign.key=${JWT_SIGN_KEY}
/// # 验签密钥：对称算法使用同一份内容，非对称算法配置公钥
/// smallrye.jwt.verify.key=${JWT_SIGN_KEY}
/// mp.jwt.verify.issuer=quarkforge
/// ```
///
/// 非对称 + `kid` 轮换：
///
/// ```properties
/// quarkforge.jwt.signing.active-kid=rsa-2026-11
/// quarkforge.jwt.signing.keys[0].id=rsa-2026-11
/// quarkforge.jwt.signing.keys[0].algorithm=RS256
/// quarkforge.jwt.signing.keys[0].private-key-location=classpath:keys/rsa-2026-11.pem
/// quarkforge.jwt.signing.keys[0].public-key-location=classpath:keys/rsa-2026-11.pub.pem
///
/// # 验签端按 JWKS 中的 kid 选公钥
/// mp.jwt.verify.publickey.location=http://auth-service/.well-known/jwks.json
/// mp.jwt.verify.publickey.algorithm=RS256
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

    /// 非对称密钥环；未配置 `quarkforge.jwt.signing.keys` 时为空实现，走 SmallRye 默认签名。
    @Inject
    JwtKeyRing keyRing;

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
        return sign(builder);
    }

    /// 优先使用密钥环中的活动私钥并写入 `kid`，便于验签端按 kid 选公钥；
    /// 未配置非对称密钥时使用 SmallRye 的 `smallrye.jwt.sign.key`。
    private String sign(JwtClaimsBuilder builder) {
        JwtKeyRing.SigningKey key = keyRing == null ? null : keyRing.active();
        if (key == null) {
            return builder.sign();
        }
        return builder.jws()
                .keyId(key.kid())
                .algorithm(key.algorithm())
                .sign(key.privateKey());
    }
}
