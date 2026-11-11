package io.github.foundry4j.quarkforge.gateway;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// ## GatewayIntegrationTest
/// 验证配置映射、Router 路由注册与转发链路在真实 Quarkus 启动流程中生效。
@QuarkusTest
class GatewayIntegrationTest {

    private static final int UPSTREAM_PORT = 19090;

    private static HttpServer upstream;

    @BeforeAll
    static void startUpstream() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress(UPSTREAM_PORT), 0);
        upstream.createContext("/", exchange -> {
            byte[] body = exchange.getRequestURI().toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @Test
    void forwardsConfiguredRoute() throws Exception {
        int port = Integer.parseInt(System.getProperty("quarkus.http.test-port", "8081"));
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/gw/hello?x=1")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("/hello?x=1", response.body());
    }
}
