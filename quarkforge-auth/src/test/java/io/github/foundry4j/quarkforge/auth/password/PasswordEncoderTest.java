package io.github.foundry4j.quarkforge.auth.password;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## PasswordEncoderTest
/// 验证 BCrypt / Argon2 的编码与校验，以及默认编码器按哈希前缀识别算法。
class PasswordEncoderTest {

    private final BcryptPasswordEncoder bcrypt = new BcryptPasswordEncoder();
    private final Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder();

    @Test
    void bcryptRoundTrip() {
        String hash = bcrypt.encode("secret");

        assertTrue(hash.startsWith("$2"));
        assertTrue(bcrypt.matches("secret", hash));
        assertFalse(bcrypt.matches("wrong", hash));
        assertFalse(bcrypt.matches("secret", null));
    }

    @Test
    void argon2RoundTrip() {
        String hash = argon2.encode("secret");

        assertTrue(hash.startsWith("$argon2id$v=19$m=65536,t=3,p=1$"));
        assertTrue(argon2.matches("secret", hash));
        assertFalse(argon2.matches("wrong", hash));
        assertFalse(argon2.matches("secret", null));
        assertFalse(argon2.matches("secret", bcrypt.encode("secret")));
    }

    @Test
    void defaultEncoderDetectsAlgorithmByHashPrefix() {
        DefaultPasswordEncoder bcryptEncoder = new DefaultPasswordEncoder("bcrypt");
        DefaultPasswordEncoder argon2Encoder = new DefaultPasswordEncoder("argon2");

        String bcryptHash = bcryptEncoder.encode("secret");
        String argon2Hash = argon2Encoder.encode("secret");

        assertTrue(bcryptHash.startsWith("$2"));
        assertTrue(argon2Hash.startsWith("$argon2id$"));
        // 切换默认算法后，历史哈希仍能通过前缀识别校验
        assertTrue(bcryptEncoder.matches("secret", argon2Hash));
        assertTrue(argon2Encoder.matches("secret", bcryptHash));
        assertFalse(argon2Encoder.matches("wrong", argon2Hash));
    }
}
