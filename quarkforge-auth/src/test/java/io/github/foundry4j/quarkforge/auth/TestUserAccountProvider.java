package io.github.foundry4j.quarkforge.auth;

import io.github.foundry4j.quarkforge.auth.account.UserAccount;
import io.github.foundry4j.quarkforge.auth.account.UserAccountProvider;
import io.github.foundry4j.quarkforge.auth.password.PasswordEncoder;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// 测试用用户数据源：alice 可正常登录，bob 已禁用。
@ApplicationScoped
public class TestUserAccountProvider implements UserAccountProvider {

    @Inject
    PasswordEncoder passwordEncoder;

    private final Map<String, UserAccount> users = new HashMap<>();

    @PostConstruct
    void init() {
        users.put("alice", new UserAccount("1", "alice", passwordEncoder.encode("secret"),
                Set.of("admin"), Set.of("user:list"), true));
        users.put("bob", new UserAccount("2", "bob", passwordEncoder.encode("secret"),
                Set.of("user"), Set.of(), false));
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    @Override
    public Optional<UserAccount> findById(String userId) {
        return users.values().stream().filter(user -> user.id().equals(userId)).findFirst();
    }
}
