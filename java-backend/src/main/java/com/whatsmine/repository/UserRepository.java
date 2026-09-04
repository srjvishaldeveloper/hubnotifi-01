package com.whatsmine.repository;

import com.whatsmine.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByWorkspaceId(Long workspaceId);

    List<User> findByClientId(Long clientId);

    Optional<User> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByEmail(String email);
}
