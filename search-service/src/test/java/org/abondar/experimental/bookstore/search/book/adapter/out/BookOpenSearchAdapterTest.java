package org.abondar.experimental.bookstore.search.book.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;
import org.abondar.experimental.bookstore.search.adapter.out.BaseOpenSearchAdapterTest;
import org.abondar.experimental.bookstore.search.book.application.exception.BookMetadataEventException;
import org.abondar.experimental.bookstore.search.book.domain.BookContentDocument;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadataDocument;
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

    var failure =
        assertThrows(BookMetadataEventException.class, () -> adapter.upsertBookMetadata(book));

    assertEquals(404, ((OpenSearchException) failure.getCause()).status());
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
    var updated =
        new BookMetadataDocument(original.bookId(), "9780134685991", "Updated title", null, null);

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
                BookMetadataDocument.class)
            .found());
    assertEquals(other, findMetadata(other.bookId()));
  }

  @Test
  void missingIndexDeletionRemainsAnError() {
    assertThrows(
        BookMetadataEventException.class, () -> adapter.deleteBook(UUID.randomUUID().toString()));
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
          new BookMetadataDocument(
              book.bookId(), book.isbn(), book.title(), book.description(), null),
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
        new BookMetadataDocument(
            book.bookId(), book.isbn(), book.title(), book.description(), null),
        findMetadata(book.bookId()));
  }

  @Test
  void differentFormatsHaveIndependentContentDocuments() throws IOException {
    indexAdapter.createIndexes();
    var id = UUID.randomUUID().toString();
    var pdf = new BookContentDocument(id, "pdf", "book.pdf", "PDF text");
    var rtf = new BookContentDocument(id, "rtf", "book.rtf", "RTF text");
    adapter.upsertBookContent(pdf);
    adapter.upsertBookContent(rtf);

    assertEquals(pdf, findContent(id, "pdf"));
    assertEquals(rtf, findContent(id, "rtf"));
    var raw =
        client
            .get(
                request -> request.index(properties.bookContentIndex()).id(id + ":pdf"),
                java.util.Map.class)
            .source();
    assertEquals("book.pdf", raw.get("originalFilename"));
  }

  @Test
  void contentReplacementPreservesOtherFormatsAndMetadata() throws IOException {
    indexAdapter.createIndexes();
    var book = metadata("Description", null);
    adapter.upsertBookMetadata(book);
    var epub = new BookContentDocument(book.bookId(), "epub", "book.epub", "EPUB text");
    adapter.upsertBookContent(epub);
    adapter.upsertBookContent(new BookContentDocument(book.bookId(), "pdf", "old.pdf", "Old text"));
    var replacement = new BookContentDocument(book.bookId(), "pdf", "new.pdf", "New text");

    adapter.upsertBookContent(replacement);
    adapter.upsertBookContent(replacement);

    assertEquals(replacement, findContent(book.bookId(), "pdf"));
    assertEquals(epub, findContent(book.bookId(), "epub"));
    assertEquals(book, findMetadata(book.bookId()));
    client.indices().refresh(request -> request.index(properties.bookContentIndex()));
    assertEquals(2L, client.count(request -> request.index(properties.bookContentIndex())).count());
  }

  @Test
  void bookDeletionRemovesEveryFormatAndPreservesOtherBooks() throws IOException {
    indexAdapter.createIndexes();
    var book = metadata("Delete", null);
    var other = metadata("Keep", null);
    adapter.upsertBookMetadata(book);
    adapter.upsertBookMetadata(other);
    for (var format : java.util.List.of("pdf", "rtf", "epub")) {
      adapter.upsertBookContent(
          new BookContentDocument(book.bookId(), format, "book." + format, "Deleted text"));
      adapter.upsertBookContent(
          new BookContentDocument(other.bookId(), format, "other." + format, "Keep text"));
    }

    adapter.deleteBook(book.bookId());
    adapter.deleteBook(book.bookId());

    for (var format : java.util.List.of("pdf", "rtf", "epub")) {
      assertFalse(
          client
              .get(
                  request ->
                      request.index(properties.bookContentIndex()).id(book.bookId() + ":" + format),
                  BookContentDocument.class)
              .found());
      assertEquals("Keep text", findContent(other.bookId(), format).content());
    }
    assertFalse(
        client
            .get(
                request -> request.index(properties.bookMetadataIndex()).id(book.bookId()),
                BookMetadataDocument.class)
            .found());
    assertEquals(other, findMetadata(other.bookId()));
  }

  private BookContentDocument findContent(String id, String format) throws IOException {
    var result =
        client.get(
            request -> request.index(properties.bookContentIndex()).id(id + ":" + format),
            BookContentDocument.class);
    assertTrue(result.found());
    return result.source();
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
          assertThrows(
              BookMetadataEventException.class, () -> unavailable.upsertBookMetadata(book));
      var deletion =
          assertThrows(
              BookMetadataEventException.class, () -> unavailable.deleteBook(book.bookId()));

      var unlink =
          assertThrows(
              BookMetadataEventException.class, () -> unavailable.unlinkCatalogId("catalog-id"));
      assertNotNull(unlink.getCause());
      assertTrue(unlink.getMessage().contains("update documents"));
      var content =
          assertThrows(
              BookMetadataEventException.class,
              () ->
                  unavailable.upsertBookContent(
                      new BookContentDocument(book.bookId(), "pdf", "book.pdf", "Text")));
      assertNotNull(content.getCause());
      assertNotNull(upsert.getCause());
      assertNotNull(deletion.getCause());
      assertTrue(upsert.getMessage().contains(book.bookId()));
      assertTrue(deletion.getMessage().contains(book.bookId()));
    }
  }

  private BookMetadataDocument findMetadata(String bookId) throws IOException {
    var result =
        client.get(
            request -> request.index(properties.bookMetadataIndex()).id(bookId),
            BookMetadataDocument.class);
    assertTrue(result.found());
    return result.source();
  }

  private BookMetadataDocument metadata(String description, String catalogId) {
    return new BookMetadataDocument(
        UUID.randomUUID().toString(), "0306406152", "Book", description, catalogId);
  }
}
