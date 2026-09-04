package com.whatsmine.repository;

import com.whatsmine.model.InboxLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InboxLabelRepository extends JpaRepository<InboxLabel, Long> {

    List<InboxLabel> findByWorkspaceIdOrderByNameAsc(Long workspaceId);

    Optional<InboxLabel> findByWorkspaceIdAndName(Long workspaceId, String name);

    Optional<InboxLabel> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
