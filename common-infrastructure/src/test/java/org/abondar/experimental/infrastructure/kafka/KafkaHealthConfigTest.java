package org.abondar.experimental.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaAdmin;

class KafkaHealthConfigTest {
  @Test
  void importsOnlyKafkaHealthAndClosesAdminClient() {
    var properties =
        Map.<String, Object>of(
            "bootstrap.servers", "localhost:9092", "security.protocol", "SASL_SSL");
    var kafkaAdmin = new KafkaAdmin(properties);
    var admin = mock(AdminClient.class);
    try (var factory = mockStatic(AdminClient.class)) {
      factory
          .when(() -> AdminClient.create(kafkaAdmin.getConfigurationProperties()))
          .thenReturn(admin);
      new ApplicationContextRunner()
          .withUserConfiguration(KafkaHealthConfig.class)
          .withBean(KafkaAdmin.class, () -> kafkaAdmin)
          .run(
              context -> {
                assertThat(context)
                    .hasSingleBean(AdminClient.class)
                    .hasSingleBean(KafkaHealthAdapter.class)
                    .hasSingleBean(KafkaHealthIndicator.class);
                assertThat(context).hasBean("kafkaHealthIndicator");
                assertThat(context)
                    .doesNotHaveBean("keycloakHealthIndicator")
                    .doesNotHaveBean("s3Client");
                factory.verify(() -> AdminClient.create(kafkaAdmin.getConfigurationProperties()));
              });
    }
    verify(admin).close();
  }
}
