package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.ScreeningRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScreeningRuleRepository extends JpaRepository<ScreeningRule, UUID> {
    Optional<ScreeningRule> findByCode(String code);
    boolean existsByCode(String code);
    List<ScreeningRule> findByActiveTrueOrderByPriorityAsc();
    List<ScreeningRule> findAllByOrderByPriorityAsc();
}
