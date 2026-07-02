package com.lacity.aipppc.dto.admin;

import jakarta.validation.constraints.NotBlank;

/** Create/update payload for a clearance rule. */
public record ClearanceRuleRequest(
    @NotBlank String code, String department, @NotBlank String clearanceName,
    @NotBlank String conditionJson, @NotBlank String reason,
    String submittalRequirementsJson, String infoUrl, Integer confidence,
    String appliesToPermitTypes, Integer priority, Boolean active
) {}
