package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserRole;
import com.worldcup.prediction.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByStatus(UserStatus status);

    List<User> findByRole(UserRole role);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.status = 'ACTIVE'
            ORDER BY u.totalPoints DESC,
                     u.exactScoreCount DESC,
                     u.correctWinnerCount DESC,
                     u.createdAt ASC
            """)
    List<User> findLeaderboard();

    @Query("""
            SELECT u FROM User u
            WHERE u.status = 'ACTIVE'
            ORDER BY u.totalPoints DESC,
                     u.exactScoreCount DESC,
                     u.correctWinnerCount DESC,
                     u.createdAt ASC
            LIMIT 10
            """)
    List<User> findTop10Leaderboard();

    long countByStatus(UserStatus status);
}
