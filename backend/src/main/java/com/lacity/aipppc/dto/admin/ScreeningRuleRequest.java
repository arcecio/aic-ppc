package com.lacity.aipppc.dto.admin;

import jakarta.validation.constraints.NotBlank;

/** Create/update payload for a screening rule. */
public record ScreeningRuleRequest(
    @NotBlank String code, @NotBlank String name, String category, String severity,
    @NotBlank String conditionJson, @NotBlank String message, String recommendation,
    String codeReference, String codeUrl, Integer confidence,
    String appliesToPermitTypes, Integer priority, Boolean active
) {}
