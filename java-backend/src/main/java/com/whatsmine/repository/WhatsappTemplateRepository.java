package com.whatsmine.repository;

import com.whatsmine.model.WhatsappTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsappTemplateRepository extends JpaRepository<WhatsappTemplate, Long> {

    List<WhatsappTemplate> findByWorkspaceId(Long workspaceId);

    List<WhatsappTemplate> findByWorkspaceIdAndStatusOrderByNameAsc(Long workspaceId, String status);

    List<WhatsappTemplate> findByWorkspaceIdAndWabaId(Long workspaceId, String wabaId);

    Optional<WhatsappTemplate> findByWorkspaceIdAndNameAndLanguage(Long workspaceId, String name, String language);

    Optional<WhatsappTemplate> findByMetaTemplateId(String metaTemplateId);
}
