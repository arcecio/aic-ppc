package com.lacity.aipppc.service.ai;

/**
 * A pluggable AI analysis backend that <b>augments</b> the rule engine
 * (SOW 2.2.3 — AI "used to enhance detection and interpretation", not the primary
 * mechanism). Implementations: {@code AnthropicAiProvider} (Claude),
 * {@code LMStudioAiProvider} (local, OpenAI protocol) and
 * {@code HeuristicAiProvider} (deterministic, offline). The active provider is
 * chosen by {@code AiAnalysisService}.
 */
public interface AiProvider {

    /** Stable id used in config and persisted on the run ({@code ai_provider_used}). */
    String type();

    /** True when the provider can actually run (e.g. Anthropic has an API key). */
    boolean available();

    AiModels.AiAnalysis analyze(AiModels.AiRequest request);
}
