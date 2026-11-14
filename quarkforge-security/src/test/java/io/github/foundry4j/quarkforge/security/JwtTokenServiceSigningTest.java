package io.github.foundry4j.quarkforge.security;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// ## JwtTokenServiceSigningTest
/// 配置密钥环后，签发出的令牌应使用 RS256 并携带 `kid`。
class JwtTokenServiceSigningTest {

    @Test
    void signsWithActiveKeyAndKid() {
        KeyPair keyPair = generateKeyPair();
        JwtKeyRing ring = new JwtKeyRing(List.of(new JwtSigningConfig.Key() {
            public String id() {
                return "rsa-2026-11";
            }

            public String algorithm() {
                return "RS256";
            }

            public Optional<String> privateKey() {
                return Optional.of(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            }

            public Optional<String> privateKeyLocation() {
                return Optional.empty();
            }

            public Optional<String> publicKey() {
                return Optional.of(pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
            }

            public Optional<String> publicKeyLocation() {
                return Optional.empty();
            }
        }), Optional.of("rsa-2026-11"));

        JwtTokenService service = new JwtTokenService();
        service.issuer = "quarkforge";
        service.ttlMinutes = 10;
        service.keyRing = ring;

        String token = service.issue("u1", "alice", Set.of("admin"), Set.of("user:list"));

        JsonObject header = header(token);
        assertEquals("RS256", header.getString("alg"));
        assertEquals("rsa-2026-11", header.getString("kid"));

        JsonObject claims = payload(token);
        assertEquals("u1", claims.getString("sub"));
        assertEquals("alice", claims.getString("upn"));
        assertNotNull(claims.getString("jti"));
    }

    private static JsonObject header(String token) {
        return decode(token.split("\\.")[0]);
    }

    private static JsonObject payload(String token) {
        return decode(token.split("\\.")[1]);
    }

    private static JsonObject decode(String part) {
        byte[] bytes = Base64.getUrlDecoder().decode(part);
        return Json.createReader(new StringReader(new String(bytes, StandardCharsets.UTF_8))).readObject();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }
}
