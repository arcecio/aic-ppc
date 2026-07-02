package com.lacity.aipppc.controller;

import com.lacity.aipppc.dto.screening.FindingDto;
import com.lacity.aipppc.dto.screening.RunDetailDto;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.service.FeedbackService;
import com.lacity.aipppc.service.PreCheckService;
import com.lacity.aipppc.service.UserService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Screening-run detail plus the applicant "flag an inaccurate finding" action
 * (SOW 2.2.4 — applicants can notify City staff of an inaccurate flag).
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final PreCheckService preCheckService;
    private final FeedbackService feedbackService;
    private final UserService userService;

    public RunController(PreCheckService preCheckService, FeedbackService feedbackService,
                         UserService userService) {
        this.preCheckService = preCheckService;
        this.feedbackService = feedbackService;
        this.userService = userService;
    }

    private User user(UserDetails ud) {
        return userService.requireUser(ud.getUsername());
    }

    @GetMapping("/{runId}")
    public RunDetailDto detail(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID runId) {
        return preCheckService.getRunDetailForUser(user(ud), runId);
    }

    @PostMapping("/findings/{findingId}/flag")
    public FindingDto flag(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID findingId,
                           @RequestBody FlagRequest req) {
        return FindingDto.from(feedbackService.flagFinding(user(ud), findingId, req.comment()));
    }

    @PostMapping("/feedback")
    public Map<String, Object> feedback(@AuthenticationPrincipal UserDetails ud,
                                        @RequestBody FeedbackRequest req) {
        var entry = feedbackService.submit(user(ud), req.type(), req.comment(), req.runId(), req.findingId());
        return Map.of("id", entry.getId(), "status", entry.getStatus());
    }

    public record FlagRequest(@NotBlank String comment) {}
    public record FeedbackRequest(String type, String comment, UUID runId, UUID findingId) {}
}
