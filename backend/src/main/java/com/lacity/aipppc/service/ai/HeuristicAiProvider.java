package com.lacity.aipppc.service.ai;

import com.lacity.aipppc.model.enums.FindingCategory;
import com.lacity.aipppc.model.enums.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, fully-offline AI stand-in. It performs a transparent keyword
 * scan over the project + document text and emits advisory findings the pure
 * rule engine might miss. This keeps the whole system functional (and tests
 * reproducible) with no API key — the RFP's AI arm degrades gracefully to a
 * documented heuristic (see docs/05-ai-and-governance.md). Always
 * {@link #available()}.
 */
@Component
public class HeuristicAiProvider implements AiProvider {

    private record Signal(String keyword, String title, String description,
                          FindingCategory category, Severity severity, String codeRef, int confidence,
                          String recommendation) {}

    private static final List<Signal> SIGNALS = List.of(
        new Signal("egress", "Verify means of egress",
            "The plans reference egress/exits. Confirm exit width, count, and travel distance meet CBC Chapter 10 for the occupancy.",
            FindingCategory.BUILDING, Severity.WARNING, "CBC Ch.10", 68,
            "Label required exit widths and maximum travel distances on the floor plan."),
        new Signal("sprinkler", "Confirm fire sprinkler scope",
            "Text mentions sprinklers. Verify NFPA 13 coverage and whether an LAFD fire-protection review is triggered.",
            FindingCategory.FIRE, Severity.WARNING, "CFC 903", 66,
            "Include a fire-protection sheet noting sprinkler design criteria."),
        new Signal("restroom", "Verify accessible restrooms",
            "Restrooms detected. Confirm at least one accessible restroom per CBC 11B-213 with compliant fixtures and clearances.",
            FindingCategory.ACCESSIBILITY, Severity.WARNING, "CBC 11B-213", 70,
            "Provide enlarged accessible-restroom plans with dimensioned clearances."),
        new Signal("parking", "Check parking & AB 2097 applicability",
            "Parking referenced. Verify stall counts, accessible stalls (CBC 11B-208), and whether AB 2097 removes minimums near transit.",
            FindingCategory.ZONING, Severity.INFORMATIONAL, "AB 2097 / LAMC 12.21-A", 60,
            "Tabulate required vs. provided parking, including accessible stalls."),
        new Signal("stair", "Verify stairway compliance",
            "Stairs detected. Confirm riser/tread, handrail, and headroom dimensions per CBC 1011.",
            FindingCategory.BUILDING, Severity.INFORMATIONAL, "CBC 1011", 62,
            "Dimension stair riser/tread and handrail heights on the plans."),
        new Signal("occupanc", "Confirm occupancy classification",
            "Occupancy language detected. Confirm the CBC occupancy group and any required separations (CBC 508).",
            FindingCategory.BUILDING, Severity.WARNING, "CBC 302 / 508", 64,
            "State the occupancy group(s) and construction type in the title block."));

    @Override
    public String type() { return "heuristic"; }

    @Override
    public boolean available() { return true; }

    @Override
    public AiModels.AiAnalysis analyze(AiModels.AiRequest request) {
        String hay = ((request.projectText() == null ? "" : request.projectText()) + " "
            + (request.documentText() == null ? "" : request.documentText()))
            .toLowerCase(Locale.ROOT);
        Set<String> existing = request.ruleFindingTitles() == null ? Set.of()
            : Set.copyOf(request.ruleFindingTitles().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());

        List<AiModels.AiFinding> findings = new ArrayList<>();
        for (Signal s : SIGNALS) {
            if (hay.contains(s.keyword()) && !existing.contains(s.title().toLowerCase(Locale.ROOT))) {
                findings.add(new AiModels.AiFinding(s.title(), s.description(), s.category(),
                    s.severity(), s.codeRef(), s.confidence(), s.recommendation(),
                    "AI keyword detection matched \"" + s.keyword() + "\" in the submission text."));
            }
        }

        boolean noText = request.documentText() == null || request.documentText().isBlank();
        if (noText) {
            findings.add(new AiModels.AiFinding(
                "Limited machine-readable content",
                "No extractable text was found in the uploaded plans (likely scanned or image-based). "
                    + "Vector-based PDFs enable more thorough automated review.",
                FindingCategory.COMPLETENESS, Severity.INFORMATIONAL, null, 55,
                "Submit vector-based PDFs where possible so text and dimensions can be read.",
                "The document text extractor returned no content."));
        }

        String summary = "Heuristic AI pass reviewed the submission text and surfaced "
            + findings.size() + " advisory item(s) to complement the rule-based findings.";
        return new AiModels.AiAnalysis(true, summary, findings, "heuristic", "keyword-v1", 0, 0);
    }
}
