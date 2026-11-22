package org.abondar.experimental.bookstore.search.actuator.opensearch;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSearchHealthAdapter {

  private final OpenSearchClient client;

  public OpensearchHealthInfo getHealthInfo() {
    var info = OpensearchHealthInfo.builder();
    try {
      var clusterHealth = client.cluster().health();
      info.clusterName(clusterHealth.clusterName())
          .status(clusterHealth.status().jsonValue())
          .numberOfNodes(clusterHealth.numberOfNodes())
          .numberOfDataNodes(clusterHealth.numberOfDataNodes())
          .activePrimaryShards(clusterHealth.activePrimaryShards())
          .unassignedShards(clusterHealth.unassignedShards())
          .timedOut(clusterHealth.timedOut());
      var infoResponse = client.info();
      info.luceneVersion(infoResponse.version().luceneVersion())
          .version(infoResponse.version().number())
          .buildDate(infoResponse.version().buildDate())
          .buildHash(infoResponse.version().buildHash());
      return info.healthy(
              !clusterHealth.timedOut()
                  && (clusterHealth.status() == HealthStatus.Green
                      || clusterHealth.status() == HealthStatus.Yellow))
          .build();
    } catch (IOException | OpenSearchException e) {
      log.error("OpenSearch health check failed", e);
      return info.healthy(false).build();
    }
  }
}
