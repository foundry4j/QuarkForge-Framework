package io.github.foundry4j.quarkforge.auth.password;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// ## Argon2PasswordEncoder
/// Argon2id 实现，基于 BouncyCastle（`org.bouncycastle:bcprov-jdk18on`）。
///
/// 该依赖是本模块的 `compileOnly` 可选依赖：使用 Argon2 时业务方需显式引入，
/// 未引入而把 `quarkforge.auth.password.algorithm` 配成 `argon2` 会在首次使用时
/// 抛出明确的缺少依赖提示，不会影响默认的 BCrypt 用户。
///
/// 编码格式：`$argon2id$v=19$m=65536,t=3,p=1$<salt>$<hash>`，参数随哈希一起存储，
/// 因此调整默认参数后历史哈希仍可校验。
public final class Argon2PasswordEncoder {

    private static final Pattern ENCODED = Pattern.compile(
            "\\$argon2id\\$v=(\\d+)\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([A-Za-z0-9+/=]+)\\$([A-Za-z0-9+/=]+)");

    private static final int VERSION = Argon2Parameters.ARGON2_VERSION_13;
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int ITERATIONS = 3;
    private static final int MEMORY_KB = 65_536;
    private static final int PARALLELISM = 1;

    private final SecureRandom random = new SecureRandom();

    public String encode(String rawPassword) {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] hash = generate(rawPassword, salt, ITERATIONS, MEMORY_KB, PARALLELISM, HASH_LENGTH);
        Base64.Encoder base64 = Base64.getEncoder();
        return "$argon2id$v=" + VERSION + "$m=" + MEMORY_KB + ",t=" + ITERATIONS + ",p=" + PARALLELISM
                + "$" + base64.encodeToString(salt) + "$" + base64.encodeToString(hash);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        Matcher matcher = ENCODED.matcher(encodedPassword);
        if (!matcher.matches()) {
            return false;
        }
        int version = Integer.parseInt(matcher.group(1));
        int memoryKb = Integer.parseInt(matcher.group(2));
        int iterations = Integer.parseInt(matcher.group(3));
        int parallelism = Integer.parseInt(matcher.group(4));
        if (version != VERSION || iterations < 1 || iterations > 10
                || memoryKb < 1_024 || memoryKb > 1_048_576
                || parallelism < 1 || parallelism > 16) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(matcher.group(5));
        byte[] expected = Base64.getDecoder().decode(matcher.group(6));
        byte[] actual = generate(rawPassword, salt, iterations, memoryKb, parallelism, expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] generate(String password, byte[] salt, int iterations, int memoryKb,
                                   int parallelism, int hashLength) {
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(VERSION)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKb)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] hash = new byte[hashLength];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), hash);
        return hash;
    }
}
