package com.whatsmine.repository;

import com.whatsmine.model.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long>, JpaSpecificationExecutor<Contact> {

    long countByWorkspaceId(Long workspaceId);

    long countByWorkspaceIdAndCreatedAtBetween(Long workspaceId, LocalDateTime from, LocalDateTime to);

    List<Contact> findByWorkspaceIdAndCreatedAtBetween(Long workspaceId, LocalDateTime from, LocalDateTime to);

    Optional<Contact> findByUuid(String uuid);

    Optional<Contact> findByWorkspaceIdAndPhoneE164(Long workspaceId, String phoneE164);

    Optional<Contact> findByWorkspaceIdAndEmail(Long workspaceId, String email);

    @Query("SELECT c FROM Contact c WHERE c.workspaceId = :workspaceId " +
            "AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(c.phoneE164) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Contact> searchContacts(@Param("workspaceId") Long workspaceId, @Param("query") String query);

    List<Contact> findTop30ByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    Page<Contact> findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);

    @Query("SELECT c FROM Contact c WHERE c.workspaceId = :workspaceId AND c.deletedAt IS NULL " +
            "AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(c.phoneE164) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "     OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY c.createdAt DESC")
    Page<Contact> searchContactsPage(@Param("workspaceId") Long workspaceId, @Param("query") String query, Pageable pageable);

    Optional<Contact> findByUuidAndWorkspaceId(String uuid, Long workspaceId);

    List<Contact> findByWorkspaceIdAndUuidIn(Long workspaceId, List<String> uuids);

    List<Contact> findByWorkspaceIdAndDeletedAtIsNull(Long workspaceId);
}
