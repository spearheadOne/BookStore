package org.abondar.experimental.bookstore.book.adapter.out;

import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.abondar.experimental.bookstore.book.application.exception.BookContentStorageException;
import org.abondar.experimental.bookstore.book.application.port.in.DownloadedBookContent;
import org.abondar.experimental.bookstore.book.application.port.out.BookContentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Repository
public class BookStorageAdapter implements BookContentRepository {

  private static final String ORIGINAL_FILENAME_METADATA = "original-filename";

  private final S3Client s3Client;

  private final String bucketName;

  public BookStorageAdapter(S3Client s3Client, @Value("${s3.bucket}") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  @Override
  public void uploadBookContent(
      String key, InputStream content, String originalFilename, long size, String contentType) {
    var request = PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType);
    if (originalFilename != null && !originalFilename.isBlank()) {
      request.metadata(
          Map.of(
              ORIGINAL_FILENAME_METADATA,
              URLEncoder.encode(originalFilename, StandardCharsets.UTF_8)));
    }

    try {
      s3Client.putObject(request.build(), RequestBody.fromInputStream(content, size));
    } catch (SdkException e) {
      throw new BookContentStorageException("Could not upload book content", e);
    }
  }

  @Override
  public Optional<DownloadedBookContent> downloadBookContent(String key) {
    try {
      var stream = s3Client.getObject(request -> request.bucket(bucketName).key(key));
      var response = stream.response();
      var encodedFilename = response.metadata().get(ORIGINAL_FILENAME_METADATA);
      var filename = key.substring(key.lastIndexOf('/') + 1);
      if (encodedFilename != null && !encodedFilename.isBlank()) {
        try {
          filename = URLDecoder.decode(encodedFilename, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
          // Objects with malformed filename metadata can still be downloaded by key.
        }
      }
      var contentType = response.contentType();
      return Optional.of(
          new DownloadedBookContent(
              stream, filename, contentType == null ? "application/octet-stream" : contentType));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    } catch (SdkException e) {
      throw new BookContentStorageException("Could not download book content", e);
    }
  }

  @Override
  public void deleteBookContent(List<String> keys) {
    if (keys.isEmpty()) {
      return;
    }
    var objectKeys = keys.stream().map(key -> ObjectIdentifier.builder().key(key).build()).toList();

    try {
      var response =
          s3Client.deleteObjects(
              request -> request.bucket(bucketName).delete(delete -> delete.objects(objectKeys)));
      if (!response.errors().isEmpty()) {
        throw new BookContentStorageException(
            "Could not delete book content: "
                + response.errors().stream()
                    .map(error -> error.key() + " (" + error.code() + ")")
                    .toList());
      }
    } catch (SdkException e) {
      throw new BookContentStorageException("Could not delete book content", e);
    }
  }
}
