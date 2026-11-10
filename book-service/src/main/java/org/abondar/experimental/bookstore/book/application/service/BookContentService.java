package org.abondar.experimental.bookstore.book.application.service;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.book.application.exception.BookNotFoundException;
import org.abondar.experimental.bookstore.book.application.port.in.DeleteBookContentUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.DownloadBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.in.DownloadedBookContent;
import org.abondar.experimental.bookstore.book.application.port.in.UploadBookUseCase;
import org.abondar.experimental.bookstore.book.application.port.out.BookContentRepository;
import org.abondar.experimental.bookstore.book.application.port.out.BookEventRepository;
import org.abondar.experimental.bookstore.book.application.port.out.BookRepository;
import org.abondar.experimental.bookstore.book.domain.Book;
import org.abondar.experimental.bookstore.common.Id;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookContentService
    implements UploadBookUseCase, DownloadBookUseCase, DeleteBookContentUseCase {

  // ZIP local header (30), entry name "mimetype" (8), and uncompressed media type (20).
  private static final int EPUB_HEADER_BYTES = 58;

  private final BookContentRepository storageRepository;
  private final BookRepository bookRepository;
  private final BookEventRepository eventRepository;

  @Override
  public Optional<DownloadedBookContent> downloadBookContent(UUID id, String format) {
    var bookFormat = BookFormat.parse(format);
    var content = storageRepository.downloadBookContent(bookFormat.key(id));
    if (content.isEmpty()) {
      log.debug("No {} content found for book {}", bookFormat, id);
    }
    return content;
  }

  @Override
  public void uploadBookContent(
      UUID id, InputStream file, String format, String originalFilename, long size) {
    var bookFormat = BookFormat.parse(format);
    var bookId = new Id<Book>(id);
    var content = validateHeader(file, bookFormat);
    var key = bookFormat.key(id);
    boolean exists =
        bookRepository.withBookLocked(
            bookId,
            () -> {
              storageRepository.uploadBookContent(
                  key, content, originalFilename, size, bookFormat.contentType);
              eventRepository.sendBookContentAvailable(
                  bookId, format, key, bookFormat.contentType, originalFilename, size);
            });
    if (!exists) {
      throw new BookNotFoundException(bookId);
    }
    log.debug("Stored {} content for book {} ({} bytes)", bookFormat, id, size);
  }

  @Override
  public void deleteBookContent(UUID id) {
    storageRepository.deleteBookContent(
        Arrays.stream(BookFormat.values()).map(format -> format.key(id)).toList());
    log.debug("Cleaned up stored content for book {}", id);
  }

  private BufferedInputStream validateHeader(InputStream file, BookFormat format) {
    var content = new BufferedInputStream(file);
    content.mark(EPUB_HEADER_BYTES);
    try {
      var header = content.readNBytes(EPUB_HEADER_BYTES);
      var ascii = new String(header, StandardCharsets.US_ASCII);
      boolean matches =
          switch (format) {
            case PDF -> ascii.startsWith("%PDF-");
            case RTF -> ascii.startsWith("{\\rtf1");
            case EPUB -> hasEpubMimetype(header);
          };
      if (!matches) {
        throw new IllegalArgumentException("File does not match the declared format: " + format);
      }
      content.reset();
      return content;
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not read the file header", e);
    }
  }

  private boolean hasEpubMimetype(byte[] header) throws IOException {
    var mimetype = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
    try (var zip = new ZipInputStream(new ByteArrayInputStream(header))) {
      var entry = zip.getNextEntry();
      return entry != null
          && entry.getName().equals("mimetype")
          && entry.getMethod() == ZipEntry.STORED
          && entry.getSize() == mimetype.length
          && entry.getExtra() == null
          && Arrays.equals(zip.readNBytes(mimetype.length), mimetype);
    }
  }

  private enum BookFormat {
    PDF("application/pdf"),
    EPUB("application/epub+zip"),
    RTF("application/rtf");

    private final String contentType;

    BookFormat(String contentType) {
      this.contentType = contentType;
    }

    private static BookFormat parse(String format) {
      if (format == null) {
        throw new IllegalArgumentException("Book format is required");
      }
      return valueOf(format.toUpperCase(Locale.ROOT));
    }

    private String key(UUID id) {
      var extension = name().toLowerCase(Locale.ROOT);
      return id + "/" + extension + "/file." + extension;
    }
  }
}
