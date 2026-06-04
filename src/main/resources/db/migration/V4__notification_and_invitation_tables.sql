-- V4__notification_and_invitation_tables.sql
-- ANSI SQL — compatible with SQLite and PostgreSQL (matches V1 pattern)

CREATE TABLE notification_log (
    id              INTEGER PRIMARY KEY,
    type            VARCHAR(50)  NOT NULL,
    recipient_id    INTEGER      REFERENCES users(id),
    recipient_email VARCHAR(255) NOT NULL,
    match_id        INTEGER      REFERENCES matches(id),
    sent_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference_key   VARCHAR(100) NOT NULL
);

CREATE UNIQUE INDEX uq_notif_ref_key ON notification_log(reference_key);
CREATE INDEX idx_notif_type          ON notification_log(type);
CREATE INDEX idx_notif_sent_at       ON notification_log(sent_at);

CREATE TABLE invitations (
    id            INTEGER PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    invited_by_id INTEGER      NOT NULL REFERENCES users(id),
    invited_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at   TIMESTAMP
);

CREATE UNIQUE INDEX uq_invitation_email ON invitations(email);
