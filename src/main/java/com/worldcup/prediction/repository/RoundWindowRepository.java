package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.RoundWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundWindowRepository extends JpaRepository<RoundWindow, Long> {

    Optional<RoundWindow> findByRoundLabel(String roundLabel);

    @Query("SELECT rw FROM RoundWindow rw ORDER BY rw.autoOpensAt ASC")
    List<RoundWindow> findAllOrderByAutoOpensAtAsc();
}
