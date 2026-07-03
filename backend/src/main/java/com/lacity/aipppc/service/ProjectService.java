package com.lacity.aipppc.service;

import com.lacity.aipppc.dto.project.*;
import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.*;
import com.lacity.aipppc.model.enums.ProjectStatus;
import com.lacity.aipppc.repository.DocumentRepository;
import com.lacity.aipppc.repository.PermitTypeRepository;
import com.lacity.aipppc.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Applicant-facing project lifecycle: intake (with parcel/GIS resolution and
 * Universal Project ID assignment), dynamic-form persistence, document upload
 * (with the pre-AI security scan), and the seamless hand-off marker to ePlanLA
 * (SOW 2.2.1, 2.2.14; Appendix 3 UC 3.2).
 */
@Service
public class ProjectService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final PermitTypeRepository permitTypeRepository;
    private final ParcelService parcelService;
    private final StorageService storageService;
    private final JsonUtil json;
    private final AuditService auditService;

    public ProjectService(ProjectRepository projectRepository,
                          DocumentRepository documentRepository,
                          PermitTypeRepository permitTypeRepository,
                          ParcelService parcelService,
                          StorageService storageService,
                          JsonUtil json,
                          AuditService auditService) {
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.permitTypeRepository = permitTypeRepository;
        this.parcelService = parcelService;
        this.storageService = storageService;
        this.json = json;
        this.auditService = auditService;
    }

    @Transactional
    public Project create(User owner, CreateProjectRequest req) {
        PermitType permitType = permitTypeRepository.findByCode(req.permitTypeCode())
            .orElseThrow(() -> ApiException.badRequest("Unknown permit type: " + req.permitTypeCode()));

        Parcel parcel = parcelService.resolve(req.apn(), req.address()).orElse(null);

        Project project = Project.builder()
            .universalProjectId(generateUniversalProjectId())
            .owner(owner)
            .title(req.title())
            .permitTypeCode(permitType.getCode())
            .projectScope(req.projectScope())
            .intendedUse(req.intendedUse())
            .description(req.description())
            .address(req.address() != null ? req.address() : (parcel != null ? parcel.getAddress() : null))
            .apn(req.apn() != null ? req.apn() : (parcel != null ? parcel.getApn() : null))
            .parcel(parcel)
            .formDataJson(req.formData() != null ? json.write(req.formData()) : "{}")
            .status(ProjectStatus.INTAKE)
            .usedAipPpc(true)
            .build();
        projectRepository.save(project);
        auditService.recordUser(owner.getEmail(), "PROJECT_CREATED", "Project", project.getId().toString(),
            "upid=" + project.getUniversalProjectId() + " permitType=" + permitType.getCode()
                + " parcelResolved=" + (parcel != null));
        return project;
    }

    public List<Project> listForOwner(User owner) {
        return projectRepository.findByOwnerOrderByCreatedAtDesc(owner);
    }

    /** Owner may access their own project; staff/admin may access any. */
    public Project requireAccessible(User user, UUID projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (!user.isStaff() && !project.getOwner().getId().equals(user.getId())) {
            throw ApiException.forbidden("You do not have access to this project");
        }
        return project;
    }

    @Transactional
    public Project update(User user, UUID projectId, UpdateProjectRequest req) {
        Project project = requireAccessible(user, projectId);
        if (req.title() != null) project.setTitle(req.title());
        if (req.projectScope() != null) project.setProjectScope(req.projectScope());
        if (req.intendedUse() != null) project.setIntendedUse(req.intendedUse());
        if (req.description() != null) project.setDescription(req.description());
        if (req.formData() != null) project.setFormDataJson(json.write(req.formData()));
        if (req.address() != null || req.apn() != null) {
            String address = req.address() != null ? req.address() : project.getAddress();
            String apn = req.apn() != null ? req.apn() : project.getApn();
            project.setAddress(address);
            project.setApn(apn);
            parcelService.resolve(apn, address).ifPresent(p -> {
                project.setParcel(p);
                if (project.getApn() == null) project.setApn(p.getApn());
            });
        }
        projectRepository.save(project);
        auditService.recordUser(user.getEmail(), "PROJECT_UPDATED", "Project", projectId.toString(), null);
        return project;
    }

    @Transactional
    public Document uploadDocument(User user, UUID projectId, MultipartFile file, String docCategory) {
        Project project = requireAccessible(user, projectId);
        StorageService.StoredFile stored = storageService.store(project.getId(), file);

        // Version = count of prior uploads of the same category + 1 (version history, SOW 2.2.1).
        int version = 1 + (int) documentRepository.findByProjectOrderByUploadedAtAsc(project).stream()
            .filter(d -> docCategory != null && docCategory.equals(d.getDocCategory())).count();

        Document doc = Document.builder()
            .project(project)
            .originalName(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename())
            .contentType(file.getContentType())
            .fileType(stored.fileType())
            .sizeBytes(stored.size())
            .storagePath(stored.storagePath())
            .docCategory(docCategory == null || docCategory.isBlank() ? null : docCategory)
            .scanStatus(stored.scanStatus())
            .scanDetail(stored.scanDetail())
            .version(version)
            .uploadedBy(user.getId())
            .build();
        documentRepository.save(doc);
        if (project.getStatus() == ProjectStatus.DRAFT) {
            project.setStatus(ProjectStatus.INTAKE);
            projectRepository.save(project);
        }
        auditService.recordUser(user.getEmail(), "DOCUMENT_UPLOADED", "Document", doc.getId().toString(),
            "project=" + projectId + " scan=" + stored.scanStatus() + " category=" + docCategory);
        return doc;
    }

    public List<Document> listDocuments(Project project) {
        return documentRepository.findByProjectOrderByUploadedAtDesc(project);
    }

    @Transactional
    public void deleteDocument(User user, UUID projectId, UUID documentId) {
        Project project = requireAccessible(user, projectId);
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> ApiException.notFound("Document not found"));
        if (!doc.getProject().getId().equals(project.getId())) {
            throw ApiException.badRequest("Document does not belong to this project");
        }
        documentRepository.delete(doc);
        auditService.recordUser(user.getEmail(), "DOCUMENT_DELETED", "Document", documentId.toString(), null);
    }

    /**
     * Deletes a project and everything under it. Restricted to the project's
     * owner (or an admin) — staff review access does not extend to deleting an
     * applicant's plan. Documents, runs, findings, and clearances are removed by
     * the {@code ON DELETE CASCADE} constraints in V3; stored upload files are
     * cleaned up afterwards.
     */
    @Transactional
    public void delete(User user, UUID projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (!project.getOwner().getId().equals(user.getId()) && !user.isAdmin()) {
            throw ApiException.forbidden("Only the project owner may delete this project");
        }
        String upid = project.getUniversalProjectId();
        projectRepository.delete(project);
        storageService.deleteProjectFiles(projectId);
        auditService.recordUser(user.getEmail(), "PROJECT_DELETED", "Project", projectId.toString(),
            "upid=" + upid);
    }

    @Transactional
    public Project markSubmittedToEplanla(User user, UUID projectId) {
        Project project = requireAccessible(user, projectId);
        project.setSubmittedToEplanlaAt(Instant.now());
        project.setStatus(ProjectStatus.SUBMITTED_TO_EPLANLA);
        projectRepository.save(project);
        auditService.recordUser(user.getEmail(), "SUBMITTED_TO_EPLANLA", "Project", projectId.toString(),
            "upid=" + project.getUniversalProjectId());
        return project;
    }

    // ── DTO mapping helpers ─────────────────────────────────────────────────────
    public ProjectDto toDto(Project project) {
        List<DocumentDto> docs = listDocuments(project).stream().map(DocumentDto::from).toList();
        return ProjectDto.from(project, docs, json::toStringList, json::toMap);
    }

    /**
     * BuildLA-style Universal Project ID: {@code LA-<year>-<6 digits>}. Retries on
     * the (rare) unique collision (SOW 2.2.1 — assign a Universal Project ID that
     * follows the project into Formal Plan Check).
     */
    private String generateUniversalProjectId() {
        int year = ZonedDateTime.now(ZoneId.of("America/Los_Angeles")).getYear();
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = String.format("LA-%d-%06d", year, RANDOM.nextInt(1_000_000));
            if (!projectRepository.existsByUniversalProjectId(candidate)) return candidate;
        }
        return "LA-" + year + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
