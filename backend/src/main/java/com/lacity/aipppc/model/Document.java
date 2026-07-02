package com.lacity.aipppc.model;

import com.lacity.aipppc.model.enums.ScanStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded plan or supporting document (SOW 2.2.1, 2.2.6). Files are validated
 * for format/size and pass a security scan before AI integration; a version
 * history is kept per project. {@code docCategory} links the file to a required-
 * document key from the permit type so completeness validation can tell what is
 * present vs. missing.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type")
    private String contentType;

    /** Normalized type: PDF, DOCX, DXF, CAD, BIM, OTHER (SOW 2.2.6). */
    @Column(name = "file_type", nullable = false, length = 16)
    private String fileType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    /** Required-document key this file satisfies, e.g. "architectural_plans". */
    @Column(name = "doc_category", length = 64)
    private String docCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 16)
    private ScanStatus scanStatus = ScanStatus.PENDING;

    @Column(name = "scan_detail")
    private String scanDetail;

    @Column(nullable = false)
    private int version = 1;

    /** Characters of text extracted for the rule engine / AI (0 if binary/scan-only). */
    @Column(name = "extracted_text_chars", nullable = false)
    private int extractedTextChars = 0;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    public Document() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getDocCategory() { return docCategory; }
    public void setDocCategory(String docCategory) { this.docCategory = docCategory; }
    public ScanStatus getScanStatus() { return scanStatus; }
    public void setScanStatus(ScanStatus scanStatus) { this.scanStatus = scanStatus; }
    public String getScanDetail() { return scanDetail; }
    public void setScanDetail(String scanDetail) { this.scanDetail = scanDetail; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public int getExtractedTextChars() { return extractedTextChars; }
    public void setExtractedTextChars(int extractedTextChars) { this.extractedTextChars = extractedTextChars; }
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Document d = new Document();
        public Builder project(Project v) { d.project = v; return this; }
        public Builder originalName(String v) { d.originalName = v; return this; }
        public Builder contentType(String v) { d.contentType = v; return this; }
        public Builder fileType(String v) { d.fileType = v; return this; }
        public Builder sizeBytes(long v) { d.sizeBytes = v; return this; }
        public Builder storagePath(String v) { d.storagePath = v; return this; }
        public Builder docCategory(String v) { d.docCategory = v; return this; }
        public Builder scanStatus(ScanStatus v) { d.scanStatus = v; return this; }
        public Builder scanDetail(String v) { d.scanDetail = v; return this; }
        public Builder version(int v) { d.version = v; return this; }
        public Builder extractedTextChars(int v) { d.extractedTextChars = v; return this; }
        public Builder uploadedBy(UUID v) { d.uploadedBy = v; return this; }
        public Document build() { return d; }
    }
}
