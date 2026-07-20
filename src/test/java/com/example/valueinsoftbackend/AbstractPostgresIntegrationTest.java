package com.example.valueinsoftbackend;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * P1-9: base class for Spring-context integration tests that run against a REAL PostgreSQL
 * (Testcontainers) with the full Flyway migration set applied — as opposed to
 * {@link AbstractIntegrationTest}, which uses in-memory H2 and cannot exercise Postgres-only
 * behaviour (schema-per-tenant, pgvector, ON CONFLICT ... RETURNING, partial indexes,
 * FOR UPDATE, plpgsql triggers/RLS).
 *
 * <p>Extend this for the isolation, finance-immutability (P1-7), and RLS (P1-6) suites so
 * their production-correct migrations/behaviour are actually verified. Requires a Docker
 * runtime (CI / local Docker). A single container is shared across the JVM.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("testpg")
@Testcontainers(disabledWithoutDocker = true)
@Tag("postgres")
public abstract class AbstractPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("vls_test")
            .withUsername("vls")
            .withPassword("vls");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;
}
