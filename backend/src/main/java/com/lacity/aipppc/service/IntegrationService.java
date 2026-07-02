package com.lacity.aipppc.service;

import com.lacity.aipppc.dto.project.CreateProjectRequest;
import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.Document;
import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.Project;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.model.enums.TriggeredBy;
import com.lacity.aipppc.repository.ProjectRepository;
import com.lacity.aipppc.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Backs the {@code /api/v1} integration surface used by ePlanLA and other City
 * systems (Appendix 3 §2.1). API-originated projects are owned by a synthetic
 * "integration" user so they still fit the same ownership/audit model, and every
 * action is attributed to the calling API client. Screening runs launched here
 * are marked {@code TriggeredBy.API}, which is what enables webhook callbacks.
 */
@Service
public class IntegrationService {

    private static final String SYSTEM_EMAIL = "integration-system@lacity.gov";

    private final ProjectService projectService;
    private final PreCheckService preCheckService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public IntegrationService(ProjectService projectService, PreCheckService preCheckService,
                              ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectService = projectService;
        this.preCheckService = preCheckService;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    public Project createProject(CreateProjectRequest req) {
        return projectService.create(systemUser(), req);
    }

    public Document uploadDocument(UUID projectId, MultipartFile file, String docCategory) {
        return projectService.uploadDocument(systemUser(), projectId, file, docCategory);
    }

    public PreCheckRun screen(UUID projectId, String clientLabel) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("Project not found"));
        return preCheckService.triggerForProject(project, TriggeredBy.API, clientLabel);
    }

    public Project requireByUpid(String universalProjectId) {
        return projectRepository.findByUniversalProjectId(universalProjectId)
            .orElseThrow(() -> ApiException.notFound("Project not found: " + universalProjectId));
    }

    /** Lazily provisioned owner for API-created projects. Login is never used. */
    private synchronized User systemUser() {
        return userRepository.findByEmail(SYSTEM_EMAIL).orElseGet(() ->
            userRepository.save(User.builder()
                .email(SYSTEM_EMAIL)
                .name("City Integration System")
                .organization("City of Los Angeles")
                .role(User.Role.APPLICANT)
                .passwordHash("!api-no-login-" + UUID.randomUUID())
                .enabled(true)
                .build()));
    }
}
