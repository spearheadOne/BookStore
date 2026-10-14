package org.abondar.experimental.bookstore;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(BaseAdapterTest.PostgresConfig.class)
public class BaseAdapterTest {

    @Autowired
    protected DSLContext dslContext;

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresConfig {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgreSQLContainer() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }
    }
}
