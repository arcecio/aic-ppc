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
 * LM Studio implementation of {@link AiProvider}, speaking the OpenAI protocol.
 * LM Studio exposes an OpenAI-compatible server (default {@code http://localhost:1234/v1})
 * so this provider posts a Chat Completions request with a system + user message and
 * parses {@code choices[0].message.content} into strict-JSON findings that augment the
 * rule engine. Because the model runs locally, City data never leaves the host — the
 * strongest possible reading of SOW 4.4 data-minimization + Exhibit 7 governance.
 *
 * <p>If the server is unreachable or the call fails, it reports
 * {@link AiModels.AiAnalysis#unavailable()} so {@code AiAnalysisService} falls back to the
 * heuristic provider — the system never hard-depends on the local model.
 */
@Component
public class LMStudioAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(LMStudioAiProvider.class);
    private static final int MAX_DOC_CHARS = 24_000;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15)).build();

    @Value("${app.ai.lmstudio.api-url:http://localhost:1234/v1}")
    private String apiUrl;
    // LM Studio does not require auth by default; sent as a Bearer token only when set.
    @Value("${app.ai.lmstudio.api-key:}")
    private String apiKey;
    @Value("${app.ai.lmstudio.default-model:local-model}")
    private String model;
    @Value("${app.ai.lmstudio.max-tokens:4096}")
    private int maxTokens;
    @Value("${app.ai.lmstudio.temperature:0.2}")
    private double temperature;

    public LMStudioAiProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() { return "lmstudio"; }

    @Override
    public boolean available() {
        // Local server: reachability is proven at call time (a failed request falls
        // back to the heuristic). "Available" just means a base URL is configured.
        return apiUrl != null && !apiUrl.isBlank();
    }

    @Override
    public AiModels.AiAnalysis analyze(AiModels.AiRequest request) {
        if (!available()) return AiModels.AiAnalysis.unavailable();
        try {
            String body = buildRequestBody(request);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(apiUrl) + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("LM Studio call failed ({}): {}", resp.statusCode(), truncate(resp.body(), 400));
                return AiModels.AiAnalysis.unavailable();
            }
            return parseResponse(resp.body());
        } catch (Exception e) {
            log.warn("LM Studio call error: {}", e.getMessage());
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
        root.put("temperature", temperature);
        root.put("stream", false);
        // Ask for JSON mode where the loaded model supports it; extractJson() below
        // still tolerates prose- or fence-wrapped output for models that ignore it.
        root.putObject("response_format").put("type", "json_object");
        ArrayNode messages = root.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", system);
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", user.toString());
        return mapper.writeValueAsString(root);
    }

    private AiModels.AiAnalysis parseResponse(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        String text = choices.isArray() && choices.size() > 0
            ? choices.get(0).path("message").path("content").asText("") : "";
        long inTok = root.path("usage").path("prompt_tokens").asLong(0);
        long outTok = root.path("usage").path("completion_tokens").asLong(0);

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
        return new AiModels.AiAnalysis(true, summary, findings, "lmstudio", model, inTok, outTok);
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

    private String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
