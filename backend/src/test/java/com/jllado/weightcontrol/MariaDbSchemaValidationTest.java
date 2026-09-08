package com.jllado.weightcontrol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {
    "app.auth.google-client-id=test-client-id",
    "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"
})
class MariaDbSchemaValidationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        @ServiceConnection
        MariaDBContainer<?> database() {
            return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("weight_control");
        }
    }

    @Test
    void migrationsMatchTheHibernateSchema() {
    }
}
