package dev.taskflow.tasks;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real database.
 *
 * Testcontainers rather than an in-memory database because the 3-to-6 column rule
 * is a deferred constraint trigger written in PL/pgSQL. H2 has no equivalent, so
 * an in-memory database would silently skip the rule this service most needs to
 * enforce.
 *
 * Deliberately not @Transactional: a deferred trigger fires at COMMIT, and a test
 * transaction that always rolls back would never fire it.
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

