package org.abondar.experimental.bookstore.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {SecurityConfig.class, SecurityConfigTest.WebConfig.class})
class SecurityConfigTest {

  private static final String REALM = "security-test";
  private static final String CLIENT_ID = "test-client";
  private static final String CLIENT_SECRET = "test-secret";
  private static final String REALM_PATH = "/realms/" + REALM;
  private static final String DISCOVERY_PATH = REALM_PATH + "/.well-known/openid-configuration";
  private static final String IMPORT_PATH = "/opt/keycloak/data/import/security-test-realm.json";
  private static final String REALM_JSON =
      """
      {
        "realm": "security-test",
        "enabled": true,
        "sslRequired": "none",
        "clients": [{
          "clientId": "test-client",
          "secret": "test-secret",
          "publicClient": false,
          "serviceAccountsEnabled": true,
          "standardFlowEnabled": false,
          "protocolMappers": [{
            "name": "audience",
            "protocol": "openid-connect",
            "protocolMapper": "oidc-audience-mapper",
            "config": {
              "included.custom.audience": "book-service",
              "access.token.claim": "true"
            }
          }]
        }]
      }
      """;

  @Container
  static final GenericContainer<?> keycloak =
      new GenericContainer<>("quay.io/keycloak/keycloak:26.8.1")
          .withExposedPorts(8080)
          .withCommand("start-dev", "--import-realm")
          .withCopyToContainer(
              Transferable.of(REALM_JSON.getBytes(StandardCharsets.UTF_8)), IMPORT_PATH)
          .waitingFor(Wait.forHttp(DISCOVERY_PATH))
          .withStartupTimeout(Duration.ofMinutes(2));

  @LocalServerPort private int port;

  private static RestTestClient client;
  private static String token;

  @BeforeAll
  static void obtainToken() {
    token = accessToken();
  }

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri", SecurityConfigTest::issuer);
  }

  private static String issuer() {
    return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080) + REALM_PATH;
  }

  private static String accessToken() {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    var response =
        RestClient.create()
            .post()
            .uri(issuer() + "/protocol/openid-connect/token")
            .headers(headers -> headers.setBasicAuth(CLIENT_ID, CLIENT_SECRET))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(TokenResponse.class);
    assertNotNull(response);
    assertNotNull(response.accessToken());
    return response.accessToken();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenResponse(@JsonProperty("access_token") String accessToken) {}

  @BeforeEach
  void setUp() {
    if (client == null) {
      client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/books/{id}", "/api/v1/authors/{id}", "/api/v1/catalogs/{id}"})
  void apiRequiresToken(String path) {
    client.get().uri(path, UUID.randomUUID()).exchange().expectStatus().isUnauthorized();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PATCH", "DELETE"})
  void apiRejectsUnauthenticatedRequestsForAllMethods(String method) {
    client
        .method(HttpMethod.valueOf(method))
        .uri("/api/v1/books/{id}", UUID.randomUUID())
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/books/{id}", "/api/v1/authors/{id}", "/api/v1/catalogs/{id}"})
  void apiRejectsInvalidToken(String path) {
    client
        .get()
        .uri(path, UUID.randomUUID())
        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/books/{id}", "/api/v1/authors/{id}", "/api/v1/catalogs/{id}"})
  void validTokenPassesSecurity(String path) {
    // No business controllers are loaded: 404 confirms the request passed authentication.
    client
        .get()
        .uri(path, UUID.randomUUID())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/actuator/health",
        "/actuator/health/readiness",
        "/swagger-ui.html",
        "/swagger-ui/index.html",
        "/v3/api-docs",
        "/v3/api-docs/swagger-config",
        "/openapi.yaml"
      })
  void publicPathsPassSecurity(String path) {
    // These handlers are absent; security must allow the request to reach routing.
    client.get().uri(path).exchange().expectStatus().isNotFound();
  }

  @Test
  void tamperedSignatureIsRejected() {
    int signatureStart = token.lastIndexOf('.') + 1;
    char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
    var tamperedToken =
        token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);

    client
        .get()
        .uri("/api/v1/books/{id}", UUID.randomUUID())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  @ImportAutoConfiguration({
    TomcatServletWebServerAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
  })
  static class WebConfig {}
}
