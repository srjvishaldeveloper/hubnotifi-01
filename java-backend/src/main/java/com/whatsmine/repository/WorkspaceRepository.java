package com.whatsmine.repository;

import com.whatsmine.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    List<Workspace> findByClientId(Long clientId);

    List<Workspace> findByOwnerId(Long ownerId);
}
