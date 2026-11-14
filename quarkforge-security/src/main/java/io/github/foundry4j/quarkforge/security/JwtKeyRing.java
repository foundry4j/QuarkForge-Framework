package io.github.foundry4j.quarkforge.security;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// ## JwtKeyRing
/// 非对称签名密钥环：启动时加载 [JwtSigningConfig] 中的全部密钥，
/// 用 `active-kid` 对应的私钥签发，并把所有公钥以 JWKS 形式暴露，配合 `kid` 完成平滑轮换。
///
/// 轮换流程：
///
/// 1. 新增一对密钥 `keys[n]`，把 `active-kid` 指向新 kid 并重启（或滚动发布）；
/// 2. 旧密钥继续保留在配置中，JWKS 同时包含新旧公钥，历史令牌仍可验签；
/// 3. 待旧令牌全部过期后再删除旧密钥配置。
///
/// 未配置任何密钥时 [active] 返回 `null`，[JwtTokenService] 回退到
/// SmallRye 的 `smallrye.jwt.sign.key`。
@ApplicationScoped
public class JwtKeyRing {

    private static final Logger LOG = Logger.getLogger(JwtKeyRing.class);

    private final List<SigningKey> keys;
    private final SigningKey active;

    @Inject
    public JwtKeyRing(JwtSigningConfig config) {
        this(config.keys(), config.activeKid());
    }

    JwtKeyRing(List<JwtSigningConfig.Key> definitions, Optional<String> activeKid) {
        List<SigningKey> loaded = new ArrayList<>();
        for (JwtSigningConfig.Key definition : definitions) {
            loaded.add(load(definition));
        }
        this.keys = List.copyOf(loaded);
        this.active = selectActive(activeKid == null ? Optional.empty() : activeKid);
        if (active != null) {
            LOG.infof("JWT 签名密钥已加载 %d 个，当前签发 kid=%s", keys.size(), active.kid());
        }
    }

    /// 是否配置了非对称密钥；`false` 时使用 SmallRye 默认签名配置。
    public boolean isConfigured() {
        return active != null;
    }

    /// 当前签发使用的密钥，未配置时返回 `null`。
    public SigningKey active() {
        return active;
    }

    /// 全部已加载密钥（含仅用于验签的旧密钥）。
    public List<SigningKey> keys() {
        return keys;
    }

    /// 生成 JWK Set（RFC 7517），供 `mp.jwt.verify.publickey.location` 使用。
    public String jwks() {
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (SigningKey key : keys) {
            JsonObjectBuilder jwk = jwk(key);
            if (jwk != null) {
                array.add(jwk);
            }
        }
        return Json.createObjectBuilder().add("keys", array).build().toString();
    }

    private static JsonObjectBuilder jwk(SigningKey key) {
        if (key.publicKey() instanceof RSAPublicKey rsa) {
            return Json.createObjectBuilder()
                    .add("kty", "RSA")
                    .add("kid", key.kid())
                    .add("use", "sig")
                    .add("alg", key.algorithm().name())
                    .add("n", base64Url(rsa.getModulus()))
                    .add("e", base64Url(rsa.getPublicExponent()));
        }
        if (key.publicKey() instanceof ECPublicKey ec) {
            String curve = curveName(ec);
            if (curve == null) {
                return null;
            }
            return Json.createObjectBuilder()
                    .add("kty", "EC")
                    .add("kid", key.kid())
                    .add("use", "sig")
                    .add("alg", key.algorithm().name())
                    .add("crv", curve)
                    .add("x", base64Url(ec.getW().getAffineX()))
                    .add("y", base64Url(ec.getW().getAffineY()));
        }
        return null;
    }

    private static SigningKey load(JwtSigningConfig.Key definition) {
        String kid = definition.id();
        if (kid == null || kid.isBlank()) {
            throw new IllegalStateException("JWT 签名密钥缺少 id");
        }
        SignatureAlgorithm algorithm = parseAlgorithm(kid, definition.algorithm());
        if (algorithm.name().startsWith("HS")) {
            throw new IllegalStateException("JWT 密钥 " + kid + " 使用对称算法，请改用 smallrye.jwt.sign.key 配置");
        }
        String privateContent = resolve(definition.privateKey(), definition.privateKeyLocation());
        if (privateContent == null) {
            throw new IllegalStateException("JWT 签名密钥 " + kid + " 缺少 private-key 或 private-key-location");
        }
        try {
            PrivateKey privateKey = KeyUtils.decodePrivateKey(privateContent, algorithm);
            PublicKey publicKey = resolvePublicKey(definition, algorithm, privateKey, kid);
            return new SigningKey(kid, algorithm, privateKey, publicKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JWT 签名密钥 " + kid + " 加载失败: " + e.getMessage(), e);
        }
    }

    private static PublicKey resolvePublicKey(JwtSigningConfig.Key definition, SignatureAlgorithm algorithm,
                                              PrivateKey privateKey, String kid) throws GeneralSecurityException {
        String publicContent = resolve(definition.publicKey(), definition.publicKeyLocation());
        if (publicContent != null) {
            return KeyUtils.decodePublicKey(publicContent, algorithm);
        }
        if (privateKey instanceof RSAPrivateCrtKey rsa) {
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
        }
        LOG.warnf("JWT 密钥 %s 未配置公钥且无法从私钥推导，JWKS 中不会包含该密钥", kid);
        return null;
    }

    private static SignatureAlgorithm parseAlgorithm(String kid, String value) {
        String name = value == null ? "RS256" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return SignatureAlgorithm.fromAlgorithm(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT 密钥 " + kid + " 的算法不受支持: " + value, e);
        }
    }

    private static String resolve(Optional<String> inline, Optional<String> location) {
        if (inline != null && inline.isPresent() && !inline.get().isBlank()) {
            return inline.get();
        }
        if (location != null && location.isPresent() && !location.get().isBlank()) {
            try {
                return KeyUtils.readKeyContent(location.get());
            } catch (IOException e) {
                throw new IllegalStateException("读取 JWT 密钥失败: " + location.get(), e);
            }
        }
        return null;
    }

    private SigningKey selectActive(Optional<String> activeKid) {
        if (keys.isEmpty()) {
            return null;
        }
        if (activeKid.isPresent()) {
            return keys.stream()
                    .filter(key -> key.kid().equals(activeKid.get()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "quarkforge.jwt.signing.active-kid 未匹配到密钥: " + activeKid.get()));
        }
        if (keys.size() == 1) {
            return keys.getFirst();
        }
        throw new IllegalStateException("配置了多个 JWT 签名密钥时必须指定 quarkforge.jwt.signing.active-kid");
    }

    private static String curveName(ECPublicKey key) {
        return switch (key.getParams().getCurve().getField().getFieldSize()) {
            case 256 -> "P-256";
            case 384 -> "P-384";
            case 521 -> "P-521";
            default -> null;
        };
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /// 一对已加载的签名密钥。
    ///
    /// @param kid        密钥 ID，写入 JWT header
    /// @param algorithm  签名算法
    /// @param privateKey 签名私钥
    /// @param publicKey  验签公钥，无法推导时为 `null`（不会出现在 JWKS 中）
    public record SigningKey(String kid, SignatureAlgorithm algorithm, PrivateKey privateKey, PublicKey publicKey) {
    }
}
