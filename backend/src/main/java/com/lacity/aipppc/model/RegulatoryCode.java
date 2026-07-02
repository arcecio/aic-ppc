package com.lacity.aipppc.model;

import com.lacity.aipppc.model.enums.Jurisdiction;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A structured entry in the regulatory knowledgebase — a code section from the
 * LAMC (Ch. I/1A zoning, Ch. IX building), Title 24, or the LADBS Clearance
 * Summary Handbook (SOW 2.1.1). Findings link to these to provide the specific
 * code reference and link required by SOW 2.2.4. {@code version} tracks which
 * code edition was applied (SOW 2.1.4).
 */
@Entity
@Table(name = "regulatory_codes")
public class RegulatoryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Stable external id used for idempotent re-seeding, e.g. "LAMC-12.21-C". */
    @Column(name = "external_id", nullable = false, unique = true, length = 128)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Jurisdiction jurisdiction = Jurisdiction.CITY_LA;

    /** Code family, e.g. "LAMC Ch.I Zoning", "Title 24", "Clearance Handbook". */
    @Column(name = "code_type", nullable = false, length = 64)
    private String codeType;

    @Column(nullable = false, length = 64)
    private String section;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column
    private String url;

    /** Free-form comma/space keywords used by the lexical retriever. */
    @Column(columnDefinition = "text")
    private String tags;

    @Column(length = 32)
    private String version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RegulatoryCode() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public Jurisdiction getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(Jurisdiction jurisdiction) { this.jurisdiction = jurisdiction; }
    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final RegulatoryCode r = new RegulatoryCode();
        public Builder externalId(String v) { r.externalId = v; return this; }
        public Builder jurisdiction(Jurisdiction v) { r.jurisdiction = v; return this; }
        public Builder codeType(String v) { r.codeType = v; return this; }
        public Builder section(String v) { r.section = v; return this; }
        public Builder title(String v) { r.title = v; return this; }
        public Builder summary(String v) { r.summary = v; return this; }
        public Builder url(String v) { r.url = v; return this; }
        public Builder tags(String v) { r.tags = v; return this; }
        public Builder version(String v) { r.version = v; return this; }
        public RegulatoryCode build() { return r; }
    }
}
