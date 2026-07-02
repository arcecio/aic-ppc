package com.lacity.aipppc.dto.project;

import com.lacity.aipppc.model.Project;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Full project record returned to the applicant/staff detail views. */
public record ProjectDto(
    UUID id,
    String universalProjectId,
    String title,
    String permitTypeCode,
    String projectScope,
    String intendedUse,
    String description,
    String address,
    String apn,
    ParcelDto parcel,
    Map<String, Object> formData,
    String status,
    Integer currentReadinessScore,
    String currentReadinessStatus,
    boolean usedAipPpc,
    String ownerName,
    String ownerEmail,
    Instant submittedToEplanlaAt,
    Instant createdAt,
    Instant updatedAt,
    List<DocumentDto> documents
) {
    public static ProjectDto from(Project p, List<DocumentDto> docs,
                                  Function<String, List<String>> jsonToList,
                                  Function<String, Map<String, Object>> jsonToMap) {
        return new ProjectDto(
            p.getId(), p.getUniversalProjectId(), p.getTitle(), p.getPermitTypeCode(),
            p.getProjectScope(), p.getIntendedUse(), p.getDescription(), p.getAddress(), p.getApn(),
            ParcelDto.from(p.getParcel(), jsonToList), jsonToMap.apply(p.getFormDataJson()),
            p.getStatus().name(), p.getCurrentReadinessScore(),
            p.getCurrentReadinessStatus() == null ? null : p.getCurrentReadinessStatus().name(),
            p.isUsedAipPpc(),
            p.getOwner() != null ? p.getOwner().getName() : null,
            p.getOwner() != null ? p.getOwner().getEmail() : null,
            p.getSubmittedToEplanlaAt(), p.getCreatedAt(), p.getUpdatedAt(), docs);
    }
}
