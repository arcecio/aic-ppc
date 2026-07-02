package com.lacity.aipppc.dto.screening;

import com.lacity.aipppc.model.Finding;

import java.time.Instant;
import java.util.UUID;

/** A finding as surfaced to applicants and staff (SOW 2.2.4, 2.2.7). */
public record FindingDto(
    UUID id,
    String category,
    String severity,
    String title,
    String description,
    String codeReference,
    String codeUrl,
    int confidence,
    String confidenceLevel,
    String triggeringCondition,
    String assumptions,
    String recommendation,
    String source,
    String ruleCode,
    Integer pageNumber,
    Double locationX,
    Double locationY,
    Double locationWidth,
    Double locationHeight,
    String staffDisposition,
    String staffComment,
    boolean applicantFlagged,
    String applicantFlagComment,
    Instant createdAt
) {
    public static FindingDto from(Finding f) {
        return new FindingDto(f.getId(), f.getCategory().name(), f.getSeverity().name(),
            f.getTitle(), f.getDescription(), f.getCodeReference(), f.getCodeUrl(),
            f.getConfidence(), f.getConfidenceLevel().name(), f.getTriggeringCondition(),
            f.getAssumptions(), f.getRecommendation(), f.getSource().name(), f.getRuleCode(),
            f.getPageNumber(), f.getLocationX(), f.getLocationY(), f.getLocationWidth(),
            f.getLocationHeight(), f.getStaffDisposition().name(), f.getStaffComment(),
            f.isApplicantFlagged(), f.getApplicantFlagComment(), f.getCreatedAt());
    }
}
