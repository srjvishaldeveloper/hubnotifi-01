package com.whatsmine.repository;

import com.whatsmine.model.FailedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FailedJobRepository extends JpaRepository<FailedJob, Long> {

    Optional<FailedJob> findByUuid(String uuid);
}
