package org.abondar.experimental.bookstore.search.book.adapter.in.api;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.abondar.experimental.bookstore.adapter.in.generated.searchservice.api.BookSearchApi;
import org.abondar.experimental.bookstore.adapter.in.generated.searchservice.model.BookContentFormat;
import org.abondar.experimental.bookstore.adapter.in.generated.searchservice.model.BookSearchResult;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchContentQuery;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchMetadataQuery;
import org.abondar.experimental.bookstore.search.book.application.port.in.SearchBookContentUseCase;
import org.abondar.experimental.bookstore.search.book.application.port.in.SearchBookMetadataUseCase;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookSearchApiAdapter implements BookSearchApi {

  private final SearchBookMetadataUseCase searchBookMetadataUseCase;
  private final SearchBookContentUseCase searchBookContentUseCase;

  @Override
  public ResponseEntity<List<BookSearchResult>> searchBookMetadata(
      @Nullable UUID bookId,
      @Nullable String isbn,
      @Nullable UUID catalogId,
      @Nullable String q,
      Integer limit) {
    if ((isbn != null && isbn.isBlank()) || (q != null && q.isBlank())) {
      throw new IllegalArgumentException("Search values must not be blank");
    }
    var query =
        new BookSearchMetadataQuery(
            bookId == null ? null : bookId.toString(),
            isbn,
            catalogId == null ? null : catalogId.toString(),
            q,
            limit);
    return ResponseEntity.ok(toResult(searchBookMetadataUseCase.searchBookMetadata(query)));
  }

  @Override
  public ResponseEntity<List<BookSearchResult>> searchBookContent(
      @Nullable UUID bookId,
      @Nullable BookContentFormat format,
      @Nullable String originalFilename,
      @Nullable String q,
      Integer limit) {
    if ((originalFilename != null && originalFilename.isBlank()) || (q != null && q.isBlank())) {
      throw new IllegalArgumentException("Search values must not be blank");
    }
    var query =
        new BookSearchContentQuery(
            bookId == null ? null : bookId.toString(),
            format == null ? null : format.getValue(),
            originalFilename,
            q,
            limit);
    return ResponseEntity.ok(toResult(searchBookContentUseCase.searchBookContent(query)));
  }

  private List<BookSearchResult> toResult(List<BookMetadataDocument> booksDocs) {
    return booksDocs.stream()
        .map(
            bd -> {
              var br = new BookSearchResult(UUID.fromString(bd.bookId()), bd.isbn(), bd.title());

              if (bd.catalogId() != null) {
                br.setCatalogId(UUID.fromString(bd.catalogId()));
              }

              if (bd.description() != null) {
                br.setDescription(bd.description());
              }

              return br;
            })
        .toList();
  }
}
