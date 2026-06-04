package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {
    Optional<Community> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Community> findAllByOrderByNameAsc();
}
