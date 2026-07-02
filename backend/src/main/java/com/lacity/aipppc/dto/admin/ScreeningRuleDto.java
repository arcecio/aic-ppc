package com.lacity.aipppc.dto.admin;

import com.lacity.aipppc.model.ScreeningRule;

import java.time.Instant;
import java.util.UUID;

/** Full screening rule for the staff rule editor (SOW 2.2.3 / Appendix 3 §5.1.6). */
public record ScreeningRuleDto(
    UUID id, String code, String name, String category, String severity,
    String conditionJson, String message, String recommendation,
    String codeReference, String codeUrl, int confidence,
    String appliesToPermitTypes, int priority, boolean active, Instant updatedAt
) {
    public static ScreeningRuleDto from(ScreeningRule r) {
        return new ScreeningRuleDto(r.getId(), r.getCode(), r.getName(), r.getCategory().name(),
            r.getSeverity().name(), r.getConditionJson(), r.getMessage(), r.getRecommendation(),
            r.getCodeReference(), r.getCodeUrl(), r.getConfidence(), r.getAppliesToPermitTypes(),
            r.getPriority(), r.isActive(), r.getUpdatedAt());
    }
}
