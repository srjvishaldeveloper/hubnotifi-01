package com.whatsmine.repository;

import com.whatsmine.model.Locale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocaleRepository extends JpaRepository<Locale, Long> {
    Optional<Locale> findByCode(String code);
    List<Locale> findByEnabledTrue();
}
