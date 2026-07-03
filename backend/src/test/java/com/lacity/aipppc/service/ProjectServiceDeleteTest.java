package com.lacity.aipppc.service;

import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.Project;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.model.enums.ProjectStatus;
import com.lacity.aipppc.repository.DocumentRepository;
import com.lacity.aipppc.repository.PermitTypeRepository;
import com.lacity.aipppc.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization and cleanup rules for project (plan) deletion: only the owner —
 * or an admin — may delete, and a successful delete removes the DB row, the
 * stored upload files, and writes an audit entry.
 */
class ProjectServiceDeleteTest {

    private ProjectRepository projectRepository;
    private StorageService storageService;
    private AuditService auditService;
    private ProjectService service;

    private final UUID projectId = UUID.randomUUID();
    private final User owner = user(User.Role.APPLICANT, "owner@example.com");
    private Project project;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        storageService = mock(StorageService.class);
        auditService = mock(AuditService.class);
        service = new ProjectService(projectRepository, mock(DocumentRepository.class),
            mock(PermitTypeRepository.class), mock(ParcelService.class), storageService,
            mock(JsonUtil.class), auditService);

        project = Project.builder()
            .universalProjectId("LA-2026-000123")
            .owner(owner)
            .title("Test project")
            .permitTypeCode("NEW_SFD")
            .status(ProjectStatus.INTAKE)
            .build();
        project.setId(projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    }

    private static User user(User.Role role, String email) {
        return User.builder().id(UUID.randomUUID()).email(email).name("Test").role(role).build();
    }

    @Test
    void ownerCanDeleteTheirProject() {
        service.delete(owner, projectId);

        verify(projectRepository).delete(project);
        verify(storageService).deleteProjectFiles(projectId);
        verify(auditService).recordUser(eq(owner.getEmail()), eq("PROJECT_DELETED"), eq("Project"),
            eq(projectId.toString()), anyString());
    }

    @Test
    void adminCanDeleteAnyProject() {
        User admin = user(User.Role.ADMIN, "admin@lacity.org");

        service.delete(admin, projectId);

        verify(projectRepository).delete(project);
        verify(storageService).deleteProjectFiles(projectId);
    }

    @Test
    void anotherApplicantCannotDelete() {
        User stranger = user(User.Role.APPLICANT, "other@example.com");

        assertThatThrownBy(() -> service.delete(stranger, projectId))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(projectRepository, never()).delete(any(Project.class));
        verify(storageService, never()).deleteProjectFiles(any());
    }

    @Test
    void staffCannotDeleteAnApplicantsProject() {
        User staff = user(User.Role.STAFF, "staff@lacity.org");

        assertThatThrownBy(() -> service.delete(staff, projectId))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(403));
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    void deletingAMissingProjectIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(projectRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(owner, missing))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }
}
