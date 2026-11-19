package org.abondar.experimental.bookstore.search.book.application.service;

import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.search.book.application.exception.BookContentEventException;
import org.abondar.experimental.bookstore.search.book.application.exception.BookMetadataEventException;
import org.abondar.experimental.bookstore.search.book.domain.BookContent;
import org.abondar.experimental.bookstore.search.book.domain.BookContentDocument;
import org.abondar.experimental.bookstore.search.config.BookContentProperties;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
@Slf4j
@RequiredArgsConstructor
public class BookContentParser {

  private final BookContentProperties properties;

  public BookContentDocument parse(BookContent content, InputStream file) {
    var parseContext = new ParseContext();

    var metadata = Metadata.newInstance(parseContext);
    metadata.set(HttpHeaders.CONTENT_TYPE, content.contentType());
    metadata.set(
        TikaCoreProperties.RESOURCE_NAME_KEY,
        content.originalFileName() == null || content.originalFileName().isBlank()
            ? "file." + content.format()
            : content.originalFileName());

    // Event size describes the upload; a stable storage key may now contain a replacement.
    try (var stream = TikaInputStream.get(file, metadata)) {
      var handler = new BodyContentHandler(properties.maxExtractedCharacters());
      new AutoDetectParser().parse(stream, handler, metadata, parseContext);
      return new BookContentDocument(
          content.bookId(), content.format(), content.originalFileName(), handler.toString());
    } catch (IOException exception) {
      throw new BookMetadataEventException(
          "Could not read content for book " + content.bookId(), exception);
    } catch (SAXException | TikaException exception) {
      log.warn(
          "Could not parse {} content for book {}", content.format(), content.bookId(), exception);
      throw new BookContentEventException(
          "Could not parse content for book " + content.bookId(), exception);
    }
  }
}
