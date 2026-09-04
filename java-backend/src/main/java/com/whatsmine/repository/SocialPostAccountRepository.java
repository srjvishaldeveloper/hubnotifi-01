package com.whatsmine.repository;

import com.whatsmine.model.SocialPostAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SocialPostAccountRepository extends JpaRepository<SocialPostAccount, Long> {
    List<SocialPostAccount> findBySocialPostId(Long socialPostId);
}
