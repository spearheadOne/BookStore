package org.abondar.experimental.bookstore.author.adapter.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.AuthorResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.AuthorWithBooksResponse;
import org.abondar.experimental.bookstore.author.application.exception.AuthorAlreadyExistsException;
import org.abondar.experimental.bookstore.author.application.exception.AuthorNotFoundException;
import org.abondar.experimental.bookstore.author.application.port.in.AddBooksToAuthorCommand;
import org.abondar.experimental.bookstore.author.application.port.in.AddBooksToAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.DeleteAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.GetAuthorByIdUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.GetAuthorWithBooksByIdUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.GetAuthorsBySortNameUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.SaveAuthorUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.SaveAuthorWithBooksUseCase;
import org.abondar.experimental.bookstore.author.application.port.in.UpdateAuthorCommand;
import org.abondar.experimental.bookstore.author.application.port.in.UpdateAuthorUseCase;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.author.domain.AuthorWithBooks;
import org.abondar.experimental.bookstore.book.application.exception.BookAlreadyExistsException;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@ExtendWith(MockitoExtension.class)
class AuthorApiAdapterTest {

  @Mock private SaveAuthorUseCase saveAuthorUseCase;
  @Mock private AddBooksToAuthorUseCase addBooksToAuthorUseCase;
  @Mock private SaveAuthorWithBooksUseCase saveAuthorWithBooksUseCase;
  @Mock private UpdateAuthorUseCase updateAuthorUseCase;
  @Mock private GetAuthorByIdUseCase getAuthorByIdUseCase;
  @Mock private GetAuthorWithBooksByIdUseCase getAuthorWithBooksByIdUseCase;
  @Mock private GetAuthorsBySortNameUseCase getAuthorsBySortNameUseCase;
  @Mock private DeleteAuthorUseCase deleteAuthorUseCase;

  private RestTestClient client;

  @BeforeEach
  void setUp() {
    client =
        RestTestClient.bindToController(
                new AuthorApiAdapter(
                    saveAuthorUseCase,
                    addBooksToAuthorUseCase,
                    saveAuthorWithBooksUseCase,
                    updateAuthorUseCase,
                    getAuthorByIdUseCase,
                    getAuthorWithBooksByIdUseCase,
                    getAuthorsBySortNameUseCase,
                    deleteAuthorUseCase))
            .configureServer(server -> server.setControllerAdvice(new AuthorApiExceptionHandler()))
            .build();
  }

