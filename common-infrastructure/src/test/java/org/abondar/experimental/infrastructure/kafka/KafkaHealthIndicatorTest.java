package org.abondar.experimental.infrastructure.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorTest {
  @Mock private AdminClient admin;
  @Mock private DescribeClusterResult cluster;
  private KafkaHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    indicator = new KafkaHealthIndicator(new KafkaHealthAdapter(admin));
  }

  @Test
  void healthyClusterExposesClusterIdAndBrokerCount() {
    when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);
    when(cluster.clusterId()).thenReturn(KafkaFuture.completedFuture("cluster-id"));
    when(cluster.nodes())
        .thenReturn(KafkaFuture.completedFuture(List.of(new Node(1, "kafka", 9092))));

    var health = indicator.health();
    assertEquals(Status.UP, health.getStatus());
    assertEquals("cluster-id", health.getDetails().get("clusterId"));
    assertEquals(1, health.getDetails().get("nodes"));
    var options = ArgumentCaptor.forClass(DescribeClusterOptions.class);
    verify(admin).describeCluster(options.capture());
    assertEquals(2000, options.getValue().timeoutMs());
  }

  @Test
  void noBrokersReturnsDown() {
    when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);
    when(cluster.clusterId()).thenReturn(KafkaFuture.completedFuture("cluster-id"));
    when(cluster.nodes()).thenReturn(KafkaFuture.completedFuture(List.of()));
    assertEquals(Status.DOWN, indicator.health().getStatus());
  }

  @Test
  void failedClusterRequestReturnsDown() {
    when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);
    var failure = new KafkaFutureImpl<String>();
    failure.completeExceptionally(new KafkaException("Broker unavailable"));
    when(cluster.clusterId()).thenReturn(failure);
    var health = indicator.health();
    assertEquals(Status.DOWN, health.getStatus());
    assertTrue(health.getDetails().isEmpty());
  }

  @Test
  void synchronousClientFailureReturnsDown() {
    when(admin.describeCluster(any(DescribeClusterOptions.class)))
        .thenThrow(new KafkaException("Client closed"));
    assertEquals(Status.DOWN, indicator.health().getStatus());
  }

  @Test
  void brokerLookupTimeoutReturnsDown() throws Exception {
    when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);
    when(cluster.clusterId()).thenReturn(KafkaFuture.completedFuture("cluster-id"));
    @SuppressWarnings("unchecked")
    KafkaFuture<Collection<Node>> nodes = mock(KafkaFuture.class);
    when(cluster.nodes()).thenReturn(nodes);
    when(nodes.get(anyLong(), eq(TimeUnit.MILLISECONDS)))
        .thenThrow(new TimeoutException("Timed out"));

    assertEquals(Status.DOWN, indicator.health().getStatus());
    verify(nodes).get(2000, TimeUnit.MILLISECONDS);
  }

  @Test
  void interruptionReturnsDownAndRestoresInterruptFlag() throws Exception {
    when(admin.describeCluster(any(DescribeClusterOptions.class))).thenReturn(cluster);
    @SuppressWarnings("unchecked")
    KafkaFuture<String> id = mock(KafkaFuture.class);
    when(cluster.clusterId()).thenReturn(id);
    when(id.get(anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new InterruptedException());

    assertFalse(Thread.currentThread().isInterrupted());
    try {
      assertEquals(Status.DOWN, indicator.health().getStatus());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }
}
