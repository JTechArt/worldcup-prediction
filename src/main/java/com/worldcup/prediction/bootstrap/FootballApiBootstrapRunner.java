package com.worldcup.prediction.bootstrap;

import com.worldcup.prediction.integration.football.BootstrapSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap")
@Slf4j
@RequiredArgsConstructor
public class FootballApiBootstrapRunner implements CommandLineRunner {

    private final BootstrapSyncService bootstrapSyncService;

    @Override
    public void run(String... args) {
        String result = bootstrapSyncService.runFullBootstrap();
        log.info("Bootstrap result:\n{}", result);
        log.info("Next: sqlite3 worldcup.db .dump > backup.sql, then update R__wc2026_data.sql");
    }
}
