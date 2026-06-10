-- =============================================================================
-- V9__round_submissions.sql
-- Track per-user, per-community, per-round prediction submission state.
-- Backfills existing prediction data on first run.
-- =============================================================================

CREATE TABLE round_submissions (
    id            INTEGER PRIMARY KEY,
    user_id       INTEGER      NOT NULL REFERENCES users(id)       ON DELETE CASCADE,
    community_id  INTEGER      NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    round_label   VARCHAR(50)  NOT NULL,
    submitted_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_round_submissions UNIQUE (user_id, community_id, round_label)
);

CREATE INDEX round_submissions_community_round_idx ON round_submissions(community_id, round_label);

-- Backfill: one row per (user, community, round) that already has predictions.
-- Uses MIN(submitted_at) from predictions as the submission timestamp.
INSERT INTO round_submissions (user_id, community_id, round_label, submitted_at)
SELECT p.user_id, p.community_id, m.round_label, MIN(p.submitted_at)
FROM predictions p
JOIN matches m ON p.match_id = m.id
WHERE m.round_label IS NOT NULL
GROUP BY p.user_id, p.community_id, m.round_label;
