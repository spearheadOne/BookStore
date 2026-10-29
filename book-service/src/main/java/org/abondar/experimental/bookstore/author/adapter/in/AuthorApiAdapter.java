package org.abondar.experimental.bookstore.author.adapter.in;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.in.ResponseMapper;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.api.AuthorApi;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.AddBooksToAuthorRequest;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.AuthorResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.AuthorWithBooksResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.BookInAuthorRegistration;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.RegisterAuthorRequest;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.RegisterAuthorWithBooksRequest;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.UpdateAuthorRequest;
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
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthorApiAdapter implements AuthorApi {

  private final SaveAuthorUseCase saveAuthorUseCase;
  private final AddBooksToAuthorUseCase addBooksToAuthorUseCase;
  private final SaveAuthorWithBooksUseCase saveAuthorWithBooksUseCase;
  private final UpdateAuthorUseCase updateAuthorUseCase;
  private final GetAuthorByIdUseCase getAuthorByIdUseCase;
  private final GetAuthorWithBooksByIdUseCase getAuthorWithBooksByIdUseCase;
  private final GetAuthorsBySortNameUseCase getAuthorsBySortNameUseCase;
  private final DeleteAuthorUseCase deleteAuthorUseCase;

  @Override
  public ResponseEntity<Void> deleteAuthor(UUID id) {
    deleteAuthorUseCase.deleteAuthor(new Id<>(id));
    return ResponseEntity.status(204).build();
  }

  @Override
  public ResponseEntity<AuthorResponse> getAuthorById(UUID id) {
    var res = getAuthorByIdUseCase.getAuthorById(new Id<>(id));
    return res.map(author -> ResponseEntity.ok(ResponseMapper.toAuthorResponse(author)))
        .orElseGet(() -> ResponseEntity.status(404).build());
  }

  @Override
  public ResponseEntity<AuthorWithBooksResponse> getAuthorWithBooksById(UUID id) {
    var res = getAuthorWithBooksByIdUseCase.getAuthorWithBooksById(new Id<>(id));
    return res.map(
            authorWithBooks ->
                ResponseEntity.ok(ResponseMapper.toAuthorWithBooksResponse(authorWithBooks)))
        .orElseGet(() -> ResponseEntity.status(404).build());
  }

  @Override
  public ResponseEntity<AuthorWithBooksResponse> addBooksToAuthor(
      UUID id, AddBooksToAuthorRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Books request is required");
    }
    var bookIds =
        request.getBookIds() == null
            ? List.<Id<Book>>of()
            : request.getBookIds().stream().map(bookId -> new Id<Book>(bookId)).toList();
    if (bookIds.isEmpty()) {
      throw new IllegalArgumentException("At least one book is required");
    }

    var result =
        addBooksToAuthorUseCase.addBooksToAuthor(
            new AddBooksToAuthorCommand(new Id<>(id), bookIds));
    return ResponseEntity.ok(ResponseMapper.toAuthorWithBooksResponse(result));
  }

  @Override
  public ResponseEntity<List<AuthorResponse>> getAuthorsBySortName(String sortName) {
    var res = getAuthorsBySortNameUseCase.getBySortName(sortName);
    return ResponseEntity.ok(res.stream().map(ResponseMapper::toAuthorResponse).toList());
  }

  @Override
  public ResponseEntity<AuthorResponse> registerAuthor(
      RegisterAuthorRequest registerAuthorRequest) {
    var author =
        Author.create(
            new Text(registerAuthorRequest.getFirstName()),
            new Text(registerAuthorRequest.getLastName()),
            registerAuthorRequest.getSortName(),
            registerAuthorRequest.getDateOfBirth(),
            registerAuthorRequest.getDateOfDeath(),
            registerAuthorRequest.getCountryCode(),
            registerAuthorRequest.getBiography());

    saveAuthorUseCase.saveAuthor(author);

    return ResponseEntity.status(201).body(ResponseMapper.toAuthorResponse(author));
  }

  @Override
  public ResponseEntity<AuthorWithBooksResponse> registerAuthorWithBooks(
      RegisterAuthorWithBooksRequest registerAuthorWithBooksRequest) {
    if (registerAuthorWithBooksRequest.getBooks() == null
        || registerAuthorWithBooksRequest.getBooks().isEmpty()) {
      throw new IllegalArgumentException("At least one book is required");
    }
    var authorRequest = registerAuthorWithBooksRequest.getAuthor();
    if (authorRequest == null) {
      throw new IllegalArgumentException("Author is required");
    }
    var author =
        Author.create(
            new Text(authorRequest.getFirstName()),
            new Text(authorRequest.getLastName()),
            authorRequest.getSortName(),
            authorRequest.getDateOfBirth(),
            authorRequest.getDateOfDeath(),
            authorRequest.getCountryCode(),
            authorRequest.getBiography());

    var books = registerAuthorWithBooksRequest.getBooks().stream().map(this::toBook).toList();

    var saved = saveAuthorWithBooksUseCase.saveAuthorWithBooks(author, books);

    return ResponseEntity.status(201).body(ResponseMapper.toAuthorWithBooksResponse(saved));
  }

  @Override
  public ResponseEntity<AuthorResponse> updateAuthor(
      UUID id, UpdateAuthorRequest updateAuthorRequest) {
    var command =
        new UpdateAuthorCommand(
            new Id<>(id),
            updateAuthorRequest.getFirstName() != null
                ? new Text(updateAuthorRequest.getFirstName())
                : null,
            updateAuthorRequest.getLastName() != null
                ? new Text(updateAuthorRequest.getLastName())
                : null,
            updateAuthorRequest.getSortName(),
            updateAuthorRequest.getDateOfBirth(),
            updateAuthorRequest.getDateOfDeath(),
            updateAuthorRequest.getCountryCode(),
            updateAuthorRequest.getBiography());

    var updated = updateAuthorUseCase.updateAuthor(command);

    return ResponseEntity.ok(ResponseMapper.toAuthorResponse(updated));
  }

  private Book toBook(BookInAuthorRegistration request) {
    if (request == null) {
      throw new IllegalArgumentException("Book is required");
    }
    var isbn = new Isbn(request.getIsbn());
    var title = new Text(request.getTitle());
    var issueYear = request.getIssueYear() == null ? null : Year.of(request.getIssueYear());
    var authorIds =
        request.getAuthorIds() == null
            ? List.<Id<Author>>of()
            : request.getAuthorIds().stream().map(authorId -> new Id<Author>(authorId)).toList();
    Id<Catalog> catalogId =
        request.getCatalogId() == null ? null : new Id<>(request.getCatalogId());

    if (request.getId() == null) {
      return Book.create(isbn, title, request.getDescription(), issueYear, authorIds, catalogId);
    }

    var now = Instant.now();
    return Book.restore(
        new Id<>(request.getId()),
        isbn,
        title,
        request.getDescription(),
        issueYear,
        authorIds,
        catalogId,
        now,
        now);
  }
}
