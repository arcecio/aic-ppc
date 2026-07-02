package com.lacity.aipppc.dto.screening;

/**
 * KPI rollup for the staff Analytics mode and ED19 performance tracking
 * (SOW 2.2.12; Appendix 3 §6.1.3, §7.1). These are the values the City would also
 * export to PowerBI/SAP.
 */
public record AnalyticsDto(
    long totalProjects,
    long projectsUsingAipPpc,
    long totalRuns,
    long completedRuns,
    long failedRuns,
    double avgProcessingMs,
    double pctWithinTarget,     // % of completed runs within the target processing time
    long targetProcessingMs,
    long totalFindings,
    long totalClearances,
    double avgReadinessScore,
    long readyForSubmission,
    long requiresAttention,
    long incomplete,
    long submittedToEplanla,    // proxy for progression / correction-cycle reduction
    long openFeedback
) {}
