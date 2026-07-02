package com.lacity.aipppc.model;

import com.lacity.aipppc.model.enums.*;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A single issue surfaced by a screening run (SOW 2.2.4). Carries the standardized
 * severity, category, the specific code reference + link, a confidence score and
 * bucket, the triggering condition and any assumptions the system made, and a
 * plain-language recommendation. Optional page + bounding box drive the visual
 * overlay on the plan viewer (SOW 2.2.7).
 *
 * <p>Human-in-the-loop: {@code staffDisposition} lets a City reviewer accept,
 * modify, or reject the finding before final disposition (Appendix 3 §5.1.5), and
 * {@code applicantFlagged} lets an applicant notify staff of an inaccurate flag
 * (SOW 2.2.4).
 */
@Entity
@Table(name = "findings")
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private PreCheckRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FindingCategory category = FindingCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity = Severity.WARNING;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String description;

    @Column(name = "code_reference")
    private String codeReference;

    @Column(name = "code_url")
    private String codeUrl;

    @Column(nullable = false)
    private int confidence = 90;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 8)
    private ConfidenceLevel confidenceLevel = ConfidenceLevel.HIGH;

    @Column(name = "triggering_condition", columnDefinition = "text")
    private String triggeringCondition;

    @Column(columnDefinition = "text")
    private String assumptions;

    @Column(columnDefinition = "text")
    private String recommendation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FindingSource source = FindingSource.RULE;

    @Column(name = "rule_code", length = 64)
    private String ruleCode;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "location_x")
    private Double locationX;
    @Column(name = "location_y")
    private Double locationY;
    @Column(name = "location_width")
    private Double locationWidth;
    @Column(name = "location_height")
    private Double locationHeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_disposition", nullable = false, length = 16)
    private StaffDisposition staffDisposition = StaffDisposition.PENDING;

    @Column(name = "staff_comment", columnDefinition = "text")
    private String staffComment;

    @Column(name = "staff_reviewed_by")
    private UUID staffReviewedBy;

    @Column(name = "staff_reviewed_at")
    private Instant staffReviewedAt;

    @Column(name = "applicant_flagged", nullable = false)
    private boolean applicantFlagged = false;

    @Column(name = "applicant_flag_comment", columnDefinition = "text")
    private String applicantFlagComment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Finding() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public PreCheckRun getRun() { return run; }
    public void setRun(PreCheckRun run) { this.run = run; }
    public FindingCategory getCategory() { return category; }
    public void setCategory(FindingCategory category) { this.category = category; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCodeReference() { return codeReference; }
    public void setCodeReference(String codeReference) { this.codeReference = codeReference; }
    public String getCodeUrl() { return codeUrl; }
    public void setCodeUrl(String codeUrl) { this.codeUrl = codeUrl; }
    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
    public ConfidenceLevel getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(ConfidenceLevel confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    public String getTriggeringCondition() { return triggeringCondition; }
    public void setTriggeringCondition(String triggeringCondition) { this.triggeringCondition = triggeringCondition; }
    public String getAssumptions() { return assumptions; }
    public void setAssumptions(String assumptions) { this.assumptions = assumptions; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public FindingSource getSource() { return source; }
    public void setSource(FindingSource source) { this.source = source; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public Double getLocationX() { return locationX; }
    public void setLocationX(Double locationX) { this.locationX = locationX; }
    public Double getLocationY() { return locationY; }
    public void setLocationY(Double locationY) { this.locationY = locationY; }
    public Double getLocationWidth() { return locationWidth; }
    public void setLocationWidth(Double locationWidth) { this.locationWidth = locationWidth; }
    public Double getLocationHeight() { return locationHeight; }
    public void setLocationHeight(Double locationHeight) { this.locationHeight = locationHeight; }
    public StaffDisposition getStaffDisposition() { return staffDisposition; }
    public void setStaffDisposition(StaffDisposition staffDisposition) { this.staffDisposition = staffDisposition; }
    public String getStaffComment() { return staffComment; }
    public void setStaffComment(String staffComment) { this.staffComment = staffComment; }
    public UUID getStaffReviewedBy() { return staffReviewedBy; }
    public void setStaffReviewedBy(UUID staffReviewedBy) { this.staffReviewedBy = staffReviewedBy; }
    public Instant getStaffReviewedAt() { return staffReviewedAt; }
    public void setStaffReviewedAt(Instant staffReviewedAt) { this.staffReviewedAt = staffReviewedAt; }
    public boolean isApplicantFlagged() { return applicantFlagged; }
    public void setApplicantFlagged(boolean applicantFlagged) { this.applicantFlagged = applicantFlagged; }
    public String getApplicantFlagComment() { return applicantFlagComment; }
    public void setApplicantFlagComment(String applicantFlagComment) { this.applicantFlagComment = applicantFlagComment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Finding f = new Finding();
        public Builder run(PreCheckRun v) { f.run = v; return this; }
        public Builder category(FindingCategory v) { f.category = v; return this; }
        public Builder severity(Severity v) { f.severity = v; return this; }
        public Builder title(String v) { f.title = v; return this; }
        public Builder description(String v) { f.description = v; return this; }
        public Builder codeReference(String v) { f.codeReference = v; return this; }
        public Builder codeUrl(String v) { f.codeUrl = v; return this; }
        public Builder confidence(int v) { f.confidence = v; f.confidenceLevel = ConfidenceLevel.fromScore(v); return this; }
        public Builder triggeringCondition(String v) { f.triggeringCondition = v; return this; }
        public Builder assumptions(String v) { f.assumptions = v; return this; }
        public Builder recommendation(String v) { f.recommendation = v; return this; }
        public Builder source(FindingSource v) { f.source = v; return this; }
        public Builder ruleCode(String v) { f.ruleCode = v; return this; }
        public Builder pageNumber(Integer v) { f.pageNumber = v; return this; }
        public Builder locationX(Double v) { f.locationX = v; return this; }
        public Builder locationY(Double v) { f.locationY = v; return this; }
        public Builder locationWidth(Double v) { f.locationWidth = v; return this; }
        public Builder locationHeight(Double v) { f.locationHeight = v; return this; }
        public Finding build() { return f; }
    }
}
