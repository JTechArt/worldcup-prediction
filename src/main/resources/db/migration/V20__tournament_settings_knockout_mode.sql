-- V20__tournament_settings_knockout_mode.sql
ALTER TABLE tournament_settings ADD COLUMN knockout_scoring_mode VARCHAR(20) NOT NULL DEFAULT 'NINETY_MINUTES';
