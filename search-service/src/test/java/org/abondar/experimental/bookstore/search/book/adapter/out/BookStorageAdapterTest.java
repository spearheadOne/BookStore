package org.abondar.experimental.bookstore.search.book.adapter.out;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import org.abondar.experimental.bookstore.search.book.application.exception.BookMetadataEventException;
import org.abondar.experimental.bookstore.search.book.domain.BookContent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Testcontainers(disabledWithoutDocker = true)
class BookStorageAdapterTest {

  private static final String BUCKET = "content-test";

  @Container
  static final GenericContainer<?> storage =
      new GenericContainer<>("chrislusf/seaweedfs:4.52")
          .withCommand("mini", "-dir=/data")
          .withEnv("AWS_ACCESS_KEY_ID", "test-access")
          .withEnv("AWS_SECRET_ACCESS_KEY", "test-secret")
          .withExposedPorts(8333)
          .waitingFor(Wait.forHttp("/healthz"));

  private static S3Client client;
  private static BookStorageAdapter adapter;

  @BeforeAll
  static void connect() {
    client =
        S3Client.builder()
            .endpointOverride(
                URI.create("http://" + storage.getHost() + ":" + storage.getMappedPort(8333)))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access", "test-secret")))
            .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(10)))
            .build();
    client.createBucket(request -> request.bucket(BUCKET));
    adapter = new BookStorageAdapter(client, BUCKET);
  }

  @AfterAll
  static void close() {
    if (client != null) client.close();
  }

  @Test
  void downloadsStoredContentAsStream() throws Exception {
    var id = UUID.randomUUID().toString();
    var key = id + "/rtf/file.rtf";
    var bytes = "{\\rtf1\\ansi Searchable book text\\par}".getBytes(StandardCharsets.US_ASCII);
    client.putObject(
        request -> request.bucket(BUCKET).key(key).contentType("application/rtf"),
        RequestBody.fromBytes(bytes));
    var content = new BookContent(id, "rtf", key, "application/rtf", "book.rtf", bytes.length);

    try (var stream = adapter.downloadBookContent(content)) {
      assertArrayEquals(bytes, stream.readAllBytes());
    }
  }

  @Test
  void oldEventMetadataDoesNotPreventReadingCurrentReplacement() throws Exception {
    var id = UUID.randomUUID().toString();
    var key = id + "/pdf/file.pdf";
    var bytes = "%PDF-current replacement".getBytes(StandardCharsets.US_ASCII);
    client.putObject(
        request -> request.bucket(BUCKET).key(key).contentType("application/pdf"),
        RequestBody.fromBytes(bytes));
    var oldEvent = new BookContent(id, "pdf", key, "application/old-type", null, 1L);

    try (var stream = adapter.downloadBookContent(oldEvent)) {
      assertArrayEquals(bytes, stream.readAllBytes());
    }
  }

  @Test
  void missingObjectPreservesCauseForKafkaRecovery() {
    var content =
        new BookContent(
            "missing", "pdf", UUID.randomUUID() + "/pdf/file.pdf", "application/pdf", null, 1L);
    var failure =
        assertThrows(BookMetadataEventException.class, () -> adapter.downloadBookContent(content));
    org.junit.jupiter.api.Assertions.assertInstanceOf(NoSuchKeyException.class, failure.getCause());
  }

  @Test
  void sdkFailurePreservesOriginalCause() {
    var unavailable = mock(S3Client.class);
    var cause = SdkClientException.create("Connection failed");
    when(unavailable.getObject(
            org.mockito.ArgumentMatchers.<Consumer<GetObjectRequest.Builder>>any()))
        .thenThrow(cause);
    var content = new BookContent("book", "pdf", "key", "application/pdf", null, 1L);

    var failure =
        assertThrows(
            BookMetadataEventException.class,
            () -> new BookStorageAdapter(unavailable, BUCKET).downloadBookContent(content));

    assertSame(cause, failure.getCause());
  }
}
