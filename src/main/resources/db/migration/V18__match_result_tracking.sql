-- V18__match_result_tracking.sql
ALTER TABLE matches ADD COLUMN result_source VARCHAR(10);
ALTER TABLE matches ADD COLUMN playoff_winner VARCHAR(10);
