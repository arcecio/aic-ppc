package com.lacity.aipppc.service;

import com.lacity.aipppc.dto.admin.*;
import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.ClearanceRule;
import com.lacity.aipppc.model.ScreeningRule;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.model.enums.Department;
import com.lacity.aipppc.model.enums.FindingCategory;
import com.lacity.aipppc.model.enums.Severity;
import com.lacity.aipppc.repository.ClearanceRuleRepository;
import com.lacity.aipppc.repository.ScreeningRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Staff CRUD over the two rule engines — the mechanism that lets City staff
 * "configure and update business rules, code references, thresholds, and policy
 * parameters without requiring vendor source code modifications"
 * (Appendix 3 §5.1.6; SOW 2.2.3, 2.2.5). Every write validates the condition JSON
 * so a bad edit is rejected up front, and every write is audited.
 */
@Service
public class RuleAdminService {

    private final ScreeningRuleRepository screeningRules;
    private final ClearanceRuleRepository clearanceRules;
    private final JsonUtil json;
    private final AuditService auditService;

    public RuleAdminService(ScreeningRuleRepository screeningRules,
                            ClearanceRuleRepository clearanceRules,
                            JsonUtil json, AuditService auditService) {
        this.screeningRules = screeningRules;
        this.clearanceRules = clearanceRules;
        this.json = json;
        this.auditService = auditService;
    }

    // ── screening rules ──────────────────────────────────────────────────────────
    public List<ScreeningRule> listScreeningRules() {
        return screeningRules.findAllByOrderByPriorityAsc();
    }

    @Transactional
    public ScreeningRule createScreeningRule(User staff, ScreeningRuleRequest req) {
        if (screeningRules.existsByCode(req.code())) {
            throw ApiException.conflict("A screening rule with code " + req.code() + " already exists");
        }
        validateCondition(req.conditionJson());
        ScreeningRule rule = new ScreeningRule();
        rule.setCode(req.code());
        applyScreening(rule, req);
        screeningRules.save(rule);
        auditService.recordUser(staff.getEmail(), "RULE_CREATED", "ScreeningRule", rule.getId().toString(),
            req.code(), null, ScreeningRuleDto.from(rule));
        return rule;
    }

    @Transactional
    public ScreeningRule updateScreeningRule(User staff, UUID id, ScreeningRuleRequest req) {
        ScreeningRule rule = screeningRules.findById(id)
            .orElseThrow(() -> ApiException.notFound("Screening rule not found"));
        validateCondition(req.conditionJson());
        ScreeningRuleDto before = ScreeningRuleDto.from(rule);
        applyScreening(rule, req);
        screeningRules.save(rule);
        auditService.recordUser(staff.getEmail(), "RULE_UPDATED", "ScreeningRule", id.toString(),
            rule.getCode(), before, ScreeningRuleDto.from(rule));
        return rule;
    }

    @Transactional
    public void deleteScreeningRule(User staff, UUID id) {
        ScreeningRule rule = screeningRules.findById(id)
            .orElseThrow(() -> ApiException.notFound("Screening rule not found"));
        ScreeningRuleDto before = ScreeningRuleDto.from(rule);
        screeningRules.delete(rule);
        auditService.recordUser(staff.getEmail(), "RULE_DELETED", "ScreeningRule", id.toString(),
            rule.getCode(), before, null);
    }

    private void applyScreening(ScreeningRule rule, ScreeningRuleRequest req) {
        rule.setName(req.name());
        rule.setCategory(parse(FindingCategory.class, req.category(), FindingCategory.GENERAL));
        rule.setSeverity(parse(Severity.class, req.severity(), Severity.WARNING));
        rule.setConditionJson(req.conditionJson());
        rule.setMessage(req.message());
        rule.setRecommendation(req.recommendation());
        rule.setCodeReference(req.codeReference());
        rule.setCodeUrl(req.codeUrl());
        rule.setConfidence(req.confidence() == null ? 90 : clamp(req.confidence()));
        rule.setAppliesToPermitTypes(req.appliesToPermitTypes());
        rule.setPriority(req.priority() == null ? 100 : req.priority());
        rule.setActive(req.active() == null || req.active());
    }

    // ── clearance rules ──────────────────────────────────────────────────────────
    public List<ClearanceRule> listClearanceRules() {
        return clearanceRules.findAllByOrderByPriorityAsc();
    }

    @Transactional
    public ClearanceRule createClearanceRule(User staff, ClearanceRuleRequest req) {
        if (clearanceRules.existsByCode(req.code())) {
            throw ApiException.conflict("A clearance rule with code " + req.code() + " already exists");
        }
        validateCondition(req.conditionJson());
        ClearanceRule rule = new ClearanceRule();
        rule.setCode(req.code());
        applyClearance(rule, req);
        clearanceRules.save(rule);
        auditService.recordUser(staff.getEmail(), "CLEARANCE_RULE_CREATED", "ClearanceRule",
            rule.getId().toString(), req.code(), null, ClearanceRuleDto.from(rule));
        return rule;
    }

    @Transactional
    public ClearanceRule updateClearanceRule(User staff, UUID id, ClearanceRuleRequest req) {
        ClearanceRule rule = clearanceRules.findById(id)
            .orElseThrow(() -> ApiException.notFound("Clearance rule not found"));
        validateCondition(req.conditionJson());
        ClearanceRuleDto before = ClearanceRuleDto.from(rule);
        applyClearance(rule, req);
        clearanceRules.save(rule);
        auditService.recordUser(staff.getEmail(), "CLEARANCE_RULE_UPDATED", "ClearanceRule",
            id.toString(), rule.getCode(), before, ClearanceRuleDto.from(rule));
        return rule;
    }

    @Transactional
    public void deleteClearanceRule(User staff, UUID id) {
        ClearanceRule rule = clearanceRules.findById(id)
            .orElseThrow(() -> ApiException.notFound("Clearance rule not found"));
        ClearanceRuleDto before = ClearanceRuleDto.from(rule);
        clearanceRules.delete(rule);
        auditService.recordUser(staff.getEmail(), "CLEARANCE_RULE_DELETED", "ClearanceRule",
            id.toString(), rule.getCode(), before, null);
    }

    private void applyClearance(ClearanceRule rule, ClearanceRuleRequest req) {
        rule.setDepartment(parse(Department.class, req.department(), Department.LADBS));
        rule.setClearanceName(req.clearanceName());
        rule.setConditionJson(req.conditionJson());
        rule.setReason(req.reason());
        rule.setSubmittalRequirementsJson(req.submittalRequirementsJson() == null
            ? "[]" : req.submittalRequirementsJson());
        rule.setInfoUrl(req.infoUrl());
        rule.setConfidence(req.confidence() == null ? 80 : clamp(req.confidence()));
        rule.setAppliesToPermitTypes(req.appliesToPermitTypes());
        rule.setPriority(req.priority() == null ? 100 : req.priority());
        rule.setActive(req.active() == null || req.active());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────
    private void validateCondition(String conditionJson) {
        var node = json.readTree(conditionJson);
        if (node == null || node.isNull()) {
            throw ApiException.badRequest("condition must be valid JSON (an all/any/not/leaf node)");
        }
        boolean structural = node.has("all") || node.has("any") || node.has("not") || node.has("field");
        if (!structural) {
            throw ApiException.badRequest("condition must contain one of: all, any, not, or field");
        }
    }

    private int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    private <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid value '" + raw + "' for " + type.getSimpleName());
        }
    }
}
