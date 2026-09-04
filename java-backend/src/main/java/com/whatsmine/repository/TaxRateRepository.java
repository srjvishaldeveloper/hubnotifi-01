package com.whatsmine.repository;

import com.whatsmine.model.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {
    List<TaxRate> findByEnabledTrue();
    Optional<TaxRate> findByCountryAndRegionAndEnabledTrue(String country, String region);
    Optional<TaxRate> findByCountryAndRegionIsNullAndEnabledTrue(String country);
}
