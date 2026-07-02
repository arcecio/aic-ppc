package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.Finding;
import com.lacity.aipppc.model.PreCheckRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FindingRepository extends JpaRepository<Finding, UUID> {
    List<Finding> findByRunOrderBySeverityAscConfidenceDesc(PreCheckRun run);
    List<Finding> findByRun(PreCheckRun run);
    long countByRunId(UUID runId);
}
