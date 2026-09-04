package com.whatsmine.repository;

import com.whatsmine.model.ContactTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactTagRepository extends JpaRepository<ContactTag, Long> {

    List<ContactTag> findByWorkspaceIdOrderByNameAsc(Long workspaceId);

    Optional<ContactTag> findByWorkspaceIdAndName(Long workspaceId, String name);
}
