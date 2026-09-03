package dev.taskflow.identity;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real database.
 *
 * The container is a static singleton started once for the JVM and reused by every
 * subclass, rather than one container per test class. Flyway runs against it, so the
 * schema under test is the same schema production gets.
 *
 * Deliberately not @Transactional. Some rules are enforced by deferred constraint
 * triggers that only fire at COMMIT, and a test transaction that always rolls back
 * would never trigger them. Tests clean up explicitly instead.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
