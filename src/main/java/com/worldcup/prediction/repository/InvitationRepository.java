package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
