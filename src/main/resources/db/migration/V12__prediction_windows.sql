CREATE TABLE prediction_window (
    id                  INTEGER PRIMARY KEY,
    label               VARCHAR(100) NOT NULL,
    open_at             TIMESTAMP    NOT NULL,
    close_at            TIMESTAMP,
    effective_close_at  TIMESTAMP,
    override_status     VARCHAR(20),
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    community_id        INTEGER      REFERENCES communities(id) ON DELETE CASCADE,
    created_by_id       INTEGER      REFERENCES users(id),
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX prediction_window_status_idx    ON prediction_window(status);
CREATE INDEX prediction_window_community_idx ON prediction_window(community_id);

CREATE TABLE prediction_window_match (
    window_id  INTEGER NOT NULL REFERENCES prediction_window(id) ON DELETE CASCADE,
    match_id   INTEGER NOT NULL REFERENCES matches(id)           ON DELETE CASCADE,
    PRIMARY KEY (window_id, match_id)
);
