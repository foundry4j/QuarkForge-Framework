package io.github.foundry4j.quarkforge.auth.password;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/// ## DefaultPasswordEncoder
/// 默认密码编码器：按配置选择 BCrypt 或 Argon2 作为新密码的算法，
/// 校验时则根据哈希前缀自动识别算法，保证切换算法后历史密码仍可登录。
///
/// | 配置 | 默认值 | 说明 |
/// |------|--------|------|
/// | `quarkforge.auth.password.algorithm` | `bcrypt` | 新密码使用的算法：`bcrypt` / `argon2` |
///
/// 业务方提供自己的 [PasswordEncoder] Bean 即可覆盖本默认实现。
@DefaultBean
@ApplicationScoped
public class DefaultPasswordEncoder implements PasswordEncoder {

    private static final String ARGON2 = "argon2";

    private final BcryptPasswordEncoder bcrypt = new BcryptPasswordEncoder();

    private volatile Argon2PasswordEncoder argon2;

    @ConfigProperty(name = "quarkforge.auth.password.algorithm", defaultValue = "bcrypt")
    String algorithm;

    public DefaultPasswordEncoder() {
    }

    DefaultPasswordEncoder(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public String encode(String rawPassword) {
        return ARGON2.equalsIgnoreCase(algorithm) ? argon2().encode(rawPassword) : bcrypt.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        return encodedPassword.startsWith("$argon2")
                ? argon2().matches(rawPassword, encodedPassword)
                : bcrypt.matches(rawPassword, encodedPassword);
    }

    @Override
    public String algorithm() {
        return ARGON2.equalsIgnoreCase(algorithm) ? ARGON2 : "bcrypt";
    }

    /// 按需加载 Argon2，避免未使用 Argon2 的应用因缺少 BouncyCastle 而启动失败。
    private Argon2PasswordEncoder argon2() {
        Argon2PasswordEncoder encoder = argon2;
        if (encoder == null) {
            synchronized (this) {
                encoder = argon2;
                if (encoder == null) {
                    try {
                        encoder = new Argon2PasswordEncoder();
                    } catch (NoClassDefFoundError error) {
                        throw new IllegalStateException(
                                "使用 Argon2 需要引入依赖 org.bouncycastle:bcprov-jdk18on", error);
                    }
                    argon2 = encoder;
                }
            }
        }
        return encoder;
    }
}
