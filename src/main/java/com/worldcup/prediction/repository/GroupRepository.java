package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByNameIgnoreCase(String name);

    @Query("SELECT g FROM Group g JOIN FETCH g.teams ORDER BY g.name")
    List<Group> findAllWithTeams();
}
