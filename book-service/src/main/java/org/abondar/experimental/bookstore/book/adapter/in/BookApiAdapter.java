package org.abondar.experimental.bookstore.book.adapter.in;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.adapter.in.ResponseMapper;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.api.BookApi;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.BookContentFormat;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.BookResponse;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.RegisterBookRequest;
import org.abondar.experimental.bookstore.adapter.in.generated.bookservice.model.UpdateBookRequest;
import org.abondar.experimental.bookstore.author.domain.Author;
import org.abondar.experimental.bookstore.book.application.port.in.DeleteBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.DownloadBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.GetBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.RegisterBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookCommand;
import org.abondar.experimental.bookstore.book.application.port.in.UpdateBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.UploadBookUseCase;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.book.domain.Isbn;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.common.Text;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BookApiAdapter implements BookApi {

  private final RegisterBookUseCase registerBookUseCase;
  private final GetBookUseCase getBookUseCase;
  private final UpdateBookUseCase updateBookUseCase;
  private final DeleteBookUseCase deleteBookUseCase;
  private final UploadBookUseCase uploadBookUseCase;
  private final DownloadBookUseCase downloadBookUseCase;

  @Override
  public ResponseEntity<Void> deleteBook(UUID id) {
    deleteBookUseCase.deleteBook(new Id<>(id));
    return ResponseEntity.status(204).build();
  }

  @Override
  public ResponseEntity<Resource> downloadBookContent(UUID id, BookContentFormat format) {
    var book = getBookUseCase.getBook(new Id<>(id));
    if (book.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    var content = downloadBookUseCase.downloadBookContent(id, format.getValue());
    return content
        .<ResponseEntity<Resource>>map(
            download ->
                ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(download.contentType()))
                    .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                            .filename(download.originalFilename(), StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(new InputStreamResource(download.stream())))
        .orElseGet(() -> ResponseEntity.notFound().build());
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

  @Override
  public ResponseEntity<Void> uploadBookContent(
      UUID id, MultipartFile file, BookContentFormat format) {
    var book = getBookUseCase.getBook(new Id<>(id));
    if (book.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    try (var content = file.getInputStream()) {
      uploadBookUseCase.uploadBookContent(
          id, content, format.getValue(), file.getOriginalFilename(), file.getSize());
      return ResponseEntity.noContent().build();
    } catch (IOException e) {
      log.debug(
          "Could not read the uploaded file for book {} ({})", id, e.getClass().getSimpleName());
      return ResponseEntity.badRequest().build();
    }
  }
}
