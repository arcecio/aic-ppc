package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.Clearance;
import com.lacity.aipppc.model.PreCheckRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClearanceRepository extends JpaRepository<Clearance, UUID> {
    List<Clearance> findByRunOrderByConfidenceDesc(PreCheckRun run);
    List<Clearance> findByRun(PreCheckRun run);
}
