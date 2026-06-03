-- V2__football_api_schema.sql
-- Adds football API enrichment tables and columns
-- ANSI SQL — compatible with SQLite and PostgreSQL

ALTER TABLE teams ADD COLUMN external_id INTEGER;
CREATE UNIQUE INDEX teams_external_id_idx ON teams(external_id);

ALTER TABLE matches ADD COLUMN lineup_fetched INTEGER NOT NULL DEFAULT 0;
CREATE INDEX matches_lineup_fetched_idx ON matches(lineup_fetched);

ALTER TABLE group_standings ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

CREATE TABLE players (
    id               INTEGER PRIMARY KEY,
    external_id      INTEGER NOT NULL,
    team_id          INTEGER NOT NULL REFERENCES teams(id),
    name             VARCHAR(100) NOT NULL,
    position         VARCHAR(20),
    nationality      VARCHAR(100),
    date_of_birth    DATE,
    shirt_number     INTEGER,
    tournament_goals INTEGER NOT NULL DEFAULT 0,
    UNIQUE(external_id)
);
CREATE INDEX players_team_id_idx ON players(team_id);
CREATE INDEX players_goals_idx ON players(tournament_goals);

CREATE TABLE match_lineups (
    id                 INTEGER PRIMARY KEY,
    match_id           INTEGER NOT NULL REFERENCES matches(id),
    team_id            INTEGER NOT NULL REFERENCES teams(id),
    player_id          INTEGER NOT NULL REFERENCES players(id),
    starting           INTEGER NOT NULL DEFAULT 0,
    shirt_number       INTEGER,
    formation_position VARCHAR(50)
);
CREATE INDEX match_lineups_match_id_idx ON match_lineups(match_id);

CREATE TABLE match_goals (
    id        INTEGER PRIMARY KEY,
    match_id  INTEGER NOT NULL REFERENCES matches(id),
    team_id   INTEGER NOT NULL REFERENCES teams(id),
    player_id INTEGER REFERENCES players(id),
    minute    INTEGER NOT NULL,
    type      VARCHAR(20) NOT NULL DEFAULT 'REGULAR'
);
CREATE INDEX match_goals_match_id_idx ON match_goals(match_id);
