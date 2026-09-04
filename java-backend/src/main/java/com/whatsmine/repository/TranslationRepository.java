package com.whatsmine.repository;

import com.whatsmine.model.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, Long> {
    List<Translation> findByLocaleCode(String localeCode);
    List<Translation> findByLocaleCodeAndGroup(String localeCode, String group);
}
