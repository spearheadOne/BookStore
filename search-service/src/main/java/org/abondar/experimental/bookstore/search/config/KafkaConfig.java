package org.abondar.experimental.bookstore.search.config;

import org.abondar.experimental.bookstore.search.book.application.exception.BookContentEventException;
import org.abondar.experimental.bookstore.search.book.application.exception.BookMetadataEventException;
import org.abondar.experimental.bookstore.search.catalog.application.exception.CatalogEventException;
import org.apache.avro.message.BadHeaderException;
import org.apache.kafka.common.TopicPartition;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

  @Bean
  public DefaultErrorHandler errorHandler(
      KafkaTemplate<String, byte[]> kafkaTemplate, TopicProperties topicProperties) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, _) ->
                new TopicPartition(
                    record.topic() + topicProperties.dltPrefix(), record.partition()));
    recoverer.setFailIfSendResultIsError(true);

    var handler = new DefaultErrorHandler(recoverer);
    handler.addRetryableExceptions(BookMetadataEventException.class, CatalogEventException.class);
    handler.addNotRetryableExceptions(
        BadHeaderException.class, IllegalArgumentException.class, BookContentEventException.class);
    handler.setBackOffFunction(
        (record, ex) -> {
          for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof OpenSearchException openSearchException
                && openSearchException.status() == 400) {
              return new FixedBackOff(0L, 0L);
            }
          }

          return new FixedBackOff(topicProperties.backoffInterval(), topicProperties.maxAttempts());
        });

    return handler;
  }
}
