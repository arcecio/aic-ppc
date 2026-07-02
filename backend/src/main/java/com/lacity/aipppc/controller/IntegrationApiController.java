package com.lacity.aipppc.controller;

import com.lacity.aipppc.dto.project.CreateProjectRequest;
import com.lacity.aipppc.dto.project.DocumentDto;
import com.lacity.aipppc.dto.project.ProjectDto;
import com.lacity.aipppc.dto.screening.RunDetailDto;
import com.lacity.aipppc.dto.screening.RunDto;
import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.Project;
import com.lacity.aipppc.security.ApiKeyAuthFilter;
import com.lacity.aipppc.service.IntegrationService;
import com.lacity.aipppc.service.PreCheckService;
import com.lacity.aipppc.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Versioned integration API for City systems (Appendix 3 §2.1). Authenticated by
 * the {@code X-API-Key} header ({@code ApiKeyAuthFilter} → ROLE_API_CLIENT).
 * Supports direct project submission, headless screening, status polling, and
 * JSON result retrieval; async completion is pushed via webhooks
 * ({@code ScreeningWebhookNotifier}). Contract is published at /swagger-ui.html.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Integration API", description = "ePlanLA / City-system integration (X-API-Key)")
public class IntegrationApiController {

    private final IntegrationService integrationService;
    private final PreCheckService preCheckService;
    private final ProjectService projectService;

    public IntegrationApiController(IntegrationService integrationService,
                                   PreCheckService preCheckService,
                                   ProjectService projectService) {
        this.integrationService = integrationService;
        this.preCheckService = preCheckService;
        this.projectService = projectService;
    }

    private String clientLabel(HttpServletRequest request) {
        Object id = request.getAttribute(ApiKeyAuthFilter.CLIENT_ID_ATTR);
        return id == null ? "api-client" : id.toString();
    }

    @Operation(summary = "Create a project record")
    @PostMapping("/projects")
    public ProjectDto createProject(@Valid @RequestBody CreateProjectRequest req) {
        return projectService.toDto(integrationService.createProject(req));
    }

    @Operation(summary = "Get a project by Universal Project ID")
    @GetMapping("/projects/{universalProjectId}")
    public ProjectDto getProject(@PathVariable String universalProjectId) {
        return projectService.toDto(integrationService.requireByUpid(universalProjectId));
    }

    @Operation(summary = "Attach a plan/supporting document to a project")
    @PostMapping(value = "/projects/{projectId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDto upload(@PathVariable UUID projectId,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(value = "docCategory", required = false) String docCategory) {
        return DocumentDto.from(integrationService.uploadDocument(projectId, file, docCategory));
    }

    @Operation(summary = "Trigger a headless screening run (async; webhook on completion)")
    @PostMapping("/projects/{projectId}/screen")
    public Map<String, Object> screen(@PathVariable UUID projectId, HttpServletRequest request) {
        PreCheckRun run = integrationService.screen(projectId, clientLabel(request));
        return Map.of(
            "runId", run.getId(),
            "projectId", projectId,
            "status", run.getStatus().name(),
            "statusUrl", "/api/v1/runs/" + run.getId(),
            "resultsUrl", "/api/v1/runs/" + run.getId() + "/results");
    }

    @Operation(summary = "One-call submit + headless screening (e.g. ePlanLA completeness check)")
    @PostMapping("/screenings")
    public Map<String, Object> submitAndScreen(@Valid @RequestBody CreateProjectRequest req,
                                               HttpServletRequest request) {
        Project project = integrationService.createProject(req);
        PreCheckRun run = integrationService.screen(project.getId(), clientLabel(request));
        return Map.of(
            "universalProjectId", project.getUniversalProjectId(),
            "projectId", project.getId(),
            "runId", run.getId(),
            "status", run.getStatus().name(),
            "statusUrl", "/api/v1/runs/" + run.getId(),
            "resultsUrl", "/api/v1/runs/" + run.getId() + "/results");
    }

    @Operation(summary = "Poll run status (pending/processing/completed/failed)")
    @GetMapping("/runs/{runId}")
    public RunDto status(@PathVariable UUID runId) {
        return RunDto.from(preCheckService.requireRun(runId));
    }

    @Operation(summary = "Retrieve structured JSON results (findings + clearances)")
    @GetMapping("/runs/{runId}/results")
    public RunDetailDto results(@PathVariable UUID runId) {
        return preCheckService.toDetail(preCheckService.requireRun(runId));
    }
}
