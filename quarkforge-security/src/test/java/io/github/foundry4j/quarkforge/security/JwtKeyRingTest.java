package io.github.foundry4j.quarkforge.security;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// ## JwtKeyRingTest
/// RSA 密钥加载、kid 选择与 JWKS 生成的验证。
class JwtKeyRingTest {

    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Test
    void selectsActiveKeyAndExposesJwks() {
        JwtKeyRing ring = new JwtKeyRing(List.of(
                key("old-key", pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()),
                        pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()))),
                Optional.of("old-key"));

        assertTrue(ring.isConfigured());
        assertEquals("old-key", ring.active().kid());
        assertEquals("RS256", ring.active().algorithm().name());

        JsonObject jwks = json(ring.jwks());
        assertEquals(1, jwks.getJsonArray("keys").size());
        JsonObject jwk = jwks.getJsonArray("keys").getJsonObject(0);
        assertEquals("RSA", jwk.getString("kty"));
        assertEquals("old-key", jwk.getString("kid"));
        assertEquals("RS256", jwk.getString("alg"));
        assertNotNull(jwk.getString("n"));
        assertNotNull(jwk.getString("e"));
    }

    @Test
    void rotatesThroughMultipleKeys() {
        KeyPair next = generateKeyPair();
        JwtKeyRing ring = new JwtKeyRing(List.of(
                key("old-key", pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()),
                        pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded())),
                key("new-key", pem("PRIVATE KEY", next.getPrivate().getEncoded()),
                        pem("PUBLIC KEY", next.getPublic().getEncoded()))),
                Optional.of("new-key"));

        assertEquals("new-key", ring.active().kid());
        // 旧密钥仍保留在 JWKS 中，历史令牌可继续验签
        assertEquals(2, json(ring.jwks()).getJsonArray("keys").size());
    }

    @Test
    void derivesPublicKeyFromRsaPrivateKey() {
        JwtKeyRing ring = new JwtKeyRing(List.of(
                key("rsa-only", pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()), null)),
                Optional.empty());

        assertEquals("rsa-only", ring.active().kid());
        assertNotNull(ring.active().publicKey());
        assertEquals(1, json(ring.jwks()).getJsonArray("keys").size());
    }

    @Test
    void requiresActiveKidWhenMultipleKeysConfigured() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new JwtKeyRing(List.of(
                key("a", pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()), null),
                key("b", pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()), null)),
                Optional.empty()));

        assertTrue(error.getMessage().contains("active-kid"));
    }

    @Test
    void staysDisabledWithoutKeys() {
        JwtKeyRing ring = new JwtKeyRing(List.of(), Optional.empty());

        assertFalse(ring.isConfigured());
        assertEquals("{\"keys\":[]}", ring.jwks());
    }

    private static JwtSigningConfig.Key key(String id, String privatePem, String publicPem) {
        return new JwtSigningConfig.Key() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String algorithm() {
                return "RS256";
            }

            @Override
            public Optional<String> privateKey() {
                return Optional.of(privatePem);
            }

            @Override
            public Optional<String> privateKeyLocation() {
                return Optional.empty();
            }

            @Override
            public Optional<String> publicKey() {
                return Optional.ofNullable(publicPem);
            }

            @Override
            public Optional<String> publicKeyLocation() {
                return Optional.empty();
            }
        };
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

    private static JsonObject json(String content) {
        return Json.createReader(new StringReader(content)).readObject();
    }
}
