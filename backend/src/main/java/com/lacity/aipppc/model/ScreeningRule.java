package com.lacity.aipppc.model;

import com.lacity.aipppc.model.enums.FindingCategory;
import com.lacity.aipppc.model.enums.Severity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A configurable pre-screening rule — the <b>primary</b> detection mechanism
 * (SOW 2.2.3: "configurable rule-based logic ... as the primary mechanism with
 * AI-assisted analysis used to enhance"). Staff CRUD these without code changes
 * (Appendix 3 §5.1.6). When a rule's {@code conditionJson} matches a project's
 * evaluation context it emits a {@link Finding} carrying this rule's severity,
 * category, code reference, message, and recommendation.
 */
@Entity
@Table(name = "screening_rules")
public class ScreeningRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FindingCategory category = FindingCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity = Severity.WARNING;

    /** Boolean condition tree evaluated against the project context (docs/04-rule-engine.md). */
    @Column(name = "condition_json", columnDefinition = "text", nullable = false)
    private String conditionJson;

    /** Human-readable finding text emitted when the rule fires. */
    @Column(name = "message", columnDefinition = "text", nullable = false)
    private String message;

    @Column(columnDefinition = "text")
    private String recommendation;

    @Column(name = "code_reference")
    private String codeReference;

    @Column(name = "code_url")
    private String codeUrl;

    /** Baseline confidence (0-100) for findings from this rule. */
    @Column(nullable = false)
    private int confidence = 90;

    /** CSV of permit-type codes this rule applies to; blank/"*" = all. */
    @Column(name = "applies_to_permit_types")
    private String appliesToPermitTypes;

    /** Lower runs earlier (review-sequence logic, SOW 2.1.3). */
    @Column(nullable = false)
    private int priority = 100;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ScreeningRule() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FindingCategory getCategory() { return category; }
    public void setCategory(FindingCategory category) { this.category = category; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public String getConditionJson() { return conditionJson; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getCodeReference() { return codeReference; }
    public void setCodeReference(String codeReference) { this.codeReference = codeReference; }
    public String getCodeUrl() { return codeUrl; }
    public void setCodeUrl(String codeUrl) { this.codeUrl = codeUrl; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
    public String getAppliesToPermitTypes() { return appliesToPermitTypes; }
    public void setAppliesToPermitTypes(String appliesToPermitTypes) { this.appliesToPermitTypes = appliesToPermitTypes; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ScreeningRule r = new ScreeningRule();
        public Builder code(String v) { r.code = v; return this; }
        public Builder name(String v) { r.name = v; return this; }
        public Builder category(FindingCategory v) { r.category = v; return this; }
        public Builder severity(Severity v) { r.severity = v; return this; }
        public Builder conditionJson(String v) { r.conditionJson = v; return this; }
        public Builder message(String v) { r.message = v; return this; }
        public Builder recommendation(String v) { r.recommendation = v; return this; }
        public Builder codeReference(String v) { r.codeReference = v; return this; }
        public Builder codeUrl(String v) { r.codeUrl = v; return this; }
        public Builder confidence(int v) { r.confidence = v; return this; }
        public Builder appliesToPermitTypes(String v) { r.appliesToPermitTypes = v; return this; }
        public Builder priority(int v) { r.priority = v; return this; }
        public Builder active(boolean v) { r.active = v; return this; }
        public ScreeningRule build() { return r; }
    }
}
