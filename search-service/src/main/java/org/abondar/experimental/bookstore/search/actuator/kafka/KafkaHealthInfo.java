package org.abondar.experimental.bookstore.search.actuator.kafka;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class KafkaHealthInfo {
  boolean isHealthy;
  String clusterId;
  int nodes;
}
