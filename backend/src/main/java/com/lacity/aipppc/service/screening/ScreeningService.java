package com.lacity.aipppc.service.screening;

import com.lacity.aipppc.model.*;
import com.lacity.aipppc.model.enums.*;
import com.lacity.aipppc.repository.*;
import com.lacity.aipppc.service.AuditService;
import com.lacity.aipppc.service.JsonUtil;
import com.lacity.aipppc.service.ai.AiAnalysisService;
import com.lacity.aipppc.service.ai.AiModels;
import com.lacity.aipppc.service.rules.RuleConditionEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Runs the pre-plan-check pipeline asynchronously (kept on its own bean so
 * Spring's {@code @Async} proxy applies, mirroring the Blue reference). Order of
 * operations follows the RFP's review-sequence logic (SOW 2.1.3): foundational
 * completeness + rule-based screening first (the primary mechanism, SOW 2.2.3),
 * clearance identification next (SOW 2.2.5), then AI augmentation to enhance
 * detection. Every finding/clearance is advisory and enters staff review as
 * PENDING (human-in-the-loop, SOW 1.1 / Appendix 3 §5.1.5).
 */
@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);
    private static final String CODE_VERSION = "LAMC/Title24 2024 seed";

    private final PreCheckRunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final PermitTypeRepository permitTypeRepository;
    private final ScreeningRuleRepository screeningRuleRepository;
    private final ClearanceRuleRepository clearanceRuleRepository;
    private final FindingRepository findingRepository;
    private final ClearanceRepository clearanceRepository;

    private final ProjectContextBuilder contextBuilder;
    private final CompletenessService completenessService;
    private final RegulatoryKnowledgeService knowledgeService;
    private final RuleConditionEvaluator evaluator;
    private final AiAnalysisService aiService;
    private final com.lacity.aipppc.service.StorageService storageService;
    private final JsonUtil json;
    private final AuditService auditService;
    private final ScreeningWebhookNotifier webhookNotifier;

    public ScreeningService(PreCheckRunRepository runRepository,
                            ProjectRepository projectRepository,
                            DocumentRepository documentRepository,
                            PermitTypeRepository permitTypeRepository,
                            ScreeningRuleRepository screeningRuleRepository,
                            ClearanceRuleRepository clearanceRuleRepository,
                            FindingRepository findingRepository,
                            ClearanceRepository clearanceRepository,
                            ProjectContextBuilder contextBuilder,
                            CompletenessService completenessService,
                            RegulatoryKnowledgeService knowledgeService,
                            RuleConditionEvaluator evaluator,
                            AiAnalysisService aiService,
                            com.lacity.aipppc.service.StorageService storageService,
                            JsonUtil json,
                            AuditService auditService,
                            ScreeningWebhookNotifier webhookNotifier) {
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.permitTypeRepository = permitTypeRepository;
        this.screeningRuleRepository = screeningRuleRepository;
        this.clearanceRuleRepository = clearanceRuleRepository;
        this.findingRepository = findingRepository;
        this.clearanceRepository = clearanceRepository;
        this.contextBuilder = contextBuilder;
        this.completenessService = completenessService;
        this.knowledgeService = knowledgeService;
        this.evaluator = evaluator;
        this.aiService = aiService;
        this.storageService = storageService;
        this.json = json;
        this.auditService = auditService;
        this.webhookNotifier = webhookNotifier;
    }

    /**
     * Async entry point. Both {@code @Async} and {@code @Transactional} sit here so
     * the transaction opens on the worker thread (the proxy chain applies the tx
     * advisor when the async task runs), giving lazy associations like
     * {@code run.getProject().getParcel()} an open session for the whole run.
     */
    @Async("screeningExecutor")
    @Transactional
    public void runScreeningAsync(UUID runId) {
        runScreening(runId);
    }

    /** Synchronous entry point (used by the async wrapper and by tests). */
    @Transactional
    public void runScreening(UUID runId) {
        PreCheckRun run = runRepository.findById(runId).orElse(null);
        if (run == null) { log.warn("Run {} not found", runId); return; }
        Project project = run.getProject();
        Instant start = Instant.now();
        run.setStatus(RunStatus.PROCESSING);
        run.setStartedAt(start);
        run.setCodeVersion(CODE_VERSION);
        runRepository.save(run);

        try {
            PermitType permitType = permitTypeRepository.findByCode(project.getPermitTypeCode()).orElse(null);
            List<Document> documents = documentRepository.findByProjectOrderByUploadedAtAsc(project);

            // 1) Extract document text (and record per-doc counts).
            StringBuilder combined = new StringBuilder();
            for (Document d : documents) {
                if (d.getScanStatus() != ScanStatus.PASSED) continue;
                String text = storageService.extractText(d.getStoragePath(), d.getFileType());
                if (text != null && !text.isBlank()) {
                    combined.append(text).append("\n");
                    if (d.getExtractedTextChars() != text.length()) {
                        d.setExtractedTextChars(text.length());
                        documentRepository.save(d);
                    }
                }
            }
            var ctx = contextBuilder.build(project, permitType, documents, combined.toString());

            List<Finding> findings = new ArrayList<>();
            List<Clearance> clearances = new ArrayList<>();

            // 2) Completeness validation (SOW 2.2.2).
            CompletenessService.Result completeness = completenessService.evaluate(run, permitType, documents);
            findings.addAll(completeness.findings());

            // 3) Rule-based pre-screening — primary mechanism (SOW 2.2.3).
            for (ScreeningRule rule : screeningRuleRepository.findByActiveTrueOrderByPriorityAsc()) {
                if (!appliesTo(rule.getAppliesToPermitTypes(), project.getPermitTypeCode())) continue;
                if (!evaluator.matches(rule.getConditionJson(), ctx)) continue;
                findings.add(Finding.builder()
                    .run(run).category(rule.getCategory()).severity(rule.getSeverity())
                    .title(rule.getName())
                    .description(TemplateRenderer.render(rule.getMessage(), ctx))
                    .codeReference(rule.getCodeReference()).codeUrl(rule.getCodeUrl())
                    .confidence(rule.getConfidence())
                    .triggeringCondition("Rule " + rule.getCode() + " matched the submission.")
                    .recommendation(TemplateRenderer.render(rule.getRecommendation(), ctx))
                    .source(FindingSource.RULE).ruleCode(rule.getCode())
                    .build());
            }

            // 4) Clearance identification (SOW 2.2.5).
            for (ClearanceRule rule : clearanceRuleRepository.findByActiveTrueOrderByPriorityAsc()) {
                if (!appliesTo(rule.getAppliesToPermitTypes(), project.getPermitTypeCode())) continue;
                if (!evaluator.matches(rule.getConditionJson(), ctx)) continue;
                clearances.add(Clearance.builder()
                    .run(run).department(rule.getDepartment())
                    .clearanceName(rule.getClearanceName())
                    .reason(TemplateRenderer.render(rule.getReason(), ctx))
                    .confidence(rule.getConfidence())
                    .submittalRequirementsJson(rule.getSubmittalRequirementsJson())
                    .infoUrl(rule.getInfoUrl())
                    .source(FindingSource.RULE).ruleCode(rule.getCode())
                    .build());
            }

            // 5) AI-assisted augmentation (SOW 2.2.3 — enhances the rules).
            List<String> ruleTitles = findings.stream().map(Finding::getTitle).toList();
            String knowledge = knowledgeService.buildContext(ctx);
            AiModels.AiRequest aiReq = new AiModels.AiRequest(
                projectInfo(project, permitType), narrative(project), combined.toString(),
                knowledge, ruleTitles);
            AiModels.AiAnalysis ai = aiService.analyze(aiReq);
            if (ai.available()) {
                for (AiModels.AiFinding af : ai.findings()) {
                    if (containsTitle(findings, af.title())) continue;
                    String url = knowledgeService.urlForReference(af.codeReference());
                    findings.add(Finding.builder()
                        .run(run).category(af.category()).severity(af.severity())
                        .title(af.title()).description(af.description())
                        .codeReference(af.codeReference()).codeUrl(url)
                        .confidence(af.confidence())
                        .triggeringCondition(af.triggeringCondition())
                        .recommendation(af.recommendation())
                        .source(FindingSource.AI).ruleCode("AI")
                        .build());
                }
                run.setAiProviderUsed(ai.providerUsed());
                run.setAiModelUsed(ai.modelUsed());
            }

            // 6) Persist + roll up.
            findingRepository.saveAll(findings);
            clearanceRepository.saveAll(clearances);

            int blocking = (int) findings.stream().filter(f -> f.getSeverity() == Severity.BLOCKING).count();
            int warning = (int) findings.stream().filter(f -> f.getSeverity() == Severity.WARNING).count();
            int info = (int) findings.stream().filter(f -> f.getSeverity() == Severity.INFORMATIONAL).count();
            boolean completenessBlocking = findings.stream().anyMatch(f ->
                f.getCategory() == FindingCategory.COMPLETENESS && f.getSeverity() == Severity.BLOCKING);

            int score = computeScore(blocking, warning, info, completeness);
            ReadinessStatus status = computeStatus(completenessBlocking, blocking, warning);

            run.setFindingCount(findings.size());
            run.setBlockingCount(blocking);
            run.setWarningCount(warning);
            run.setInfoCount(info);
            run.setClearanceCount(clearances.size());
            run.setReadinessScore(score);
            run.setReadinessStatus(status);
            run.setSummary(buildSummary(status, score, blocking, warning, clearances.size(), ai));
            run.setStatus(RunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            run.setProcessingMs(Duration.between(start, run.getCompletedAt()).toMillis());
            runRepository.save(run);

            project.setCurrentReadinessScore(score);
            project.setCurrentReadinessStatus(status);
            project.setStatus(ProjectStatus.SCREENED);
            projectRepository.save(project);

            auditService.recordSystem("SCREENING_COMPLETED", "PreCheckRun", runId.toString(),
                "score=" + score + " status=" + status + " findings=" + findings.size()
                    + " clearances=" + clearances.size());
            log.info("Screening {} complete: score={} status={} findings={} clearances={} ({} ms)",
                runId, score, status, findings.size(), clearances.size(), run.getProcessingMs());

            webhookNotifier.notifyCompleted(run);
        } catch (Exception e) {
            log.error("Screening {} failed: {}", runId, e.getMessage(), e);
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(Instant.now());
            run.setProcessingMs(Duration.between(start, run.getCompletedAt()).toMillis());
            runRepository.save(run);
            auditService.recordSystem("SCREENING_FAILED", "PreCheckRun", runId.toString(), e.getMessage());
            webhookNotifier.notifyCompleted(run);
        }
    }

    // ── scoring & status ────────────────────────────────────────────────────────
    int computeScore(int blocking, int warning, int info, CompletenessService.Result completeness) {
        int score = 100 - (blocking * 12) - (warning * 5) - (info * 1);
        if (completeness.requiredCount() > 0) {
            int missing = completeness.requiredCount() - completeness.presentRequiredCount();
            score -= missing * 6; // extra weight on missing required docs
        }
        return Math.max(0, Math.min(100, score));
    }

    ReadinessStatus computeStatus(boolean completenessBlocking, int blocking, int warning) {
        if (completenessBlocking) return ReadinessStatus.INCOMPLETE;
        if (blocking > 0 || warning > 0) return ReadinessStatus.REQUIRES_ATTENTION;
        return ReadinessStatus.READY_FOR_SUBMISSION;
    }

    private String buildSummary(ReadinessStatus status, int score, int blocking, int warning,
                                int clearances, AiModels.AiAnalysis ai) {
        String base = "Submission readiness: " + humanize(status) + " (score " + score + "/100). "
            + blocking + " blocking, " + warning + " warning finding(s); "
            + clearances + " likely clearance(s) identified.";
        if (ai.available() && ai.summary() != null) base += " AI note: " + ai.summary();
        return base + " Results are advisory only and do not constitute Formal Plan Check approval; "
            + "final determinations are made by City of Los Angeles staff.";
    }

    private String humanize(ReadinessStatus s) {
        return switch (s) {
            case READY_FOR_SUBMISSION -> "Ready for Submission";
            case REQUIRES_ATTENTION -> "Requires Attention";
            case INCOMPLETE -> "Incomplete";
            case NOT_ASSESSED -> "Not Assessed";
        };
    }

    private boolean appliesTo(String csv, String permitTypeCode) {
        if (csv == null || csv.isBlank() || csv.trim().equals("*")) return true;
        for (String part : csv.split(",")) {
            if (part.trim().equalsIgnoreCase(permitTypeCode)) return true;
        }
        return false;
    }

    private boolean containsTitle(List<Finding> findings, String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return findings.stream().anyMatch(f -> f.getTitle() != null
            && f.getTitle().toLowerCase(Locale.ROOT).equals(t));
    }

    private String projectInfo(Project p, PermitType pt) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(p.getTitle())
          .append("\nPermit type: ").append(pt != null ? pt.getName() : p.getPermitTypeCode())
          .append("\nUniversal Project ID: ").append(p.getUniversalProjectId());
        if (p.getAddress() != null) sb.append("\nAddress: ").append(p.getAddress());
        if (p.getParcel() != null) {
            sb.append("\nZone: ").append(p.getParcel().getZone());
            sb.append("\nOverlays: ").append(json.toStringList(p.getParcel().getOverlaysJson()));
            sb.append("\nHazards: ").append(json.toStringList(p.getParcel().getHazardZonesJson()));
        }
        return sb.toString();
    }

    private String narrative(Project p) {
        return String.join(" ",
            p.getProjectScope() == null ? "" : p.getProjectScope(),
            p.getIntendedUse() == null ? "" : "Intended use: " + p.getIntendedUse(),
            p.getDescription() == null ? "" : p.getDescription()).trim();
    }
}
