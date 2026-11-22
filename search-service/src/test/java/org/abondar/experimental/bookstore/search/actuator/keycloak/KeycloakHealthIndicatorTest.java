package org.abondar.experimental.bookstore.search.actuator.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class KeycloakHealthIndicatorTest {
  private HttpServer server;
  private String issuer;
  private KeycloakHealthIndicator indicator;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/bookstore";
    indicator = new KeycloakHealthIndicator(issuer);
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void reachableRealmReportsUpAndIssuer() {
    respond(200, "{\"issuer\":\"" + issuer + "\",\"token_endpoint\":\"unused\"}");
    var health = indicator.health();
    assertEquals(Status.UP, health.getStatus());
    assertEquals(issuer, health.getDetails().get("issuer"));
  }

  @Test
  void wrongIssuerReportsDown() {
    respond(200, "{\"issuer\":\"http://different-issuer/realms/bookstore\"}");
    assertEquals(Status.DOWN, indicator.health().getStatus());
  }

  @Test
  void missingIssuerReportsDown() {
    respond(200, "{}");
    assertEquals(Status.DOWN, indicator.health().getStatus());
  }

  @Test
  void unavailableEndpointReportsDownWithoutLeakingResponseBody() {
    respond(503, "private server diagnostics");
    var health = indicator.health();
    assertEquals(Status.DOWN, health.getStatus());
    assertFalse(health.getDetails().toString().contains("private server diagnostics"));
  }

  @Test
  void malformedDiscoveryReportsDown() {
    respond(200, "invalid JSON");
    assertEquals(Status.DOWN, indicator.health().getStatus());
  }

  @Test
  void unreachableServerReportsDown() {
    server.stop(0);
    assertEquals(Status.DOWN, indicator.health().getStatus());
  }

  private void respond(int status, String body) {
    server.createContext(
        "/realms/bookstore/.well-known/openid-configuration",
        exchange -> {
          try (exchange) {
            assertEquals("GET", exchange.getRequestMethod());
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
          }
        });
  }
}
