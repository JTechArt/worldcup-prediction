-- =============================================================================
-- V5__multi_community.sql
-- Multi-community architecture: new tables, altered tables, data truncation
-- =============================================================================

-- Phase 1: Truncate community-related data (order matters for FK constraints)
DELETE FROM notification_log;
DELETE FROM invitations;
DELETE FROM tournament_winner_predictions;
DELETE FROM predictions;
DELETE FROM oauth_identities;
DELETE FROM audit_logs;
DELETE FROM users;

-- Phase 2: Create new tables

CREATE TABLE communities (
    id              INTEGER PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    slug            VARCHAR(50)  NOT NULL,
    description     VARCHAR(500),
    created_by_id   INTEGER REFERENCES users(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX communities_slug_idx ON communities(slug);

CREATE TABLE community_memberships (
    id                   INTEGER PRIMARY KEY,
    community_id         INTEGER     NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    user_id              INTEGER     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role                 VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    status               VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_points         INTEGER     NOT NULL DEFAULT 0,
    exact_score_count    INTEGER     NOT NULL DEFAULT 0,
    correct_winner_count INTEGER     NOT NULL DEFAULT 0,
    correct_draw_count   INTEGER     NOT NULL DEFAULT 0,
    joined_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(community_id, user_id)
);

CREATE INDEX community_memberships_user_id_idx ON community_memberships(user_id);
CREATE INDEX community_memberships_community_id_idx ON community_memberships(community_id);
CREATE INDEX community_memberships_status_idx ON community_memberships(status);

-- Phase 3: Alter users table
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

-- Drop stat columns from users (SQLite: recreate without them is complex,
-- so we leave them as unused columns for SQLite compat; PostgreSQL can DROP COLUMN).
-- For now, columns remain but are no longer read by the application.

-- Phase 4: Add community_id to predictions
ALTER TABLE predictions ADD COLUMN community_id INTEGER REFERENCES communities(id);

-- Drop old unique constraint and add new one
-- SQLite doesn't support DROP CONSTRAINT, so we create a new index
-- The old unique index predictions_user_match_idx is from the JPA annotation
-- We'll rely on JPA entity-level constraint for the new unique (user_id, match_id, community_id)
CREATE UNIQUE INDEX predictions_user_match_community_idx ON predictions(user_id, match_id, community_id);

-- Phase 5: Add community_id to tournament_winner_predictions
ALTER TABLE tournament_winner_predictions ADD COLUMN community_id INTEGER REFERENCES communities(id);
CREATE UNIQUE INDEX twp_user_community_idx ON tournament_winner_predictions(user_id, community_id);

-- Phase 6: Add community_id to invitations
ALTER TABLE invitations ADD COLUMN community_id INTEGER REFERENCES communities(id);

-- Phase 7: Add community_id to notification_log
ALTER TABLE notification_log ADD COLUMN community_id INTEGER REFERENCES communities(id);
