package com.lacity.aipppc.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Feedback captured for continuous improvement (SOW 2.2.13). Sources include
 * applicants flagging an inaccurate finding, and City staff logging missed
 * detections or rule-tuning notes during QA (Appendix 3 §6.1.4). All model/rule
 * updates remain subject to City review — this table is the auditable inbox.
 */
@Entity
@Table(name = "feedback_entries")
public class FeedbackEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "finding_id")
    private UUID findingId;

    @Column(name = "clearance_id")
    private UUID clearanceId;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitter_role", length = 16)
    private String submitterRole;

    /** INACCURATE_FLAG | MISSED_DETECTION | RULE_TUNING | GENERAL. */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(columnDefinition = "text", nullable = false)
    private String comment;

    /** OPEN | REVIEWED | APPLIED | DISMISSED. */
    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FeedbackEntry() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }
    public UUID getFindingId() { return findingId; }
    public void setFindingId(UUID findingId) { this.findingId = findingId; }
    public UUID getClearanceId() { return clearanceId; }
    public void setClearanceId(UUID clearanceId) { this.clearanceId = clearanceId; }
    public UUID getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(UUID submittedBy) { this.submittedBy = submittedBy; }
    public String getSubmitterRole() { return submitterRole; }
    public void setSubmitterRole(String submitterRole) { this.submitterRole = submitterRole; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final FeedbackEntry f = new FeedbackEntry();
        public Builder runId(UUID v) { f.runId = v; return this; }
        public Builder findingId(UUID v) { f.findingId = v; return this; }
        public Builder clearanceId(UUID v) { f.clearanceId = v; return this; }
        public Builder submittedBy(UUID v) { f.submittedBy = v; return this; }
        public Builder submitterRole(String v) { f.submitterRole = v; return this; }
        public Builder type(String v) { f.type = v; return this; }
        public Builder comment(String v) { f.comment = v; return this; }
        public Builder status(String v) { f.status = v; return this; }
        public FeedbackEntry build() { return f; }
    }
}
