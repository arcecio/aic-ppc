package com.lacity.aipppc.service;

import com.lacity.aipppc.dto.screening.AnalyticsDto;
import com.lacity.aipppc.model.enums.ReadinessStatus;
import com.lacity.aipppc.model.enums.RunStatus;
import com.lacity.aipppc.repository.FeedbackEntryRepository;
import com.lacity.aipppc.repository.PreCheckRunRepository;
import com.lacity.aipppc.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Computes the KPI dashboard metrics from the transactional tables. Supports the
 * City's ability to measure submission quality, processing time, and adoption
 * (SOW 2.2.12) — the same figures exported to PowerBI/SAP in production.
 */
@Service
public class AnalyticsService {

    private final ProjectRepository projectRepository;
    private final PreCheckRunRepository runRepository;
    private final FeedbackEntryRepository feedbackRepository;

    @Value("${app.screening.target-processing-ms:1800000}")
    private long targetProcessingMs;

    public AnalyticsService(ProjectRepository projectRepository,
                            PreCheckRunRepository runRepository,
                            FeedbackEntryRepository feedbackRepository) {
        this.projectRepository = projectRepository;
        this.runRepository = runRepository;
        this.feedbackRepository = feedbackRepository;
    }

    public AnalyticsDto compute() {
        long totalProjects = projectRepository.count();
        long usingAip = projectRepository.countByUsedAipPpcTrue();
        long totalRuns = runRepository.count();
        long completed = runRepository.countCompleted();
        long failed = runRepository.countByStatus(RunStatus.FAILED);
        double avgMs = runRepository.averageProcessingMs();
        long withinTarget = runRepository.countCompletedWithinMs(targetProcessingMs);
        double pctWithin = completed == 0 ? 100.0 : (withinTarget * 100.0 / completed);

        return new AnalyticsDto(
            totalProjects, usingAip, totalRuns, completed, failed,
            round(avgMs), round(pctWithin), targetProcessingMs,
            runRepository.totalFindings(), runRepository.totalClearances(),
            round(runRepository.averageReadinessScore()),
            projectRepository.countByCurrentReadinessStatus(ReadinessStatus.READY_FOR_SUBMISSION),
            projectRepository.countByCurrentReadinessStatus(ReadinessStatus.REQUIRES_ATTENTION),
            projectRepository.countByCurrentReadinessStatus(ReadinessStatus.INCOMPLETE),
            projectRepository.countBySubmittedToEplanlaAtNotNull(),
            feedbackRepository.countByStatus("OPEN"));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
