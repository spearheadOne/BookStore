package org.abondar.experimental.bookstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
public class S3Config {

  @Bean(destroyMethod = "close")
  public S3Client s3Client(S3Properties properties) {
    var builder =
        S3Client.builder()
            .region(Region.of(properties.region()))
            .forcePathStyle(properties.pathStyle())
            .httpClientBuilder(
                Apache5HttpClient.builder().connectionTimeout(properties.connectTimeout()))
            .overrideConfiguration(config -> config.apiCallTimeout(properties.requestTimeout()));

    if (properties.endpoint() != null) {
      builder.endpointOverride(properties.endpoint());
    }

    return builder.build();
  }
}
