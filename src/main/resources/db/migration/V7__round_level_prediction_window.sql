-- =============================================================================
-- V7__round_level_prediction_window.sql
-- Move prediction window from per-match to per-round (RoundWindow entity).
-- =============================================================================

-- 1. Create round_windows table
CREATE TABLE round_windows (
    id                 INTEGER PRIMARY KEY,
    round_label        VARCHAR(50)  NOT NULL UNIQUE,
    override_status    VARCHAR(20),
    auto_opens_at      TIMESTAMP,
    auto_closes_at     TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Populate from existing match data
INSERT INTO round_windows (round_label, auto_opens_at, auto_closes_at)
SELECT m.round_label,
       DATETIME(MIN(m.kickoff_time), '-24 hours'),
       DATETIME(MAX(m.kickoff_time), '-1 hours')
FROM matches m
WHERE m.round_label IS NOT NULL
GROUP BY m.round_label;

-- 3. Rebuild matches table without prediction window columns
CREATE TABLE matches_new (
    id                           INTEGER PRIMARY KEY,
    external_id                  VARCHAR(50),
    stage                        VARCHAR(50)  NOT NULL,
    group_id                     INTEGER REFERENCES groups(id),
    match_number                 INTEGER      NOT NULL,
    round_label                  VARCHAR(50),
    home_team_id                 INTEGER REFERENCES teams(id),
    away_team_id                 INTEGER REFERENCES teams(id),
    home_team_placeholder        VARCHAR(100),
    away_team_placeholder        VARCHAR(100),
    kickoff_time                 TIMESTAMP    NOT NULL,
    venue                        VARCHAR(200),
    city                         VARCHAR(100),
    status                       VARCHAR(50)  NOT NULL DEFAULT 'SCHEDULED',
    home_score                   INTEGER,
    away_score                   INTEGER,
    home_score_90                INTEGER,
    away_score_90                INTEGER,
    lineup_fetched               INTEGER      NOT NULL DEFAULT 0,
    result_entered_at            TIMESTAMP,
    result_entered_by_id         INTEGER REFERENCES users(id),
    created_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(match_number)
);

INSERT INTO matches_new (
    id, external_id, stage, group_id, match_number, round_label,
    home_team_id, away_team_id, home_team_placeholder, away_team_placeholder,
    kickoff_time, venue, city, status, home_score, away_score,
    home_score_90, away_score_90, lineup_fetched,
    result_entered_at, result_entered_by_id, created_at, updated_at
)
SELECT
    id, external_id, stage, group_id, match_number, round_label,
    home_team_id, away_team_id, home_team_placeholder, away_team_placeholder,
    kickoff_time, venue, city, status, home_score, away_score,
    home_score_90, away_score_90, lineup_fetched,
    result_entered_at, result_entered_by_id, created_at, updated_at
FROM matches;

DROP TABLE matches;
ALTER TABLE matches_new RENAME TO matches;

-- 4. Recreate all matches indexes (no prediction_window_idx)
CREATE INDEX matches_stage_idx ON matches(stage);
CREATE INDEX matches_kickoff_time_idx ON matches(kickoff_time);
CREATE INDEX matches_status_idx ON matches(status);
CREATE INDEX matches_group_id_idx ON matches(group_id);
CREATE INDEX matches_home_team_id_idx ON matches(home_team_id);
CREATE INDEX matches_away_team_id_idx ON matches(away_team_id);
CREATE INDEX matches_lineup_fetched_idx ON matches(lineup_fetched);
CREATE INDEX matches_round_label_idx ON matches(round_label);
