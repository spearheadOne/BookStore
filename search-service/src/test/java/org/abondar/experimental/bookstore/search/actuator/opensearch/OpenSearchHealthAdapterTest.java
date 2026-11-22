package org.abondar.experimental.bookstore.search.actuator.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.cluster.OpenSearchClusterClient;
import org.opensearch.client.opensearch.core.InfoResponse;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class OpenSearchHealthAdapterTest {
  @Mock private OpenSearchClient client;
  @Mock private OpenSearchClusterClient cluster;
  @Mock private HealthResponse clusterHealth;
  private OpenSearchHealthAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new OpenSearchHealthAdapter(client);
  }

  @ParameterizedTest
  @CsvSource({"Green,false,true", "Yellow,false,true", "Red,false,false", "Green,true,false"})
  void mapsClusterStatusAndBuildDetails(HealthStatus status, boolean timedOut, boolean healthy)
      throws IOException {
    when(client.cluster()).thenReturn(cluster);
    when(cluster.health()).thenReturn(clusterHealth);
    when(clusterHealth.clusterName()).thenReturn("search-cluster");
    when(clusterHealth.status()).thenReturn(status);
    when(clusterHealth.timedOut()).thenReturn(timedOut);
    when(clusterHealth.numberOfNodes()).thenReturn(3);
    when(clusterHealth.numberOfDataNodes()).thenReturn(2);
    when(clusterHealth.activePrimaryShards()).thenReturn(6);
    when(clusterHealth.unassignedShards()).thenReturn(1);
    var info = mock(InfoResponse.class, RETURNS_DEEP_STUBS);
    when(client.info()).thenReturn(info);
    when(info.version().number()).thenReturn("3.9.0");
    when(info.version().luceneVersion()).thenReturn("10.3.0");
    when(info.version().buildDate()).thenReturn("2026-01-01");
    when(info.version().buildHash()).thenReturn("build-hash");

    var result = adapter.getHealthInfo();
    assertEquals(healthy, result.isHealthy());
    var healthAdapter = mock(OpenSearchHealthAdapter.class);
    when(healthAdapter.getHealthInfo()).thenReturn(result);
    var health = new OpensearchHealthIndicator(healthAdapter).health();
    assertEquals(healthy ? Status.UP : Status.DOWN, health.getStatus());
    assertEquals("search-cluster", health.getDetails().get("clusterName"));
    assertEquals(status.jsonValue(), health.getDetails().get("status"));
    assertEquals(3, health.getDetails().get("numberOfNodes"));
    assertEquals(2, health.getDetails().get("numberOfDataNodes"));
    assertEquals(6, health.getDetails().get("activePrimaryShards"));
    assertEquals(1, health.getDetails().get("unassignedShards"));
    assertEquals(timedOut, health.getDetails().get("timedOut"));
    assertEquals("3.9.0", health.getDetails().get("version"));
    assertEquals("10.3.0", health.getDetails().get("luceneVersion"));
    assertEquals("2026-01-01", health.getDetails().get("buildDate"));
    assertEquals("build-hash", health.getDetails().get("buildHash"));
    verify(client).info();
    verify(cluster).health();
  }

  @Test
  void unavailableClusterReturnsDown() throws IOException {
    when(client.cluster()).thenReturn(cluster);
    when(cluster.health()).thenThrow(new IOException("Unavailable"));

    var health = new OpensearchHealthIndicator(adapter).health();
    assertEquals(Status.DOWN, health.getStatus());
    assertTrue(health.getDetails().isEmpty());
  }

  @Test
  void emptyClusterIsHealthyWithoutCheckingIndexExistence() throws IOException {
    when(client.cluster()).thenReturn(cluster);
    when(cluster.health()).thenReturn(clusterHealth);
    when(clusterHealth.clusterName()).thenReturn("empty-cluster");
    when(clusterHealth.status()).thenReturn(HealthStatus.Green);
    when(client.info()).thenReturn(mock(InfoResponse.class, RETURNS_DEEP_STUBS));

    var result = adapter.getHealthInfo();
    assertTrue(result.isHealthy());
    assertEquals(0, result.getActivePrimaryShards());
  }

  @Test
  void infoFailureReturnsDownWithAvailableClusterDiagnostics() throws IOException {
    when(client.cluster()).thenReturn(cluster);
    when(cluster.health()).thenReturn(clusterHealth);
    when(clusterHealth.clusterName()).thenReturn("search-cluster");
    when(clusterHealth.status()).thenReturn(HealthStatus.Green);
    when(client.info()).thenThrow(new IOException("Unavailable"));

    var result = adapter.getHealthInfo();
    assertFalse(result.isHealthy());
    var healthAdapter = mock(OpenSearchHealthAdapter.class);
    when(healthAdapter.getHealthInfo()).thenReturn(result);
    var health = new OpensearchHealthIndicator(healthAdapter).health();
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("search-cluster", health.getDetails().get("clusterName"));
    assertFalse(health.getDetails().containsKey("version"));
  }
}
