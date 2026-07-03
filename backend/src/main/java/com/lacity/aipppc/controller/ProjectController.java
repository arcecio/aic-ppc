package com.lacity.aipppc.controller;

import com.lacity.aipppc.dto.project.*;
import com.lacity.aipppc.dto.screening.RunDetailDto;
import com.lacity.aipppc.dto.screening.RunDto;
import com.lacity.aipppc.model.Document;
import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.Project;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.model.enums.TriggeredBy;
import com.lacity.aipppc.service.PreCheckService;
import com.lacity.aipppc.service.ProjectService;
import com.lacity.aipppc.service.ReportService;
import com.lacity.aipppc.service.StorageService;
import com.lacity.aipppc.service.UserService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Applicant-facing Pre-Plan Check mode (SOW 1.2.1): create/track projects, upload
 * plans, run screenings, retrieve results, export the PDF report, and mark the
 * hand-off to ePlanLA.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final PreCheckService preCheckService;
    private final ReportService reportService;
    private final StorageService storageService;
    private final UserService userService;

    public ProjectController(ProjectService projectService, PreCheckService preCheckService,
                             ReportService reportService, StorageService storageService,
                             UserService userService) {
        this.projectService = projectService;
        this.preCheckService = preCheckService;
        this.reportService = reportService;
        this.storageService = storageService;
        this.userService = userService;
    }

    private User user(UserDetails ud) {
        return userService.requireUser(ud.getUsername());
    }

    @PostMapping
    public ResponseEntity<ProjectDto> create(@AuthenticationPrincipal UserDetails ud,
                                             @Valid @RequestBody CreateProjectRequest req) {
        Project project = projectService.create(user(ud), req);
        return ResponseEntity.ok(projectService.toDto(project));
    }

    @GetMapping
    public List<ProjectSummaryDto> list(@AuthenticationPrincipal UserDetails ud) {
        return projectService.listForOwner(user(ud)).stream().map(ProjectSummaryDto::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectDto get(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        return projectService.toDto(projectService.requireAccessible(user(ud), id));
    }

    @PatchMapping("/{id}")
    public ProjectDto update(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id,
                             @RequestBody UpdateProjectRequest req) {
        return projectService.toDto(projectService.update(user(ud), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        projectService.delete(user(ud), id);
        return ResponseEntity.noContent().build();
    }

    // ── documents ────────────────────────────────────────────────────────────────
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDto upload(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(value = "docCategory", required = false) String docCategory) {
        return DocumentDto.from(projectService.uploadDocument(user(ud), id, file, docCategory));
    }

    @GetMapping("/{id}/documents")
    public List<DocumentDto> documents(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        Project project = projectService.requireAccessible(user(ud), id);
        return projectService.listDocuments(project).stream().map(DocumentDto::from).toList();
    }

    @GetMapping("/{id}/documents/{docId}/download")
    public ResponseEntity<ByteArrayResource> download(@AuthenticationPrincipal UserDetails ud,
                                                      @PathVariable UUID id, @PathVariable UUID docId) {
        Project project = projectService.requireAccessible(user(ud), id);
        Document doc = projectService.listDocuments(project).stream()
            .filter(d -> d.getId().equals(docId)).findFirst()
            .orElseThrow(() -> com.lacity.aipppc.exception.ApiException.notFound("Document not found"));
        byte[] bytes = storageService.read(doc.getStoragePath());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getOriginalName() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new ByteArrayResource(bytes));
    }

    @DeleteMapping("/{id}/documents/{docId}")
    public ResponseEntity<Void> deleteDocument(@AuthenticationPrincipal UserDetails ud,
                                               @PathVariable UUID id, @PathVariable UUID docId) {
        projectService.deleteDocument(user(ud), id, docId);
        return ResponseEntity.noContent().build();
    }

    // ── screening ────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/screen")
    public RunDto screen(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        User u = user(ud);
        TriggeredBy trigger = u.isStaff() ? TriggeredBy.STAFF : TriggeredBy.APPLICANT;
        PreCheckRun run = preCheckService.trigger(u, id, trigger, u.getEmail());
        return RunDto.from(run);
    }

    @GetMapping("/{id}/runs")
    public List<RunDto> runs(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        Project project = projectService.requireAccessible(user(ud), id);
        return preCheckService.listRuns(project).stream().map(RunDto::from).toList();
    }

    @GetMapping("/{id}/runs/latest")
    public ResponseEntity<RunDetailDto> latestRun(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        Project project = projectService.requireAccessible(user(ud), id);
        PreCheckRun run = preCheckService.latestRun(project);
        if (run == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(preCheckService.toDetail(run));
    }

    @PostMapping("/{id}/submit-eplanla")
    public ProjectDto submitToEplanla(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        return projectService.toDto(projectService.markSubmittedToEplanla(user(ud), id));
    }

    // ── report ───────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/report")
    public ResponseEntity<ByteArrayResource> report(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        Project project = projectService.requireAccessible(user(ud), id);
        PreCheckRun run = preCheckService.latestRun(project);
        if (run == null) {
            throw com.lacity.aipppc.exception.ApiException.badRequest("No screening run to report on yet.");
        }
        byte[] pdf = reportService.generate(project, preCheckService.toDetail(run));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"AIP-PPC-" + project.getUniversalProjectId() + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(new ByteArrayResource(pdf));
    }
}
