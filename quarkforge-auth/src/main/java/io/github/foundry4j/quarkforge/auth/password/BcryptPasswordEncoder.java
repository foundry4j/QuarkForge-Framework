package io.github.foundry4j.quarkforge.auth.password;

import io.quarkus.elytron.security.common.BcryptUtil;

/// ## BcryptPasswordEncoder
/// BCrypt 实现，基于 Quarkus 内置的 [BcryptUtil]（`quarkus-elytron-security-common`），
/// 哈希结果形如 `$2a$10$...`。
public final class BcryptPasswordEncoder {

    public String encode(String rawPassword) {
        return BcryptUtil.bcryptHash(rawPassword);
    }

    /// 仅处理 `$2` 前缀的哈希，其他格式直接返回 `false`，避免交由 BcryptUtil 抛异常。
    public boolean matches(String rawPassword, String encodedPassword) {
        return rawPassword != null && encodedPassword != null
                && encodedPassword.startsWith("$2")
                && BcryptUtil.matches(rawPassword, encodedPassword);
    }
}
