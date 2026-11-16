package org.abondar.experimental.bookstore.search.book.adapter.in.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.abondar.experimental.bookstore.events.avro.BookContentAvailable;
import org.abondar.experimental.bookstore.events.avro.BookContentFormat;
import org.abondar.experimental.bookstore.events.avro.BookDeleted;
import org.abondar.experimental.bookstore.events.avro.BookMetadataUpserted;
import org.abondar.experimental.bookstore.search.adapter.in.event.BaseEventAdapterTest;
import org.abondar.experimental.bookstore.search.book.domain.BookMetadata;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.FieldValue;

class BookEventAdapterTest extends BaseEventAdapterTest {

  @Test
  void metadataEventIsConsumedAndIndexed() throws Exception {
    var id = UUID.randomUUID();
    var catalog = UUID.randomUUID();

    publishAndAwait(metadata(id, "Book", "Description", catalog));

    assertEquals(
        new BookMetadata(id.toString(), "0306406152", "Book", "Description", catalog.toString()),
        find(id));
  }

  @Test
  void metadataEventPreservesNullOptionalFields() throws Exception {
    var id = UUID.randomUUID();

    publishAndAwait(metadata(id, "Book", null, null));

    assertEquals(new BookMetadata(id.toString(), "0306406152", "Book", null, null), find(id));
  }

  @Test
  void laterSnapshotReplacesMetadataAndClearsOptionalFields() throws Exception {
    var id = UUID.randomUUID();
    publishAndAwait(metadata(id, "Original title", "Description", UUID.randomUUID()));

    publishAndAwait(metadata(id, "Updated title", null, null));

    assertEquals(
        new BookMetadata(id.toString(), "0306406152", "Updated title", null, null), find(id));
  }

  @Test
  void duplicateEventKeepsOneMetadataDocument() throws Exception {
    var id = UUID.randomUUID();
    var event = metadata(id, "Book", null, null);

    publishAndAwait(event);
    publishAndAwait(event);

    client.indices().refresh(request -> request.index(METADATA_INDEX));
    var count =
        client.count(
            request ->
                request
                    .index(METADATA_INDEX)
                    .query(
                        query ->
                            query.term(
                                term -> term.field("bookId").value(FieldValue.of(id.toString())))));
    assertEquals(1L, count.count());
    assertEquals(new BookMetadata(id.toString(), "0306406152", "Book", null, null), find(id));
  }

  @Test
  void repeatedDeletionRemovesMetadataAndPreservesOtherBooks() throws Exception {
    var id = UUID.randomUUID();
    var otherId = UUID.randomUUID();
    publishAndAwait(metadata(id, "Delete this", null, null));
    publishAndAwait(metadata(otherId, "Keep this", null, null));
    var deletion = new BookDeleted(UUID.randomUUID(), Instant.now(), id);

    publishAndAwait(deletion);
    publishAndAwait(deletion);

    assertFalse(
        client
            .get(request -> request.index(METADATA_INDEX).id(id.toString()), BookMetadata.class)
            .found());
    assertEquals(
        new BookMetadata(otherId.toString(), "0306406152", "Keep this", null, null), find(otherId));
  }

  @Test
  void contentEventDoesNotCreateMetadataDocument() throws Exception {
    var id = UUID.randomUUID();
    var event =
        new BookContentAvailable(
            UUID.randomUUID(),
            Instant.now(),
            id,
            BookContentFormat.pdf,
            id + "/pdf/file.pdf",
            "application/pdf",
            "book.pdf",
            100L);

    publishAndAwait(event);

    assertFalse(
        client
            .get(request -> request.index(METADATA_INDEX).id(id.toString()), BookMetadata.class)
            .found());
  }

  private BookMetadata find(UUID id) throws Exception {
    var result =
        client.get(request -> request.index(METADATA_INDEX).id(id.toString()), BookMetadata.class);
    assertTrue(result.found());
    return result.source();
  }

  private BookMetadataUpserted metadata(UUID id, String title, String description, UUID catalogId) {
    return new BookMetadataUpserted(
        UUID.randomUUID(), Instant.now(), id, "0306406152", title, description, catalogId);
  }
}
