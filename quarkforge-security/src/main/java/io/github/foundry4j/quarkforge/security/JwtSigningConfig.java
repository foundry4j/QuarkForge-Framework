package io.github.foundry4j.quarkforge.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/// ## JwtSigningConfig
/// JWT 非对称签名与密钥轮换配置，与 SmallRye JWT 的配置习惯保持一致：
/// 密钥内容支持 PEM / JWK，`location` 支持 `classpath:`、文件路径与 URL。
///
/// ```properties
/// # 当前用于签发的 kid；只有一个密钥时可省略
/// quarkforge.jwt.signing.active-kid=rsa-2026-11
///
/// quarkforge.jwt.signing.keys[0].id=rsa-2026-11
/// quarkforge.jwt.signing.keys[0].algorithm=RS256
/// quarkforge.jwt.signing.keys[0].private-key-location=classpath:keys/rsa-2026-11.pem
/// quarkforge.jwt.signing.keys[0].public-key-location=classpath:keys/rsa-2026-11.pub.pem
///
/// # 旧密钥保留在配置中供历史令牌验签，切换时只改 active-kid
/// quarkforge.jwt.signing.keys[1].id=rsa-2026-05
/// ...
/// ```
///
/// 未配置任何密钥时回退到 SmallRye 的 `smallrye.jwt.sign.key`（兼容原有 HS256/JWK 用法）。
@ConfigMapping(prefix = "quarkforge.jwt.signing")
public interface JwtSigningConfig {

    /// 当前签发使用的密钥 ID；配置多个密钥时必填。
    Optional<String> activeKid();

    /// 密钥列表，旧密钥保留用于验签，新令牌只用 `active-kid` 签发。
    List<Key> keys();

    /// 单个签名密钥。
    interface Key {

        /// 密钥 ID，写入 JWT header 的 `kid`。
        String id();

        /// 签名算法，默认 `RS256`；支持 `RS256/384/512`、`PS256/384/512`、`ES256/384/512`。
        @WithDefault("RS256")
        String algorithm();

        /// 私钥内容（PEM / JWK），与 `private-key-location` 二选一。
        Optional<String> privateKey();

        /// 私钥位置，支持 `classpath:`、文件路径与 URL。
        Optional<String> privateKeyLocation();

        /// 公钥内容（PEM / JWK），缺省时 RSA 私钥会自动推导。
        Optional<String> publicKey();

        /// 公钥位置，支持 `classpath:`、文件路径与 URL。
        Optional<String> publicKeyLocation();
    }
}
