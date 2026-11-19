package org.abondar.experimental.bookstore.search.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.abondar.experimental.bookstore.search.book.adapter.in.api.BookSearchApiAdapter;
import org.abondar.experimental.bookstore.search.book.application.port.in.SearchBookContentUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.SearchBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.catalog.adapter.in.api.CatalogApiAdapter;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.SearchCatalogUseCase;
import org.abondar.experimental.bookstore.search.index.adapter.in.IndexApiAdapter;
import org.abondar.experimental.bookstore.search.index.application.port.in.CreateIndexUseCase;
import org.abondar.experimental.bookstore.search.index.application.port.in.DeleteIndexUseCase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
  private static final String CLIENT_ID = "book-search";
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
        "clients": [
          {
            "clientId": "book-search",
            "secret": "test-secret",
            "publicClient": false,
            "serviceAccountsEnabled": true,
            "standardFlowEnabled": false,
            "protocolMappers": [
              {
                "name": "audience",
                "protocol": "openid-connect",
                "protocolMapper": "oidc-audience-mapper",
                "config": {
                  "included.custom.audience": "book-search",
                  "access.token.claim": "true"
                }
              }
            ]
          },
          {
            "clientId": "book-search-admin",
            "secret": "test-secret",
            "publicClient": false,
            "serviceAccountsEnabled": true,
            "standardFlowEnabled": false,
            "protocolMappers": [
              {
                "name": "audience",
                "protocol": "openid-connect",
                "protocolMapper": "oidc-audience-mapper",
                "config": {
                  "included.custom.audience": "book-search",
                  "access.token.claim": "true"
                }
              },
              {
                "name": "client-roles",
                "protocol": "openid-connect",
                "protocolMapper": "oidc-usermodel-client-role-mapper",
                "config": {
                  "usermodel.clientRoleMapping.clientId": "book-search",
                  "claim.name": "resource_access.book-search.roles",
                  "jsonType.label": "String",
                  "multivalued": "true",
                  "access.token.claim": "true"
                }
              }
            ]
          },
          {
            "clientId": "wrong-audience",
            "secret": "test-secret",
            "publicClient": false,
            "serviceAccountsEnabled": true,
            "standardFlowEnabled": false,
            "protocolMappers": [
              {
                "name": "audience",
                "protocol": "openid-connect",
                "protocolMapper": "oidc-audience-mapper",
                "config": {
                  "included.custom.audience": "book-service",
                  "access.token.claim": "true"
                }
              }
            ]
          },
          {
            "clientId": "wrong-client-role",
            "secret": "test-secret",
            "publicClient": false,
            "serviceAccountsEnabled": true,
            "standardFlowEnabled": false,
            "protocolMappers": [
              {
                "name": "audience",
                "protocol": "openid-connect",
                "protocolMapper": "oidc-audience-mapper",
                "config": {
                  "included.custom.audience": "book-search",
                  "access.token.claim": "true"
                }
              },
              {
                "name": "client-roles",
                "protocol": "openid-connect",
                "protocolMapper": "oidc-usermodel-client-role-mapper",
                "config": {
                  "usermodel.clientRoleMapping.clientId": "wrong-audience",
                  "claim.name": "resource_access.wrong-audience.roles",
                  "jsonType.label": "String",
                  "multivalued": "true",
                  "access.token.claim": "true"
                }
              }
            ]
          }
        ],
        "roles": {
          "client": {
            "book-search": [
              {
                "name": "search-admin"
              }
            ],
            "wrong-audience": [
              {
                "name": "search-admin"
              }
            ]
          }
        },
        "users": [
          {
            "username": "service-account-book-search-admin",
            "enabled": true,
            "serviceAccountClientId": "book-search-admin",
            "clientRoles": {
              "book-search": [
                "search-admin"
              ]
            }
          },
          {
            "username": "service-account-wrong-client-role",
            "enabled": true,
            "serviceAccountClientId": "wrong-client-role",
            "clientRoles": {
              "wrong-audience": [
                "search-admin"
              ]
            }
          }
        ]
      }
      """;

  @Container
  static final GenericContainer<?> keycloak =
      new GenericContainer<>("quay.io/keycloak/keycloak:26.8.1")
          .withExposedPorts(8080)
          .withCommand("start-dev", "--import-realm")
          .withCopyToContainer(
              Transferable.of(REALM_JSON.getBytes(StandardCharsets.UTF_8)), IMPORT_PATH)
          .withCopyToContainer(
              Transferable.of(
                  REALM_JSON
                      .replace("\"security-test\"", "\"other-security-test\"")
                      .getBytes(StandardCharsets.UTF_8)),
              "/opt/keycloak/data/import/other-security-test-realm.json")
          .waitingFor(Wait.forHttp(DISCOVERY_PATH))
          .withStartupTimeout(Duration.ofMinutes(2));

  @LocalServerPort private int port;

  private static RestTestClient client;
  private static String token;
  private static String adminToken;
  private static String wrongAudienceToken;
  private static String wrongClientRoleToken;
  private static String wrongIssuerToken;
  @Autowired private IndexOperations indexOperations;
  private static final AtomicLong limiterTime = new AtomicLong();

  @Autowired private RateLimiterRegistry rateLimiters;

  @BeforeAll
  static void obtainToken() {
    token = accessToken(issuer(), CLIENT_ID);
    adminToken = accessToken(issuer(), "book-search-admin");
    wrongAudienceToken = accessToken(issuer(), "wrong-audience");
    wrongClientRoleToken = accessToken(issuer(), "wrong-client-role");
    wrongIssuerToken =
        accessToken(issuer().replace("security-test", "other-security-test"), CLIENT_ID);
  }

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri", SecurityConfigTest::issuer);
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> issuer() + "/protocol/openid-connect/certs");
    registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> "book-search");
  }

  private static String issuer() {
    return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080) + REALM_PATH;
  }

  private static String accessToken(String issuer, String clientId) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    var response =
        RestClient.create()
            .post()
            .uri(issuer + "/protocol/openid-connect/token")
            .headers(headers -> headers.setBasicAuth(clientId, CLIENT_SECRET))
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
    limiterTime.addAndGet(Duration.ofSeconds(1).toNanos());
    indexOperations.creates.set(0);
    indexOperations.deletes.set(0);
    if (client == null) {
      client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/api/v1/search/books", "/api/v1/search/books/content", "/api/v1/search/catalogs"})
  void apiRequiresToken(String path) {
    client.get().uri(path).exchange().expectStatus().isUnauthorized();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PATCH", "DELETE"})
  void apiRejectsUnauthenticatedRequestsForAllMethods(String method) {
    client
        .method(HttpMethod.valueOf(method))
        .uri("/api/v1/search/books")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/api/v1/search/books", "/api/v1/search/books/content", "/api/v1/search/catalogs"})
  void apiRejectsInvalidToken(String path) {
    authenticatedRequest(HttpMethod.GET, path, "invalid-token").expectStatus().isUnauthorized();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/api/v1/search/books", "/api/v1/search/books/content", "/api/v1/search/catalogs"})
  void validTokenPassesSecurity(String path) {
    authenticatedRequest(HttpMethod.GET, path, token).expectStatus().isOk();
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

    authenticatedRequest(HttpMethod.GET, "/api/v1/search/books", tamperedToken)
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void readLimitReturns429AndRecoversAfterRefresh() {
    authenticatedRequest(HttpMethod.GET).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET).expectStatus().isEqualTo(429);

    limiterTime.addAndGet(Duration.ofSeconds(1).toNanos());

    authenticatedRequest(HttpMethod.GET).expectStatus().isOk();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PATCH", "DELETE"})
  void writeMethodsShareLimit(String method) {
    authenticatedRequest(HttpMethod.POST).expectStatus().isEqualTo(405);
    authenticatedRequest(HttpMethod.valueOf(method)).expectStatus().isEqualTo(429);
  }

  @Test
  void readAndWriteLimitsAreIndependent() {
    authenticatedRequest(HttpMethod.GET).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET).expectStatus().isEqualTo(429);
    authenticatedRequest(HttpMethod.POST).expectStatus().isEqualTo(405);
    authenticatedRequest(HttpMethod.POST).expectStatus().isEqualTo(429);
  }

  @Test
  void publicPathsBypassExhaustedLimits() {
    rateLimiters.rateLimiter("reads").drainPermissions();
    rateLimiters.rateLimiter("writes").drainPermissions();

    client.get().uri("/openapi.yaml").exchange().expectStatus().isNotFound();
  }

  @Test
  void exhaustedLimitDoesNotReplaceAuthenticationErrors() {
    rateLimiters.rateLimiter("reads").drainPermissions();

    client.get().uri("/api/v1/search/books").exchange().expectStatus().isUnauthorized();
    authenticatedRequest(HttpMethod.GET, "/api/v1/search/books", "invalid-token")
        .expectStatus()
        .isUnauthorized();
  }

  private RestTestClient.ResponseSpec authenticatedRequest(HttpMethod method) {
    return authenticatedRequest(method, "/api/v1/search/books", token);
  }

  private RestTestClient.ResponseSpec authenticatedRequest(
      HttpMethod method, String path, String bearerToken, Object... uriVariables) {
    return client
        .method(method)
        .uri(path, uriVariables)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
        .exchange();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "DELETE"})
  void internalOperationsRequireAuthentication(String method) {
    client
        .method(HttpMethod.valueOf(method))
        .uri("/internal/api/v1/indexes")
        .exchange()
        .expectStatus()
        .isUnauthorized();
    assertEquals(0, indexOperations.creates.get());
    assertEquals(0, indexOperations.deletes.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "DELETE"})
  void ordinarySearchTokenCannotAdministerIndexes(String method) {
    authenticatedRequest(HttpMethod.valueOf(method), "/internal/api/v1/indexes", token)
        .expectStatus()
        .isForbidden();
    assertEquals(0, indexOperations.creates.get());
    assertEquals(0, indexOperations.deletes.get());
  }

  @Test
  void adminRoleAllowsIndexCreationAndDeletion() {
    authenticatedRequest(HttpMethod.POST, "/internal/api/v1/indexes", adminToken)
        .expectStatus()
        .isCreated();
    authenticatedRequest(HttpMethod.DELETE, "/internal/api/v1/indexes", adminToken)
        .expectStatus()
        .isNoContent();
    assertEquals(1, indexOperations.creates.get());
    assertEquals(1, indexOperations.deletes.get());
  }

  @Test
  void sameRoleNameOnAnotherClientDoesNotGrantIndexAdministration() {
    authenticatedRequest(HttpMethod.GET, "/api/v1/search/books", wrongClientRoleToken)
        .expectStatus()
        .isOk();
    authenticatedRequest(HttpMethod.POST, "/internal/api/v1/indexes", wrongClientRoleToken)
        .expectStatus()
        .isForbidden();
    assertEquals(0, indexOperations.creates.get());
  }

  @Test
  void wrongAudienceIsRejected() {
    authenticatedRequest(HttpMethod.GET, "/api/v1/search/books", wrongAudienceToken)
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void wrongIssuerIsRejected() {
    authenticatedRequest(HttpMethod.GET, "/api/v1/search/books", wrongIssuerToken)
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void readLimitIsSharedAcrossSearchEndpoints() {
    authenticatedRequest(HttpMethod.GET, "/api/v1/search/books", token).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET, "/api/v1/search/catalogs", token).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET, "/api/v1/search/books/content", token)
        .expectStatus()
        .isEqualTo(429);
  }

  @Test
  void headAndGetShareReadLimit() {
    authenticatedRequest(HttpMethod.HEAD).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET).expectStatus().isEqualTo(429);
  }

  @Test
  void indexAdministrationBypassesPublicApiRateLimits() {
    authenticatedRequest(HttpMethod.GET).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET).expectStatus().isOk();
    authenticatedRequest(HttpMethod.GET).expectStatus().isEqualTo(429);
    authenticatedRequest(HttpMethod.POST).expectStatus().isEqualTo(405);
    authenticatedRequest(HttpMethod.POST).expectStatus().isEqualTo(429);
    authenticatedRequest(HttpMethod.POST, "/internal/api/v1/indexes", adminToken)
        .expectStatus()
        .isCreated();
  }

  @Test
  void authenticationIsStateless() {
    authenticatedRequest(HttpMethod.GET)
        .expectStatus()
        .isOk()
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE);
    client.get().uri("/api/v1/search/books").exchange().expectStatus().isUnauthorized();
  }

  static class IndexOperations implements CreateIndexUseCase, DeleteIndexUseCase {
    final AtomicInteger creates = new AtomicInteger();
    final AtomicInteger deletes = new AtomicInteger();

    @Override
    public void createIndexes() {
      creates.incrementAndGet();
    }

    @Override
    public void deleteIndexes() {
      deletes.incrementAndGet();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  @Import({BookSearchApiAdapter.class, CatalogApiAdapter.class, IndexApiAdapter.class})
  @ImportAutoConfiguration({
    TomcatServletWebServerAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
  })
  static class WebConfig {

    @Bean
    SearchBookMetadataUseCase metadataSearch() {
      return query -> List.of();
    }

    @Bean
    SearchBookContentUseCase contentSearch() {
      return query -> List.of();
    }

    @Bean
    SearchCatalogUseCase catalogSearch() {
      return query -> List.of();
    }

    @Bean
    IndexOperations indexOperations() {
      return new IndexOperations();
    }

    @Bean
    RateLimiterRegistry rateLimiterRegistry() {
      var config =
          RateLimiterConfig.custom()
              .limitForPeriod(2)
              .limitRefreshPeriod(Duration.ofSeconds(1))
              .timeoutDuration(Duration.ZERO)
              .build();
      var registry = RateLimiterRegistry.of(config);
      // Real limiters with a test clock: each test starts a fresh cycle without sleeping.
      registry.rateLimiter("reads");
      registry.replace("reads", RateLimiter.of("reads", config, limiterTime::get));
      var writeConfig = RateLimiterConfig.from(config).limitForPeriod(1).build();
      registry.rateLimiter("writes");
      registry.replace("writes", RateLimiter.of("writes", writeConfig, limiterTime::get));
      return registry;
    }
  }
}
