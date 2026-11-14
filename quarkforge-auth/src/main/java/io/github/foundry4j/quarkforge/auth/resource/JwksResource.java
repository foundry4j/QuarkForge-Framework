package io.github.foundry4j.quarkforge.auth.resource;

import io.github.foundry4j.quarkforge.security.JwtKeyRing;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/// ## JwksResource
/// 暴露 JWK Set 端点，供其他服务通过
/// `mp.jwt.verify.publickey.location=http://auth-service/.well-known/jwks.json`
/// 按 `kid` 拉取公钥，实现 RS256 密钥轮换。
///
/// 未配置 `quarkforge.jwt.signing.keys` 时返回 404。
@Path("/.well-known/jwks.json")
@PermitAll
public class JwksResource {

    @Inject
    JwtKeyRing keyRing;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response jwks() {
        if (!keyRing.isConfigured()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(keyRing.jwks()).build();
    }
}
