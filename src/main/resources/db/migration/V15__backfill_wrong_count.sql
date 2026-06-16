UPDATE community_memberships
SET wrong_count = (
    SELECT COUNT(*)
    FROM predictions p
    WHERE p.user_id = community_memberships.user_id
      AND p.community_id = community_memberships.community_id
      AND p.score_result = 'WRONG'
);
