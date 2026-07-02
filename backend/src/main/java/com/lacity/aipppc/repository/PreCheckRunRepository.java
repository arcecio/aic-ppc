package com.lacity.aipppc.repository;

import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.Project;
import com.lacity.aipppc.model.enums.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreCheckRunRepository extends JpaRepository<PreCheckRun, UUID> {
    List<PreCheckRun> findByProjectOrderByCreatedAtDesc(Project project);
    Optional<PreCheckRun> findTopByProjectOrderByCreatedAtDesc(Project project);
    Page<PreCheckRun> findByStatusOrderByCompletedAtDesc(RunStatus status, Pageable pageable);
    Page<PreCheckRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(RunStatus status);

    // ── ED19 / SOW 2.2.12 analytics ───────────────────────────────────────────
    @Query("select count(r) from PreCheckRun r where r.status = 'COMPLETED'")
    long countCompleted();

    @Query("select coalesce(avg(r.processingMs), 0) from PreCheckRun r where r.processingMs is not null")
    double averageProcessingMs();

    @Query("select coalesce(sum(r.findingCount), 0) from PreCheckRun r")
    long totalFindings();

    @Query("select coalesce(sum(r.clearanceCount), 0) from PreCheckRun r")
    long totalClearances();

    @Query("select coalesce(avg(r.readinessScore), 0) from PreCheckRun r where r.readinessScore is not null")
    double averageReadinessScore();

    // Share of completed runs at or under the SOW 2.2.10 target processing time.
    @Query("select count(r) from PreCheckRun r where r.status = 'COMPLETED' "
        + "and r.processingMs is not null and r.processingMs <= :thresholdMs")
    long countCompletedWithinMs(long thresholdMs);
}
