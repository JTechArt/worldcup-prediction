-- =============================================================
-- R__wc2026_data.sql  (Flyway Repeatable Migration)
-- WC2026 seed data — generated from bootstrap on 2026-06-04
-- Source: football-data.org API (season=2026)
-- ANSI SQL — compatible with SQLite and PostgreSQL
-- Idempotent via ON CONFLICT DO NOTHING / DO UPDATE
-- =============================================================

-- TEAMS
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Uruguay', 'Uruguay', 'URY', 'uy', NULL, 758)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Germany', 'Germany', 'GER', 'de', NULL, 759)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Spain', 'Spain', 'ESP', 'es', NULL, 760)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Paraguay', 'Paraguay', 'PAR', 'py', NULL, 761)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Argentina', 'Argentina', 'ARG', 'ar', NULL, 762)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Ghana', 'Ghana', 'GHA', 'gh', NULL, 763)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Brazil', 'Brazil', 'BRA', 'br', NULL, 764)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Portugal', 'Portugal', 'POR', 'pt', NULL, 765)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Japan', 'Japan', 'JPN', 'jp', NULL, 766)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Mexico', 'Mexico', 'MEX', 'mx', NULL, 769)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('England', 'England', 'ENG', 'gb-eng', NULL, 770)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('United States', 'USA', 'USA', 'us', NULL, 771)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('South Korea', 'Korea Republic', 'KOR', 'kr', NULL, 772)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('France', 'France', 'FRA', 'fr', NULL, 773)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('South Africa', 'South Africa', 'RSA', 'za', NULL, 774)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Algeria', 'Algeria', 'ALG', 'dz', NULL, 778)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Australia', 'Australia', 'AUS', 'au', NULL, 779)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('New Zealand', 'New Zealand', 'NZL', 'nz', NULL, 783)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Switzerland', 'Switzerland', 'SUI', 'ch', NULL, 788)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Ecuador', 'Ecuador', 'ECU', 'ec', NULL, 791)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Sweden', 'Sweden', 'SWE', 'se', NULL, 792)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Czechia', 'Czechia', 'CZE', 'cz', NULL, 798)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Croatia', 'Croatia', 'CRO', 'hr', NULL, 799)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Saudi Arabia', 'Saudi Arabia', 'KSA', 'sa', NULL, 801)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Tunisia', 'Tunisia', 'TUN', 'tn', NULL, 802)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Turkey', 'Turkey', 'TUR', 'tr', NULL, 803)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Senegal', 'Senegal', 'SEN', 'sn', NULL, 804)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Belgium', 'Belgium', 'BEL', 'be', NULL, 805)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Morocco', 'Morocco', 'MAR', 'ma', NULL, 815)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Austria', 'Austria', 'AUT', 'at', NULL, 816)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Colombia', 'Colombia', 'COL', 'co', NULL, 818)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Egypt', 'Egypt', 'EGY', 'eg', NULL, 825)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Canada', 'Canada', 'CAN', 'ca', NULL, 828)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Haiti', 'Haiti', 'HAI', 'ht', NULL, 836)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Iran', 'Iran', 'IRN', 'ir', NULL, 840)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Bosnia-Herzegovina', 'Bosnia-H.', 'BIH', 'ba', NULL, 1060)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Panama', 'Panama', 'PAN', 'pa', NULL, 1836)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Cape Verde Islands', 'Cape Verde', 'CPV', 'cv', NULL, 1930)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Congo DR', 'Congo DR', 'COD', 'cd', NULL, 1934)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Ivory Coast', 'Ivory Coast', 'CIV', 'ci', NULL, 1935)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Qatar', 'Qatar', 'QAT', 'qa', NULL, 8030)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Jordan', 'Jordan', 'JOR', 'jo', NULL, 8049)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Iraq', 'Iraq', 'IRQ', 'iq', NULL, 8062)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Uzbekistan', 'Uzbekistan', 'UZB', 'uz', NULL, 8070)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Netherlands', 'Netherlands', 'NED', 'nl', NULL, 8601)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Norway', 'Norway', 'NOR', 'no', NULL, 8872)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Scotland', 'Scotland', 'SCO', 'gb-sct', NULL, 8873)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;
INSERT INTO teams (name, short_name, fifa_code, flag_code, confederation, external_id) VALUES ('Curaçao', 'Curaçao', 'CUW', 'cw', NULL, 9460)
ON CONFLICT (fifa_code) DO UPDATE SET
  name=excluded.name, short_name=excluded.short_name,
  flag_code=excluded.flag_code, confederation=excluded.confederation,
  external_id=excluded.external_id;

