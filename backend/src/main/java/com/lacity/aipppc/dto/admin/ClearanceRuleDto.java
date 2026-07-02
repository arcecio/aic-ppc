package com.lacity.aipppc.dto.admin;

import com.lacity.aipppc.model.ClearanceRule;

import java.time.Instant;
import java.util.UUID;

/** Full clearance rule for the staff rule editor (SOW 2.1.5 / 2.2.5). */
public record ClearanceRuleDto(
    UUID id, String code, String department, String clearanceName,
    String conditionJson, String reason, String submittalRequirementsJson,
    String infoUrl, int confidence, String appliesToPermitTypes,
    int priority, boolean active, Instant updatedAt
) {
    public static ClearanceRuleDto from(ClearanceRule r) {
        return new ClearanceRuleDto(r.getId(), r.getCode(), r.getDepartment().name(),
            r.getClearanceName(), r.getConditionJson(), r.getReason(),
            r.getSubmittalRequirementsJson(), r.getInfoUrl(), r.getConfidence(),
            r.getAppliesToPermitTypes(), r.getPriority(), r.isActive(), r.getUpdatedAt());
    }
}
