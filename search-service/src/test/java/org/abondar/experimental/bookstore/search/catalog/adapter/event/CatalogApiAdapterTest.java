package org.abondar.experimental.bookstore.search.catalog.adapter.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.abondar.experimental.bookstore.adapter.in.generated.searchservice.model.CatalogSearchResult;
import org.abondar.experimental.bookstore.search.catalog.application.domain.CatalogDocument;
import org.abondar.experimental.bookstore.search.catalog.application.exception.CatalogSearchException;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.CatalogSearchQuery;
import org.abondar.experimental.bookstore.search.catalog.application.port.in.SearchCatalogUseCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

@ExtendWith(MockitoExtension.class)
class CatalogApiAdapterTest {
  private static LocalValidatorFactoryBean validator;
  @Mock private SearchCatalogUseCase useCase;
  private RestTestClient client;

  @BeforeAll
  static void startValidator() {
    validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
  }

  @AfterAll
  static void closeValidator() {
    validator.close();
  }

  @BeforeEach
  void setUp() {
    var proxy = new ProxyFactory(new CatalogApiAdapter(useCase));
    proxy.setProxyTargetClass(true);
    proxy.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
    var mvc =
        MockMvcBuilders.standaloneSetup(proxy.getProxy())
            .setControllerAdvice(new CatalogSearchExceptionHandler())
            .build();
    client = RestTestClient.bindTo(mvc).build();
  }

  @Test
  void mapsSearchCriteriaAndResults() {
    var id = UUID.randomUUID();
    var query = new CatalogSearchQuery(id.toString(), "Science", 5);
    when(useCase.searchCatalogs(query))
        .thenReturn(List.of(new CatalogDocument(id.toString(), "Science Fiction")));

    var response =
        client
            .get()
            .uri("/api/v1/search/catalogs?id=" + id + "&name=Science&limit=5")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(CatalogSearchResult[].class)
            .returnResult()
            .getResponseBody();

    assertNotNull(response);
    assertEquals(1, response.length);
    assertEquals(id, response[0].getCatalogId());
    assertEquals("Science Fiction", response[0].getName());
    verify(useCase).searchCatalogs(query);
  }

  @Test
  void nameOnlySearchUsesTheDefaultLimit() {
    var query = new CatalogSearchQuery(null, "Science", 20);
    when(useCase.searchCatalogs(query)).thenReturn(List.of());

    client
        .get()
        .uri("/api/v1/search/catalogs?name=Science")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
    verify(useCase).searchCatalogs(query);
  }

  @Test
  void noCriteriaReturnsAnEmptyArrayWhenNothingIsIndexed() {
    var query = new CatalogSearchQuery(null, null, 20);
    when(useCase.searchCatalogs(query)).thenReturn(List.of());

    client
        .get()
        .uri("/api/v1/search/catalogs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
    verify(useCase).searchCatalogs(query);
  }

  @Test
  void searchFailuresReturnServiceUnavailable() {
    var query = new CatalogSearchQuery(null, "Science", 20);
    when(useCase.searchCatalogs(query))
        .thenThrow(new CatalogSearchException("Unavailable", new IOException("Disconnected")));

    client
        .get()
        .uri("/api/v1/search/catalogs?name=Science")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/search/catalogs?id=invalid",
        "/api/v1/search/catalogs?limit=0",
        "/api/v1/search/catalogs?limit=101",
        "/api/v1/search/catalogs?limit=abc",
        "/api/v1/search/catalogs?name=",
        "/api/v1/search/catalogs?name=%20",
        "/api/v1/search/catalogs?name=%09"
      })
  void invalidRequestsReturnBadRequest(String uri) {
    client.get().uri(URI.create(uri)).exchange().expectStatus().isBadRequest();
    verifyNoInteractions(useCase);
  }
}
