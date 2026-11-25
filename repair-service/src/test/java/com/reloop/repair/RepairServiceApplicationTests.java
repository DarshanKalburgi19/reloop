package com.reloop.repair;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the whole Spring context starts: beans are wired,
 * the H2 datasource connects and JPA initialises.
 */
@SpringBootTest
class RepairServiceApplicationTests {

    @Test
    void contextLoads() {
        // No assertions needed: reaching this line means the context started.
    }
}
