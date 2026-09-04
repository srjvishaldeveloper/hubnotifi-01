package com.whatsmine.repository;

import com.whatsmine.model.ChannelAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelAccountRepository extends JpaRepository<ChannelAccount, Long> {

    List<ChannelAccount> findByWorkspaceIdAndStatus(Long workspaceId, String status);

    Optional<ChannelAccount> findByPhoneNumberIdAndChannel(String phoneNumberId, String channel);

    List<ChannelAccount> findByWorkspaceId(Long workspaceId);
}
