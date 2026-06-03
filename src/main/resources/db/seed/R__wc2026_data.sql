-- =============================================================
-- R__wc2026_data.sql  (Flyway Repeatable Migration)
-- WC2026 seed data: 48 teams, 12 groups, 80 match slots
-- ANSI SQL — compatible with SQLite and PostgreSQL
-- Idempotent via ON CONFLICT DO NOTHING
-- =============================================================

-- ---------------------------------------------------------------
-- TEAMS (48 qualified nations)
-- ---------------------------------------------------------------
INSERT INTO teams (name, fifa_code, flag_code, confederation) VALUES
('United States',        'USA', 'us',     'CONCACAF'),
('Mexico',               'MEX', 'mx',     'CONCACAF'),
('Canada',               'CAN', 'ca',     'CONCACAF'),
('Jamaica',              'JAM', 'jm',     'CONCACAF'),
('Argentina',            'ARG', 'ar',     'CONMEBOL'),
('Chile',                'CHI', 'cl',     'CONMEBOL'),
('Peru',                 'PER', 'pe',     'CONMEBOL'),
('Bolivia',              'BOL', 'bo',     'CONMEBOL'),
('Brazil',               'BRA', 'br',     'CONMEBOL'),
('Uruguay',              'URU', 'uy',     'CONMEBOL'),
('Ecuador',              'ECU', 'ec',     'CONMEBOL'),
('Venezuela',            'VEN', 've',     'CONMEBOL'),
('Colombia',             'COL', 'co',     'CONMEBOL'),
('Panama',               'PAN', 'pa',     'CONCACAF'),
('Costa Rica',           'CRC', 'cr',     'CONCACAF'),
('Honduras',             'HON', 'hn',     'CONCACAF'),
('Spain',                'ESP', 'es',     'UEFA'),
('Portugal',             'POR', 'pt',     'UEFA'),
('Croatia',              'CRO', 'hr',     'UEFA'),
('Turkey',               'TUR', 'tr',     'UEFA'),
('France',               'FRA', 'fr',     'UEFA'),
('Belgium',              'BEL', 'be',     'UEFA'),
('Morocco',              'MAR', 'ma',     'CAF'),
('Tunisia',              'TUN', 'tn',     'CAF'),
('Germany',              'GER', 'de',     'UEFA'),
('Netherlands',          'NED', 'nl',     'UEFA'),
('Austria',              'AUT', 'at',     'UEFA'),
('Romania',              'ROU', 'ro',     'UEFA'),
('England',              'ENG', 'gb-eng', 'UEFA'),
('Serbia',               'SRB', 'rs',     'UEFA'),
('Czech Republic',       'CZE', 'cz',     'UEFA'),
('Albania',              'ALB', 'al',     'UEFA'),
('Italy',                'ITA', 'it',     'UEFA'),
('Switzerland',          'SUI', 'ch',     'UEFA'),
('Greece',               'GRE', 'gr',     'UEFA'),
('Norway',               'NOR', 'no',     'UEFA'),
('Japan',                'JPN', 'jp',     'AFC'),
('South Korea',          'KOR', 'kr',     'AFC'),
('Iran',                 'IRN', 'ir',     'AFC'),
('Australia',            'AUS', 'au',     'AFC'),
('Senegal',              'SEN', 'sn',     'CAF'),
('Egypt',                'EGY', 'eg',     'CAF'),
('Nigeria',              'NGA', 'ng',     'CAF'),
('Cote d''Ivoire',       'CIV', 'ci',     'CAF'),
('Saudi Arabia',         'KSA', 'sa',     'AFC'),
('Qatar',                'QAT', 'qa',     'AFC'),
('United Arab Emirates', 'UAE', 'ae',     'AFC'),
('Jordan',               'JOR', 'jo',     'AFC')
ON CONFLICT (fifa_code) DO NOTHING;

