package org.abondar.experimental.bookstore.book.adapter.in;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.abondar.experimental.bookstore.config.EpubProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BookEpubUploadValidatorTest {

  private static final int MIB = 1024 * 1024;
  private final BookEpubUploadValidator validator =
      new BookEpubUploadValidator(new EpubProperties(1, 2));

  @Test
  void acceptsExactlyTheExpandedSizeLimit() throws IOException {
    var compressed = zip(new byte[MIB]);
    var stream = new TrackingStream(compressed);

    assertTrue(compressed.length < MIB);
    assertTrue(validator.isEpubValid(stream));
    assertTrue(stream.closed);
  }

  @Test
  void rejectsOneExpandedByteOverTheLimitEvenWhenArchiveIsSmall() throws IOException {
    var compressed = zip(new byte[MIB + 1]);
    var stream = new TrackingStream(compressed);

    assertTrue(compressed.length < MIB);
    assertFalse(validator.isEpubValid(stream));
    assertTrue(stream.closed);
  }

  @Test
  void countsExpandedBytesAcrossEntries() throws IOException {
    var compressed = zip(new byte[600_000], new byte[600_000]);

    assertFalse(validator.isEpubValid(new ByteArrayInputStream(compressed)));
  }

  @Test
  void acceptsExactlyTheEntryCountLimit() throws IOException {
    assertTrue(validator.isEpubValid(new ByteArrayInputStream(zip(new byte[0], new byte[0]))));
  }

  @Test
  void rejectsOneEntryOverTheLimitIncludingEmptyEntries() throws IOException {
    var stream = new TrackingStream(zip(new byte[0], new byte[0], new byte[0]));

    assertFalse(validator.isEpubValid(stream));
    assertTrue(stream.closed);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not a ZIP archive"})
  void rejectsNonZipContent(String content) {
    var stream = new TrackingStream(content.getBytes(StandardCharsets.UTF_8));

    assertThrows(IOException.class, () -> validator.isEpubValid(stream));
    assertTrue(stream.closed);
  }

  @Test
  void rejectsAnEmptyArchive() throws IOException {
    assertThrows(IOException.class, () -> validator.isEpubValid(new ByteArrayInputStream(zip())));
  }

  @Test
  void rejectsATruncatedEntry() throws IOException {
    var compressed = zip("chapter text".getBytes(StandardCharsets.UTF_8));
    var stream = new TrackingStream(Arrays.copyOf(compressed, 20));

    assertThrows(IOException.class, () -> validator.isEpubValid(stream));
    assertTrue(stream.closed);
  }

  @Test
  void rejectsAnEntryWithInvalidChecksum() throws IOException {
    var content = "chapter text".getBytes(StandardCharsets.UTF_8);
    var checksum = new CRC32();
    checksum.update(content);
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      var entry = new ZipEntry("chapter.xhtml");
      entry.setMethod(ZipEntry.STORED);
      entry.setSize(content.length);
      entry.setCompressedSize(content.length);
      entry.setCrc(checksum.getValue());
      zip.putNextEntry(entry);
      zip.write(content);
      zip.closeEntry();
    }
    var corrupted = output.toByteArray();
    // The payload follows the fixed 30-byte local header and the entry name.
    corrupted[30 + "chapter.xhtml".length()] ^= 1;

    assertThrows(
        IOException.class, () -> validator.isEpubValid(new ByteArrayInputStream(corrupted)));
  }

  @ParameterizedTest
  @CsvSource({"0, 1", "-1, 1", "1, 0", "1, -1"})
  void rejectsNonpositiveConfiguration(long sizeMb, int entries) {
    assertThrows(IllegalArgumentException.class, () -> new EpubProperties(sizeMb, entries));
  }

  private byte[] zip(byte[]... entries) throws IOException {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      for (int i = 0; i < entries.length; i++) {
        zip.putNextEntry(new ZipEntry("chapter-" + i + ".xhtml"));
        zip.write(entries[i]);
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }

  private static class TrackingStream extends ByteArrayInputStream {
    private boolean closed;

    TrackingStream(byte[] content) {
      super(content);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
