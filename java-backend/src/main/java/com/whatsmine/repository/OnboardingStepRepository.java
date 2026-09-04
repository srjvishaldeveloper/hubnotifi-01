package com.whatsmine.repository;

import com.whatsmine.model.OnboardingStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OnboardingStepRepository extends JpaRepository<OnboardingStep, Long> {
    List<OnboardingStep> findByUserId(Long userId);
    Optional<OnboardingStep> findByUserIdAndStepKey(Long userId, String stepKey);
}
