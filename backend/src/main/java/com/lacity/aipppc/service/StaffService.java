package com.lacity.aipppc.service;

import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.Clearance;
import com.lacity.aipppc.model.Finding;
import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.model.enums.RunStatus;
import com.lacity.aipppc.model.enums.StaffDisposition;
import com.lacity.aipppc.repository.ClearanceRepository;
import com.lacity.aipppc.repository.FindingRepository;
import com.lacity.aipppc.repository.PreCheckRunRepository;
import com.lacity.aipppc.repository.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Staff-facing Review &amp; Analytics mode operations (SOW 1.2.2). Implements the
 * human-in-the-loop control: staff can accept, modify, or reject any AI/rule
 * finding or clearance before final disposition (Appendix 3 §5.1.5), with every
 * override written to the audit log (SOW 4.1.3).
 */
@Service
public class StaffService {

    private final PreCheckRunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final FindingRepository findingRepository;
    private final ClearanceRepository clearanceRepository;
    private final AuditService auditService;

    public StaffService(PreCheckRunRepository runRepository,
                        ProjectRepository projectRepository,
                        FindingRepository findingRepository,
                        ClearanceRepository clearanceRepository,
                        AuditService auditService) {
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.findingRepository = findingRepository;
        this.clearanceRepository = clearanceRepository;
        this.auditService = auditService;
    }

    public Page<PreCheckRun> listRuns(String status, int page, int size) {
        PageRequest pr = PageRequest.of(page, size);
        if (status == null || status.isBlank()) {
            return runRepository.findAllByOrderByCreatedAtDesc(pr);
        }
        return runRepository.findByStatusOrderByCompletedAtDesc(
            RunStatus.valueOf(status.toUpperCase(Locale.ROOT)), pr);
    }

    @Transactional
    public Finding reviewFinding(User staff, UUID findingId, String disposition, String comment) {
        Finding finding = findingRepository.findById(findingId)
            .orElseThrow(() -> ApiException.notFound("Finding not found"));
        finding.setStaffDisposition(parse(disposition));
        finding.setStaffComment(comment);
        finding.setStaffReviewedBy(staff.getId());
        finding.setStaffReviewedAt(Instant.now());
        findingRepository.save(finding);
        auditService.recordUser(staff.getEmail(), "FINDING_REVIEWED", "Finding", findingId.toString(),
            "disposition=" + disposition);
        return finding;
    }

    @Transactional
    public Clearance reviewClearance(User staff, UUID clearanceId, String disposition, String comment) {
        Clearance clearance = clearanceRepository.findById(clearanceId)
            .orElseThrow(() -> ApiException.notFound("Clearance not found"));
        clearance.setStaffDisposition(parse(disposition));
        clearance.setStaffComment(comment);
        clearance.setStaffReviewedBy(staff.getId());
        clearance.setStaffReviewedAt(Instant.now());
        clearanceRepository.save(clearance);
        auditService.recordUser(staff.getEmail(), "CLEARANCE_REVIEWED", "Clearance", clearanceId.toString(),
            "disposition=" + disposition);
        return clearance;
    }

    public List<com.lacity.aipppc.model.Project> allProjects() {
        return projectRepository.findAll();
    }

    private StaffDisposition parse(String disposition) {
        try {
            return StaffDisposition.valueOf(disposition.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid disposition: " + disposition
                + " (expected ACCEPTED, MODIFIED, REJECTED, or PENDING)");
        }
    }
}
