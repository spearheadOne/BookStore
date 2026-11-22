package org.abondar.experimental.bookstore;

import org.abondar.experimental.bookstore.config.CacheProperties;
import org.abondar.experimental.bookstore.config.EpubProperties;
import org.abondar.experimental.bookstore.config.TopicProperties;
import org.abondar.experimental.infrastructure.kafka.KafkaHealthConfig;
import org.abondar.experimental.infrastructure.keycloak.KeycloakHealthIndicator;
import org.abondar.experimental.infrastructure.s3.S3Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({S3Config.class, KafkaHealthConfig.class, KeycloakHealthIndicator.class})
@EnableConfigurationProperties({CacheProperties.class, TopicProperties.class, EpubProperties.class})
public class BookServiceApplication {

  static void main(String[] args) {
    SpringApplication.run(BookServiceApplication.class, args);
  }
}
