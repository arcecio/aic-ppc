package com.lacity.aipppc.service;

import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.*;
import com.lacity.aipppc.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Continuous-improvement feedback loop (SOW 2.2.13; Appendix 3 §6.1.4). Applicants
 * flag findings they believe are inaccurate (SOW 2.2.4); staff log missed
 * detections and rule-tuning notes during QA. Everything lands in the auditable
 * feedback inbox — model/rule changes remain subject to City review.
 */
@Service
public class FeedbackService {

    private final FeedbackEntryRepository feedbackRepository;
    private final FindingRepository findingRepository;
    private final ProjectService projectService;
    private final AuditService auditService;

    public FeedbackService(FeedbackEntryRepository feedbackRepository,
                           FindingRepository findingRepository,
                           ProjectService projectService,
                           AuditService auditService) {
        this.feedbackRepository = feedbackRepository;
        this.findingRepository = findingRepository;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    @Transactional
    public Finding flagFinding(User user, UUID findingId, String comment) {
        Finding finding = findingRepository.findById(findingId)
            .orElseThrow(() -> ApiException.notFound("Finding not found"));
        // Access check via the owning project.
        projectService.requireAccessible(user, finding.getRun().getProject().getId());

        finding.setApplicantFlagged(true);
        finding.setApplicantFlagComment(comment);
        findingRepository.save(finding);

        feedbackRepository.save(FeedbackEntry.builder()
            .runId(finding.getRun().getId())
            .findingId(finding.getId())
            .submittedBy(user.getId())
            .submitterRole(user.getRole().name())
            .type("INACCURATE_FLAG")
            .comment(comment == null ? "(no comment)" : comment)
            .status("OPEN")
            .build());
        auditService.recordUser(user.getEmail(), "FINDING_FLAGGED", "Finding", findingId.toString(), comment);
        return finding;
    }

    @Transactional
    public FeedbackEntry submit(User user, String type, String comment, UUID runId, UUID findingId) {
        FeedbackEntry entry = feedbackRepository.save(FeedbackEntry.builder()
            .runId(runId).findingId(findingId)
            .submittedBy(user.getId()).submitterRole(user.getRole().name())
            .type(type == null ? "GENERAL" : type)
            .comment(comment == null ? "" : comment)
            .status("OPEN")
            .build());
        auditService.recordUser(user.getEmail(), "FEEDBACK_SUBMITTED", "FeedbackEntry",
            entry.getId().toString(), type);
        return entry;
    }

    public List<FeedbackEntry> list(String status) {
        return status == null || status.isBlank()
            ? feedbackRepository.findAllByOrderByCreatedAtDesc()
            : feedbackRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public FeedbackEntry updateStatus(User staff, UUID id, String status) {
        FeedbackEntry entry = feedbackRepository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Feedback entry not found"));
        entry.setStatus(status);
        feedbackRepository.save(entry);
        auditService.recordUser(staff.getEmail(), "FEEDBACK_STATUS", "FeedbackEntry", id.toString(), status);
        return entry;
    }
}
