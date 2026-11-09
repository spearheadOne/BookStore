package org.abondar.experimental.bookstore.book.adapter.out;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers(disabledWithoutDocker = true)
class BookStorageAdapterTest {

  private static final int S3_PORT = 8333;
  private static final String BUCKET = "book-store-test";
  private static final String ACCESS_KEY = "test-access-key";
  private static final String SECRET_KEY = "test-secret-key";
  private static final byte[] CONTENT = {0, 1, 2, -1};

  @Container
  static final GenericContainer<?> seaweedfs =
      new GenericContainer<>("chrislusf/seaweedfs:4.52")
          .withCommand("mini", "-dir=/data")
          .withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
          .withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
          .withExposedPorts(S3_PORT)
          .waitingFor(Wait.forHttp("/healthz"));

  private static S3Client s3Client;
  private static BookStorageAdapter adapter;

  @BeforeAll
  static void connect() {
    var endpoint =
        URI.create("http://" + seaweedfs.getHost() + ":" + seaweedfs.getMappedPort(S3_PORT));
    s3Client =
        S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
            .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(10)))
            .build();
    s3Client.createBucket(request -> request.bucket(BUCKET));
    adapter = new BookStorageAdapter(s3Client, BUCKET);
  }

  @AfterAll
  static void closeClient() {
    if (s3Client != null) {
      s3Client.close();
    }
  }

  @Test
  void uploadsAndDownloadsContentWithOriginalFilename() throws IOException {
    var key = pdfKey();
    var filename = "Über Bücher + Notizen.pdf";

    upload(key, filename);

    var downloaded = adapter.downloadBookContent(key).orElseThrow();
    try (var content = downloaded.stream()) {
      assertArrayEquals(CONTENT, content.readAllBytes());
      assertEquals(filename, downloaded.originalFilename());
      assertEquals("application/pdf", downloaded.contentType());
    }

    var object = s3Client.headObject(request -> request.bucket(BUCKET).key(key));
    assertEquals((long) CONTENT.length, object.contentLength().longValue());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void usesObjectBasenameWhenOriginalFilenameIsMissingOrBlank(String filename) throws IOException {
    var key = pdfKey();

    upload(key, filename);

    var object = s3Client.headObject(request -> request.bucket(BUCKET).key(key));
    assertFalse(object.metadata().containsKey("original-filename"));

    var downloaded = adapter.downloadBookContent(key).orElseThrow();
    try (var content = downloaded.stream()) {
      assertEquals("file.pdf", downloaded.originalFilename());
      assertArrayEquals(CONTENT, content.readAllBytes());
    }
  }

  @Test
  void deletesMultipleFormatsAndAllowsRepeatDeletion() {
    var prefix = UUID.randomUUID().toString();
    var pdfKey = prefix + "/pdf/file.pdf";
    var epubKey = prefix + "/epub/file.epub";
    upload(pdfKey, "book.pdf");
    adapter.uploadBookContent(
        epubKey,
        new ByteArrayInputStream(CONTENT),
        "book.epub",
        CONTENT.length,
        "application/epub+zip");
    var keys = List.of(pdfKey, epubKey, prefix + "/rtf/file.rtf");

    adapter.deleteBookContent(keys);
    adapter.deleteBookContent(keys);

    assertTrue(adapter.downloadBookContent(pdfKey).isEmpty());
    assertTrue(adapter.downloadBookContent(epubKey).isEmpty());
  }

  @Test
  void downloadOfMissingContentReturnsEmpty() {
    assertTrue(adapter.downloadBookContent(pdfKey()).isEmpty());
  }

  @Test
  void replacingPdfKeepsEpubAndRtfForTheSameBook() throws IOException {
    var prefix = UUID.randomUUID().toString();
    var pdf = prefix + "/pdf/file.pdf";
    var epub = prefix + "/epub/file.epub";
    var rtf = prefix + "/rtf/file.rtf";
    var replacement = new byte[] {8, 9, 10};
    upload(pdf, "original.pdf");
    adapter.uploadBookContent(
        epub,
        new ByteArrayInputStream(CONTENT),
        "book.epub",
        CONTENT.length,
        "application/epub+zip");
    adapter.uploadBookContent(
        rtf, new ByteArrayInputStream(CONTENT), "book.rtf", CONTENT.length, "application/rtf");

    adapter.uploadBookContent(
        pdf,
        new ByteArrayInputStream(replacement),
        "replacement.pdf",
        replacement.length,
        "application/pdf");

    assertStoredContent(pdf, "replacement.pdf", replacement);
    assertStoredContent(epub, "book.epub", CONTENT);
    assertStoredContent(rtf, "book.rtf", CONTENT);
  }

  private static void assertStoredContent(String key, String filename, byte[] expected)
      throws IOException {
    var downloaded = adapter.downloadBookContent(key).orElseThrow();
    try (var stream = downloaded.stream()) {
      assertEquals(filename, downloaded.originalFilename());
      assertArrayEquals(expected, stream.readAllBytes());
    }
  }

  private static void upload(String key, String filename) {
    adapter.uploadBookContent(
        key, new ByteArrayInputStream(CONTENT), filename, CONTENT.length, "application/pdf");
  }

  private static String pdfKey() {
    return UUID.randomUUID() + "/pdf/file.pdf";
  }
}
