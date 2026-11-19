package org.abondar.experimental.bookstore.search.book.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.abondar.experimental.bookstore.search.book.application.exception.BookContentEventException;
import org.abondar.experimental.bookstore.search.book.application.exception.BookMetadataEventException;
import org.abondar.experimental.bookstore.search.book.domain.BookContent;
import org.abondar.experimental.bookstore.search.config.BookContentProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookContentParserTest {

  private final BookContentParser parser =
      new BookContentParser(new BookContentProperties(10_000_000));

  @ParameterizedTest
  @ValueSource(strings = {"pdf", "epub", "rtf"})
  void extractsTextAndPreservesDocumentMetadata(String format) throws IOException {
    var bytes =
        switch (format) {
          case "pdf" -> pdf();
          case "epub" -> epub();
          default -> "{\\rtf1\\ansi Searchable book text\\par}".getBytes(StandardCharsets.US_ASCII);
        };
    var type =
        switch (format) {
          case "pdf" -> "application/pdf";
          case "epub" -> "application/epub+zip";
          default -> "application/rtf";
        };
    var content = new BookContent("book-id", format, "key", type, "book." + format, bytes.length);
    var stream = new TrackingStream(bytes);

    var result = parser.parse(content, stream);

    assertEquals(content.bookId(), result.bookId());
    assertEquals(content.format(), result.format());
    assertEquals(content.originalFileName(), result.originalFilename());
    assertTrue(result.content().contains("Searchable book text"), result.content());
    assertTrue(stream.closed);
  }

  @Test
  void missingFilenameUsesFormatHintAndKeepsOriginalFilenameNull() {
    var bytes = "{\\rtf1\\ansi Searchable book text\\par}".getBytes(StandardCharsets.US_ASCII);
    var content = new BookContent("book-id", "rtf", "key", "application/rtf", null, bytes.length);

    var result = parser.parse(content, new ByteArrayInputStream(bytes));

    assertEquals(null, result.originalFilename());
    assertTrue(result.content().contains("Searchable book text"));
  }

  @Test
  void corruptPdfRetainsCauseAndClosesStream() {
    var stream = new TrackingStream("%PDF-1.7\nbroken".getBytes(StandardCharsets.US_ASCII));
    var content = new BookContent("book-id", "pdf", "key", "application/pdf", "book.pdf", 15L);

    var failure =
        assertThrows(BookContentEventException.class, () -> parser.parse(content, stream));

    assertNotNull(failure.getCause());
    assertTrue(stream.closed);
  }

  @Test
  void extractsTextBeyondTikasDefaultLimitWithoutTruncation() {
    var text = "text ".repeat(25_000) + "end-of-book";
    var bytes = ("{\\rtf1\\ansi " + text + "\\par}").getBytes(StandardCharsets.US_ASCII);
    var content =
        new BookContent("book-id", "rtf", "key", "application/rtf", "book.rtf", bytes.length);

    var result = parser.parse(content, new ByteArrayInputStream(bytes));

    assertTrue(result.content().length() > 100_000);
    assertTrue(result.content().contains(text));
  }

  @Test
  void outputLimitFailureIsNotSilentlyIndexedAsPartialText() {
    var limitedParser = new BookContentParser(new BookContentProperties(1_000));
    var bytes =
        ("{\\rtf1\\ansi " + "text ".repeat(25_000) + "\\par}").getBytes(StandardCharsets.US_ASCII);
    var content =
        new BookContent("book-id", "rtf", "key", "application/rtf", "book.rtf", bytes.length);

    var failure =
        assertThrows(
            BookContentEventException.class,
            () -> limitedParser.parse(content, new ByteArrayInputStream(bytes)));

    assertNotNull(failure.getCause());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void rejectsNonpositiveExtractionLimit(int limit) {
    assertThrows(IllegalArgumentException.class, () -> new BookContentProperties(limit));
  }

  @Test
  void streamReadFailureRemainsRetryable() {
    var content = new BookContent("book-id", "pdf", "key", "application/pdf", "book.pdf", 15L);
    var stream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Connection lost");
          }
        };

    var failure =
        assertThrows(BookMetadataEventException.class, () -> parser.parse(content, stream));

    assertNotNull(failure.getCause());
  }

  private byte[] pdf() throws IOException {
    try (var document = new PDDocument();
        var bytes = new ByteArrayOutputStream()) {
      var page = new PDPage();
      document.addPage(page);
      try (var writer = new PDPageContentStream(document, page)) {
        writer.beginText();
        writer.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        writer.newLineAtOffset(50, 700);
        writer.showText("Searchable book text");
        writer.endText();
      }
      document.save(bytes);
      return bytes.toByteArray();
    }
  }

  private byte[] epub() throws IOException {
    try (var bytes = new ByteArrayOutputStream();
        var zip = new ZipOutputStream(bytes)) {
      var mimetype = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
      var checksum = new CRC32();
      checksum.update(mimetype);
      var entry = new ZipEntry("mimetype");
      entry.setMethod(ZipEntry.STORED);
      entry.setSize(mimetype.length);
      entry.setCompressedSize(mimetype.length);
      entry.setCrc(checksum.getValue());
      zip.putNextEntry(entry);
      zip.write(mimetype);
      zip.closeEntry();
      entry(
          zip,
          "META-INF/container.xml",
          """
          <?xml version="1.0"?>
          <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
          </container>
          """);
      entry(
          zip,
          "OPS/package.opf",
          """
          <?xml version="1.0"?>
          <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
              <dc:identifier id="id">test-book</dc:identifier><dc:title>Book</dc:title><dc:language>en</dc:language>
            </metadata>
            <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="chapter"/></spine>
          </package>
          """);
      entry(
          zip,
          "OPS/chapter.xhtml",
          """
          <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Book</title></head>
          <body><p>Searchable book text</p></body></html>
          """);
      zip.finish();
      return bytes.toByteArray();
    }
  }

  private void entry(ZipOutputStream zip, String name, String text) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(text.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private static class TrackingStream extends ByteArrayInputStream {
    private boolean closed;

    TrackingStream(byte[] bytes) {
      super(bytes);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}
