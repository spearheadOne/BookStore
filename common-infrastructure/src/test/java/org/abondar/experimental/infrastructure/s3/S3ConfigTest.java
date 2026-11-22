package org.abondar.experimental.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

class S3ConfigTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(S3Config.class)
          .withPropertyValues(
              "s3.bucket=books",
              "s3.region=us-east-1",
              "s3.connect-timeout=1s",
              "s3.request-timeout=2m");

  @Test
  void bindsEndpointPathStyleRegionAndTimeoutsWithoutOtherInfrastructure() {
    context
        .withPropertyValues("s3.endpoint=http://localhost:9000", "s3.path-style=true")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(S3Client.class).hasSingleBean(S3Properties.class);
              var properties = ctx.getBean(S3Properties.class);
              assertThat(properties.bucket()).isEqualTo("books");
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
              var configuration = ctx.getBean(S3Client.class).serviceClientConfiguration();
              assertThat(configuration.region()).isEqualTo(Region.US_EAST_1);
              assertThat(configuration.endpointOverride())
                  .contains(URI.create("http://localhost:9000"));
              assertThat(ctx.getBean(S3Properties.class).pathStyle()).isTrue();
              assertThat(configuration.overrideConfiguration().apiCallTimeout())
                  .contains(Duration.ofMinutes(2));
              assertThat(configuration.credentialsProvider())
                  .isInstanceOf(DefaultCredentialsProvider.class);
              assertThat(ctx)
                  .doesNotHaveBean("adminClient")
                  .doesNotHaveBean("keycloakHealthIndicator");
            });
  }

  @Test
  void missingEndpointPreservesAwsEndpointAndVirtualHostedAccess() {
    context.run(
        ctx -> {
          var configuration = ctx.getBean(S3Client.class).serviceClientConfiguration();
          assertThat(configuration.endpointOverride()).isEmpty();
          assertThat(ctx.getBean(S3Properties.class).pathStyle()).isFalse();
        });
  }
}
