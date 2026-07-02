package com.lacity.aipppc.integration;

import com.lacity.aipppc.dto.project.CreateProjectRequest;
import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.Project;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.model.enums.RunStatus;
import com.lacity.aipppc.model.enums.TriggeredBy;
import com.lacity.aipppc.repository.*;
import com.lacity.aipppc.service.ProjectService;
import com.lacity.aipppc.service.screening.ScreeningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end pipeline test against a real Postgres (Testcontainers). Boots the full
 * Spring context — which runs Flyway + the reference-data seeder — then intakes a
 * hillside multi-family project and runs the screening engine, asserting that the
 * seeded rules and clearance rules fire and readiness is computed. Run via
 * {@code ./gradlew testAll} on Colima.
 */
@SpringBootTest(properties = {
    "app.ai.provider=none",
    "app.bootstrap.admin-email="
})
@Testcontainers
class ScreeningIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.storage.base-path", () -> System.getProperty("java.io.tmpdir") + "/aip-test-storage");
    }

    @Autowired UserRepository userRepository;
    @Autowired ProjectService projectService;
    @Autowired ScreeningService screeningService;
    @Autowired PreCheckRunRepository runRepository;
    @Autowired FindingRepository findingRepository;
    @Autowired ClearanceRepository clearanceRepository;
    @Autowired ScreeningRuleRepository screeningRuleRepository;
    @Autowired ParcelRepository parcelRepository;

    @Test
    void seederPopulatesReferenceData() {
        assertThat(screeningRuleRepository.count()).isGreaterThanOrEqualTo(20);
        assertThat(parcelRepository.count()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void screensHillsideMultiFamilyProjectEndToEnd() {
        User owner = userRepository.save(User.builder()
            .email("integration-arch@example.com").name("Int Arch")
            .passwordHash("{noop}x").role(User.Role.APPLICANT).enabled(true).build());

        Project project = projectService.create(owner, new CreateProjectRequest(
            "Hillside 8-unit", "MULTIFAMILY_NEW",
            "New 3-story 8-unit building with grading and a new driveway in the public right-of-way",
            "Residential multi-family", "8-unit condominium",
            "8080 Mulholland Dr, Los Angeles, CA 90046", null,
            Map.of("units", 8, "stories", 3, "squareFootage", 14000, "valuation", 4200000, "gradingCubicYards", 600)));

        // Parcel should resolve to the seeded hillside/VHFHSZ parcel.
        assertThat(project.getParcel()).isNotNull();
        assertThat(project.getParcel().getZone()).isEqualTo("RE15-1-H");

        PreCheckRun run = runRepository.save(PreCheckRun.builder()
            .project(project).status(RunStatus.PENDING).triggeredBy(TriggeredBy.APPLICANT).build());

        // Synchronous run (transactional via the injected proxy).
        screeningService.runScreening(run.getId());

        PreCheckRun done = runRepository.findById(run.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(done.getReadinessStatus()).isNotNull();
        assertThat(done.getReadinessScore()).isNotNull();
        assertThat(done.getFindingCount()).isGreaterThan(0);
        assertThat(done.getClearanceCount()).isGreaterThan(0);

        var findings = findingRepository.findByRun(done);
        // Hillside + VHFHSZ + multi-family should trigger fire/zoning/accessibility rules.
        assertThat(findings).anyMatch(f -> f.getRuleCode() != null
            && (f.getRuleCode().contains("FIRE") || f.getRuleCode().contains("VHFHSZ")
                || f.getCategory().name().equals("FIRE")));
        // Missing required docs → completeness blocking findings → INCOMPLETE readiness.
        assertThat(findings).anyMatch(f -> f.getRuleCode() != null && f.getRuleCode().startsWith("COMPLETENESS"));

        var clearances = clearanceRepository.findByRun(done);
        assertThat(clearances).isNotEmpty();
        List<String> departments = clearances.stream().map(c -> c.getDepartment().name()).toList();
        assertThat(departments).contains("LAFD"); // fire/life-safety on a VHFHSZ multi-family project
    }
}
