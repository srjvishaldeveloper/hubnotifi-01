package com.whatsmine.repository;

import com.whatsmine.model.ClientSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientSettingRepository extends JpaRepository<ClientSetting, Long> {
    List<ClientSetting> findByClientId(Long clientId);
    Optional<ClientSetting> findByClientIdAndKey(Long clientId, String key);
}
