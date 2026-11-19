package org.abondar.experimental.bookstore.search.book.adapter.out;

import java.io.InputStream;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.abondar.experimental.bookstore.search.book.application.exception.BookMetadataEventException;
import org.abondar.experimental.bookstore.search.book.application.port.out.BookStorageRepository;
import org.abondar.experimental.bookstore.search.book.domain.BookContent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;

@Slf4j
@Repository
public class BookStorageAdapter implements BookStorageRepository {

  private final S3Client s3Client;

  private final String bucketName;

  public BookStorageAdapter(S3Client s3Client, @Value("${s3.bucket}") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  @Override
  public InputStream downloadBookContent(BookContent bookContent) {
    try {
      var stream =
          s3Client.getObject(request -> request.bucket(bucketName).key(bookContent.storageKey()));
      var response = stream.response();
      var metadataMatches =
          Objects.equals(bookContent.sizeBytes(), response.contentLength())
              && Objects.equals(bookContent.contentType(), response.contentType());
      log.info(
          "Book content download opened bookId={} format={} bucket={} key={}"
              + " eventSizeBytes={} objectSizeBytes={} eventContentType={}"
              + " objectContentType={} metadataMatches={}",
          bookContent.bookId(),
          bookContent.format(),
          bucketName,
          bookContent.storageKey(),
          bookContent.sizeBytes(),
          response.contentLength(),
          bookContent.contentType(),
          response.contentType(),
          metadataMatches);
      return stream;
    } catch (SdkException exception) {
      log.warn(
          "Book content download failed bookId={} format={} bucket={} key={} errorType={}",
          bookContent.bookId(),
          bookContent.format(),
          bucketName,
          bookContent.storageKey(),
          exception.getClass().getSimpleName());
      throw new BookMetadataEventException(
          "Book content download failed for " + bookContent.bookId(), exception);
    }
  }
}
