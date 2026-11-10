package org.abondar.experimental.bookstore.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.stream.Stream;
import org.abondar.experimental.bookstore.events.avro.BookContentAvailable;
import org.abondar.experimental.bookstore.events.avro.BookDeleted;
import org.abondar.experimental.bookstore.events.avro.BookMetadataUpserted;
import org.abondar.experimental.bookstore.events.avro.CatalogDeleted;
import org.abondar.experimental.bookstore.events.avro.CatalogUpserted;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EventSchemaCompatibilityTest {

  // Frozen v1 schemas represent deployed readers and stored Kafka messages.
  // Retain them when evolving the production schemas.
  static Stream<Arguments> schemas() {
    return Stream.of(
        Arguments.of("book-metadata-upserted", BookMetadataUpserted.getClassSchema()),
        Arguments.of("book-deleted", BookDeleted.getClassSchema()),
        Arguments.of("book-content-available", BookContentAvailable.getClassSchema()),
        Arguments.of("catalog-upserted", CatalogUpserted.getClassSchema()),
        Arguments.of("catalog-deleted", CatalogDeleted.getClassSchema()));
  }

  @ParameterizedTest(name = "{0}: current reader accepts v1 messages")
  @MethodSource("schemas")
  void currentReaderAcceptsV1Writer(String name, Schema current) throws IOException {
    assertCompatible(current, v1(name));
  }

  @ParameterizedTest(name = "{0}: v1 reader accepts current messages")
  @MethodSource("schemas")
  void v1ReaderAcceptsCurrentWriter(String name, Schema current) throws IOException {
    assertCompatible(v1(name), current);
  }

  private Schema v1(String name) throws IOException {
    try (var input = getClass().getResourceAsStream("/avro/v1/" + name + ".avsc")) {
      assertNotNull(input, "Missing frozen schema: " + name);
      return new Schema.Parser().parse(input);
    }
  }

  private void assertCompatible(Schema reader, Schema writer) {
    var result = SchemaCompatibility.checkReaderWriterCompatibility(reader, writer);
    assertEquals(
        SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
        result.getType(),
        result::getDescription);
  }
}
