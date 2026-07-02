package com.lacity.aipppc.model;

import com.lacity.aipppc.model.enums.ReadinessStatus;
import com.lacity.aipppc.model.enums.RunStatus;
import com.lacity.aipppc.model.enums.TriggeredBy;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One execution of the pre-plan-check screening pipeline over a project's current
 * documents and context. Holds the readiness outcome, finding/clearance rollups,
 * processing time (for the SOW 2.2.10 KPI), and which AI provider (if any)
 * augmented the rules. A project may have many runs — supporting version
 * comparison across resubmittals (SOW 2.2.11).
 */
@Entity
@Table(name = "precheck_runs")
public class PreCheckRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status = RunStatus.PENDING;

    @Column(name = "readiness_score")
    private Integer readinessScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "readiness_status", length = 32)
    private ReadinessStatus readinessStatus;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "finding_count", nullable = false)
    private int findingCount = 0;
    @Column(name = "blocking_count", nullable = false)
    private int blockingCount = 0;
    @Column(name = "warning_count", nullable = false)
    private int warningCount = 0;
    @Column(name = "info_count", nullable = false)
    private int infoCount = 0;
    @Column(name = "clearance_count", nullable = false)
    private int clearanceCount = 0;

    @Column(name = "processing_ms")
    private Long processingMs;

    @Column(name = "ai_provider_used", length = 32)
    private String aiProviderUsed;

    @Column(name = "ai_model_used")
    private String aiModelUsed;

    @Column(name = "code_version", length = 32)
    private String codeVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 16)
    private TriggeredBy triggeredBy = TriggeredBy.APPLICANT;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PreCheckRun() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public Integer getReadinessScore() { return readinessScore; }
    public void setReadinessScore(Integer readinessScore) { this.readinessScore = readinessScore; }
    public ReadinessStatus getReadinessStatus() { return readinessStatus; }
    public void setReadinessStatus(ReadinessStatus readinessStatus) { this.readinessStatus = readinessStatus; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public int getFindingCount() { return findingCount; }
    public void setFindingCount(int findingCount) { this.findingCount = findingCount; }
    public int getBlockingCount() { return blockingCount; }
    public void setBlockingCount(int blockingCount) { this.blockingCount = blockingCount; }
    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }
    public int getInfoCount() { return infoCount; }
    public void setInfoCount(int infoCount) { this.infoCount = infoCount; }
    public int getClearanceCount() { return clearanceCount; }
    public void setClearanceCount(int clearanceCount) { this.clearanceCount = clearanceCount; }
    public Long getProcessingMs() { return processingMs; }
    public void setProcessingMs(Long processingMs) { this.processingMs = processingMs; }
    public String getAiProviderUsed() { return aiProviderUsed; }
    public void setAiProviderUsed(String aiProviderUsed) { this.aiProviderUsed = aiProviderUsed; }
    public String getAiModelUsed() { return aiModelUsed; }
    public void setAiModelUsed(String aiModelUsed) { this.aiModelUsed = aiModelUsed; }
    public String getCodeVersion() { return codeVersion; }
    public void setCodeVersion(String codeVersion) { this.codeVersion = codeVersion; }
    public TriggeredBy getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(TriggeredBy triggeredBy) { this.triggeredBy = triggeredBy; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final PreCheckRun r = new PreCheckRun();
        public Builder project(Project v) { r.project = v; return this; }
        public Builder status(RunStatus v) { r.status = v; return this; }
        public Builder triggeredBy(TriggeredBy v) { r.triggeredBy = v; return this; }
        public Builder codeVersion(String v) { r.codeVersion = v; return this; }
        public PreCheckRun build() { return r; }
    }
}
