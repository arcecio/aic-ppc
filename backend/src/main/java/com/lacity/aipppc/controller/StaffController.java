package com.lacity.aipppc.controller;

import com.lacity.aipppc.dto.project.ProjectSummaryDto;
import com.lacity.aipppc.dto.screening.*;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.service.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff-facing Review &amp; Analytics mode (SOW 1.2.2). All endpoints require the
 * STAFF or ADMIN role (see {@code SecurityConfig}). Provides KPI analytics, the
 * review queue, run detail, human-in-the-loop dispositions, and the feedback inbox.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final AnalyticsService analyticsService;
    private final StaffService staffService;
    private final PreCheckService preCheckService;
    private final FeedbackService feedbackService;
    private final UserService userService;

    public StaffController(AnalyticsService analyticsService, StaffService staffService,
                          PreCheckService preCheckService, FeedbackService feedbackService,
                          UserService userService) {
        this.analyticsService = analyticsService;
        this.staffService = staffService;
        this.preCheckService = preCheckService;
        this.feedbackService = feedbackService;
        this.userService = userService;
    }

    private User user(UserDetails ud) {
        return userService.requireUser(ud.getUsername());
    }

    @GetMapping("/analytics")
    public AnalyticsDto analytics() {
        return analyticsService.compute();
    }

    @GetMapping("/projects")
    public List<ProjectSummaryDto> projects() {
        return staffService.allProjects().stream().map(ProjectSummaryDto::from).toList();
    }

    @GetMapping("/runs")
    public List<RunDto> runs(@RequestParam(value = "status", required = false) String status,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             @RequestParam(value = "size", defaultValue = "50") int size) {
        return staffService.listRuns(status, page, size).stream().map(RunDto::from).toList();
    }

    @GetMapping("/runs/{runId}")
    public RunDetailDto run(@PathVariable UUID runId) {
        return preCheckService.toDetail(preCheckService.requireRun(runId));
    }

    @PostMapping("/findings/{findingId}/review")
    public FindingDto reviewFinding(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID findingId,
                                    @Valid @RequestBody DispositionRequest req) {
        return FindingDto.from(staffService.reviewFinding(user(ud), findingId, req.disposition(), req.comment()));
    }

    @PostMapping("/clearances/{clearanceId}/review")
    public Map<String, Object> reviewClearance(@AuthenticationPrincipal UserDetails ud,
                                               @PathVariable UUID clearanceId,
                                               @Valid @RequestBody DispositionRequest req) {
        var c = staffService.reviewClearance(user(ud), clearanceId, req.disposition(), req.comment());
        return Map.of("id", c.getId(), "staffDisposition", c.getStaffDisposition().name());
    }

    @GetMapping("/feedback")
    public List<Map<String, Object>> feedback(@RequestParam(value = "status", required = false) String status) {
        return feedbackService.list(status).stream().map(f -> Map.<String, Object>of(
            "id", f.getId(), "type", f.getType(), "comment", f.getComment(),
            "status", f.getStatus(), "runId", f.getRunId() == null ? "" : f.getRunId(),
            "findingId", f.getFindingId() == null ? "" : f.getFindingId(),
            "submitterRole", f.getSubmitterRole() == null ? "" : f.getSubmitterRole(),
            "createdAt", f.getCreatedAt())).toList();
    }

    @PatchMapping("/feedback/{id}")
    public Map<String, Object> updateFeedback(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id,
                                              @RequestBody Map<String, String> body) {
        var entry = feedbackService.updateStatus(user(ud), id, body.getOrDefault("status", "REVIEWED"));
        return Map.of("id", entry.getId(), "status", entry.getStatus());
    }
}
