package org.abondar.experimental.bookstore;

import org.abondar.experimental.bookstore.config.CacheProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataRedisTest
@Import(BaseRedisAdapterTest.RedisConfig.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@EnableConfigurationProperties(CacheProperties.class)
public abstract class BaseRedisAdapterTest {

  @Autowired protected StringRedisTemplate redisTemplate;

  @TestConfiguration(proxyBeanMethods = false)
  static class RedisConfig {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
      return new GenericContainer<>("redis:8.4-alpine").withExposedPorts(6379);
    }
  }
}
