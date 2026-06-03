package com.worldcup.prediction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WorldCupPredictionApplicationTests {

    @Test
    void contextLoads() {
        // Spring context must start up cleanly with Flyway migrations applied against SQLite
    }
}
