package org.abondar.experimental.bookstore.book.adapter.in;

import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.api.BookApi;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.BookResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.RegisterBookRequest;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.UpdateBookRequest;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.GetBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.RegisterBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookUseCase;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
        return ResponseEntity
                .status(204)
                .build();
    }

    @Override
    public ResponseEntity<BookResponse> getBook(UUID id) {
        var book = getBookUseCase.getBook(new Id<>(id));
        return book.map(value -> ResponseEntity
                .status(200)
                .body(mapToResponse(value))).orElseGet(() -> ResponseEntity
                .status(404)
                .build());

    }

    @Override
    public ResponseEntity<BookResponse> registerBook(RegisterBookRequest registerBookRequest) {
        var book = Book.create(
                new Isbn(registerBookRequest.getIsbn()),
                new Text(registerBookRequest.getTitle()),
                registerBookRequest.getDescription(),
                registerBookRequest.getIssueYear() != null ? Year.of(registerBookRequest.getIssueYear()) : null,
                List.of(),
                null
        );

        registerBookUseCase.registerBook(book);

        return ResponseEntity.status(201).body(mapToResponse(book));
    }

    @Override
    public ResponseEntity<BookResponse> updateBook(UUID id, UpdateBookRequest updateBookRequest) {
        var bookId = new Id<Book>(id);
        var existing = getBookUseCase.getBook(bookId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var existingBook = existing.get();
        var now = Instant.now();
        var book = Book.restore(
                bookId,
                new Isbn(updateBookRequest.getIsbn()),
                new Text(updateBookRequest.getTitle()),
                updateBookRequest.getDescription(),
                updateBookRequest.getIssueYear() != null ? Year.of(updateBookRequest.getIssueYear()) : null,
                updateBookRequest.getAuthorIds().stream()
                        .map(authorId -> new Id<Author>(authorId))
                        .toList(),
                updateBookRequest.getCatalogId() != null ? new Id<>(updateBookRequest.getCatalogId()) : null,
                existingBook.getCreatedAt(),
                now.isAfter(existingBook.getUpdatedAt()) ? now : existingBook.getUpdatedAt()
        );

        updateBookUseCase.updateBook(book);

        return ResponseEntity.ok(mapToResponse(book));
    }

    private BookResponse mapToResponse(Book book) {
        var response = new BookResponse();
        response.setId(book.getId().value());
        response.setTitle(book.getTitle().value());
        response.setIsbn(book.getIsbn().value());
        response.setDescription(book.getDescription());
        response.setAuthorIds(book.getAuthorIds()
                .stream()
                .map(Id::value)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        response.setCatalogId(book.getCatalogId() == null ? null : book.getCatalogId().value());
        response.setIssueYear(book.getIssueYear() == null ? null : book.getIssueYear().getValue());
        response.setCreatedAt(OffsetDateTime.ofInstant(book.getCreatedAt(), ZoneOffset.UTC));
        response.setUpdatedAt(OffsetDateTime.ofInstant(book.getUpdatedAt(), ZoneOffset.UTC));


        return response;
    }


}
