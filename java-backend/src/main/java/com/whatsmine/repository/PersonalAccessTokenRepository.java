package com.whatsmine.repository;

import com.whatsmine.model.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {

    Optional<PersonalAccessToken> findByToken(String token);

    List<PersonalAccessToken> findByTokenableTypeAndTokenableId(String tokenableType, Long tokenableId);
}
