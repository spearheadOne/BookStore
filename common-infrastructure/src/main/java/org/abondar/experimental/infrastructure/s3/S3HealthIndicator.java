package org.abondar.experimental.infrastructure.s3;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Component("s3HealthIndicator")
@RequiredArgsConstructor
public class S3HealthIndicator implements HealthIndicator {
  private final S3Client client;
  private final S3Properties properties;

  @Override
  public Health health() {
    var request =
        HeadBucketRequest.builder()
            .bucket(properties.bucket())
            .overrideConfiguration(
                config ->
                    config
                        .apiCallTimeout(Duration.ofSeconds(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(1)))
            .build();
    try {
      client.headBucket(request);
      return Health.up().withDetail("bucket", properties.bucket()).build();
    } catch (SdkException exception) {
      return Health.down()
          .withDetail("bucket", properties.bucket())
          .withDetail("reason", "Bucket unavailable or not accessible")
          .build();
    }
  }
}
