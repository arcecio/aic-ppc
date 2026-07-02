package com.lacity.aipppc.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lacity.aipppc.model.enums.FindingCategory;
import com.lacity.aipppc.model.enums.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Anthropic Claude implementation of {@link AiProvider}. Sends the project +
 * extracted-document text and retrieved code context to the Messages API and asks
 * for strict-JSON findings that augment the rule engine. If the key is absent or
 * the call fails, it reports {@link AiModels.AiAnalysis#unavailable()} so
 * {@code AiAnalysisService} falls back to the heuristic provider — the system
 * never hard-depends on an external model (SOW 4.4 data-minimization + resilience).
 *
 * <p>Governance: City data is sent only for the screening task, is not used for
 * training, and the model's output is advisory and subject to human review
 * (Exhibit 7; SOW 4.1.3). See docs/05-ai-and-governance.md.
 */
@Component
public class AnthropicAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAiProvider.class);
    private static final int MAX_DOC_CHARS = 24_000;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15)).build();

    @Value("${app.ai.anthropic.api-key:}")
    private String apiKey;
    @Value("${app.ai.anthropic.api-url:https://api.anthropic.com/v1}")
    private String apiUrl;
    @Value("${app.ai.anthropic.default-model:claude-sonnet-4-6}")
    private String model;
    @Value("${app.ai.anthropic.max-tokens:4096}")
    private int maxTokens;

    public AnthropicAiProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() { return "anthropic"; }

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AiModels.AiAnalysis analyze(AiModels.AiRequest request) {
        if (!available()) return AiModels.AiAnalysis.unavailable();
        try {
            String body = buildRequestBody(request);
            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/messages"))
                .timeout(Duration.ofSeconds(120))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Anthropic call failed ({}): {}", resp.statusCode(), truncate(resp.body(), 400));
                return AiModels.AiAnalysis.unavailable();
            }
            return parseResponse(resp.body());
        } catch (Exception e) {
            log.warn("Anthropic call error: {}", e.getMessage());
            return AiModels.AiAnalysis.unavailable();
        }
    }

    private String buildRequestBody(AiModels.AiRequest req) throws Exception {
        String system = """
            You are an advisory pre-plan-check assistant for the City of Los Angeles LADBS.
            You AUGMENT a rule-based engine — surface only issues the listed rule findings did not
            already cover. You never approve plans; City staff make all final determinations.
            Return STRICT JSON only, no prose, matching:
            {"summary": string,
             "findings": [{"title": string, "description": string,
                "category": one of [COMPLETENESS,ZONING,BUILDING,STRUCTURAL,ACCESSIBILITY,FIRE,MECHANICAL,ELECTRICAL,PLUMBING,GREEN,GENERAL],
                "severity": one of [BLOCKING,WARNING,INFORMATIONAL],
                "codeReference": string, "confidence": integer 0-100,
                "recommendation": string, "triggeringCondition": string}]}
            Cite specific LAMC / Title 24 / CBC sections where possible. Be conservative with confidence.
            """;

        StringBuilder user = new StringBuilder();
        user.append("PROJECT:\n").append(req.projectInfo()).append("\n\n");
        if (req.projectText() != null && !req.projectText().isBlank()) {
            user.append("PROJECT NARRATIVE:\n").append(req.projectText()).append("\n\n");
        }
        if (req.knowledgeContext() != null && !req.knowledgeContext().isBlank()) {
            user.append("RELEVANT CODE CONTEXT:\n").append(req.knowledgeContext()).append("\n\n");
        }
        if (req.ruleFindingTitles() != null && !req.ruleFindingTitles().isEmpty()) {
            user.append("ALREADY FLAGGED BY RULES (do not repeat):\n- ")
                .append(String.join("\n- ", req.ruleFindingTitles())).append("\n\n");
        }
        String docText = req.documentText() == null ? "" : req.documentText();
        user.append("EXTRACTED DOCUMENT TEXT (may be truncated):\n")
            .append(truncate(docText, MAX_DOC_CHARS));

        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        root.put("system", system);
        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", user.toString());
        return mapper.writeValueAsString(root);
    }

    private AiModels.AiAnalysis parseResponse(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        String text = root.path("content").isArray() && root.path("content").size() > 0
            ? root.path("content").get(0).path("text").asText("") : "";
        long inTok = root.path("usage").path("input_tokens").asLong(0);
        long outTok = root.path("usage").path("output_tokens").asLong(0);

        JsonNode payload = mapper.readTree(extractJson(text));
        String summary = payload.path("summary").asText("AI review complete.");
        List<AiModels.AiFinding> findings = new ArrayList<>();
        for (JsonNode f : payload.path("findings")) {
            findings.add(new AiModels.AiFinding(
                f.path("title").asText("AI finding"),
                f.path("description").asText(""),
                parseCategory(f.path("category").asText()),
                parseSeverity(f.path("severity").asText()),
                f.hasNonNull("codeReference") ? f.get("codeReference").asText() : null,
                clamp(f.path("confidence").asInt(60)),
                f.hasNonNull("recommendation") ? f.get("recommendation").asText() : null,
                f.hasNonNull("triggeringCondition") ? f.get("triggeringCondition").asText() : null));
        }
        return new AiModels.AiAnalysis(true, summary, findings, "anthropic", model, inTok, outTok);
    }

    /** Tolerates models that wrap JSON in prose or ```json fences. */
    private String extractJson(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : "{}";
    }

    private FindingCategory parseCategory(String raw) {
        try { return FindingCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return FindingCategory.GENERAL; }
    }

    private Severity parseSeverity(String raw) {
        try { return Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return Severity.INFORMATIONAL; }
    }

    private int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
