package com.whatsmine.repository;

import com.whatsmine.model.CmsPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CmsPageRepository extends JpaRepository<CmsPage, Long> {
    Optional<CmsPage> findBySlug(String slug);
}
