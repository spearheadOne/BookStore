package org.abondar.experimental.bookstore.search;

import org.abondar.experimental.bookstore.search.config.OpenSearchProperties;
import org.abondar.experimental.bookstore.search.config.TopicProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({OpenSearchProperties.class, TopicProperties.class})
public class SearchServiceApplication {

  static void main(String[] args) {
    SpringApplication.run(SearchServiceApplication.class, args);
  }
}
