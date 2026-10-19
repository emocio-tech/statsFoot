package com.emocio.analysefoot;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Base PostgreSQL dédiée aux tests d'intégration (vidée à chaque test).
 * Les tests qui l'utilisent sont ignorés si elle n'est pas joignable.
 */
public final class PostgresTestSupport {

    private static final String URL = env("TEST_DB_URL", "jdbc:postgresql://localhost:5432/analysefoot_test");
    private static final String USERNAME = env("TEST_DB_USERNAME", "analysefoot");
    private static final String PASSWORD = env("TEST_DB_PASSWORD", "analysefoot");

    private PostgresTestSupport() {
    }

    /** Condition JUnit : {@code @EnabledIf("com.emocio.analysefoot.PostgresTestSupport#isAvailable")}. */
    public static boolean isAvailable() {
        try (Connection ignored = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USERNAME);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
