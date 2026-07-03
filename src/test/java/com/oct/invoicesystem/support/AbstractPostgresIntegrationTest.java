package com.oct.invoicesystem.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Base class for integration tests that MUST run against a real PostgreSQL instance rather than
 * the H2 ({@code MODE=PostgreSQL}) database used by the default {@code test} profile.
 *
 * <p>H2 silently infers the SQL type of null bind parameters, so it cannot reproduce the
 * PostgreSQL {@code SQLGrammarException} raised when an untyped nullable parameter appears in a
 * {@code (:param IS NULL OR column = :param)} predicate (see PROB-038 / PROB-054). Any test that
 * needs to prove such a query is portable to production must extend this class.</p>
 *
 * <p>It targets the host-native development database described in {@code CLAUDE.md}
 * (PostgreSQL on {@code localhost:5433}, database {@code oct_invoice}). The connection is wired via
 * {@link DynamicPropertySource}, overriding the H2 settings of {@code application-test.yml}, and
 * Flyway is left disabled because that database already carries the full schema. When the database
 * is unreachable (e.g. CI without a local Postgres) the whole test is cleanly skipped via
 * {@link org.junit.jupiter.api.Assumptions}, so it never turns the suite red.</p>
 *
 * <p>Tests extending this class MUST stay read-only: they share the developer's database.</p>
 */
@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    private static final String HOST = "localhost";
    private static final int PORT = 5433;
    private static final String DB = "oct_invoice";
    private static final String USER = "postgres";
    private static final String PASSWORD = getDbPassword();

    private static String getDbPassword() {
        String envPass = System.getenv("DB_PASSWORD");
        if (envPass != null) return envPass;
        try {
            java.util.Properties props = new java.util.Properties();
            props.load(new java.io.FileInputStream(".env"));
            return props.getProperty("DB_PASSWORD", "postgres");
        } catch (Exception e) {
            return "postgres";
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // Gate context startup: if the dev Postgres is down, abort (skip) before Spring tries to
        // build a datasource against an unreachable host, which would fail the test instead.
        assumeTrue(isReachable(),
                "Local PostgreSQL " + HOST + ":" + PORT + " not reachable — skipping real-Postgres integration test");
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://" + HOST + ":" + PORT + "/" + DB);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    private static boolean isReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
