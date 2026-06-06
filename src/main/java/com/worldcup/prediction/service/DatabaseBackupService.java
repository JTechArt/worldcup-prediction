package com.worldcup.prediction.service;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Service
public class DatabaseBackupService {

    private final DataSource dataSource;
    private final String dbPath;

    public DatabaseBackupService(DataSource dataSource,
                                 @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.dataSource = dataSource;
        this.dbPath = parseSqlitePath(datasourceUrl);
    }

    private String parseSqlitePath(String url) {
        if (url == null || !url.startsWith("jdbc:sqlite:")) {
            return null;
        }
        return url.substring("jdbc:sqlite:".length());
    }

    public boolean isSqlite() {
        return dbPath != null;
    }

    public String getDbPath() {
        return dbPath;
    }

    /**
     * Creates a clean backup of the database using SQLite's VACUUM INTO.
     * Returns the path to the backup file (caller must delete it after use).
     */
    public Path createBackup() throws SQLException, IOException {
        if (!isSqlite()) {
            throw new IllegalStateException("Backup is only supported for SQLite databases");
        }
        Path backupFile = Files.createTempFile("worldcup-backup-", ".db");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM INTO '" + backupFile.toAbsolutePath().toString().replace("'", "''") + "'");
        }
        log.info("Database backup created at {}", backupFile);
        return backupFile;
    }

    /**
     * Restores the database from an uploaded stream.
     * Checkpoints WAL, replaces the file, and evicts pooled connections.
     */
    public void restore(InputStream uploadedStream) throws SQLException, IOException {
        if (!isSqlite()) {
            throw new IllegalStateException("Restore is only supported for SQLite databases");
        }
        Path dbFile = resolveDbPath();

        // Write uploaded content to a temp file first (validate it's a real SQLite file)
        Path tempRestore = Files.createTempFile("worldcup-restore-", ".db");
        try {
            Files.copy(uploadedStream, tempRestore, StandardCopyOption.REPLACE_EXISTING);
            validateSqliteFile(tempRestore);

            // Checkpoint WAL so all writes are flushed to the main db file
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA wal_checkpoint(FULL)");
            }

            // Atomically replace the database file
            Files.copy(tempRestore, dbFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Database restored from uploaded file to {}", dbFile);

            // Evict pooled connections so they reconnect to the new file
            if (dataSource instanceof HikariDataSource hikari) {
                hikari.getHikariPoolMXBean().softEvictConnections();
                log.info("HikariCP connections soft-evicted after restore");
            }
        } finally {
            Files.deleteIfExists(tempRestore);
        }
    }

    private Path resolveDbPath() {
        Path p = Paths.get(dbPath);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p);
        }
        return p;
    }

    private void validateSqliteFile(Path file) throws IOException {
        byte[] header = Files.readAllBytes(file);
        if (header.length < 16) {
            throw new IOException("Uploaded file is too small to be a valid SQLite database");
        }
        String magic = new String(header, 0, 16);
        if (!magic.startsWith("SQLite format 3")) {
            throw new IOException("Uploaded file is not a valid SQLite database");
        }
    }
}
