package com.whatsmine.repository;

import com.whatsmine.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmail(String email);

    List<Client> findByStatus(String status);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Client> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Client> findTop6ByOrderByCreatedAtDesc();
}
