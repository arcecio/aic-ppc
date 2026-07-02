package com.lacity.aipppc.model;

import com.lacity.aipppc.model.enums.Department;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A configurable clearance-identification rule (SOW 2.1.5, 2.2.5). When its
 * {@code conditionJson} matches the project context it emits a {@link Clearance}
 * naming the department, why it is likely required, its confidence, and the
 * submittal documents needed to obtain it. Rules are the primary mechanism;
 * AI augments detection of relevant features.
 */
@Entity
@Table(name = "clearance_rules")
public class ClearanceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Department department = Department.LADBS;

    @Column(name = "clearance_name", nullable = false)
    private String clearanceName;

    @Column(name = "condition_json", columnDefinition = "text", nullable = false)
    private String conditionJson;

    /** Why the clearance is triggered — shown to the applicant (SOW 2.2.5). */
    @Column(columnDefinition = "text", nullable = false)
    private String reason;

    /** JSON array of submittal-requirement strings needed to obtain the clearance. */
    @Column(name = "submittal_requirements_json", columnDefinition = "text")
    private String submittalRequirementsJson;

    @Column(name = "info_url")
    private String infoUrl;

    @Column(nullable = false)
    private int confidence = 80;

    @Column(name = "applies_to_permit_types")
    private String appliesToPermitTypes;

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

    public ClearanceRule() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public String getClearanceName() { return clearanceName; }
    public void setClearanceName(String clearanceName) { this.clearanceName = clearanceName; }
    public String getConditionJson() { return conditionJson; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSubmittalRequirementsJson() { return submittalRequirementsJson; }
    public void setSubmittalRequirementsJson(String submittalRequirementsJson) { this.submittalRequirementsJson = submittalRequirementsJson; }
    public String getInfoUrl() { return infoUrl; }
    public void setInfoUrl(String infoUrl) { this.infoUrl = infoUrl; }
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
        private final ClearanceRule r = new ClearanceRule();
        public Builder code(String v) { r.code = v; return this; }
        public Builder department(Department v) { r.department = v; return this; }
        public Builder clearanceName(String v) { r.clearanceName = v; return this; }
        public Builder conditionJson(String v) { r.conditionJson = v; return this; }
        public Builder reason(String v) { r.reason = v; return this; }
        public Builder submittalRequirementsJson(String v) { r.submittalRequirementsJson = v; return this; }
        public Builder infoUrl(String v) { r.infoUrl = v; return this; }
        public Builder confidence(int v) { r.confidence = v; return this; }
        public Builder appliesToPermitTypes(String v) { r.appliesToPermitTypes = v; return this; }
        public Builder priority(int v) { r.priority = v; return this; }
        public Builder active(boolean v) { r.active = v; return this; }
        public ClearanceRule build() { return r; }
    }
}
