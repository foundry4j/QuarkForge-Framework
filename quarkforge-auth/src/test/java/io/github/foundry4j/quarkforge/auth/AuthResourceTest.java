package io.github.foundry4j.quarkforge.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/// ## AuthResourceTest
/// 验证登录、刷新、注销端点的 JSON 契约与错误状态码。
@QuarkusTest
class AuthResourceTest {

    @Test
    void loginReturnsTokens() {
        given().contentType("application/json")
                .body(Map.of("username", "alice", "password", "secret"))
                .when().post("/auth/login")
                .then().statusCode(200)
                .body("code", equalTo(200))
                .body("data.accessToken", notNullValue())
                .body("data.refreshToken", notNullValue())
                .body("data.tokenType", equalTo("Bearer"));
    }

    @Test
    void wrongPasswordReturns401() {
        given().contentType("application/json")
                .body(Map.of("username", "alice", "password", "wrong"))
                .when().post("/auth/login")
                .then().statusCode(401)
                .body("code", equalTo(40100));
    }

    @Test
    void refreshAndLogout() {
        String refreshToken = given().contentType("application/json")
                .body(Map.of("username", "alice", "password", "secret"))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("data.refreshToken");

        String rotated = given().contentType("application/json")
                .body(Map.of("refreshToken", refreshToken))
                .when().post("/auth/refresh")
                .then().statusCode(200)
                .body("data.accessToken", notNullValue())
                .extract().path("data.refreshToken");

        given().contentType("application/json")
                .body(Map.of("refreshToken", rotated))
                .when().post("/auth/logout")
                .then().statusCode(200)
                .body("code", equalTo(200));

        given().contentType("application/json")
                .body(Map.of("refreshToken", rotated))
                .when().post("/auth/refresh")
                .then().statusCode(401);
    }
}
