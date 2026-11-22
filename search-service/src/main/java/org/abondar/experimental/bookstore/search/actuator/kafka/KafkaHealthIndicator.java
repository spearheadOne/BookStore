package org.abondar.experimental.bookstore.search.actuator.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaHealthIndicator implements HealthIndicator {

  private final KafkaHealthAdapter adapter;

  @Override
  public Health health() {
    var info = adapter.getHealthInfo();

    if (info.isHealthy()) {
      return Health.up()
          .withDetail("clusterId", info.getClusterId())
          .withDetail("nodes", info.getNodes())
          .build();
    } else {
      return Health.down().build();
    }
  }
}