-- ---------------------------------------------------------------
-- GROUPS
-- ---------------------------------------------------------------
INSERT INTO groups (name) VALUES
('A'), ('B'), ('C'), ('D'), ('E'), ('F'),
('G'), ('H'), ('I'), ('J'), ('K'), ('L')
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------
-- GROUP_TEAMS
-- ---------------------------------------------------------------
INSERT INTO group_teams (group_id, team_id)
SELECT g.id, t.id FROM groups g, teams t
WHERE (g.name = 'A' AND t.fifa_code IN ('USA','MEX','CAN','JAM'))
   OR (g.name = 'B' AND t.fifa_code IN ('ARG','CHI','PER','BOL'))
   OR (g.name = 'C' AND t.fifa_code IN ('BRA','URU','ECU','VEN'))
   OR (g.name = 'D' AND t.fifa_code IN ('COL','PAN','CRC','HON'))
   OR (g.name = 'E' AND t.fifa_code IN ('ESP','POR','CRO','TUR'))
   OR (g.name = 'F' AND t.fifa_code IN ('FRA','BEL','MAR','TUN'))
   OR (g.name = 'G' AND t.fifa_code IN ('GER','NED','AUT','ROU'))
   OR (g.name = 'H' AND t.fifa_code IN ('ENG','SRB','CZE','ALB'))
   OR (g.name = 'I' AND t.fifa_code IN ('ITA','SUI','GRE','NOR'))
   OR (g.name = 'J' AND t.fifa_code IN ('JPN','KOR','IRN','AUS'))
   OR (g.name = 'K' AND t.fifa_code IN ('SEN','EGY','NGA','CIV'))
   OR (g.name = 'L' AND t.fifa_code IN ('KSA','QAT','UAE','JOR'))
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------
-- GROUP STAGE MATCHES (48 matches, 1–72)
-- Uses CTEs for SQLite + PostgreSQL cross-compatibility.
-- prediction_window_open = 0 (admin opens windows via admin panel)
-- ---------------------------------------------------------------

