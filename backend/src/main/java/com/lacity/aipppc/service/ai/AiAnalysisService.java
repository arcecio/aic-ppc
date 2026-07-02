package com.lacity.aipppc.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Selects the AI provider that augments a screening run. Preference order:
 * the configured provider ({@code app.ai.provider}) when it is available, then
 * the always-on heuristic. This guarantees the AI arm is present without ever
 * hard-failing when the Anthropic key is missing (SOW 2.2.3 — AI enhances the
 * rules; docs/05-ai-and-governance.md).
 */
@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final Map<String, AiProvider> providers;
    private final AiProvider heuristic;

    @Value("${app.ai.provider:anthropic}")
    private String configuredProvider;

    public AiAnalysisService(List<AiProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(AiProvider::type, Function.identity()));
        this.heuristic = providers.get("heuristic");
    }

    /** Returns the provider that will actually be used for the next run. */
    public AiProvider activeProvider() {
        if (!"none".equalsIgnoreCase(configuredProvider)) {
            AiProvider p = providers.get(configuredProvider.toLowerCase());
            if (p != null && p.available()) return p;
        }
        return heuristic;
    }

    public AiModels.AiAnalysis analyze(AiModels.AiRequest request) {
        AiProvider provider = activeProvider();
        try {
            AiModels.AiAnalysis result = provider.analyze(request);
            if (result.available()) return result;
            // Configured provider bailed out — fall back to the heuristic.
            if (provider != heuristic && heuristic != null) {
                log.info("AI provider '{}' unavailable at call time; using heuristic.", provider.type());
                return heuristic.analyze(request);
            }
            return result;
        } catch (RuntimeException e) {
            log.warn("AI provider '{}' threw: {}. Falling back to heuristic.", provider.type(), e.getMessage());
            return heuristic != null ? heuristic.analyze(request) : AiModels.AiAnalysis.unavailable();
        }
    }
}
