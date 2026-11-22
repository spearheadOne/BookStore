package org.abondar.experimental.infrastructure.kafka;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class KafkaHealthInfo {
  boolean isHealthy;
  String clusterId;
  int nodes;
}