  @Test
  void registerAuthor() {
    var response =
        client
            .post()
            .uri("/api/v1/authors")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                                        {"firstName":"Ada","lastName":"Lovelace","sortName":"Lovelace, Ada",
                                         "dateOfBirth":"1815-12-10","countryCode":"GB"}
                                        """)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(AuthorResponse.class)
            .returnResult()
            .getResponseBody();

    var captor = ArgumentCaptor.forClass(Author.class);
    verify(saveAuthorUseCase).saveAuthor(captor.capture());
    var author = captor.getValue();
    assertEquals(new Text("Ada"), author.getFirstName());
    assertEquals(LocalDate.of(1815, 12, 10), author.getDateOfBirth());
    assertNotNull(response);
    assertEquals(author.getId().value(), response.getId());
    assertEquals("GB", response.getCountryCode());
    assertNotNull(response.getCreatedAt());
  }

  @Test
  void registerAuthorRejectsDuplicate() {
    doThrow(new AuthorAlreadyExistsException())
        .when(saveAuthorUseCase)
        .saveAuthor(any(Author.class));

    client
        .post()
        .uri("/api/v1/authors")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}")
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void registerAuthorRejectsInvalidCountry() {
    client
        .post()
        .uri("/api/v1/authors")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"countryCode\":\"GBR\"}")
        .exchange()
        .expectStatus()
        .isBadRequest();

    verify(saveAuthorUseCase, never()).saveAuthor(any(Author.class));
  }

  @Test
  void getAuthorById() {
    var author = author();
    when(getAuthorByIdUseCase.getAuthorById(author.getId())).thenReturn(Optional.of(author));

    var response =
        client
            .get()
            .uri("/api/v1/authors/{id}", author.getId().value())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AuthorResponse.class)
            .returnResult()
            .getResponseBody();

    assertNotNull(response);
    assertEquals(author.getId().value(), response.getId());
    assertEquals(author.getCountryCode(), response.getCountryCode());
  }

  @Test
  void getMissingAuthor() {
    var id = UUID.randomUUID();
    when(getAuthorByIdUseCase.getAuthorById(new Id<Author>(id))).thenReturn(Optional.empty());

    client.get().uri("/api/v1/authors/{id}", id).exchange().expectStatus().isNotFound();
  }

  @Test
  void getAuthorWithBooks() {
    var author = author();
    var book =
        Book.create(
            new Isbn("0306406152"), new Text("Book"), null, null, List.of(author.getId()), null);
    when(getAuthorWithBooksByIdUseCase.getAuthorWithBooksById(author.getId()))
        .thenReturn(Optional.of(new AuthorWithBooks(author, List.of(book))));

    var response =
        client
            .get()
            .uri("/api/v1/authors/{id}/books", author.getId().value())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AuthorWithBooksResponse.class)
            .returnResult()
            .getResponseBody();

    assertNotNull(response);
    assertEquals(author.getId().value(), response.getAuthor().getId());
    assertEquals(book.getId().value(), response.getBooks().getFirst().getId());
    assertNull(response.getBooks().getFirst().getCatalogId());
  }

  @Test
  void getMissingAuthorWithBooks() {
    var id = UUID.randomUUID();
    when(getAuthorWithBooksByIdUseCase.getAuthorWithBooksById(new Id<Author>(id)))
        .thenReturn(Optional.empty());

    client.get().uri("/api/v1/authors/{id}/books", id).exchange().expectStatus().isNotFound();
  }

  @Test
  void findAuthorsBySortNameReturnsEmptyArray() {
    when(getAuthorsBySortNameUseCase.getBySortName("Nobody")).thenReturn(List.of());

    client
        .get()
        .uri(uri -> uri.path("/api/v1/authors").queryParam("sortName", "Nobody").build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
  }

  @Test
  void findAuthorsBySortName() {
    var author = author();
    when(getAuthorsBySortNameUseCase.getBySortName("Lovelace, Ada")).thenReturn(List.of(author));

    client
        .get()
        .uri(uri -> uri.path("/api/v1/authors").queryParam("sortName", "Lovelace, Ada").build())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].id")
        .isEqualTo(author.getId().value().toString());
  }

  @Test
  void registerAuthorWithBooks() {
    var existingBookId = UUID.randomUUID();
    var otherAuthorId = UUID.randomUUID();
    var catalogId = UUID.randomUUID();
    when(saveAuthorWithBooksUseCase.saveAuthorWithBooks(any(Author.class), anyList()))
        .thenAnswer(
            invocation -> {
              Author author = invocation.getArgument(0);
              List<Book> books = invocation.getArgument(1);
              return new AuthorWithBooks(
                  author, books.stream().map(book -> book.withAuthor(author.getId())).toList());
            });

    var response =
        client
            .post()
            .uri("/api/v1/authors/with-books")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                                        {"author":{"firstName":"Ada","lastName":"Lovelace"},
                                         "books":[{"id":"%s","isbn":"0306406152","title":"Existing book",
                                         "authorIds":["%s"],"catalogId":"%s"}]}
                                        """
                    .formatted(existingBookId, otherAuthorId, catalogId))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(AuthorWithBooksResponse.class)
            .returnResult()
            .getResponseBody();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Book>> booksCaptor = ArgumentCaptor.forClass(List.class);
    verify(saveAuthorWithBooksUseCase)
        .saveAuthorWithBooks(any(Author.class), booksCaptor.capture());
    var suppliedBook = booksCaptor.getValue().getFirst();
    assertEquals(existingBookId, suppliedBook.getId().value());
    assertEquals(List.of(new Id<Author>(otherAuthorId)), suppliedBook.getAuthorIds());
    assertEquals(catalogId, suppliedBook.getCatalogId().value());
    assertNotNull(response);
    assertEquals(existingBookId, response.getBooks().getFirst().getId());
    assertEquals(2, response.getBooks().getFirst().getAuthorIds().size());
  }

  @Test
  void addExistingBooksToAuthor() {
    var author = author();
    var existingBookId = UUID.randomUUID();
    when(addBooksToAuthorUseCase.addBooksToAuthor(any(AddBooksToAuthorCommand.class)))
        .thenReturn(new AuthorWithBooks(author, List.of()));

    var response =
        client
            .post()
            .uri("/api/v1/authors/{id}/books", author.getId().value())
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {"bookIds":["%s"]}
                """
                    .formatted(existingBookId))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AuthorWithBooksResponse.class)
            .returnResult()
            .getResponseBody();

    var captor = ArgumentCaptor.forClass(AddBooksToAuthorCommand.class);
    verify(addBooksToAuthorUseCase).addBooksToAuthor(captor.capture());
    var command = captor.getValue();
    assertEquals(author.getId(), command.authorId());
    assertEquals(List.of(new Id<Book>(existingBookId)), command.bookIds());
    assertNotNull(response);
    assertEquals(author.getId().value(), response.getAuthor().getId());
  }

  @Test
  void addBooksToAuthorRejectsEmptyRequest() {
    client
        .post()
        .uri("/api/v1/authors/{id}/books", UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{}")
        .exchange()
        .expectStatus()
        .isBadRequest();

    verify(addBooksToAuthorUseCase, never()).addBooksToAuthor(any());
  }

  @Test
  void addBooksToAuthorReturnsNotFoundForMissingBook() {
    var bookId = UUID.randomUUID();
    when(addBooksToAuthorUseCase.addBooksToAuthor(any(AddBooksToAuthorCommand.class)))
        .thenThrow(new BookNotFoundException(new Id<Book>(bookId)));

    client
        .post()
        .uri("/api/v1/authors/{id}/books", UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"bookIds\":[\"" + bookId + "\"]}")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void registerAuthorWithBooksRejectsEmptyBooks() {
    client
        .post()
        .uri("/api/v1/authors/with-books")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"author\":{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"},\"books\":[]}")
        .exchange()
        .expectStatus()
        .isBadRequest();

    verify(saveAuthorWithBooksUseCase, never()).saveAuthorWithBooks(any(), anyList());
  }

  @Test
  void registerAuthorWithBooksMapsBookConflict() {
    when(saveAuthorWithBooksUseCase.saveAuthorWithBooks(any(), anyList()))
        .thenThrow(new BookAlreadyExistsException(new Isbn("0306406152")));

    client
        .post()
        .uri("/api/v1/authors/with-books")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
                                {"author":{"firstName":"Ada","lastName":"Lovelace"},
                                 "books":[{"isbn":"0306406152","title":"Book"}]}
                                """)
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void registerAuthorWithBooksMapsDatabaseConflict() {
    when(saveAuthorWithBooksUseCase.saveAuthorWithBooks(any(), anyList()))
        .thenThrow(new DuplicateKeyException("Duplicate book ISBN"));

    client
        .post()
        .uri("/api/v1/authors/with-books")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
                                {"author":{"firstName":"Ada","lastName":"Lovelace"},
                                 "books":[{"isbn":"0306406152","title":"Book"}]}
                                """)
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void patchAuthorUsesPartialCommand() {
    var author = author();
    var updated =
        Author.restore(
            author.getId(),
            new Text("Augusta"),
            author.getLastName(),
            author.getSortName(),
            author.getDateOfBirth(),
            author.getDateOfDeath(),
            author.getCountryCode(),
            author.getBiography(),
            author.getCreatedAt(),
            author.getUpdatedAt());
    when(updateAuthorUseCase.updateAuthor(any(UpdateAuthorCommand.class))).thenReturn(updated);

    var response =
        client
            .patch()
            .uri("/api/v1/authors/{id}", author.getId().value())
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"firstName\":\"Augusta\",\"lastName\":null}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(AuthorResponse.class)
            .returnResult()
            .getResponseBody();

    var captor = ArgumentCaptor.forClass(UpdateAuthorCommand.class);
    verify(updateAuthorUseCase).updateAuthor(captor.capture());
    assertEquals(author.getId(), captor.getValue().id());
    assertEquals(new Text("Augusta"), captor.getValue().firstName());
    assertNull(captor.getValue().lastName());
    assertNotNull(response);
    assertEquals("Augusta", response.getFirstName());
  }

  @Test
  void patchMissingAuthor() {
    var id = UUID.randomUUID();
    when(updateAuthorUseCase.updateAuthor(any(UpdateAuthorCommand.class)))
        .thenThrow(new AuthorNotFoundException(new Id<Author>(id)));

    client
        .patch()
        .uri("/api/v1/authors/{id}", id)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"firstName\":\"Augusta\"}")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void deleteAuthor() {
    var id = UUID.randomUUID();
    client.delete().uri("/api/v1/authors/{id}", id).exchange().expectStatus().isNoContent();

    verify(deleteAuthorUseCase).deleteAuthor(new Id<Author>(id));
  }

  private Author author() {
    return Author.create(
        new Text("Ada"),
        new Text("Lovelace"),
        "Lovelace, Ada",
        LocalDate.of(1815, 12, 10),
        null,
        "GB",
        null);
  }
}
