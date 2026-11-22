package org.abondar.experimental.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3HealthIndicatorTest {
  @Mock private S3Client client;
  private S3HealthIndicator indicator;

  @BeforeEach
  void setUp() {
    indicator =
        new S3HealthIndicator(
            client,
            new S3Properties(
                null, "books", "us-east-1", false, Duration.ofSeconds(1), Duration.ofMinutes(2)));
  }

  @Test
  void accessibleBucketReportsUpWithShortPerRequestTimeouts() {
    when(client.headBucket(any(HeadBucketRequest.class)))
        .thenReturn(HeadBucketResponse.builder().build());
    var health = indicator.health();
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("bucket", "books");

    var request = ArgumentCaptor.forClass(HeadBucketRequest.class);
    verify(client).headBucket(request.capture());
    assertThat(request.getValue().bucket()).isEqualTo("books");
    var configuration = request.getValue().overrideConfiguration().orElseThrow();
    assertThat(configuration.apiCallTimeout()).contains(Duration.ofSeconds(2));
    assertThat(configuration.apiCallAttemptTimeout()).contains(Duration.ofSeconds(1));
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 403, 404, 503})
  void missingDeniedOrUnavailableBucketReportsDownWithoutExposingDiagnostics(int status) {
    when(client.headBucket(any(HeadBucketRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(status).message("private diagnostics").build());
    var health = indicator.health();
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("bucket", "books");
    assertThat(health.getDetails().toString()).doesNotContain("private diagnostics");
  }

  @ParameterizedTest
  @MethodSource("clientFailures")
  void clientFailureOrTimeoutReportsDownWithoutExposingDiagnostics(SdkClientException failure) {
    when(client.headBucket(any(HeadBucketRequest.class))).thenThrow(failure);
    var health = indicator.health();
    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails().toString()).doesNotContain("private diagnostics");
  }

  static Stream<SdkClientException> clientFailures() {
    return Stream.of(
        SdkClientException.create("private diagnostics"),
        ApiCallTimeoutException.create("private diagnostics", null));
  }

  @Test
  void stalledHeadRequestReturnsDownBeforeTheClientsLongerTimeout() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var release = new CountDownLatch(1);
    var request = new AtomicReference<String>();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      server.setExecutor(executor);
      server.createContext(
          "/books",
          exchange -> {
            try (exchange) {
              request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
              try {
                release.await(10, TimeUnit.SECONDS);
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
            }
          });
      server.start();
      var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
      try (var slowClient =
          S3Client.builder()
              .endpointOverride(endpoint)
              .region(Region.US_EAST_1)
              .forcePathStyle(true)
              .credentialsProvider(
                  StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
              .httpClientBuilder(Apache5HttpClient.builder().socketTimeout(Duration.ofSeconds(10)))
              .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(10)))
              .build()) {
        var probe =
            new S3HealthIndicator(
                slowClient,
                new S3Properties(
                    endpoint,
                    "books",
                    "us-east-1",
                    true,
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(10)));
        assertTimeout(
            Duration.ofSeconds(5),
            () -> {
              assertThat(probe.health().getStatus()).isEqualTo(Status.DOWN);
            });
        assertThat(request.get()).isEqualTo("HEAD /books");
        assertThat(slowClient.serviceClientConfiguration().overrideConfiguration().apiCallTimeout())
            .contains(Duration.ofSeconds(10));
      } finally {
        release.countDown();
        server.stop(0);
      }
    }
  }
}
