package com.lacity.aipppc.model;

import com.lacity.aipppc.model.enums.ProjectStatus;
import com.lacity.aipppc.model.enums.ReadinessStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * The verified project record established at intake (SOW 2.2.1). Carries a
 * Universal Project ID (BuildLA-style tracking number) that follows the project
 * into Formal Plan Check, the selected permit type, the resolved parcel/zoning
 * context, the applicant's dynamic-form answers, and the latest screening
 * outcome. {@code usedAipPpc} feeds the ED19 KPI that distinguishes assisted from
 * unassisted submissions (SOW 2.2.12).
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** BuildLA Universal Project ID, e.g. "LA-2026-000123". Unique across the system. */
    @Column(name = "universal_project_id", nullable = false, unique = true, length = 32)
    private String universalProjectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String title;

    @Column(name = "permit_type_code", nullable = false, length = 64)
    private String permitTypeCode;

    @Column(name = "project_scope", columnDefinition = "text")
    private String projectScope;

    @Column(name = "intended_use")
    private String intendedUse;

    @Column(columnDefinition = "text")
    private String description;

    @Column
    private String address;

    @Column(length = 32)
    private String apn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id")
    private Parcel parcel;

    /** JSON object of dynamic-form field answers keyed by field id. */
    @Column(name = "form_data_json", columnDefinition = "text")
    private String formDataJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Column(name = "current_readiness_score")
    private Integer currentReadinessScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_readiness_status", length = 32)
    private ReadinessStatus currentReadinessStatus = ReadinessStatus.NOT_ASSESSED;

    @Column(name = "used_aip_ppc", nullable = false)
    private boolean usedAipPpc = true;

    @Column(name = "submitted_to_eplanla_at")
    private Instant submittedToEplanlaAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Project() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUniversalProjectId() { return universalProjectId; }
    public void setUniversalProjectId(String universalProjectId) { this.universalProjectId = universalProjectId; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPermitTypeCode() { return permitTypeCode; }
    public void setPermitTypeCode(String permitTypeCode) { this.permitTypeCode = permitTypeCode; }
    public String getProjectScope() { return projectScope; }
    public void setProjectScope(String projectScope) { this.projectScope = projectScope; }
    public String getIntendedUse() { return intendedUse; }
    public void setIntendedUse(String intendedUse) { this.intendedUse = intendedUse; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getApn() { return apn; }
    public void setApn(String apn) { this.apn = apn; }
    public Parcel getParcel() { return parcel; }
    public void setParcel(Parcel parcel) { this.parcel = parcel; }
    public String getFormDataJson() { return formDataJson; }
    public void setFormDataJson(String formDataJson) { this.formDataJson = formDataJson; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public Integer getCurrentReadinessScore() { return currentReadinessScore; }
    public void setCurrentReadinessScore(Integer currentReadinessScore) { this.currentReadinessScore = currentReadinessScore; }
    public ReadinessStatus getCurrentReadinessStatus() { return currentReadinessStatus; }
    public void setCurrentReadinessStatus(ReadinessStatus currentReadinessStatus) { this.currentReadinessStatus = currentReadinessStatus; }
    public boolean isUsedAipPpc() { return usedAipPpc; }
    public void setUsedAipPpc(boolean usedAipPpc) { this.usedAipPpc = usedAipPpc; }
    public Instant getSubmittedToEplanlaAt() { return submittedToEplanlaAt; }
    public void setSubmittedToEplanlaAt(Instant submittedToEplanlaAt) { this.submittedToEplanlaAt = submittedToEplanlaAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Project p = new Project();
        public Builder universalProjectId(String v) { p.universalProjectId = v; return this; }
        public Builder owner(User v) { p.owner = v; return this; }
        public Builder title(String v) { p.title = v; return this; }
        public Builder permitTypeCode(String v) { p.permitTypeCode = v; return this; }
        public Builder projectScope(String v) { p.projectScope = v; return this; }
        public Builder intendedUse(String v) { p.intendedUse = v; return this; }
        public Builder description(String v) { p.description = v; return this; }
        public Builder address(String v) { p.address = v; return this; }
        public Builder apn(String v) { p.apn = v; return this; }
        public Builder parcel(Parcel v) { p.parcel = v; return this; }
        public Builder formDataJson(String v) { p.formDataJson = v; return this; }
        public Builder status(ProjectStatus v) { p.status = v; return this; }
        public Builder currentReadinessScore(Integer v) { p.currentReadinessScore = v; return this; }
        public Builder currentReadinessStatus(ReadinessStatus v) { p.currentReadinessStatus = v; return this; }
        public Builder usedAipPpc(boolean v) { p.usedAipPpc = v; return this; }
        public Project build() { return p; }
    }
}
