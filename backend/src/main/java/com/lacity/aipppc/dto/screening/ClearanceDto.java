package com.lacity.aipppc.dto.screening;

import com.lacity.aipppc.model.Clearance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** A likely-required clearance as surfaced to applicants and staff (SOW 2.2.5). */
public record ClearanceDto(
    UUID id,
    String department,
    String clearanceName,
    String reason,
    int confidence,
    String confidenceLevel,
    List<String> submittalRequirements,
    String infoUrl,
    String source,
    String ruleCode,
    String staffDisposition,
    String staffComment,
    Instant createdAt
) {
    public static ClearanceDto from(Clearance c, Function<String, List<String>> jsonToList) {
        return new ClearanceDto(c.getId(), c.getDepartment().name(), c.getClearanceName(),
            c.getReason(), c.getConfidence(), c.getConfidenceLevel().name(),
            jsonToList.apply(c.getSubmittalRequirementsJson()), c.getInfoUrl(),
            c.getSource().name(), c.getRuleCode(), c.getStaffDisposition().name(),
            c.getStaffComment(), c.getCreatedAt());
    }
}
