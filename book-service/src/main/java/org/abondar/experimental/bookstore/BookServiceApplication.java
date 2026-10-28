package org.abondar.experimental.bookstore;

import org.abondar.experimental.bookstore.config.CacheProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CacheProperties.class)
public class BookServiceApplication {

  static void main(String[] args) {
    SpringApplication.run(BookServiceApplication.class, args);
  }
}
