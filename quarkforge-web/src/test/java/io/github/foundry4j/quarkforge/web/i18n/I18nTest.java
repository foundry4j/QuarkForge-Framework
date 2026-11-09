package io.github.foundry4j.quarkforge.web.i18n;

import io.github.foundry4j.quarkforge.core.api.CommonErrorCode;
import io.github.foundry4j.quarkforge.core.exception.BizException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/// 端到端验证：错误码文案随 `Accept-Language` 切换。
@QuarkusTest
class I18nTest {

    @Test
    void localizesBizExceptionByAcceptLanguage() {
        given().header("Accept-Language", "en")
                .when().get("/i18n/not-found")
                .then().statusCode(404)
                .body("message", is("The requested resource does not exist"));
    }

    @Test
    void fallsBackToChineseWithoutLanguageHeader() {
        given().when().get("/i18n/not-found")
                .then().statusCode(404)
                .body("message", is("请求的资源不存在"));
    }

    @Test
    void keepsCustomMessage() {
        given().header("Accept-Language", "en")
                .when().get("/i18n/custom")
                .then().statusCode(404)
                .body("message", is("库存不足"));
    }

    @Path("/i18n")
    @ApplicationScoped
    public static class I18nResource {

        @GET
        @Path("/not-found")
        public String notFound() {
            throw new BizException(CommonErrorCode.NOT_FOUND);
        }

        @GET
        @Path("/custom")
        public String custom() {
            throw new BizException(CommonErrorCode.NOT_FOUND, "库存不足");
        }
    }
}
