package org.abondar.experimental.bookstore.search.catalog.adapter.in.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.abondar.experimental.bookstore.events.avro.BookMetadataUpserted;
import org.abondar.experimental.bookstore.events.avro.CatalogDeleted;
import org.abondar.experimental.bookstore.events.avro.CatalogUpserted;
import org.abondar.experimental.bookstore.search.adapter.in.event.BaseEventAdapterTest;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.FieldValue;

class CatalogEventAdapterTest extends BaseEventAdapterTest {

  @Test
  void consumesCatalogUpsertAndStoresStructuredDocument() throws Exception {
    var id = UUID.randomUUID();

    publishAndAwait(catalog(id, "Science Fiction"));

    assertEquals(Map.of("catalogId", id.toString(), "name", "Science Fiction"), find(id));
  }

  @Test
  void laterSnapshotReplacesCatalogName() throws Exception {
    var id = UUID.randomUUID();
    publishAndAwait(catalog(id, "Original name"));

    publishAndAwait(catalog(id, "Updated name"));

    assertEquals("Updated name", find(id).get("name"));
  }

  @Test
  void duplicateDeliveryKeepsOneCatalogDocument() throws Exception {
    var id = UUID.randomUUID();
    var event = catalog(id, "Catalog");

    publishAndAwait(event);
    publishAndAwait(event);

    client.indices().refresh(request -> request.index(CATALOG_INDEX));
    var count =
        client.count(
            request ->
                request
                    .index(CATALOG_INDEX)
                    .query(
                        query ->
                            query.term(
                                term ->
                                    term.field("catalogId").value(FieldValue.of(id.toString())))));
    assertEquals(1L, count.count());
  }

  @Test
  void repeatedDeletionPreservesBookAndContentDocuments() throws Exception {
    var catalogId = UUID.randomUUID();
    var bookId = UUID.randomUUID();
    publishAndAwait(catalog(catalogId, "Catalog"));
    publishAndAwait(
        new BookMetadataUpserted(
            UUID.randomUUID(), Instant.now(), bookId, "0306406152", "Book", null, catalogId));
    var contentId = bookId + ":pdf";
    client.index(
        request ->
            request
                .index("test-book-content")
                .id(contentId)
                .document(
                    Map.of("bookId", bookId.toString(), "format", "pdf", "content", "Book text")));
    var deletion = new CatalogDeleted(UUID.randomUUID(), Instant.now(), catalogId);

    publishAndAwait(deletion);
    publishAndAwait(deletion);

    assertFalse(
        client
            .get(request -> request.index(CATALOG_INDEX).id(catalogId.toString()), Map.class)
            .found());
    var book =
        client.get(
            request -> request.index(METADATA_INDEX).id(bookId.toString()), BookMetadata.class);
    assertTrue(book.found());
    assertEquals(
        new BookMetadata(bookId.toString(), "0306406152", "Book", null, null), book.source());
    assertTrue(
        client.get(request -> request.index("test-book-content").id(contentId), Map.class).found());
  }

  private Map<?, ?> find(UUID id) throws Exception {
    var result = client.get(request -> request.index(CATALOG_INDEX).id(id.toString()), Map.class);
    assertTrue(result.found());
    return result.source();
  }

  private CatalogUpserted catalog(UUID id, String name) {
    return new CatalogUpserted(UUID.randomUUID(), Instant.now(), id, name);
  }
}
