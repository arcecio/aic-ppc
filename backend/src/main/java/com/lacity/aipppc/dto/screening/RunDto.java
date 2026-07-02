package com.lacity.aipppc.dto.screening;

import com.lacity.aipppc.model.PreCheckRun;

import java.time.Instant;
import java.util.UUID;

/** Screening run summary (status, readiness, rollups) — SOW 1.2.3 / 2.2.12. */
public record RunDto(
    UUID id,
    UUID projectId,
    String universalProjectId,
    String status,
    Integer readinessScore,
    String readinessStatus,
    String summary,
    int findingCount,
    int blockingCount,
    int warningCount,
    int infoCount,
    int clearanceCount,
    Long processingMs,
    String aiProviderUsed,
    String aiModelUsed,
    String codeVersion,
    String triggeredBy,
    String errorMessage,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt
) {
    public static RunDto from(PreCheckRun r) {
        return new RunDto(r.getId(), r.getProject().getId(), r.getProject().getUniversalProjectId(),
            r.getStatus().name(), r.getReadinessScore(),
            r.getReadinessStatus() == null ? null : r.getReadinessStatus().name(),
            r.getSummary(), r.getFindingCount(), r.getBlockingCount(), r.getWarningCount(),
            r.getInfoCount(), r.getClearanceCount(), r.getProcessingMs(), r.getAiProviderUsed(),
            r.getAiModelUsed(), r.getCodeVersion(), r.getTriggeredBy().name(), r.getErrorMessage(),
            r.getStartedAt(), r.getCompletedAt(), r.getCreatedAt());
    }
}
