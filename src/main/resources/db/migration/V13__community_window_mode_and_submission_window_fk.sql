ALTER TABLE communities ADD COLUMN window_mode_override VARCHAR(10);

ALTER TABLE round_submissions
    ADD COLUMN prediction_window_id INTEGER REFERENCES prediction_window(id) ON DELETE SET NULL;

CREATE INDEX round_submissions_window_idx
    ON round_submissions(community_id, prediction_window_id)
    WHERE prediction_window_id IS NOT NULL;
