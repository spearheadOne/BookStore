package org.abondar.experimental.bookstore.book.adapter.in;

import java.time.Year;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.in.ResponseMapper;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.api.BookApi;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.BookResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.RegisterBookRequest;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.UpdateBookRequest;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.GetBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.RegisterBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookCommand;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookUseCase;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookApiAdapter implements BookApi {

  private final RegisterBookUseCase registerBookUseCase;
  private final GetBookUseCase getBookUseCase;
  private final UpdateBookUseCase updateBookUseCase;
  private final DeleteBookUseCase deleteBookUseCase;

  @Override
  public ResponseEntity<Void> deleteBook(UUID id) {
    deleteBookUseCase.deleteBook(new Id<>(id));
    return ResponseEntity.status(204).build();
  }

  @Override
  public ResponseEntity<BookResponse> getBook(UUID id) {
    var book = getBookUseCase.getBook(new Id<>(id));
    return book.map(value -> ResponseEntity.status(200).body(ResponseMapper.toBookResponse(value)))
        .orElseGet(() -> ResponseEntity.status(404).build());
  }

  @Override
  public ResponseEntity<BookResponse> registerBook(RegisterBookRequest registerBookRequest) {
    var book =
        Book.create(
            new Isbn(registerBookRequest.getIsbn()),
            new Text(registerBookRequest.getTitle()),
            registerBookRequest.getDescription(),
            registerBookRequest.getIssueYear() != null
                ? Year.of(registerBookRequest.getIssueYear())
                : null,
            List.of(),
            null);

    registerBookUseCase.registerBook(book);

    return ResponseEntity.status(201).body(ResponseMapper.toBookResponse(book));
  }

  @Override
  public ResponseEntity<BookResponse> updateBook(UUID id, UpdateBookRequest updateBookRequest) {
    var command =
        new UpdateBookCommand(
            new Id<>(id),
            updateBookRequest.getIsbn() != null ? new Isbn(updateBookRequest.getIsbn()) : null,
            updateBookRequest.getTitle() != null ? new Text(updateBookRequest.getTitle()) : null,
            updateBookRequest.getDescription(),
            updateBookRequest.getIssueYear() != null
                ? Year.of(updateBookRequest.getIssueYear())
                : null,
            updateBookRequest.getAuthorIds() != null
                ? updateBookRequest.getAuthorIds().stream()
                    .map(authorId -> new Id<Author>(authorId))
                    .toList()
                : null,
            updateBookRequest.getCatalogId() != null
                ? new Id<>(updateBookRequest.getCatalogId())
                : null);

    var updated = updateBookUseCase.updateBook(command);

    return ResponseEntity.ok(ResponseMapper.toBookResponse(updated));
  }
}
