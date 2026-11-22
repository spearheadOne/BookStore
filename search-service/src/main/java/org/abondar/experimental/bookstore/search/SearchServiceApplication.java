package org.abondar.experimental.bookstore.search;

import org.abondar.experimental.bookstore.search.config.BookContentProperties;
import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.abondar.experimental.bookstore.search.config.TopicProperties;
import org.abondar.experimental.infrastructure.kafka.KafkaHealthConfig;
import org.abondar.experimental.infrastructure.keycloak.KeycloakHealthIndicator;
import org.abondar.experimental.infrastructure.s3.S3Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({S3Config.class, KafkaHealthConfig.class, KeycloakHealthIndicator.class})
@EnableConfigurationProperties({
  OpenSearchProperties.class,
  TopicProperties.class,
  BookContentProperties.class
})
public class SearchServiceApplication {

  static void main(String[] args) {
    SpringApplication.run(SearchServiceApplication.class, args);
  }
}
