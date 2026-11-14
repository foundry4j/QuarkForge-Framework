package io.github.foundry4j.quarkforge.auth.account;

import java.util.Set;

/// ## UserAccount
/// 登录所需的用户视图，由业务方通过 [UserAccountProvider] 提供。
///
/// @param id              用户 ID，写入 JWT `sub` claim
/// @param username        用户名，写入 JWT `upn` claim
/// @param encodedPassword 密码哈希，由 [io.github.foundry4j.quarkforge.auth.password.PasswordEncoder] 生成
/// @param roles           角色集合，写入 JWT `groups` claim
/// @param permissions     权限集合，写入 JWT `perms` claim
/// @param enabled         账号是否启用，`false` 时拒绝登录
public record UserAccount(String id, String username, String encodedPassword,
                          Set<String> roles, Set<String> permissions, boolean enabled) {

    public UserAccount {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
