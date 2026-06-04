-- =============================================================================
-- V3__test_data.sql — Local development test data
-- 10 users, 3 completed matches with results, 6 users with scored predictions
-- PredictionScore enum: EXACT(3), CORRECT_DRAW(2), CORRECT_WINNER(1), WRONG(0)
-- =============================================================================

-- ---------------------------------------------------------------
-- USERS  (10 participants — 6 predict, 4 do not)
-- ---------------------------------------------------------------
INSERT INTO users (email, first_name, last_name, display_name, avatar_url,
                   status, role, total_points, exact_score_count,
                   correct_winner_count, correct_draw_count,
                   created_at, updated_at) VALUES
-- Points from 3 completed matches (calculated below)
('alice.smith@test.com',  'Alice',   'Smith',    'Alice Smith',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=alice',
 'ACTIVE','PARTICIPANT', 9, 3, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('bob.jones@test.com',    'Bob',     'Jones',    'Bob Jones',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=bob',
 'ACTIVE','PARTICIPANT', 4, 0, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('charlie.lee@test.com',  'Charlie', 'Lee',      'Charlie Lee',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=charlie',
 'ACTIVE','PARTICIPANT', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('diana.wang@test.com',   'Diana',   'Wang',     'Diana Wang',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=diana',
 'ACTIVE','PARTICIPANT', 6, 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('eve.martinez@test.com', 'Eve',     'Martinez', 'Eve Martinez',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=eve',
 'ACTIVE','PARTICIPANT', 7, 2, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('frank.kim@test.com',    'Frank',   'Kim',      'Frank Kim',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=frank',
 'ACTIVE','PARTICIPANT', 2, 0, 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('grace.brown@test.com',  'Grace',   'Brown',    'Grace Brown',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=grace',
 'ACTIVE','PARTICIPANT', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('henry.davis@test.com',  'Henry',   'Davis',    'Henry Davis',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=henry',
 'ACTIVE','PARTICIPANT', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('iris.chen@test.com',    'Iris',    'Chen',     'Iris Chen',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=iris',
 'ACTIVE','PARTICIPANT', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('james.taylor@test.com', 'James',   'Taylor',   'James Taylor',
 'https://api.dicebear.com/9.x/avataaars/svg?seed=james',
 'ACTIVE','PARTICIPANT', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ---------------------------------------------------------------
-- COMPLETED matches with results
-- Match 1: Mexico 2-0 South Africa   (home win)
-- Match 2: South Korea 1-1 Czechia   (draw)
-- Match 3: Canada 3-1 Bosnia-Herz.   (home win)
-- ---------------------------------------------------------------
UPDATE matches SET status='COMPLETED', home_score=2, away_score=0,
    result_entered_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
WHERE match_number=1;

UPDATE matches SET status='COMPLETED', home_score=1, away_score=1,
    result_entered_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
WHERE match_number=2;

UPDATE matches SET status='COMPLETED', home_score=3, away_score=1,
    result_entered_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
WHERE match_number=3;

-- ---------------------------------------------------------------
-- SCORED PREDICTIONS for completed matches (1-3)
--
-- Alice  9pts: m1 EXACT(3) + m2 EXACT(3) + m3 EXACT(3)
-- Bob    4pts: m1 CORRECT_WINNER(1) + m2 CORRECT_DRAW(2) + m3 CORRECT_WINNER(1)
-- Charlie 0pts: m1 WRONG + m2 WRONG + m3 WRONG
-- Diana  6pts: m1 EXACT(3) + m2 CORRECT_DRAW(2) + m3 CORRECT_WINNER(1)
-- Eve    7pts: m1 CORRECT_WINNER(1) + m2 EXACT(3) + m3 EXACT(3)
-- Frank  2pts: m1 CORRECT_WINNER(1) + m2 WRONG + m3 CORRECT_WINNER(1)
-- ---------------------------------------------------------------

-- ALICE
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,2,0,'EXACT',3,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='alice.smith@test.com' AND m.match_number=1;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,1,1,'EXACT',3,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='alice.smith@test.com' AND m.match_number=2;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,3,1,'EXACT',3,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='alice.smith@test.com' AND m.match_number=3;

-- BOB
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,1,0,'CORRECT_WINNER',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='bob.jones@test.com' AND m.match_number=1;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,0,0,'CORRECT_DRAW',2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='bob.jones@test.com' AND m.match_number=2;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,2,0,'CORRECT_WINNER',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='bob.jones@test.com' AND m.match_number=3;

-- CHARLIE
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,0,2,'WRONG',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='charlie.lee@test.com' AND m.match_number=1;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,2,1,'WRONG',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='charlie.lee@test.com' AND m.match_number=2;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,1,2,'WRONG',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='charlie.lee@test.com' AND m.match_number=3;

-- DIANA
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,2,0,'EXACT',3,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='diana.wang@test.com' AND m.match_number=1;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,2,2,'CORRECT_DRAW',2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='diana.wang@test.com' AND m.match_number=2;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,2,1,'CORRECT_WINNER',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='diana.wang@test.com' AND m.match_number=3;

-- EVE
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,3,0,'CORRECT_WINNER',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='eve.martinez@test.com' AND m.match_number=1;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,1,1,'EXACT',3,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='eve.martinez@test.com' AND m.match_number=2;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,3,1,'EXACT',3,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='eve.martinez@test.com' AND m.match_number=3;

-- FRANK
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,2,1,'CORRECT_WINNER',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='frank.kim@test.com' AND m.match_number=1;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,1,0,'WRONG',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='frank.kim@test.com' AND m.match_number=2;
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id,m.id,2,1,'CORRECT_WINNER',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM users u,matches m WHERE u.email='frank.kim@test.com' AND m.match_number=3;

-- ---------------------------------------------------------------
-- PENDING predictions for matches 4-12 (all 6 predictors)
-- Varied scores based on user+match combination
-- ---------------------------------------------------------------
INSERT INTO predictions (user_id,match_id,predicted_home,predicted_away,score_result,points_awarded,submitted_at,updated_at)
SELECT u.id, m.id,
    CASE (ABS(u.id + m.match_number) % 5) WHEN 0 THEN 2 WHEN 1 THEN 1 WHEN 2 THEN 0 WHEN 3 THEN 3 ELSE 2 END,
    CASE (ABS(u.id + m.match_number + 1) % 4) WHEN 0 THEN 1 WHEN 1 THEN 0 WHEN 2 THEN 2 ELSE 1 END,
    'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u
CROSS JOIN matches m
WHERE u.email IN ('alice.smith@test.com','bob.jones@test.com','charlie.lee@test.com',
                  'diana.wang@test.com','eve.martinez@test.com','frank.kim@test.com')
AND m.match_number BETWEEN 4 AND 12
ON CONFLICT (user_id, match_id) DO NOTHING;
