package com.whatsmine.repository;

import com.whatsmine.model.WhatsappWidget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsappWidgetRepository extends JpaRepository<WhatsappWidget, Long> {

    List<WhatsappWidget> findByWorkspaceIdOrderByIdDesc(Long workspaceId);

    Optional<WhatsappWidget> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<WhatsappWidget> findByWidgetKey(String widgetKey);
}