-- GROUPS
INSERT INTO groups (name) VALUES ('A') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('B') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('C') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('D') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('E') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('F') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('G') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('H') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('I') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('J') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('K') ON CONFLICT (name) DO NOTHING;
INSERT INTO groups (name) VALUES ('L') ON CONFLICT (name) DO NOTHING;

-- MATCHES (group stage)
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537327', 'GROUP', (SELECT id FROM groups WHERE name='A'), 1, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='MEX'), (SELECT id FROM teams WHERE fifa_code='RSA'), 1781190000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537328', 'GROUP', (SELECT id FROM groups WHERE name='A'), 2, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='KOR'), (SELECT id FROM teams WHERE fifa_code='CZE'), 1781215200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537333', 'GROUP', (SELECT id FROM groups WHERE name='B'), 3, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='CAN'), (SELECT id FROM teams WHERE fifa_code='BIH'), 1781276400000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537345', 'GROUP', (SELECT id FROM groups WHERE name='D'), 4, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='USA'), (SELECT id FROM teams WHERE fifa_code='PAR'), 1781298000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537334', 'GROUP', (SELECT id FROM groups WHERE name='B'), 5, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='QAT'), (SELECT id FROM teams WHERE fifa_code='SUI'), 1781362800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537339', 'GROUP', (SELECT id FROM groups WHERE name='C'), 6, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='BRA'), (SELECT id FROM teams WHERE fifa_code='MAR'), 1781373600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537340', 'GROUP', (SELECT id FROM groups WHERE name='C'), 7, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='HAI'), (SELECT id FROM teams WHERE fifa_code='SCO'), 1781384400000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537346', 'GROUP', (SELECT id FROM groups WHERE name='D'), 8, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='AUS'), (SELECT id FROM teams WHERE fifa_code='TUR'), 1781395200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537351', 'GROUP', (SELECT id FROM groups WHERE name='E'), 9, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='GER'), (SELECT id FROM teams WHERE fifa_code='CUW'), 1781442000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537357', 'GROUP', (SELECT id FROM groups WHERE name='F'), 10, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='NED'), (SELECT id FROM teams WHERE fifa_code='JPN'), 1781452800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537352', 'GROUP', (SELECT id FROM groups WHERE name='E'), 11, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='CIV'), (SELECT id FROM teams WHERE fifa_code='ECU'), 1781463600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537358', 'GROUP', (SELECT id FROM groups WHERE name='F'), 12, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='SWE'), (SELECT id FROM teams WHERE fifa_code='TUN'), 1781474400000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537369', 'GROUP', (SELECT id FROM groups WHERE name='H'), 13, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='ESP'), (SELECT id FROM teams WHERE fifa_code='CPV'), 1781524800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537363', 'GROUP', (SELECT id FROM groups WHERE name='G'), 14, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='BEL'), (SELECT id FROM teams WHERE fifa_code='EGY'), 1781535600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537370', 'GROUP', (SELECT id FROM groups WHERE name='H'), 15, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='KSA'), (SELECT id FROM teams WHERE fifa_code='URY'), 1781546400000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537364', 'GROUP', (SELECT id FROM groups WHERE name='G'), 16, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='IRN'), (SELECT id FROM teams WHERE fifa_code='NZL'), 1781557200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537391', 'GROUP', (SELECT id FROM groups WHERE name='I'), 17, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='FRA'), (SELECT id FROM teams WHERE fifa_code='SEN'), 1781622000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537392', 'GROUP', (SELECT id FROM groups WHERE name='I'), 18, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='IRQ'), (SELECT id FROM teams WHERE fifa_code='NOR'), 1781632800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537397', 'GROUP', (SELECT id FROM groups WHERE name='J'), 19, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='ARG'), (SELECT id FROM teams WHERE fifa_code='ALG'), 1781643600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537398', 'GROUP', (SELECT id FROM groups WHERE name='J'), 20, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='AUT'), (SELECT id FROM teams WHERE fifa_code='JOR'), 1781654400000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537403', 'GROUP', (SELECT id FROM groups WHERE name='K'), 21, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='POR'), (SELECT id FROM teams WHERE fifa_code='COD'), 1781701200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537409', 'GROUP', (SELECT id FROM groups WHERE name='L'), 22, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='ENG'), (SELECT id FROM teams WHERE fifa_code='CRO'), 1781712000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537410', 'GROUP', (SELECT id FROM groups WHERE name='L'), 23, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='GHA'), (SELECT id FROM teams WHERE fifa_code='PAN'), 1781722800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537404', 'GROUP', (SELECT id FROM groups WHERE name='K'), 24, 'Matchday 1', (SELECT id FROM teams WHERE fifa_code='UZB'), (SELECT id FROM teams WHERE fifa_code='COL'), 1781733600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537329', 'GROUP', (SELECT id FROM groups WHERE name='A'), 25, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='CZE'), (SELECT id FROM teams WHERE fifa_code='RSA'), 1781784000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537335', 'GROUP', (SELECT id FROM groups WHERE name='B'), 26, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='SUI'), (SELECT id FROM teams WHERE fifa_code='BIH'), 1781794800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537336', 'GROUP', (SELECT id FROM groups WHERE name='B'), 27, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='CAN'), (SELECT id FROM teams WHERE fifa_code='QAT'), 1781805600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537330', 'GROUP', (SELECT id FROM groups WHERE name='A'), 28, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='MEX'), (SELECT id FROM teams WHERE fifa_code='KOR'), 1781816400000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537348', 'GROUP', (SELECT id FROM groups WHERE name='D'), 29, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='USA'), (SELECT id FROM teams WHERE fifa_code='AUS'), 1781881200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537342', 'GROUP', (SELECT id FROM groups WHERE name='C'), 30, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='SCO'), (SELECT id FROM teams WHERE fifa_code='MAR'), 1781892000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537341', 'GROUP', (SELECT id FROM groups WHERE name='C'), 31, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='BRA'), (SELECT id FROM teams WHERE fifa_code='HAI'), 1781901000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537347', 'GROUP', (SELECT id FROM groups WHERE name='D'), 32, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='TUR'), (SELECT id FROM teams WHERE fifa_code='PAR'), 1781910000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537359', 'GROUP', (SELECT id FROM groups WHERE name='F'), 33, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='NED'), (SELECT id FROM teams WHERE fifa_code='SWE'), 1781960400000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537353', 'GROUP', (SELECT id FROM groups WHERE name='E'), 34, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='GER'), (SELECT id FROM teams WHERE fifa_code='CIV'), 1781971200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537354', 'GROUP', (SELECT id FROM groups WHERE name='E'), 35, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='ECU'), (SELECT id FROM teams WHERE fifa_code='CUW'), 1781985600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537360', 'GROUP', (SELECT id FROM groups WHERE name='F'), 36, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='TUN'), (SELECT id FROM teams WHERE fifa_code='JPN'), 1782000000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537371', 'GROUP', (SELECT id FROM groups WHERE name='H'), 37, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='ESP'), (SELECT id FROM teams WHERE fifa_code='KSA'), 1782043200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537365', 'GROUP', (SELECT id FROM groups WHERE name='G'), 38, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='BEL'), (SELECT id FROM teams WHERE fifa_code='IRN'), 1782054000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537372', 'GROUP', (SELECT id FROM groups WHERE name='H'), 39, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='URY'), (SELECT id FROM teams WHERE fifa_code='CPV'), 1782064800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537366', 'GROUP', (SELECT id FROM groups WHERE name='G'), 40, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='NZL'), (SELECT id FROM teams WHERE fifa_code='EGY'), 1782075600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537399', 'GROUP', (SELECT id FROM groups WHERE name='J'), 41, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='ARG'), (SELECT id FROM teams WHERE fifa_code='AUT'), 1782133200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537393', 'GROUP', (SELECT id FROM groups WHERE name='I'), 42, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='FRA'), (SELECT id FROM teams WHERE fifa_code='IRQ'), 1782147600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537394', 'GROUP', (SELECT id FROM groups WHERE name='I'), 43, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='NOR'), (SELECT id FROM teams WHERE fifa_code='SEN'), 1782158400000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537400', 'GROUP', (SELECT id FROM groups WHERE name='J'), 44, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='JOR'), (SELECT id FROM teams WHERE fifa_code='ALG'), 1782169200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537405', 'GROUP', (SELECT id FROM groups WHERE name='K'), 45, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='POR'), (SELECT id FROM teams WHERE fifa_code='UZB'), 1782219600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537411', 'GROUP', (SELECT id FROM groups WHERE name='L'), 46, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='ENG'), (SELECT id FROM teams WHERE fifa_code='GHA'), 1782230400000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537412', 'GROUP', (SELECT id FROM groups WHERE name='L'), 47, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='PAN'), (SELECT id FROM teams WHERE fifa_code='CRO'), 1782241200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537406', 'GROUP', (SELECT id FROM groups WHERE name='K'), 48, 'Matchday 2', (SELECT id FROM teams WHERE fifa_code='COL'), (SELECT id FROM teams WHERE fifa_code='COD'), 1782252000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537337', 'GROUP', (SELECT id FROM groups WHERE name='B'), 49, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='SUI'), (SELECT id FROM teams WHERE fifa_code='CAN'), 1782313200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537338', 'GROUP', (SELECT id FROM groups WHERE name='B'), 50, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='BIH'), (SELECT id FROM teams WHERE fifa_code='QAT'), 1782313200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537344', 'GROUP', (SELECT id FROM groups WHERE name='C'), 51, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='MAR'), (SELECT id FROM teams WHERE fifa_code='HAI'), 1782324000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537343', 'GROUP', (SELECT id FROM groups WHERE name='C'), 52, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='SCO'), (SELECT id FROM teams WHERE fifa_code='BRA'), 1782324000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537331', 'GROUP', (SELECT id FROM groups WHERE name='A'), 53, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='CZE'), (SELECT id FROM teams WHERE fifa_code='MEX'), 1782334800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537332', 'GROUP', (SELECT id FROM groups WHERE name='A'), 54, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='RSA'), (SELECT id FROM teams WHERE fifa_code='KOR'), 1782334800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537355', 'GROUP', (SELECT id FROM groups WHERE name='E'), 55, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='ECU'), (SELECT id FROM teams WHERE fifa_code='GER'), 1782403200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537356', 'GROUP', (SELECT id FROM groups WHERE name='E'), 56, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='CUW'), (SELECT id FROM teams WHERE fifa_code='CIV'), 1782403200000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537361', 'GROUP', (SELECT id FROM groups WHERE name='F'), 57, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='TUN'), (SELECT id FROM teams WHERE fifa_code='NED'), 1782414000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537362', 'GROUP', (SELECT id FROM groups WHERE name='F'), 58, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='JPN'), (SELECT id FROM teams WHERE fifa_code='SWE'), 1782414000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537349', 'GROUP', (SELECT id FROM groups WHERE name='D'), 59, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='TUR'), (SELECT id FROM teams WHERE fifa_code='USA'), 1782424800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537350', 'GROUP', (SELECT id FROM groups WHERE name='D'), 60, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='PAR'), (SELECT id FROM teams WHERE fifa_code='AUS'), 1782424800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537395', 'GROUP', (SELECT id FROM groups WHERE name='I'), 61, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='NOR'), (SELECT id FROM teams WHERE fifa_code='FRA'), 1782486000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537396', 'GROUP', (SELECT id FROM groups WHERE name='I'), 62, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='SEN'), (SELECT id FROM teams WHERE fifa_code='IRQ'), 1782486000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537373', 'GROUP', (SELECT id FROM groups WHERE name='H'), 63, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='URY'), (SELECT id FROM teams WHERE fifa_code='ESP'), 1782504000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537374', 'GROUP', (SELECT id FROM groups WHERE name='H'), 64, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='CPV'), (SELECT id FROM teams WHERE fifa_code='KSA'), 1782504000000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537367', 'GROUP', (SELECT id FROM groups WHERE name='G'), 65, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='NZL'), (SELECT id FROM teams WHERE fifa_code='BEL'), 1782514800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537368', 'GROUP', (SELECT id FROM groups WHERE name='G'), 66, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='EGY'), (SELECT id FROM teams WHERE fifa_code='IRN'), 1782514800000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537413', 'GROUP', (SELECT id FROM groups WHERE name='L'), 67, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='PAN'), (SELECT id FROM teams WHERE fifa_code='ENG'), 1782579600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537414', 'GROUP', (SELECT id FROM groups WHERE name='L'), 68, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='CRO'), (SELECT id FROM teams WHERE fifa_code='GHA'), 1782579600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537407', 'GROUP', (SELECT id FROM groups WHERE name='K'), 69, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='COL'), (SELECT id FROM teams WHERE fifa_code='POR'), 1782588600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537408', 'GROUP', (SELECT id FROM groups WHERE name='K'), 70, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='COD'), (SELECT id FROM teams WHERE fifa_code='UZB'), 1782588600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537401', 'GROUP', (SELECT id FROM groups WHERE name='J'), 71, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='JOR'), (SELECT id FROM teams WHERE fifa_code='ARG'), 1782597600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;
INSERT INTO matches (external_id, stage, group_id, match_number, round_label, home_team_id, away_team_id, kickoff_time, status, lineup_fetched) VALUES ('537402', 'GROUP', (SELECT id FROM groups WHERE name='J'), 72, 'Matchday 3', (SELECT id FROM teams WHERE fifa_code='ALG'), (SELECT id FROM teams WHERE fifa_code='AUT'), 1782597600000, 'SCHEDULED', 0) ON CONFLICT (match_number) DO NOTHING;

