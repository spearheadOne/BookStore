package org.abondar.experimental.bookstore;

import org.abondar.experimental.bookstore.config.CacheProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataRedisTest
@ContextConfiguration(classes = BaseRedisAdapterTest.RedisConfig.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
public abstract class BaseRedisAdapterTest {

  @Autowired protected StringRedisTemplate redisTemplate;

  @Configuration(proxyBeanMethods = false)
  @TestComponent
  @AutoConfigurationPackage
  @EnableConfigurationProperties(CacheProperties.class)
  static class RedisConfig {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
      return new GenericContainer<>("redis:8.4-alpine").withExposedPorts(6379);
    }
  }
}
