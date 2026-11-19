package org.abondar.experimental.bookstore.search.book.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.abondar.experimental.bookstore.search.adapter.out.BaseOpenSearchAdapterTest;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchContentQuery;
import org.abondar.experimental.bookstore.search.book.application.port.in.BookSearchMetadataQuery;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookStorageRepository;
import org.abondar.experimental.bookstore.search.book.application.service.BookContentParser;
import org.abondar.experimental.bookstore.search.book.application.service.BookContentService;
import org.abondar.experimental.bookstore.search.book.domain.BookContentDocument;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookSearchEndpointsIntegrationTest extends BaseOpenSearchAdapterTest {
  private BookOpenSearchAdapter adapter;

  @BeforeEach
  void setUpSearch() {
    indexAdapter.createIndexes();
    adapter = new BookOpenSearchAdapter(properties, client);
  }

  @Test
  void metadataSearchUsesOnlyMetadataAndAppliesItsFilters() throws IOException {
    var catalog = UUID.randomUUID().toString();
    var title =
        new BookMetadataDocument(
            UUID.randomUUID().toString(), "0306406152", "Java guide", null, catalog);
    var description =
        new BookMetadataDocument(
            UUID.randomUUID().toString(), "9780134685991", "Handbook", "Java examples", catalog);
    var other =
        new BookMetadataDocument(
            UUID.randomUUID().toString(), "9780321349606", "Other", null, null);
    for (var book : List.of(title, description, other)) adapter.upsertBookMetadata(book);
    refresh();
    client.indices().delete(request -> request.index(properties.bookContentIndex()));

    var results =
        adapter.searchBookMetadata(new BookSearchMetadataQuery(null, null, catalog, "java", 10));
    assertEquals(Set.of(title, description), Set.copyOf(results));
    assertEquals(
        List.of(title),
        adapter.searchBookMetadata(
            new BookSearchMetadataQuery(title.bookId(), title.isbn(), catalog, "Java", 10)));
    assertTrue(
        adapter
            .searchBookMetadata(new BookSearchMetadataQuery(null, null, null, "missingword", 10))
            .isEmpty());
  }

  @Test
  void contentSearchLoadsFullMetadataByMatchingIdsWithoutSearchingMetadataText()
      throws IOException {
    var book =
        new BookMetadataDocument(
            UUID.randomUUID().toString(),
            "0306406152",
            "Handbook",
            "Description",
            UUID.randomUUID().toString());
    var titleOnly =
        new BookMetadataDocument(UUID.randomUUID().toString(), "9780134685991", "Java", null, null);
    adapter.upsertBookMetadata(book);
    adapter.upsertBookMetadata(titleOnly);
    adapter.upsertBookContent(
        new BookContentDocument(book.bookId(), "pdf", "Book.pdf", "Java examples"));
    adapter.upsertBookContent(
        new BookContentDocument(book.bookId(), "epub", "Book.epub", "Java examples"));
    adapter.upsertBookContent(
        new BookContentDocument(titleOnly.bookId(), "pdf", "Other.pdf", "Kotlin examples"));
    refresh();
    var service =
        new BookContentService(
            adapter, mock(BookStorageRepository.class), mock(BookContentParser.class));

    assertEquals(
        List.of(book),
        service.searchBookContent(new BookSearchContentQuery(null, "pdf", "Book.pdf", "Java", 10)));
    assertEquals(
        List.of(book),
        service.searchBookContent(new BookSearchContentQuery(null, null, null, "Java", 10)));
    assertTrue(
        service
            .searchBookContent(new BookSearchContentQuery(null, "pdf", "book.pdf", "Java", 10))
            .isEmpty());
    assertTrue(
        service
            .searchBookContent(new BookSearchContentQuery(null, "rtf", null, "Java", 10))
            .isEmpty());
  }

  private void refresh() throws IOException {
    client
        .indices()
        .refresh(
            request ->
                request.index(properties.bookMetadataIndex(), properties.bookContentIndex()));
  }
}
