package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.ClearanceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClearanceRuleRepository extends JpaRepository<ClearanceRule, UUID> {
    Optional<ClearanceRule> findByCode(String code);
    boolean existsByCode(String code);
    List<ClearanceRule> findByActiveTrueOrderByPriorityAsc();
    List<ClearanceRule> findAllByOrderByPriorityAsc();
}
