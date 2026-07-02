package com.lacity.aipppc.dto.project;

import com.lacity.aipppc.model.Project;

import java.time.Instant;
import java.util.UUID;

/** Lightweight row for dashboards / project lists. */
public record ProjectSummaryDto(
    UUID id,
    String universalProjectId,
    String title,
    String permitTypeCode,
    String address,
    String status,
    Integer currentReadinessScore,
    String currentReadinessStatus,
    String ownerName,
    Instant createdAt,
    Instant updatedAt
) {
    public static ProjectSummaryDto from(Project p) {
        return new ProjectSummaryDto(p.getId(), p.getUniversalProjectId(), p.getTitle(),
            p.getPermitTypeCode(), p.getAddress(), p.getStatus().name(),
            p.getCurrentReadinessScore(),
            p.getCurrentReadinessStatus() == null ? null : p.getCurrentReadinessStatus().name(),
            p.getOwner() != null ? p.getOwner().getName() : null,
            p.getCreatedAt(), p.getUpdatedAt());
    }
}