-- GROUP_TEAMS
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='A'), (SELECT id FROM teams WHERE fifa_code='CZE'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='A'), (SELECT id FROM teams WHERE fifa_code='MEX'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='A'), (SELECT id FROM teams WHERE fifa_code='RSA'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='A'), (SELECT id FROM teams WHERE fifa_code='KOR'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='B'), (SELECT id FROM teams WHERE fifa_code='BIH'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='B'), (SELECT id FROM teams WHERE fifa_code='CAN'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='B'), (SELECT id FROM teams WHERE fifa_code='QAT'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='B'), (SELECT id FROM teams WHERE fifa_code='SUI'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='C'), (SELECT id FROM teams WHERE fifa_code='BRA'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='C'), (SELECT id FROM teams WHERE fifa_code='HAI'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='C'), (SELECT id FROM teams WHERE fifa_code='MAR'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='C'), (SELECT id FROM teams WHERE fifa_code='SCO'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='D'), (SELECT id FROM teams WHERE fifa_code='AUS'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='D'), (SELECT id FROM teams WHERE fifa_code='PAR'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='D'), (SELECT id FROM teams WHERE fifa_code='TUR'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='D'), (SELECT id FROM teams WHERE fifa_code='USA'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='E'), (SELECT id FROM teams WHERE fifa_code='CUW'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='E'), (SELECT id FROM teams WHERE fifa_code='ECU'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='E'), (SELECT id FROM teams WHERE fifa_code='GER'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='E'), (SELECT id FROM teams WHERE fifa_code='CIV'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='F'), (SELECT id FROM teams WHERE fifa_code='JPN'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='F'), (SELECT id FROM teams WHERE fifa_code='NED'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='F'), (SELECT id FROM teams WHERE fifa_code='SWE'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='F'), (SELECT id FROM teams WHERE fifa_code='TUN'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='G'), (SELECT id FROM teams WHERE fifa_code='BEL'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='G'), (SELECT id FROM teams WHERE fifa_code='EGY'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='G'), (SELECT id FROM teams WHERE fifa_code='IRN'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='G'), (SELECT id FROM teams WHERE fifa_code='NZL'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='H'), (SELECT id FROM teams WHERE fifa_code='CPV'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='H'), (SELECT id FROM teams WHERE fifa_code='KSA'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='H'), (SELECT id FROM teams WHERE fifa_code='ESP'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='H'), (SELECT id FROM teams WHERE fifa_code='URY'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='I'), (SELECT id FROM teams WHERE fifa_code='FRA'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='I'), (SELECT id FROM teams WHERE fifa_code='IRQ'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='I'), (SELECT id FROM teams WHERE fifa_code='NOR'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='I'), (SELECT id FROM teams WHERE fifa_code='SEN'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='J'), (SELECT id FROM teams WHERE fifa_code='ALG'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='J'), (SELECT id FROM teams WHERE fifa_code='ARG'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='J'), (SELECT id FROM teams WHERE fifa_code='AUT'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='J'), (SELECT id FROM teams WHERE fifa_code='JOR'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='K'), (SELECT id FROM teams WHERE fifa_code='COL'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='K'), (SELECT id FROM teams WHERE fifa_code='COD'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='K'), (SELECT id FROM teams WHERE fifa_code='POR'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='K'), (SELECT id FROM teams WHERE fifa_code='UZB'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='L'), (SELECT id FROM teams WHERE fifa_code='CRO'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='L'), (SELECT id FROM teams WHERE fifa_code='ENG'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='L'), (SELECT id FROM teams WHERE fifa_code='GHA'));
INSERT OR IGNORE INTO group_teams (group_id, team_id) VALUES ((SELECT id FROM groups WHERE name='L'), (SELECT id FROM teams WHERE fifa_code='PAN'));
