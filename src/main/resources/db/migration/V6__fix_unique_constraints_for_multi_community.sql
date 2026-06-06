-- =============================================================================
-- V6__fix_unique_constraints_for_multi_community.sql
-- Remove old per-user unique constraints that block multi-community usage.
-- SQLite cannot DROP CONSTRAINT, so we recreate affected tables.
-- =============================================================================

-- ---- tournament_winner_predictions ----

CREATE TABLE tournament_winner_predictions_new (
    id             INTEGER PRIMARY KEY,
    user_id        INTEGER   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id        INTEGER   NOT NULL REFERENCES teams(id),
    community_id   INTEGER   NOT NULL REFERENCES communities(id),
    points_awarded INTEGER   NOT NULL DEFAULT 0,
    scored         INTEGER   NOT NULL DEFAULT 0,
    submitted_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tournament_winner_predictions_new
    (id, user_id, team_id, community_id, points_awarded, scored, submitted_at, updated_at)
SELECT id, user_id, team_id, community_id, points_awarded, scored, submitted_at, updated_at
FROM tournament_winner_predictions;

DROP TABLE tournament_winner_predictions;
ALTER TABLE tournament_winner_predictions_new RENAME TO tournament_winner_predictions;

CREATE UNIQUE INDEX twp_user_community_idx ON tournament_winner_predictions(user_id, community_id);
CREATE INDEX twp_user_id_idx ON tournament_winner_predictions(user_id);
CREATE INDEX twp_team_id_idx ON tournament_winner_predictions(team_id);

-- ---- predictions ----

CREATE TABLE predictions_new (
    id               INTEGER PRIMARY KEY,
    user_id          INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    match_id         INTEGER     NOT NULL REFERENCES matches(id),
    community_id     INTEGER     NOT NULL REFERENCES communities(id),
    predicted_home   INTEGER     NOT NULL,
    predicted_away   INTEGER     NOT NULL,
    score_result     VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    points_awarded   INTEGER     NOT NULL DEFAULT 0,
    submitted_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at        TIMESTAMP,
    edited_by_admin  INTEGER     NOT NULL DEFAULT 0,
    admin_edit_note  VARCHAR(500)
);

INSERT INTO predictions_new
    (id, user_id, match_id, community_id, predicted_home, predicted_away,
     score_result, points_awarded, submitted_at, updated_at, locked_at,
     edited_by_admin, admin_edit_note)
SELECT id, user_id, match_id, community_id, predicted_home, predicted_away,
       score_result, points_awarded, submitted_at, updated_at, locked_at,
       edited_by_admin, admin_edit_note
FROM predictions;

DROP TABLE predictions;
ALTER TABLE predictions_new RENAME TO predictions;

CREATE UNIQUE INDEX predictions_user_match_community_idx ON predictions(user_id, match_id, community_id);
CREATE INDEX predictions_user_id_idx ON predictions(user_id);
CREATE INDEX predictions_match_id_idx ON predictions(match_id);
CREATE INDEX predictions_score_result_idx ON predictions(score_result);
