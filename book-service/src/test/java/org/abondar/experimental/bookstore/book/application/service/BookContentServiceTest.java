package org.abondar.experimental.bookstore.book.application.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.abondar.experimental.bookstore.book.application.port.in.DownloadedBookContent;
import org.abondar.experimental.bookstore.book.application.port.out.BookContentRepository;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookContentServiceTest {

  @Mock private BookContentRepository storageRepository;
  @Mock private BookRepository bookRepository;
  @InjectMocks private BookContentService service;

  @ParameterizedTest
  @CsvSource({"pdf,application/pdf", "epub,application/epub+zip", "rtf,application/rtf"})
  void uploadUsesTheFormatKeyAndMediaType(String format, String contentType) throws IOException {
    var id = UUID.randomUUID();
    var bookId = new Id<Book>(id);
    var filename = "my-book." + format;
    var key = id + "/" + format + "/file." + format;
    var bytes = validContent(format);
    when(bookRepository.withBookLocked(eq(bookId), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return true;
            });
    doAnswer(
            invocation -> {
              assertArrayEquals(bytes, invocation.<InputStream>getArgument(1).readAllBytes());
              return null;
            })
        .when(storageRepository)
        .uploadBookContent(
            eq(key),
            any(InputStream.class),
            eq(filename),
            eq((long) bytes.length),
            eq(contentType));

    service.uploadBookContent(id, new ByteArrayInputStream(bytes), format, filename, bytes.length);

    verify(storageRepository)
        .uploadBookContent(
            eq(key),
            any(InputStream.class),
            eq(filename),
            eq((long) bytes.length),
            eq(contentType));
  }

  @ParameterizedTest
  @ValueSource(strings = {"pdf", "rtf", "epub"})
  void uploadRejectsMismatchedFileBytes(String format) {
    var bytes = "plain text".getBytes(StandardCharsets.US_ASCII);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.uploadBookContent(
                UUID.randomUUID(),
                new ByteArrayInputStream(bytes),
                format,
                "fake." + format,
                bytes.length));

    verifyNoInteractions(bookRepository, storageRepository);
  }

  @Test
  void uploadRejectsAnOrdinaryZipAsEpub() throws IOException {
    var bytes = epubZip("text/plain");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.uploadBookContent(
                UUID.randomUUID(),
                new ByteArrayInputStream(bytes),
                "epub",
                "fake.epub",
                bytes.length));

    verifyNoInteractions(bookRepository, storageRepository);
  }

  @Test
  void uploadDoesNotWriteWhenBookWasDeletedBeforeTheLock() throws IOException {
    var id = UUID.randomUUID();
    var bytes = validContent("pdf");
    when(bookRepository.withBookLocked(eq(new Id<Book>(id)), any(Runnable.class)))
        .thenReturn(false);

    assertThrows(
        BookNotFoundException.class,
        () ->
            service.uploadBookContent(
                id, new ByteArrayInputStream(bytes), "pdf", "my-book.pdf", bytes.length));

    verifyNoInteractions(storageRepository);
  }

  @Test
  void downloadReturnsContentForTheRequestedFormat() {
    var id = UUID.randomUUID();
    var content =
        new DownloadedBookContent(
            new ByteArrayInputStream(new byte[] {1}), "my-book.epub", "application/epub+zip");
    when(storageRepository.downloadBookContent(id + "/epub/file.epub"))
        .thenReturn(Optional.of(content));

    assertSame(content, service.downloadBookContent(id, "epub").orElseThrow());
  }

  @Test
  void downloadReturnsEmptyWhenTheFormatIsAbsent() {
    var id = UUID.randomUUID();
    when(storageRepository.downloadBookContent(id + "/rtf/file.rtf")).thenReturn(Optional.empty());

    assertTrue(service.downloadBookContent(id, "rtf").isEmpty());
  }

  @Test
  void unsupportedFormatDoesNotReachStorage() {
    var id = UUID.randomUUID();
    var content = new ByteArrayInputStream(new byte[] {1});

    assertThrows(
        IllegalArgumentException.class,
        () -> service.uploadBookContent(id, content, "docx", "my-book.docx", 1));
    assertThrows(IllegalArgumentException.class, () -> service.downloadBookContent(id, "docx"));

    verifyNoInteractions(storageRepository);
  }

  @Test
  void deleteTargetsEverySupportedFormatExactlyOnce() {
    var id = UUID.randomUUID();

    service.deleteBookContent(id);

    verify(storageRepository)
        .deleteBookContent(
            List.of(id + "/pdf/file.pdf", id + "/epub/file.epub", id + "/rtf/file.rtf"));
  }

  private static byte[] validContent(String format) throws IOException {
    return switch (format) {
      case "pdf" -> "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII);
      case "rtf" -> "{\\rtf1 text}".getBytes(StandardCharsets.US_ASCII);
      case "epub" -> epubZip("application/epub+zip");
      default -> throw new IllegalArgumentException(format);
    };
  }

  private static byte[] epubZip(String mediaType) throws IOException {
    var type = mediaType.getBytes(StandardCharsets.US_ASCII);
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      var entry = new ZipEntry("mimetype");
      entry.setMethod(ZipEntry.STORED);
      entry.setSize(type.length);
      var crc = new CRC32();
      crc.update(type);
      entry.setCrc(crc.getValue());
      zip.putNextEntry(entry);
      zip.write(type);
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }
}
