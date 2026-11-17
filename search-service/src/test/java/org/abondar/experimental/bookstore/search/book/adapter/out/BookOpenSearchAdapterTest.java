package org.abondar.experimental.bookstore.search.book.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.UUID;
import org.abondar.experimental.bookstore.search.adapter.out.BaseOpenSearchAdapterTest;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;
import org.abondar.experimental.bookstore.search.config.OpenSearchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;

class BookOpenSearchAdapterTest extends BaseOpenSearchAdapterTest {

  private BookOpenSearchAdapter adapter;

  @BeforeEach
  void setUpBookAdapter() {
    adapter = new BookOpenSearchAdapter(properties, client);
  }

  @Test
  void missingIndexIsRejectedWithoutCreatingDynamicMappings() throws IOException {
    var book = metadata("Description", null);

    var failure = assertThrows(OpenSearchException.class, () -> adapter.upsertBookMetadata(book));

    assertEquals(404, failure.status());
    assertFalse(
        client.indices().exists(request -> request.index(properties.bookMetadataIndex())).value());
  }

  @Test
  void savesAndFindsBookMetadata() throws IOException {
    indexAdapter.createIndexes();
    var book = metadata("Description", UUID.randomUUID().toString());

    adapter.upsertBookMetadata(book);

    assertEquals(book, findMetadata(book.bookId()));
  }

  @Test
  void replacesExistingMetadataAndClearsOptionalFields() throws IOException {
    indexAdapter.createIndexes();
    var original = metadata("Original description", UUID.randomUUID().toString());
    adapter.upsertBookMetadata(original);
    var updated = new BookMetadata(original.bookId(), "9780134685991", "Updated title", null, null);

    adapter.upsertBookMetadata(updated);

    assertEquals(updated, findMetadata(original.bookId()));
  }

  @Test
  void duplicateUpsertKeepsOneDocument() throws IOException {
    indexAdapter.createIndexes();
    var book = metadata(null, null);

    adapter.upsertBookMetadata(book);
    adapter.upsertBookMetadata(book);

    client.indices().refresh(request -> request.index(properties.bookMetadataIndex()));
    assertEquals(
        1L, client.count(request -> request.index(properties.bookMetadataIndex())).count());
    assertEquals(book, findMetadata(book.bookId()));
  }

  @Test
  void deletesBookMetadataAndAllowsRepeatedDeletion() throws IOException {
    indexAdapter.createIndexes();
    var book = metadata(null, null);
    var other = metadata("Keep this book", null);
    adapter.upsertBookMetadata(book);
    adapter.upsertBookMetadata(other);

    adapter.deleteBook(book.bookId());
    adapter.deleteBook(book.bookId());

    assertFalse(
        client
            .get(
                request -> request.index(properties.bookMetadataIndex()).id(book.bookId()),
                BookMetadata.class)
            .found());
    assertEquals(other, findMetadata(other.bookId()));
  }

  @Test
  void missingIndexDeletionRemainsAnError() {
    assertThrows(OpenSearchException.class, () -> adapter.deleteBook(UUID.randomUUID().toString()));
  }

  @Test
  void unlinkCatalogClearsOnlyMatchingAssociationsAndPreservesDocuments() throws IOException {
    indexAdapter.createIndexes();
    var catalogId = UUID.randomUUID().toString();
    var first = metadata("First description", catalogId);
    var second = metadata("Second description", catalogId);
    var other = metadata("Other catalog", UUID.randomUUID().toString());
    var unassigned = metadata("No catalog", null);
    for (var book : java.util.List.of(first, second, other, unassigned)) {
      adapter.upsertBookMetadata(book);
    }
    var contentId = first.bookId() + ":pdf";
    client.index(
        request ->
            request
                .index(properties.bookContentIndex())
                .id(contentId)
                .document(
                    java.util.Map.of(
                        "bookId", first.bookId(), "format", "pdf", "content", "Keep content")));
    client.indices().refresh(request -> request.index(properties.bookMetadataIndex()));

    adapter.unlinkCatalogId(catalogId);
    adapter.unlinkCatalogId(catalogId);

    for (var book : java.util.List.of(first, second)) {
      assertEquals(
          new BookMetadata(book.bookId(), book.isbn(), book.title(), book.description(), null),
          findMetadata(book.bookId()));
    }
    assertEquals(other, findMetadata(other.bookId()));
    assertEquals(unassigned, findMetadata(unassigned.bookId()));
    assertTrue(
        client
            .get(
                request -> request.index(properties.bookContentIndex()).id(contentId),
                java.util.Map.class)
            .found());
  }

  @Test
  void unlinkUnknownCatalogPreservesAllMetadata() throws IOException {
    indexAdapter.createIndexes();
    var book = metadata("Description", UUID.randomUUID().toString());
    adapter.upsertBookMetadata(book);
    client.indices().refresh(request -> request.index(properties.bookMetadataIndex()));

    adapter.unlinkCatalogId(UUID.randomUUID().toString());

    assertEquals(book, findMetadata(book.bookId()));
  }

  @Test
  void unlinkSeesImmediatelyPrecedingMetadataUpsert() throws IOException {
    indexAdapter.createIndexes();
    var catalogId = UUID.randomUUID().toString();
    var book = metadata("Description", catalogId);
    adapter.upsertBookMetadata(book);

    adapter.unlinkCatalogId(catalogId);

    assertEquals(
        new BookMetadata(book.bookId(), book.isbn(), book.title(), book.description(), null),
        findMetadata(book.bookId()));
  }

  @Test
  void metadataConnectionFailuresKeepTheirCauses() throws IOException {
    try (var server = new ServerSocket(0);
        var unavailableTransport =
            new OpenSearchConfig()
                .openSearchTransport(
                    properties("http://localhost:" + server.getLocalPort(), "unavailable"))) {
      var unavailable =
          new BookOpenSearchAdapter(properties, new OpenSearchClient(unavailableTransport));
      var book = metadata(null, null);

      var upsert =
          assertThrows(UncheckedIOException.class, () -> unavailable.upsertBookMetadata(book));
      var deletion =
          assertThrows(UncheckedIOException.class, () -> unavailable.deleteBook(book.bookId()));

      var unlink =
          assertThrows(UncheckedIOException.class, () -> unavailable.unlinkCatalogId("catalog-id"));
      assertNotNull(unlink.getCause());
      assertTrue(unlink.getMessage().contains("update documents"));
      assertNotNull(upsert.getCause());
      assertNotNull(deletion.getCause());
      assertTrue(upsert.getMessage().contains(book.bookId()));
      assertTrue(deletion.getMessage().contains(book.bookId()));
    }
  }

  private BookMetadata findMetadata(String bookId) throws IOException {
    var result =
        client.get(
            request -> request.index(properties.bookMetadataIndex()).id(bookId),
            BookMetadata.class);
    assertTrue(result.found());
    return result.source();
  }

  private BookMetadata metadata(String description, String catalogId) {
    return new BookMetadata(
        UUID.randomUUID().toString(), "0306406152", "Book", description, catalogId);
  }
}
