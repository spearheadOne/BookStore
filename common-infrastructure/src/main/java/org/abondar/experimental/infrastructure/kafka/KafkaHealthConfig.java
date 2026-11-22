package org.abondar.experimental.infrastructure.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration(proxyBeanMethods = false)
@Import({KafkaHealthAdapter.class, KafkaHealthIndicator.class})
public class KafkaHealthConfig {

  @Bean(destroyMethod = "close")
  AdminClient adminClient(KafkaAdmin kafkaAdmin) {
    return AdminClient.create(kafkaAdmin.getConfigurationProperties());
  }
}
