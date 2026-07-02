package com.lacity.aipppc.service.ai;

import com.lacity.aipppc.model.enums.FindingCategory;
import com.lacity.aipppc.model.enums.Severity;

import java.util.List;

/** DTOs shared by the AI provider abstraction. */
public final class AiModels {

    private AiModels() {}

    /** Input to an AI-assisted analysis pass. */
    public record AiRequest(
        String projectInfo,      // title, permit type, address, zone, overlays
        String projectText,      // scope + description + intended use
        String documentText,     // concatenated extracted document text (may be large)
        String knowledgeContext, // retrieved regulatory snippets
        List<String> ruleFindingTitles // titles already found by the rule engine (avoid dupes)
    ) {}

    /** One AI-surfaced finding. Mirrors {@code Finding} fields the AI can set. */
    public record AiFinding(
        String title,
        String description,
        FindingCategory category,
        Severity severity,
        String codeReference,
        int confidence,
        String recommendation,
        String triggeringCondition
    ) {}

    /** Result of an AI pass. {@code available=false} means no model ran. */
    public record AiAnalysis(
        boolean available,
        String summary,
        List<AiFinding> findings,
        String providerUsed,
        String modelUsed,
        long inputTokens,
        long outputTokens
    ) {
        public static AiAnalysis unavailable() {
            return new AiAnalysis(false, null, List.of(), null, null, 0, 0);
        }
    }
}
