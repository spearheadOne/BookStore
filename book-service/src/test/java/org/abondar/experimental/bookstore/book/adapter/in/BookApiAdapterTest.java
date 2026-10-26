package org.abondar.experimental.bookstore.book.adapter.in;

import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.BookResponse;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.abondar.experimental.bookstore.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.GetBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.RegisterBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookCommand;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookUseCase;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookApiAdapterTest {

  @Mock
  private RegisterBookUseCase registerBookUseCase;

  @Mock
  private GetBookUseCase getBookUseCase;

  @Mock
  private UpdateBookUseCase updateBookUseCase;

  @Mock
  private DeleteBookUseCase deleteBookUseCase;

  private RestTestClient client;

  @BeforeEach
  void setUp() {
    client =
            RestTestClient.bindToController(
                            new BookApiAdapter(
                                    registerBookUseCase, getBookUseCase, updateBookUseCase, deleteBookUseCase))
                    .configureServer(server -> server.setControllerAdvice(new BookApiExceptionHandler()))
                    .build();
  }

  @Test
  void registerBook() {
    BookResponse result =
            client
                    .post()
                    .uri("/api/v1/books")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            """
                        {
                          "isbn": "0306406152",
                          "title": "  The Book  ",
                          "description": "A description",
                          "issueYear": 1980
                        }
                        """)
                    .exchange()
                    .expectStatus()
                    .isCreated()
                    .expectBody(BookResponse.class)
                    .returnResult()
                    .getResponseBody();

    var captor = ArgumentCaptor.forClass(Book.class);
    verify(registerBookUseCase).registerBook(captor.capture());
    var book = captor.getValue();

    assertEquals(new Isbn("0306406152"), book.getIsbn());
    assertEquals(new Text("The Book"), book.getTitle());
    assertEquals(Year.of(1980), book.getIssueYear());
    assertTrue(book.getAuthorIds().isEmpty());
    assertNull(book.getCatalogId());
    assertEquals(book.getCreatedAt(), book.getUpdatedAt());

    assertNotNull(result);
    assertEquals(book.getId().value(), result.getId());
    assertEquals("The Book", result.getTitle());
    assertEquals("A description", result.getDescription());
    assertEquals(Integer.valueOf(1980), result.getIssueYear());
    assertTrue(result.getAuthorIds().isEmpty());
    assertNull(result.getCatalogId());
    assertEquals(
            OffsetDateTime.ofInstant(book.getCreatedAt(), ZoneOffset.UTC), result.getCreatedAt());
    assertEquals(
            OffsetDateTime.ofInstant(book.getUpdatedAt(), ZoneOffset.UTC), result.getUpdatedAt());
  }

  @Test
  void getBook() {
    var book = existingBook();
    when(getBookUseCase.getBook(book.getId())).thenReturn(Optional.of(book));

    BookResponse result =
            client
                    .get()
                    .uri("/api/v1/books/{id}", book.getId().value())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(BookResponse.class)
                    .returnResult()
                    .getResponseBody();

    assertNotNull(result);
    assertEquals(book.getId().value(), result.getId());
    assertEquals(book.getIsbn().value(), result.getIsbn());
    assertEquals(book.getTitle().value(), result.getTitle());
    assertEquals(
            book.getAuthorIds().stream().map(Id::value).toList(), List.copyOf(result.getAuthorIds()));
    assertEquals(book.getCatalogId().value(), result.getCatalogId());
    assertEquals(
            OffsetDateTime.ofInstant(book.getCreatedAt(), ZoneOffset.UTC), result.getCreatedAt());
    assertEquals(
            OffsetDateTime.ofInstant(book.getUpdatedAt(), ZoneOffset.UTC), result.getUpdatedAt());
    verify(getBookUseCase).getBook(book.getId());
  }

  @Test
  void registerBookWithInvalidIsbn() {
    client
            .post()
            .uri("/api/v1/books")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                    """
                        {"isbn":"123","title":"Invalid ISBN"}
                        """)
            .exchange()
            .expectStatus()
            .isBadRequest();

    verify(registerBookUseCase, never()).registerBook(any(Book.class));
  }

  @Test
  void registerBookWithExistingIsbn() {
    doThrow(new BookAlreadyExistsException(new Isbn("0306406152")))
            .when(registerBookUseCase)
            .registerBook(any(Book.class));

    client
            .post()
            .uri("/api/v1/books")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                    """
                        {"isbn":"0306406152","title":"Duplicate"}
                        """)
            .exchange()
            .expectStatus()
            .isEqualTo(409);
  }

  @Test
  void getMissingBook() {
    var id = UUID.randomUUID();
    when(getBookUseCase.getBook(new Id<Book>(id))).thenReturn(Optional.empty());

    client
            .get()
            .uri("/api/v1/books/{id}", id)
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .isEmpty();
  }

  @Test
  void updateBook() {
    var existing = existingBook();
    var newAuthorId = UUID.randomUUID();
    var newCatalogId = UUID.randomUUID();
    var updated =
            Book.restore(
                    existing.getId(),
                    new Isbn("9780306406157"),
                    new Text("Updated Book"),
                    "Updated description",
                    Year.of(1981),
                    List.of(new Id<Author>(newAuthorId)),
                    new Id<Catalog>(newCatalogId),
                    existing.getCreatedAt(),
                    existing.getUpdatedAt().plusSeconds(1));
    when(updateBookUseCase.updateBook(any(UpdateBookCommand.class))).thenReturn(updated);

    BookResponse result =
            client
                    .patch()
                    .uri("/api/v1/books/{id}", existing.getId().value())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            """
                        {
                          "isbn": "9780306406157",
                          "title": "Updated Book",
                          "description": "Updated description",
                          "issueYear": 1981,
                          "authorIds": ["%s"],
                          "catalogId": "%s"
                        }
                                    """
                                    .formatted(newAuthorId, newCatalogId))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(BookResponse.class)
                    .returnResult()
                    .getResponseBody();

    var captor = ArgumentCaptor.forClass(UpdateBookCommand.class);
    verify(updateBookUseCase).updateBook(captor.capture());
    var command = captor.getValue();

    assertEquals(existing.getId(), command.id());
    assertEquals(new Isbn("9780306406157"), command.isbn());
    assertEquals(new Text("Updated Book"), command.title());
    assertEquals(Year.of(1981), command.issueYear());
    assertEquals(List.of(new Id<Author>(newAuthorId)), command.authorIds());
    assertEquals(new Id<Catalog>(newCatalogId), command.catalogId());
    verify(getBookUseCase, never()).getBook(any());

    assertNotNull(result);
    assertEquals(newAuthorId, result.getAuthorIds().iterator().next());
    assertEquals(newCatalogId, result.getCatalogId());
    assertEquals(
            OffsetDateTime.ofInstant(updated.getCreatedAt(), ZoneOffset.UTC), result.getCreatedAt());
    assertEquals(
            OffsetDateTime.ofInstant(updated.getUpdatedAt(), ZoneOffset.UTC), result.getUpdatedAt());
  }

  @Test
  void patchBookAcceptsOnlyChangedFields() {
    var existing = existingBook();
    when(updateBookUseCase.updateBook(any(UpdateBookCommand.class))).thenReturn(existing);

    client
            .patch()
            .uri("/api/v1/books/{id}", existing.getId().value())
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"title\":\"New title\",\"isbn\":null}")
            .exchange()
            .expectStatus()
            .isOk();

    var captor = ArgumentCaptor.forClass(UpdateBookCommand.class);
    verify(updateBookUseCase).updateBook(captor.capture());
    assertEquals(new Text("New title"), captor.getValue().title());
    assertNull(captor.getValue().isbn());
    assertNull(captor.getValue().authorIds());
  }

  @Test
  void updateMissingBook() {
    var id = UUID.randomUUID();
    when(updateBookUseCase.updateBook(any(UpdateBookCommand.class)))
            .thenThrow(new BookNotFoundException(new Id<Book>(id)));

    client
            .patch()
            .uri("/api/v1/books/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                    """
                        {"isbn":"0306406152","title":"Missing Book","authorIds":[]}
                        """)
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .isEmpty();

    verify(getBookUseCase, never()).getBook(any());
  }

  @Test
  void updateBookWithExistingIsbn() {
    var existing = existingBook();
    when(updateBookUseCase.updateBook(any(UpdateBookCommand.class)))
            .thenThrow(new BookAlreadyExistsException(new Isbn("9780306406157")));

    client
            .patch()
            .uri("/api/v1/books/{id}", existing.getId().value())
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                    """
                        {"isbn":"9780306406157","title":"Updated","authorIds":[]}
                        """)
            .exchange()
            .expectStatus()
            .isEqualTo(409);
  }

  @Test
  void deleteBook() {
    var id = UUID.randomUUID();

    client
            .delete()
            .uri("/api/v1/books/{id}", id)
            .exchange()
            .expectStatus()
            .isNoContent()
            .expectBody()
            .isEmpty();

    verify(deleteBookUseCase).deleteBook(new Id<>(id));
  }

  private Book existingBook() {
    return Book.restore(
            new Id<>(UUID.randomUUID()),
            new Isbn("0306406152"),
            new Text("The Book"),
            "A description",
            Year.of(1980),
            List.of(new Id<>(UUID.randomUUID())),
            new Id<>(UUID.randomUUID()),
            Instant.parse("2024-01-02T03:04:05Z"),
            Instant.parse("2024-06-07T08:09:10Z"));
  }
}
