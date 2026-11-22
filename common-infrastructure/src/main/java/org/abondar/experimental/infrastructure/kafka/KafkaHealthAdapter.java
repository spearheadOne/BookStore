package org.abondar.experimental.infrastructure.kafka;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.common.KafkaException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component("kafkaHealthAdapter")
public class KafkaHealthAdapter {

  private static final int TIMEOUT_MILLIS = 2000;

  private final AdminClient adminClient;

  public KafkaHealthInfo getHealthInfo() {
    var info = KafkaHealthInfo.builder();

    try {
      var cluster =
          adminClient.describeCluster(new DescribeClusterOptions().timeoutMs(TIMEOUT_MILLIS));
      var clusterId = cluster.clusterId().get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
      var nodes = cluster.nodes().get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).size();
      return info.isHealthy(nodes > 0).clusterId(clusterId).nodes(nodes).build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.debug("Kafka health check interrupted", e);
      return info.isHealthy(false).build();
    } catch (ExecutionException | TimeoutException | KafkaException e) {
      log.error("Error getting kafka health info", e);
      return info.isHealthy(false).build();
    }
  }
}
