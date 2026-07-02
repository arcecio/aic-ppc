package com.lacity.aipppc.service;

import com.lacity.aipppc.dto.screening.ClearanceDto;
import com.lacity.aipppc.dto.screening.FindingDto;
import com.lacity.aipppc.dto.screening.RunDetailDto;
import com.lacity.aipppc.dto.screening.RunDto;
import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.Project;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.model.enums.ProjectStatus;
import com.lacity.aipppc.model.enums.RunStatus;
import com.lacity.aipppc.model.enums.TriggeredBy;
import com.lacity.aipppc.repository.ClearanceRepository;
import com.lacity.aipppc.repository.FindingRepository;
import com.lacity.aipppc.repository.PreCheckRunRepository;
import com.lacity.aipppc.repository.ProjectRepository;
import com.lacity.aipppc.service.screening.ScreeningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Triggers and retrieves pre-plan-check screening runs. Trigger is a thin,
 * synchronous step (create PENDING run, kick off {@code @Async} screening, return
 * immediately) so the caller can poll status/results — the same async contract
 * the integration API exposes to City systems (SOW 1.2.3, Appendix 3 §2.1.4).
 */
@Service
public class PreCheckService {

    private final PreCheckRunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final FindingRepository findingRepository;
    private final ClearanceRepository clearanceRepository;
    private final ScreeningService screeningService;
    private final ProjectService projectService;
    private final JsonUtil json;
    private final AuditService auditService;

    public PreCheckService(PreCheckRunRepository runRepository,
                           ProjectRepository projectRepository,
                           FindingRepository findingRepository,
                           ClearanceRepository clearanceRepository,
                           ScreeningService screeningService,
                           ProjectService projectService,
                           JsonUtil json,
                           AuditService auditService) {
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.findingRepository = findingRepository;
        this.clearanceRepository = clearanceRepository;
        this.screeningService = screeningService;
        this.projectService = projectService;
        this.json = json;
        this.auditService = auditService;
    }

    // NOTE: deliberately NOT @Transactional. The PENDING run must be committed
    // before the @Async screening task starts on another thread, otherwise the
    // worker's findById races the commit and sees nothing. createPendingRun()'s
    // repository saves each commit on return, so the run is durable before dispatch.
    public PreCheckRun trigger(User user, UUID projectId, TriggeredBy triggeredBy, String actorLabel) {
        Project project = projectService.requireAccessible(user, projectId);
        PreCheckRun run = createPendingRun(project, triggeredBy);
        auditService.record(triggeredBy == TriggeredBy.API ? "API_CLIENT" : "USER",
            actorLabel, actorLabel, "SCREENING_TRIGGERED", "PreCheckRun", run.getId().toString(),
            "project=" + projectId);
        screeningService.runScreeningAsync(run.getId());
        return run;
    }

    /** Used by the integration API where access is by API key, not a user. */
    public PreCheckRun triggerForProject(Project project, TriggeredBy triggeredBy, String actorLabel) {
        PreCheckRun run = createPendingRun(project, triggeredBy);
        auditService.recordApi(actorLabel, "SCREENING_TRIGGERED", "PreCheckRun", run.getId().toString(),
            "project=" + project.getId());
        screeningService.runScreeningAsync(run.getId());
        return run;
    }

    private PreCheckRun createPendingRun(Project project, TriggeredBy triggeredBy) {
        if (project.getStatus() == ProjectStatus.DRAFT || project.getStatus() == ProjectStatus.INTAKE) {
            project.setStatus(ProjectStatus.SCREENING);
            projectRepository.save(project);
        }
        return runRepository.save(PreCheckRun.builder()
            .project(project).status(RunStatus.PENDING).triggeredBy(triggeredBy).build());
    }

    public List<PreCheckRun> listRuns(Project project) {
        return runRepository.findByProjectOrderByCreatedAtDesc(project);
    }

    public PreCheckRun latestRun(Project project) {
        return runRepository.findTopByProjectOrderByCreatedAtDesc(project).orElse(null);
    }

    public PreCheckRun requireRun(UUID runId) {
        return runRepository.findById(runId)
            .orElseThrow(() -> ApiException.notFound("Screening run not found"));
    }

    /** Access-controlled run detail (owner or staff). */
    public RunDetailDto getRunDetailForUser(User user, UUID runId) {
        PreCheckRun run = requireRun(runId);
        projectService.requireAccessible(user, run.getProject().getId());
        return toDetail(run);
    }

    public RunDetailDto toDetail(PreCheckRun run) {
        List<FindingDto> findings = findingRepository.findByRun(run).stream()
            .map(FindingDto::from)
            .sorted(Comparator.<FindingDto>comparingInt(f -> severityRank(f.severity()))
                .thenComparing(Comparator.comparingInt(FindingDto::confidence).reversed()))
            .toList();
        List<ClearanceDto> clearances = clearanceRepository.findByRunOrderByConfidenceDesc(run).stream()
            .map(c -> ClearanceDto.from(c, json::toStringList)).toList();
        return new RunDetailDto(RunDto.from(run), findings, clearances);
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "BLOCKING" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }
}
