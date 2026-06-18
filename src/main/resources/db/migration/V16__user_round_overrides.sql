CREATE TABLE user_round_overrides (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL REFERENCES users(id),
    community_id  INTEGER NOT NULL REFERENCES communities(id),
    round_label   VARCHAR(50) NOT NULL,
    created_by_id INTEGER REFERENCES users(id),
    used_at       TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_round_override UNIQUE (user_id, community_id, round_label)
);
