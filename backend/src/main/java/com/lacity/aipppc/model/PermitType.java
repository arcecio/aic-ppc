package com.lacity.aipppc.model;

import com.lacity.aipppc.model.enums.PermitCategory;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A selectable permit / project type. Drives the dynamic application form and the
 * completeness checklist (SOW 2.2.1). Both {@code formSchemaJson} (dynamic fields
 * that show/hide by project scope) and {@code requiredDocsJson} (the document
 * checklist) are staff-configurable data — no code changes required
 * (Appendix 3 §5.1.6, §6.1.5).
 */
@Entity
@Table(name = "permit_types")
public class PermitType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PermitCategory category = PermitCategory.OTHER;

    @Column(columnDefinition = "text")
    private String description;

    /** JSON array of dynamic form field descriptors (see docs/03-domain-model.md). */
    @Column(name = "form_schema_json", columnDefinition = "text")
    private String formSchemaJson;

    /** JSON array of required-document descriptors: {docKey,label,required,description}. */
    @Column(name = "required_docs_json", columnDefinition = "text")
    private String requiredDocsJson;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PermitType() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PermitCategory getCategory() { return category; }
    public void setCategory(PermitCategory category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFormSchemaJson() { return formSchemaJson; }
    public void setFormSchemaJson(String formSchemaJson) { this.formSchemaJson = formSchemaJson; }
    public String getRequiredDocsJson() { return requiredDocsJson; }
    public void setRequiredDocsJson(String requiredDocsJson) { this.requiredDocsJson = requiredDocsJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final PermitType p = new PermitType();
        public Builder code(String v) { p.code = v; return this; }
        public Builder name(String v) { p.name = v; return this; }
        public Builder category(PermitCategory v) { p.category = v; return this; }
        public Builder description(String v) { p.description = v; return this; }
        public Builder formSchemaJson(String v) { p.formSchemaJson = v; return this; }
        public Builder requiredDocsJson(String v) { p.requiredDocsJson = v; return this; }
        public Builder active(boolean v) { p.active = v; return this; }
        public PermitType build() { return p; }
    }
}
