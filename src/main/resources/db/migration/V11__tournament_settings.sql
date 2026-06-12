CREATE TABLE tournament_settings (
    id                                 INTEGER PRIMARY KEY,
    window_mode                        VARCHAR(10)  NOT NULL DEFAULT 'ROUND',
    daily_window_close_offset_minutes  INTEGER      NOT NULL DEFAULT 30,
    updated_at                         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tournament_settings (id, window_mode, daily_window_close_offset_minutes)
VALUES (1, 'ROUND', 30);
