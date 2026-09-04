package com.whatsmine.repository;

import com.whatsmine.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findBySlug(String slug);

    List<Plan> findByEnabledTrueOrderBySortOrderAsc();

    List<Plan> findAllByOrderBySortOrderAsc();

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    @Query("SELECT COALESCE(MAX(p.sortOrder), 0) FROM Plan p")
    Integer findMaxSortOrder();
}
