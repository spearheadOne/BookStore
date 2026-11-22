package org.abondar.experimental.bookstore.search.actuator.opensearch;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpensearchHealthIndicator implements HealthIndicator {

  private final OpenSearchHealthAdapter adapter;

  @Override
  public Health health() {
    var healthInfo = adapter.getHealthInfo();
    var health = healthInfo.isHealthy() ? Health.up() : Health.down();
    if (healthInfo.getClusterName() != null) {
      health
          .withDetail("clusterName", healthInfo.getClusterName())
          .withDetail("status", healthInfo.getStatus())
          .withDetail("numberOfNodes", healthInfo.getNumberOfNodes())
          .withDetail("numberOfDataNodes", healthInfo.getNumberOfDataNodes())
          .withDetail("activePrimaryShards", healthInfo.getActivePrimaryShards())
          .withDetail("unassignedShards", healthInfo.getUnassignedShards())
          .withDetail("timedOut", healthInfo.isTimedOut());
    }
    if (healthInfo.getVersion() != null) {
      health
          .withDetail("luceneVersion", healthInfo.getLuceneVersion())
          .withDetail("version", healthInfo.getVersion())
          .withDetail("buildDate", healthInfo.getBuildDate())
          .withDetail("buildHash", healthInfo.getBuildHash());
    }
    return health.build();
  }
}
