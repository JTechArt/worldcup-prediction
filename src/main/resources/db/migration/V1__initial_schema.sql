-- =============================================================================
-- V1__initial_schema.sql
-- World Cup 2026 Prediction Game — Initial Database Schema
-- ANSI SQL — compatible with SQLite and PostgreSQL
-- =============================================================================

-- -----------------------------------------------------------------------------
-- USERS
-- -----------------------------------------------------------------------------

CREATE TABLE users (
    id                   INTEGER PRIMARY KEY,
    email                VARCHAR(255) NOT NULL,
    first_name           VARCHAR(100) NOT NULL,
    last_name            VARCHAR(100) NOT NULL,
    display_name         VARCHAR(200),
    avatar_url           VARCHAR(1000),
    status               VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    role                 VARCHAR(50)  NOT NULL DEFAULT 'PARTICIPANT',
    total_points         INTEGER      NOT NULL DEFAULT 0,
    exact_score_count    INTEGER      NOT NULL DEFAULT 0,
    correct_winner_count INTEGER      NOT NULL DEFAULT 0,
    correct_draw_count   INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at          TIMESTAMP,
    approved_by_id       INTEGER REFERENCES users(id)
);

CREATE UNIQUE INDEX users_email_idx ON users(email);
CREATE INDEX users_status_idx ON users(status);
CREATE INDEX users_role_idx ON users(role);
CREATE INDEX users_total_points_idx ON users(total_points);

-- -----------------------------------------------------------------------------
-- OAUTH IDENTITIES
-- -----------------------------------------------------------------------------

CREATE TABLE oauth_identities (
    id               INTEGER PRIMARY KEY,
    user_id          INTEGER      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         VARCHAR(50)  NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    avatar_url       VARCHAR(1000),
    linked_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at    TIMESTAMP,
    UNIQUE(provider, provider_subject)
);

CREATE INDEX oauth_identities_user_id_idx ON oauth_identities(user_id);
CREATE INDEX oauth_identities_email_idx ON oauth_identities(email);

-- -----------------------------------------------------------------------------
-- TEAMS
-- -----------------------------------------------------------------------------

CREATE TABLE teams (
    id            INTEGER PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    short_name    VARCHAR(50),
    fifa_code     VARCHAR(3)   NOT NULL,
    flag_code     VARCHAR(10)  NOT NULL,
    confederation VARCHAR(20),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(fifa_code)
);

CREATE INDEX teams_name_idx ON teams(name);

-- -----------------------------------------------------------------------------
-- GROUPS
-- -----------------------------------------------------------------------------

CREATE TABLE groups (
    id         INTEGER PRIMARY KEY,
    name       VARCHAR(2) NOT NULL,
    created_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name)
);

CREATE TABLE group_teams (
    group_id INTEGER NOT NULL REFERENCES groups(id),
    team_id  INTEGER NOT NULL REFERENCES teams(id),
    PRIMARY KEY (group_id, team_id)
);

CREATE TABLE group_standings (
    id              INTEGER PRIMARY KEY,
    group_id        INTEGER NOT NULL REFERENCES groups(id),
    team_id         INTEGER NOT NULL REFERENCES teams(id),
    played          INTEGER NOT NULL DEFAULT 0,
    won             INTEGER NOT NULL DEFAULT 0,
    drawn           INTEGER NOT NULL DEFAULT 0,
    lost            INTEGER NOT NULL DEFAULT 0,
    goals_for       INTEGER NOT NULL DEFAULT 0,
    goals_against   INTEGER NOT NULL DEFAULT 0,
    goal_difference INTEGER NOT NULL DEFAULT 0,
    points          INTEGER NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(group_id, team_id)
);

CREATE INDEX group_standings_group_id_idx ON group_standings(group_id);

-- -----------------------------------------------------------------------------
-- MATCHES
-- -----------------------------------------------------------------------------

CREATE TABLE matches (
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
    prediction_window_open       INTEGER      NOT NULL DEFAULT 0,
    prediction_window_opens_at   TIMESTAMP,
    prediction_window_closes_at  TIMESTAMP,
    result_entered_at            TIMESTAMP,
    result_entered_by_id         INTEGER REFERENCES users(id),
    created_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(match_number)
);

CREATE INDEX matches_stage_idx ON matches(stage);
CREATE INDEX matches_kickoff_time_idx ON matches(kickoff_time);
CREATE INDEX matches_status_idx ON matches(status);
CREATE INDEX matches_group_id_idx ON matches(group_id);
CREATE INDEX matches_home_team_id_idx ON matches(home_team_id);
CREATE INDEX matches_away_team_id_idx ON matches(away_team_id);
CREATE INDEX matches_prediction_window_idx ON matches(prediction_window_open, prediction_window_closes_at);

-- -----------------------------------------------------------------------------
-- PREDICTIONS
-- -----------------------------------------------------------------------------

CREATE TABLE predictions (
    id               INTEGER PRIMARY KEY,
    user_id          INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    match_id         INTEGER     NOT NULL REFERENCES matches(id),
    predicted_home   INTEGER     NOT NULL,
    predicted_away   INTEGER     NOT NULL,
    score_result     VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    points_awarded   INTEGER     NOT NULL DEFAULT 0,
    submitted_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at        TIMESTAMP,
    edited_by_admin  INTEGER     NOT NULL DEFAULT 0,
    admin_edit_note  VARCHAR(500),
    UNIQUE(user_id, match_id)
);

CREATE INDEX predictions_user_id_idx ON predictions(user_id);
CREATE INDEX predictions_match_id_idx ON predictions(match_id);
CREATE INDEX predictions_score_result_idx ON predictions(score_result);

-- -----------------------------------------------------------------------------
-- TOURNAMENT WINNER PREDICTIONS
-- -----------------------------------------------------------------------------

CREATE TABLE tournament_winner_predictions (
    id             INTEGER PRIMARY KEY,
    user_id        INTEGER   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id        INTEGER   NOT NULL REFERENCES teams(id),
    points_awarded INTEGER   NOT NULL DEFAULT 0,
    scored         INTEGER   NOT NULL DEFAULT 0,
    submitted_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX twp_user_id_idx ON tournament_winner_predictions(user_id);
CREATE INDEX twp_team_id_idx ON tournament_winner_predictions(team_id);

-- -----------------------------------------------------------------------------
-- AUDIT LOGS
-- -----------------------------------------------------------------------------

CREATE TABLE audit_logs (
    id          INTEGER PRIMARY KEY,
    actor_id    INTEGER REFERENCES users(id),
    action      VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id   INTEGER,
    old_value   TEXT,
    new_value   TEXT,
    note        VARCHAR(1000),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX audit_logs_actor_id_idx ON audit_logs(actor_id);
CREATE INDEX audit_logs_action_idx ON audit_logs(action);
CREATE INDEX audit_logs_target_idx ON audit_logs(target_type, target_id);
CREATE INDEX audit_logs_created_at_idx ON audit_logs(created_at);
