package com.worldcup.prediction.repository;

import com.worldcup.prediction.domain.OAuthIdentity;
import com.worldcup.prediction.domain.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {

    Optional<OAuthIdentity> findByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);

    List<OAuthIdentity> findByUserId(Long userId);

    Optional<OAuthIdentity> findByEmailIgnoreCaseAndProvider(String email, OAuthProvider provider);

    boolean existsByProviderAndProviderSubject(OAuthProvider provider, String providerSubject);

    Optional<OAuthIdentity> findByUserIdAndProvider(Long userId, OAuthProvider provider);
}
