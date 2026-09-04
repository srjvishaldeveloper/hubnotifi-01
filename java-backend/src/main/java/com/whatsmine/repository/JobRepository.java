package com.whatsmine.repository;

import com.whatsmine.model.Job;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    @Query("SELECT j FROM Job j WHERE j.queue = :queue AND j.availableAt <= :now AND (j.reservedAt IS NULL OR j.reservedAt <= :reservedBefore) ORDER BY j.id ASC")
    List<Job> findNextAvailableJobInQueue(@Param("queue") String queue, @Param("now") long now, @Param("reservedBefore") long reservedBefore, Pageable pageable);

    @Query("SELECT j FROM Job j WHERE j.availableAt <= :now AND (j.reservedAt IS NULL OR j.reservedAt <= :reservedBefore) ORDER BY j.id ASC")
    List<Job> findNextAvailableJob(@Param("now") long now, @Param("reservedBefore") long reservedBefore, Pageable pageable);

    long countByQueue(String queue);
}
