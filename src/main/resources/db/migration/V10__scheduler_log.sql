-- V10__scheduler_log.sql
-- ANSI SQL — compatible with SQLite and PostgreSQL (matches V1 pattern)

CREATE TABLE scheduler_log (
    id              INTEGER PRIMARY KEY,
    job_name        VARCHAR(100)  NOT NULL,
    status          VARCHAR(15)   NOT NULL,
    started_at      TIMESTAMP     NOT NULL,
    finished_at     TIMESTAMP,
    items_processed INTEGER       NOT NULL DEFAULT 0,
    message         VARCHAR(500),
    error_detail    TEXT
);

CREATE INDEX scheduler_log_job_name_idx   ON scheduler_log (job_name);
CREATE INDEX scheduler_log_started_at_idx ON scheduler_log (started_at);
