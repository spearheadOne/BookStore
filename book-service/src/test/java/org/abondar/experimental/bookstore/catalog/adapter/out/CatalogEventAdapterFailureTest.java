package org.abondar.experimental.bookstore.catalog.adapter.out;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.abondar.experimental.bookstore.catalog.application.exception.CatalogEventPublicationException;
import org.abondar.experimental.bookstore.catalog.domain.Catalog;
import org.abondar.experimental.bookstore.common.Id;
import org.abondar.experimental.bookstore.config.TopicProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class CatalogEventAdapterFailureTest {

  @Mock private KafkaTemplate<String, byte[]> kafkaTemplate;
  private CatalogEventAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new CatalogEventAdapter(kafkaTemplate, new TopicProperties("books", "catalogs"));
  }

  @Test
  void propagatesFailedBrokerAcknowledgment() {
    var failure = new IllegalStateException("Broker rejected the message");
    when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
        .thenReturn(CompletableFuture.<SendResult<String, byte[]>>failedFuture(failure));

    var exception =
        assertThrows(
            CatalogEventPublicationException.class,
            () -> adapter.sendCatalogDeleted(Id.<Catalog>generate()));

    assertSame(failure, exception.getCause().getCause());
  }

  @Test
  void propagatesImmediateSendFailure() {
    var failure = new IllegalStateException("Producer unavailable");
    when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class))).thenThrow(failure);

    var exception =
        assertThrows(
            CatalogEventPublicationException.class,
            () -> adapter.sendCatalogDeleted(Id.<Catalog>generate()));

    assertSame(failure, exception.getCause());
  }
}
