-- Seed data was created with kickoff times 4 hours too early for the 15:00 UTC slot.
-- Those matches should be at 19:00 UTC (= 23:00 Armenia/UTC+4), not 15:00 UTC (= 19:00 Armenia).
-- Shift all affected matches forward by 4 hours (14400000 ms).
UPDATE matches
SET kickoff_time = kickoff_time + 14400000
WHERE time(kickoff_time / 1000, 'unixepoch') = '15:00:00';
