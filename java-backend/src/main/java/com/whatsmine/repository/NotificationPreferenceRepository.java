package com.whatsmine.repository;

import com.whatsmine.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    List<NotificationPreference> findByUserId(Long userId);
    Optional<NotificationPreference> findByUserIdAndEventAndChannel(Long userId, String event, String channel);
}
