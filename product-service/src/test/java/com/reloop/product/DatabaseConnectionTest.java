package com.reloop.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the service really connects to an H2 database (Phase 1 requirement:
 * "database connections"). Also guards against tests accidentally hitting the
 * file-based demo database instead of the in-memory test database.
 */
@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void h2InMemoryDatabaseIsReachable() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2), "Database connection should be valid");
            assertEquals("H2", connection.getMetaData().getDatabaseProductName());
            String url = connection.getMetaData().getURL();
            assertTrue(url.contains(":mem:"), "Tests must use the in-memory H2 database, got: " + url);
        }
    }
}