WITH gs(mn, gn, hc, ac, rl, kt) AS (
    SELECT 1,  'A', 'USA', 'MEX', 'Group Stage MD1', '2026-06-11 21:00:00' UNION ALL
    SELECT 2,  'A', 'CAN', 'JAM', 'Group Stage MD1', '2026-06-12 00:00:00' UNION ALL
    SELECT 3,  'A', 'MEX', 'JAM', 'Group Stage MD2', '2026-06-16 18:00:00' UNION ALL
    SELECT 4,  'A', 'USA', 'CAN', 'Group Stage MD2', '2026-06-16 21:00:00' UNION ALL
    SELECT 5,  'A', 'USA', 'JAM', 'Group Stage MD3', '2026-06-22 00:00:00' UNION ALL
    SELECT 6,  'A', 'MEX', 'CAN', 'Group Stage MD3', '2026-06-22 00:00:00' UNION ALL
    SELECT 7,  'B', 'ARG', 'CHI', 'Group Stage MD1', '2026-06-12 18:00:00' UNION ALL
    SELECT 8,  'B', 'PER', 'BOL', 'Group Stage MD1', '2026-06-12 21:00:00' UNION ALL
    SELECT 9,  'B', 'ARG', 'PER', 'Group Stage MD2', '2026-06-17 18:00:00' UNION ALL
    SELECT 10, 'B', 'CHI', 'BOL', 'Group Stage MD2', '2026-06-17 21:00:00' UNION ALL
    SELECT 11, 'B', 'ARG', 'BOL', 'Group Stage MD3', '2026-06-22 21:00:00' UNION ALL
    SELECT 12, 'B', 'CHI', 'PER', 'Group Stage MD3', '2026-06-22 21:00:00' UNION ALL
    SELECT 13, 'C', 'BRA', 'URU', 'Group Stage MD1', '2026-06-13 00:00:00' UNION ALL
    SELECT 14, 'C', 'ECU', 'VEN', 'Group Stage MD1', '2026-06-13 18:00:00' UNION ALL
    SELECT 15, 'C', 'BRA', 'ECU', 'Group Stage MD2', '2026-06-18 18:00:00' UNION ALL
    SELECT 16, 'C', 'URU', 'VEN', 'Group Stage MD2', '2026-06-18 21:00:00' UNION ALL
    SELECT 17, 'C', 'BRA', 'VEN', 'Group Stage MD3', '2026-06-23 21:00:00' UNION ALL
    SELECT 18, 'C', 'URU', 'ECU', 'Group Stage MD3', '2026-06-23 21:00:00' UNION ALL
    SELECT 19, 'D', 'COL', 'PAN', 'Group Stage MD1', '2026-06-13 21:00:00' UNION ALL
    SELECT 20, 'D', 'CRC', 'HON', 'Group Stage MD1', '2026-06-14 00:00:00' UNION ALL
    SELECT 21, 'D', 'COL', 'CRC', 'Group Stage MD2', '2026-06-19 18:00:00' UNION ALL
    SELECT 22, 'D', 'PAN', 'HON', 'Group Stage MD2', '2026-06-19 21:00:00' UNION ALL
    SELECT 23, 'D', 'COL', 'HON', 'Group Stage MD3', '2026-06-24 21:00:00' UNION ALL
    SELECT 24, 'D', 'PAN', 'CRC', 'Group Stage MD3', '2026-06-24 21:00:00' UNION ALL
    SELECT 25, 'E', 'ESP', 'TUR', 'Group Stage MD1', '2026-06-14 18:00:00' UNION ALL
    SELECT 26, 'E', 'POR', 'CRO', 'Group Stage MD1', '2026-06-14 21:00:00' UNION ALL
    SELECT 27, 'E', 'ESP', 'POR', 'Group Stage MD2', '2026-06-19 18:00:00' UNION ALL
    SELECT 28, 'E', 'CRO', 'TUR', 'Group Stage MD2', '2026-06-19 21:00:00' UNION ALL
    SELECT 29, 'E', 'ESP', 'CRO', 'Group Stage MD3', '2026-06-24 18:00:00' UNION ALL
    SELECT 30, 'E', 'POR', 'TUR', 'Group Stage MD3', '2026-06-24 18:00:00' UNION ALL
    SELECT 31, 'F', 'FRA', 'MAR', 'Group Stage MD1', '2026-06-15 00:00:00' UNION ALL
    SELECT 32, 'F', 'BEL', 'TUN', 'Group Stage MD1', '2026-06-15 18:00:00' UNION ALL
    SELECT 33, 'F', 'FRA', 'BEL', 'Group Stage MD2', '2026-06-20 18:00:00' UNION ALL
    SELECT 34, 'F', 'MAR', 'TUN', 'Group Stage MD2', '2026-06-20 21:00:00' UNION ALL
    SELECT 35, 'F', 'FRA', 'TUN', 'Group Stage MD3', '2026-06-25 21:00:00' UNION ALL
    SELECT 36, 'F', 'BEL', 'MAR', 'Group Stage MD3', '2026-06-25 21:00:00' UNION ALL
    SELECT 37, 'G', 'GER', 'AUT', 'Group Stage MD1', '2026-06-15 21:00:00' UNION ALL
    SELECT 38, 'G', 'NED', 'ROU', 'Group Stage MD1', '2026-06-16 00:00:00' UNION ALL
    SELECT 39, 'G', 'GER', 'NED', 'Group Stage MD2', '2026-06-21 18:00:00' UNION ALL
    SELECT 40, 'G', 'AUT', 'ROU', 'Group Stage MD2', '2026-06-21 21:00:00' UNION ALL
    SELECT 41, 'G', 'GER', 'ROU', 'Group Stage MD3', '2026-06-26 18:00:00' UNION ALL
    SELECT 42, 'G', 'NED', 'AUT', 'Group Stage MD3', '2026-06-26 18:00:00' UNION ALL
    SELECT 43, 'H', 'ENG', 'CZE', 'Group Stage MD1', '2026-06-16 18:00:00' UNION ALL
    SELECT 44, 'H', 'SRB', 'ALB', 'Group Stage MD1', '2026-06-16 21:00:00' UNION ALL
    SELECT 45, 'H', 'ENG', 'ALB', 'Group Stage MD2', '2026-06-21 18:00:00' UNION ALL
    SELECT 46, 'H', 'CZE', 'SRB', 'Group Stage MD2', '2026-06-21 21:00:00' UNION ALL
    SELECT 47, 'H', 'ENG', 'SRB', 'Group Stage MD3', '2026-06-26 21:00:00' UNION ALL
    SELECT 48, 'H', 'CZE', 'ALB', 'Group Stage MD3', '2026-06-26 21:00:00' UNION ALL
    SELECT 49, 'I', 'ITA', 'GRE', 'Group Stage MD1', '2026-06-17 00:00:00' UNION ALL
    SELECT 50, 'I', 'SUI', 'NOR', 'Group Stage MD1', '2026-06-17 18:00:00' UNION ALL
    SELECT 51, 'I', 'ITA', 'SUI', 'Group Stage MD2', '2026-06-22 18:00:00' UNION ALL
    SELECT 52, 'I', 'NOR', 'GRE', 'Group Stage MD2', '2026-06-22 21:00:00' UNION ALL
    SELECT 53, 'I', 'ITA', 'NOR', 'Group Stage MD3', '2026-06-27 18:00:00' UNION ALL
    SELECT 54, 'I', 'SUI', 'GRE', 'Group Stage MD3', '2026-06-27 18:00:00' UNION ALL
    SELECT 55, 'J', 'JPN', 'IRN', 'Group Stage MD1', '2026-06-17 21:00:00' UNION ALL
    SELECT 56, 'J', 'KOR', 'AUS', 'Group Stage MD1', '2026-06-18 00:00:00' UNION ALL
    SELECT 57, 'J', 'JPN', 'KOR', 'Group Stage MD2', '2026-06-23 18:00:00' UNION ALL
    SELECT 58, 'J', 'AUS', 'IRN', 'Group Stage MD2', '2026-06-23 21:00:00' UNION ALL
    SELECT 59, 'J', 'JPN', 'AUS', 'Group Stage MD3', '2026-06-28 18:00:00' UNION ALL
    SELECT 60, 'J', 'KOR', 'IRN', 'Group Stage MD3', '2026-06-28 18:00:00' UNION ALL
    SELECT 61, 'K', 'SEN', 'NGA', 'Group Stage MD1', '2026-06-18 18:00:00' UNION ALL
    SELECT 62, 'K', 'EGY', 'CIV', 'Group Stage MD1', '2026-06-18 21:00:00' UNION ALL
    SELECT 63, 'K', 'SEN', 'EGY', 'Group Stage MD2', '2026-06-24 18:00:00' UNION ALL
    SELECT 64, 'K', 'NGA', 'CIV', 'Group Stage MD2', '2026-06-24 21:00:00' UNION ALL
    SELECT 65, 'K', 'SEN', 'CIV', 'Group Stage MD3', '2026-06-29 18:00:00' UNION ALL
    SELECT 66, 'K', 'EGY', 'NGA', 'Group Stage MD3', '2026-06-29 18:00:00' UNION ALL
    SELECT 67, 'L', 'KSA', 'UAE', 'Group Stage MD1', '2026-06-19 00:00:00' UNION ALL
    SELECT 68, 'L', 'QAT', 'JOR', 'Group Stage MD1', '2026-06-19 18:00:00' UNION ALL
    SELECT 69, 'L', 'KSA', 'QAT', 'Group Stage MD2', '2026-06-25 18:00:00' UNION ALL
    SELECT 70, 'L', 'UAE', 'JOR', 'Group Stage MD2', '2026-06-25 21:00:00' UNION ALL
    SELECT 71, 'L', 'KSA', 'JOR', 'Group Stage MD3', '2026-06-30 18:00:00' UNION ALL
    SELECT 72, 'L', 'QAT', 'UAE', 'Group Stage MD3', '2026-06-30 18:00:00'
)
INSERT INTO matches (match_number, stage, round_label, group_id, home_team_id, away_team_id, kickoff_time, status, prediction_window_open)
SELECT gs.mn, 'GROUP', gs.rl, g.id, ht.id, at_.id, gs.kt, 'SCHEDULED', 0
FROM gs
JOIN groups g ON g.name = gs.gn
JOIN teams ht ON ht.fifa_code = gs.hc
JOIN teams at_ ON at_.fifa_code = gs.ac
ON CONFLICT (match_number) DO NOTHING;

