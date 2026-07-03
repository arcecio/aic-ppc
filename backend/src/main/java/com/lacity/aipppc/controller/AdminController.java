package com.lacity.aipppc.controller;

import com.lacity.aipppc.dto.admin.*;
import com.lacity.aipppc.dto.auth.UserDto;
import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.AuditLog;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.repository.AuditLogRepository;
import com.lacity.aipppc.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    /**
     * Filterable audit query surface (SOW 2.2.14). All filters combine with AND;
     * {@code entityType} + {@code entityId} together give the per-entity change
     * trail (e.g. every edit of one screening rule). {@code from}/{@code to}
     * accept an ISO instant or a plain date ({@code to} as a date is inclusive).
     */
    @GetMapping("/audit")
    public List<Map<String, Object>> audit(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "100") int size,
            @RequestParam(value = "actorType", required = false) String actorType,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "entityId", required = false) String entityId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        List<Specification<AuditLog>> specs = new ArrayList<>();
        if (hasText(actorType)) {
            String v = actorType.trim().toUpperCase(Locale.ROOT);
            specs.add((r, q, cb) -> cb.equal(r.get("actorType"), v));
        }
        if (hasText(actor)) {
            String like = "%" + actor.trim().toLowerCase(Locale.ROOT) + "%";
            specs.add((r, q, cb) -> cb.or(
                cb.like(cb.lower(r.get("actorId")), like),
                cb.like(cb.lower(r.get("actorLabel")), like)));
        }
        if (hasText(action)) {
            String v = action.trim().toUpperCase(Locale.ROOT);
            specs.add((r, q, cb) -> cb.equal(r.get("action"), v));
        }
        if (hasText(entityType)) {
            String v = entityType.trim().toLowerCase(Locale.ROOT);
            specs.add((r, q, cb) -> cb.equal(cb.lower(r.get("entityType")), v));
        }
        if (hasText(entityId)) {
            String v = entityId.trim();
            specs.add((r, q, cb) -> cb.equal(r.get("entityId"), v));
        }
        Instant fromTs = parseTimestamp(from, false);
        if (fromTs != null) {
            specs.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), fromTs));
        }
        Instant toTs = parseTimestamp(to, true);
        if (toTs != null) {
            specs.add((r, q, cb) -> cb.lessThan(r.get("createdAt"), toTs));
        }
        return auditLogRepository.findAll(Specification.allOf(specs),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
            .stream().map(this::auditRow).toList();
    }

    private boolean hasText(String v) {
        return v != null && !v.isBlank();
    }

    /** ISO instant, or a plain date taken as UTC midnight ({@code endOfDay} pushes to the next midnight). */
    private Instant parseTimestamp(String raw, boolean endOfDay) {
        if (!hasText(raw)) return null;
        String v = raw.trim();
        try {
            return Instant.parse(v);
        } catch (Exception ignored) { /* fall through to plain date */ }
        try {
            LocalDate date = LocalDate.parse(v);
            return (endOfDay ? date.plusDays(1) : date).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid timestamp '" + v
                + "' — use an ISO instant (2026-07-01T00:00:00Z) or date (2026-07-01)");
        }
    }

    private Map<String, Object> auditRow(AuditLog a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("actorType", a.getActorType());
        row.put("actorLabel", a.getActorLabel() == null ? "" : a.getActorLabel());
        row.put("action", a.getAction());
        row.put("entityType", a.getEntityType() == null ? "" : a.getEntityType());
        row.put("entityId", a.getEntityId() == null ? "" : a.getEntityId());
        row.put("detail", a.getDetail() == null ? "" : a.getDetail());
        row.put("ipAddress", a.getIpAddress() == null ? "" : a.getIpAddress());
        row.put("beforeJson", a.getBeforeJson());
        row.put("afterJson", a.getAfterJson());
        row.put("createdAt", a.getCreatedAt());
        return row;
    }
}
