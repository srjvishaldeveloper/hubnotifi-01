package com.whatsmine.repository;

import com.whatsmine.model.SocialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {
    List<SocialAccount> findByWorkspaceId(Long workspaceId);
}