-- ---------------------------------------------------------------
-- KNOCKOUT STAGE (32 matches, 73–104)
-- Teams NULL until group stage determines qualifiers.
-- ---------------------------------------------------------------
INSERT INTO matches (match_number, stage, round_label, kickoff_time, status, prediction_window_open) VALUES
(73,  'ROUND_OF_32',  'Round of 32',   '2026-07-02 18:00:00', 'SCHEDULED', 0),
(74,  'ROUND_OF_32',  'Round of 32',   '2026-07-02 21:00:00', 'SCHEDULED', 0),
(75,  'ROUND_OF_32',  'Round of 32',   '2026-07-03 18:00:00', 'SCHEDULED', 0),
(76,  'ROUND_OF_32',  'Round of 32',   '2026-07-03 21:00:00', 'SCHEDULED', 0),
(77,  'ROUND_OF_32',  'Round of 32',   '2026-07-04 18:00:00', 'SCHEDULED', 0),
(78,  'ROUND_OF_32',  'Round of 32',   '2026-07-04 21:00:00', 'SCHEDULED', 0),
(79,  'ROUND_OF_32',  'Round of 32',   '2026-07-05 18:00:00', 'SCHEDULED', 0),
(80,  'ROUND_OF_32',  'Round of 32',   '2026-07-05 21:00:00', 'SCHEDULED', 0),
(81,  'ROUND_OF_32',  'Round of 32',   '2026-07-06 18:00:00', 'SCHEDULED', 0),
(82,  'ROUND_OF_32',  'Round of 32',   '2026-07-06 21:00:00', 'SCHEDULED', 0),
(83,  'ROUND_OF_32',  'Round of 32',   '2026-07-07 18:00:00', 'SCHEDULED', 0),
(84,  'ROUND_OF_32',  'Round of 32',   '2026-07-07 21:00:00', 'SCHEDULED', 0),
(85,  'ROUND_OF_32',  'Round of 32',   '2026-07-08 18:00:00', 'SCHEDULED', 0),
(86,  'ROUND_OF_32',  'Round of 32',   '2026-07-08 21:00:00', 'SCHEDULED', 0),
(87,  'ROUND_OF_32',  'Round of 32',   '2026-07-09 18:00:00', 'SCHEDULED', 0),
(88,  'ROUND_OF_32',  'Round of 32',   '2026-07-09 21:00:00', 'SCHEDULED', 0),
(89,  'ROUND_OF_16',  'Round of 16',   '2026-07-13 18:00:00', 'SCHEDULED', 0),
(90,  'ROUND_OF_16',  'Round of 16',   '2026-07-13 21:00:00', 'SCHEDULED', 0),
(91,  'ROUND_OF_16',  'Round of 16',   '2026-07-14 18:00:00', 'SCHEDULED', 0),
(92,  'ROUND_OF_16',  'Round of 16',   '2026-07-14 21:00:00', 'SCHEDULED', 0),
(93,  'ROUND_OF_16',  'Round of 16',   '2026-07-15 18:00:00', 'SCHEDULED', 0),
(94,  'ROUND_OF_16',  'Round of 16',   '2026-07-15 21:00:00', 'SCHEDULED', 0),
(95,  'ROUND_OF_16',  'Round of 16',   '2026-07-16 18:00:00', 'SCHEDULED', 0),
(96,  'ROUND_OF_16',  'Round of 16',   '2026-07-16 21:00:00', 'SCHEDULED', 0),
(97,  'QUARTER_FINAL','Quarter Final', '2026-07-18 18:00:00', 'SCHEDULED', 0),
(98,  'QUARTER_FINAL','Quarter Final', '2026-07-18 21:00:00', 'SCHEDULED', 0),
(99,  'QUARTER_FINAL','Quarter Final', '2026-07-19 18:00:00', 'SCHEDULED', 0),
(100, 'QUARTER_FINAL','Quarter Final', '2026-07-19 21:00:00', 'SCHEDULED', 0),
(101, 'SEMI_FINAL',   'Semi Final',    '2026-07-22 21:00:00', 'SCHEDULED', 0),
(102, 'SEMI_FINAL',   'Semi Final',    '2026-07-23 21:00:00', 'SCHEDULED', 0),
(103, 'THIRD_PLACE',  'Third Place',   '2026-07-25 18:00:00', 'SCHEDULED', 0),
(104, 'FINAL',        'Final',         '2026-07-26 21:00:00', 'SCHEDULED', 0)
ON CONFLICT (match_number) DO NOTHING;
