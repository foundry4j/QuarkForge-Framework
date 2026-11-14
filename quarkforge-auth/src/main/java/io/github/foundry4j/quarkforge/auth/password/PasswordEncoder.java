package io.github.foundry4j.quarkforge.auth.password;

/// ## PasswordEncoder
/// 密码哈希 SPI。默认实现 [DefaultPasswordEncoder] 支持 BCrypt 与 Argon2id，
/// 业务方也可以提供自己的 `@ApplicationScoped` 实现来替换。
///
/// 编码结果自带算法前缀（`$2a$...` / `$argon2id$...`），
/// 因此切换默认算法后，历史密码仍能通过 [matches] 校验。
public interface PasswordEncoder {

    /// 对明文密码做哈希。
    String encode(String rawPassword);

    /// 校验明文密码与已存储哈希是否匹配。
    boolean matches(String rawPassword, String encodedPassword);

    /// 当前默认的算法标识：`bcrypt` 或 `argon2`。
    String algorithm();
}
