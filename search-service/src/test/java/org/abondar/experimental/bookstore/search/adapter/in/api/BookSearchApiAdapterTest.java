package org.abondar.experimental.bookstore.search.adapter.in.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.abondar.experimental.bookstore.adapter.in.generated.searchservice.model.BookSearchResult;
import org.abondar.experimental.bookstore.search.book.application.exception.BookSearchException;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchContentQuery;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchMetadataQuery;
import org.abondar.experimental.bookstore.search.book.application.port.in.SearchBookContentUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.SearchBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
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
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

@ExtendWith(MockitoExtension.class)
class BookSearchApiAdapterTest {
  private static LocalValidatorFactoryBean validator;
  @Mock private SearchBookMetadataUseCase metadataUseCase;
  @Mock private SearchBookContentUseCase contentUseCase;
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
    var converter = new DefaultFormattingConversionService();
    converter.addConverter(new BookContentFormatConverter());
    var proxy = new ProxyFactory(new BookSearchApiAdapter(metadataUseCase, contentUseCase));
    proxy.setProxyTargetClass(true);
    proxy.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
    var mvc =
        MockMvcBuilders.standaloneSetup(proxy.getProxy())
            .setControllerAdvice(new BookSearchApiExceptionHandler())
            .setConversionService(converter)
            .build();
    client = RestTestClient.bindTo(mvc).build();
  }

  @Test
  void metadataEndpointCallsOnlyMetadataAndMapsAllFields() {
    var bookId = UUID.randomUUID();
    var catalogId = UUID.randomUUID();
    var query =
        new BookSearchMetadataQuery(
            bookId.toString(), "0306406152", catalogId.toString(), "Java", 10);
    var book =
        new BookMetadataDocument(
            bookId.toString(), "0306406152", "Java", "Description", catalogId.toString());
    when(metadataUseCase.searchBookMetadata(query)).thenReturn(List.of(book));
    var response =
        client
            .get()
            .uri(
                "/api/v1/search/books?bookId="
                    + bookId
                    + "&isbn=0306406152&catalogId="
                    + catalogId
                    + "&q=Java&limit=10")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(BookSearchResult[].class)
            .returnResult()
            .getResponseBody();
    assertNotNull(response);
    assertEquals(1, response.length);
    assertEquals(bookId, response[0].getBookId());
    assertEquals(catalogId, response[0].getCatalogId());
    assertEquals("Description", response[0].getDescription());
    assertEquals("0306406152", response[0].getIsbn());
    verify(metadataUseCase).searchBookMetadata(query);
    verifyNoInteractions(contentUseCase);
  }

  @Test
  void contentEndpointCallsOnlyContentAndReturnsMetadata() {
    var bookId = UUID.randomUUID();
    var query = new BookSearchContentQuery(bookId.toString(), "pdf", "book.pdf", "Java", 10);
    when(contentUseCase.searchBookContent(query))
        .thenReturn(
            List.of(
                new BookMetadataDocument(bookId.toString(), "0306406152", "Handbook", null, null)));
    var response =
        client
            .get()
            .uri(
                "/api/v1/search/books/content?bookId="
                    + bookId
                    + "&format=pdf&originalFilename=book.pdf&q=Java&limit=10")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(BookSearchResult[].class)
            .returnResult()
            .getResponseBody();
    assertNotNull(response);
    assertEquals(1, response.length);
    assertEquals(bookId, response[0].getBookId());
    assertEquals("Handbook", response[0].getTitle());
    verify(contentUseCase).searchBookContent(query);
    verifyNoInteractions(metadataUseCase);
  }

  @Test
  void metadataNoMatchesReturnsEmptyArrayWithDefaultLimit() {
    var query = new BookSearchMetadataQuery(null, null, null, null, 20);
    when(metadataUseCase.searchBookMetadata(query)).thenReturn(List.of());
    client
        .get()
        .uri("/api/v1/search/books")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
    verifyNoInteractions(contentUseCase);
  }

  @Test
  void contentNoMatchesReturnsEmptyArrayWithDefaultLimit() {
    var query = new BookSearchContentQuery(null, null, null, null, 20);
    when(contentUseCase.searchBookContent(query)).thenReturn(List.of());
    client
        .get()
        .uri("/api/v1/search/books/content")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
    verifyNoInteractions(metadataUseCase);
  }

  @Test
  void rejectsUnsupportedContentFormat() {
    client
        .get()
        .uri("/api/v1/search/books/content?format=txt")
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(metadataUseCase, contentUseCase);
  }

  @Test
  void metadataSearchFailureReturnsServiceUnavailable() {
    var query = new BookSearchMetadataQuery(null, null, null, "Java", 20);
    when(metadataUseCase.searchBookMetadata(query))
        .thenThrow(
            new BookSearchException("Metadata unavailable", new IOException("Disconnected")));

    client
        .get()
        .uri("/api/v1/search/books?q=Java")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .isEmpty();
    verifyNoInteractions(contentUseCase);
  }

  @Test
  void contentSearchFailureReturnsServiceUnavailable() {
    var query = new BookSearchContentQuery(null, null, null, "Java", 20);
    when(contentUseCase.searchBookContent(query))
        .thenThrow(new BookSearchException("Content unavailable", new IOException("Disconnected")));

    client
        .get()
        .uri("/api/v1/search/books/content?q=Java")
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .isEmpty();
    verifyNoInteractions(metadataUseCase);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/v1/search/books?bookId=invalid",
        "/api/v1/search/books?catalogId=invalid",
        "/api/v1/search/books?limit=0",
        "/api/v1/search/books?limit=101",
        "/api/v1/search/books?q=%20",
        "/api/v1/search/books?q=%09",
        "/api/v1/search/books?q=%0A",
        "/api/v1/search/books?q=%0B",
        "/api/v1/search/books/content?bookId=invalid",
        "/api/v1/search/books/content?limit=0",
        "/api/v1/search/books/content?limit=101"
      })
  void invalidSearchRequestsReturnBadRequest(String uri) {
    client.get().uri(URI.create(uri)).exchange().expectStatus().isBadRequest();
    verifyNoInteractions(metadataUseCase, contentUseCase);
  }

  @Test
  void singleLetterTextIsAValidSearch() {
    var query = new BookSearchMetadataQuery(null, null, null, "n", 20);
    when(metadataUseCase.searchBookMetadata(query)).thenReturn(List.of());
    client.get().uri("/api/v1/search/books?q=n").exchange().expectStatus().isOk();
    verify(metadataUseCase).searchBookMetadata(query);
  }
}
