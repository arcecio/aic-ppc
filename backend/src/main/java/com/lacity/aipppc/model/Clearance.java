package com.lacity.aipppc.model;

import com.lacity.aipppc.model.enums.ConfidenceLevel;
import com.lacity.aipppc.model.enums.Department;
import com.lacity.aipppc.model.enums.FindingSource;
import com.lacity.aipppc.model.enums.StaffDisposition;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A likely-required departmental clearance identified for a run (SOW 2.2.5).
 * Names the issuing department, why it is triggered, confidence, an info link,
 * and the submittal requirements (JSON array) the applicant must include to
 * obtain it. Subject to staff QA via {@code staffDisposition} — final clearance
 * determination remains with City staff.
 */
@Entity
@Table(name = "clearances")
public class Clearance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private PreCheckRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Department department = Department.LADBS;

    @Column(name = "clearance_name", nullable = false)
    private String clearanceName;

    @Column(columnDefinition = "text", nullable = false)
    private String reason;

    @Column(nullable = false)
    private int confidence = 80;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 8)
    private ConfidenceLevel confidenceLevel = ConfidenceLevel.MEDIUM;

    /** JSON array of submittal-requirement strings. */
    @Column(name = "submittal_requirements_json", columnDefinition = "text")
    private String submittalRequirementsJson;

    @Column(name = "info_url")
    private String infoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FindingSource source = FindingSource.RULE;

    @Column(name = "rule_code", length = 64)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_disposition", nullable = false, length = 16)
    private StaffDisposition staffDisposition = StaffDisposition.PENDING;

    @Column(name = "staff_comment", columnDefinition = "text")
    private String staffComment;

    @Column(name = "staff_reviewed_by")
    private UUID staffReviewedBy;

    @Column(name = "staff_reviewed_at")
    private Instant staffReviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Clearance() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PreCheckRun getRun() { return run; }
    public void setRun(PreCheckRun run) { this.run = run; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public String getClearanceName() { return clearanceName; }
    public void setClearanceName(String clearanceName) { this.clearanceName = clearanceName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
    public ConfidenceLevel getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(ConfidenceLevel confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    public String getSubmittalRequirementsJson() { return submittalRequirementsJson; }
    public void setSubmittalRequirementsJson(String submittalRequirementsJson) { this.submittalRequirementsJson = submittalRequirementsJson; }
    public String getInfoUrl() { return infoUrl; }
    public void setInfoUrl(String infoUrl) { this.infoUrl = infoUrl; }
    public FindingSource getSource() { return source; }
    public void setSource(FindingSource source) { this.source = source; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public StaffDisposition getStaffDisposition() { return staffDisposition; }
    public void setStaffDisposition(StaffDisposition staffDisposition) { this.staffDisposition = staffDisposition; }
    public String getStaffComment() { return staffComment; }
    public void setStaffComment(String staffComment) { this.staffComment = staffComment; }
    public UUID getStaffReviewedBy() { return staffReviewedBy; }
    public void setStaffReviewedBy(UUID staffReviewedBy) { this.staffReviewedBy = staffReviewedBy; }
    public Instant getStaffReviewedAt() { return staffReviewedAt; }
    public void setStaffReviewedAt(Instant staffReviewedAt) { this.staffReviewedAt = staffReviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Clearance c = new Clearance();
        public Builder run(PreCheckRun v) { c.run = v; return this; }
        public Builder department(Department v) { c.department = v; return this; }
        public Builder clearanceName(String v) { c.clearanceName = v; return this; }
        public Builder reason(String v) { c.reason = v; return this; }
        public Builder confidence(int v) { c.confidence = v; c.confidenceLevel = ConfidenceLevel.fromScore(v); return this; }
        public Builder submittalRequirementsJson(String v) { c.submittalRequirementsJson = v; return this; }
        public Builder infoUrl(String v) { c.infoUrl = v; return this; }
        public Builder source(FindingSource v) { c.source = v; return this; }
        public Builder ruleCode(String v) { c.ruleCode = v; return this; }
        public Clearance build() { return c; }
    }
}
