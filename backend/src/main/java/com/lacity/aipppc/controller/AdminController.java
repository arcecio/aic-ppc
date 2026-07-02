package com.lacity.aipppc.controller;

import com.lacity.aipppc.dto.admin.*;
import com.lacity.aipppc.dto.auth.UserDto;
import com.lacity.aipppc.model.AuditLog;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.repository.AuditLogRepository;
import com.lacity.aipppc.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administrative surface (ADMIN role, see {@code SecurityConfig}): configure the
 * rule engines without code changes (SOW 2.2.3 / Appendix 3 §5.1.6), manage
 * users' roles (deliverable 4.2 role-to-position mapping), issue/revoke
 * integration API keys, and inspect the audit trail (SOW 2.2.14).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RuleAdminService ruleAdminService;
    private final ApiClientService apiClientService;
    private final AdminUserService adminUserService;
    private final AuditLogRepository auditLogRepository;
    private final UserService userService;

    public AdminController(RuleAdminService ruleAdminService, ApiClientService apiClientService,
                          AdminUserService adminUserService, AuditLogRepository auditLogRepository,
                          UserService userService) {
        this.ruleAdminService = ruleAdminService;
        this.apiClientService = apiClientService;
        this.adminUserService = adminUserService;
        this.auditLogRepository = auditLogRepository;
        this.userService = userService;
    }

    private User user(UserDetails ud) {
        return userService.requireUser(ud.getUsername());
    }

    // ── screening rules ──────────────────────────────────────────────────────────
    @GetMapping("/screening-rules")
    public List<ScreeningRuleDto> screeningRules() {
        return ruleAdminService.listScreeningRules().stream().map(ScreeningRuleDto::from).toList();
    }

    @PostMapping("/screening-rules")
    public ScreeningRuleDto createScreeningRule(@AuthenticationPrincipal UserDetails ud,
                                                @Valid @RequestBody ScreeningRuleRequest req) {
        return ScreeningRuleDto.from(ruleAdminService.createScreeningRule(user(ud), req));
    }

    @PutMapping("/screening-rules/{id}")
    public ScreeningRuleDto updateScreeningRule(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id,
                                                @Valid @RequestBody ScreeningRuleRequest req) {
        return ScreeningRuleDto.from(ruleAdminService.updateScreeningRule(user(ud), id, req));
    }

    @DeleteMapping("/screening-rules/{id}")
    public ResponseEntity<Void> deleteScreeningRule(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        ruleAdminService.deleteScreeningRule(user(ud), id);
        return ResponseEntity.noContent().build();
    }

    // ── clearance rules ──────────────────────────────────────────────────────────
    @GetMapping("/clearance-rules")
    public List<ClearanceRuleDto> clearanceRules() {
        return ruleAdminService.listClearanceRules().stream().map(ClearanceRuleDto::from).toList();
    }

    @PostMapping("/clearance-rules")
    public ClearanceRuleDto createClearanceRule(@AuthenticationPrincipal UserDetails ud,
                                                @Valid @RequestBody ClearanceRuleRequest req) {
        return ClearanceRuleDto.from(ruleAdminService.createClearanceRule(user(ud), req));
    }

    @PutMapping("/clearance-rules/{id}")
    public ClearanceRuleDto updateClearanceRule(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id,
                                                @Valid @RequestBody ClearanceRuleRequest req) {
        return ClearanceRuleDto.from(ruleAdminService.updateClearanceRule(user(ud), id, req));
    }

    @DeleteMapping("/clearance-rules/{id}")
    public ResponseEntity<Void> deleteClearanceRule(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        ruleAdminService.deleteClearanceRule(user(ud), id);
        return ResponseEntity.noContent().build();
    }

    // ── API clients ──────────────────────────────────────────────────────────────
    @GetMapping("/api-clients")
    public List<ApiClientDto> apiClients() {
        return apiClientService.list();
    }

    @PostMapping("/api-clients")
    public ApiClientCreatedDto createApiClient(@AuthenticationPrincipal UserDetails ud,
                                               @Valid @RequestBody CreateApiClientRequest req) {
        return apiClientService.create(user(ud), req);
    }

    @DeleteMapping("/api-clients/{id}")
    public ResponseEntity<Void> revokeApiClient(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        apiClientService.revoke(user(ud), id);
        return ResponseEntity.noContent().build();
    }

    // ── users ────────────────────────────────────────────────────────────────────
    @GetMapping("/users")
    public List<UserDto> users() {
        return adminUserService.listAll().stream().map(UserDto::from).toList();
    }

    @PatchMapping("/users/{id}/role")
    public UserDto setRole(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id,
                           @RequestBody Map<String, String> body) {
        return UserDto.from(adminUserService.setRole(user(ud), id, body.getOrDefault("role", "APPLICANT")));
    }

    @PatchMapping("/users/{id}/enabled")
    public UserDto setEnabled(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id,
                              @RequestBody Map<String, Boolean> body) {
        return UserDto.from(adminUserService.setEnabled(user(ud), id, body.getOrDefault("enabled", true)));
    }

    // ── audit log ────────────────────────────────────────────────────────────────
    @GetMapping("/audit")
    public List<Map<String, Object>> audit(@RequestParam(value = "page", defaultValue = "0") int page,
                                           @RequestParam(value = "size", defaultValue = "100") int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
            .stream().map(this::auditRow).toList();
    }

    private Map<String, Object> auditRow(AuditLog a) {
        return Map.of(
            "id", a.getId(), "actorType", a.getActorType(),
            "actorLabel", a.getActorLabel() == null ? "" : a.getActorLabel(),
            "action", a.getAction(),
            "entityType", a.getEntityType() == null ? "" : a.getEntityType(),
            "entityId", a.getEntityId() == null ? "" : a.getEntityId(),
            "detail", a.getDetail() == null ? "" : a.getDetail(),
            "createdAt", a.getCreatedAt());
    }
}
