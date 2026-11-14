package io.github.foundry4j.quarkforge.auth.account;

import java.util.Optional;

/// ## UserAccountProvider
/// 用户数据源 SPI：框架不内置用户表，业务方从自己的用户表/用户中心实现本接口。
///
/// ```java
/// @ApplicationScoped
/// public class DbUserAccountProvider implements UserAccountProvider {
///     public Optional<UserAccount> findByUsername(String username) { ... }
///     public Optional<UserAccount> findById(String userId) { ... }
/// }
/// ```
///
/// 登录只用到 [findByUsername]；刷新令牌需要重新签发 JWT，会用到 [findById]，
/// 因此两个方法都需要实现。未提供任何实现时，登录接口返回“未配置用户数据源”。
public interface UserAccountProvider {

    /// 按用户名查找账号。
    Optional<UserAccount> findByUsername(String username);

    /// 按用户 ID 查找账号，刷新令牌时用于重新读取角色与权限。
    Optional<UserAccount> findById(String userId);
}
