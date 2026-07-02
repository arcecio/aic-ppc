package com.lacity.aipppc.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/**
 * Hugging Face Text Embeddings Inference (TEI) provider, talking the
 * OpenAI-compatible {@code POST /v1/embeddings} shape — the same sidecar setup as
 * Blue. Default model intfloat/e5-large-v2 (1024-dim, MIT licensed); e5 requires
 * the {@code passage:} / {@code query:} prefixes, applied here so callers never
 * see them.
 *
 * <p>Availability is probed lazily and failures are cached for a cool-down
 * window, so a missing sidecar costs one fast connection-refused per window and
 * the retriever degrades to lexical-only (never blocking a screening run).
 */
@Component
public class TeiEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(TeiEmbeddingProvider.class);
    private static final long COOLDOWN_MS = 300_000; // 5 minutes

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2)).build();

    @Value("${app.embedding.enabled:true}")
    private boolean enabled;
    @Value("${app.embedding.url:http://localhost:8086}")
    private String url;
    @Value("${app.embedding.model:intfloat/e5-large-v2}")
    private String model;
    @Value("${app.embedding.dim:1024}")
    private int dim;

    private volatile long unavailableUntil = 0;

    public TeiEmbeddingProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() { return "tei"; }

    @Override
    public boolean available() {
        return enabled && url != null && !url.isBlank() && System.currentTimeMillis() >= unavailableUntil;
    }

    @Override
    public List<float[]> embedPassages(List<String> texts) {
        return embed(texts.stream().map(t -> "passage: " + t).toList());
    }

    @Override
    public float[] embedQuery(String text) {
        List<float[]> out = embed(List.of("query: " + text));
        return out.isEmpty() ? null : out.get(0);
    }

    private List<float[]> embed(List<String> inputs) {
        if (!available()) return List.of();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            ArrayNode arr = body.putArray("input");
            inputs.forEach(arr::add);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url + "/v1/embeddings"))
                .timeout(Duration.ofSeconds(30))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                markUnavailable("HTTP " + resp.statusCode());
                return List.of();
            }
            JsonNode data = mapper.readTree(resp.body()).path("data");
            if (!data.isArray() || data.size() != inputs.size()) {
                markUnavailable("unexpected response shape");
                return List.of();
            }
            List<float[]> vectors = new ArrayList<>(inputs.size());
            for (JsonNode item : data) {
                JsonNode emb = item.path("embedding");
                if (!emb.isArray() || emb.size() != dim) {
                    markUnavailable("embedding dim " + emb.size() + " != " + dim);
                    return List.of();
                }
                float[] v = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) v[i] = (float) emb.get(i).asDouble();
                vectors.add(v);
            }
            return vectors;
        } catch (Exception e) {
            markUnavailable(e.getMessage());
            return List.of();
        }
    }

    private void markUnavailable(String reason) {
        unavailableUntil = System.currentTimeMillis() + COOLDOWN_MS;
        log.info("Embedding sidecar unavailable ({}); vector retrieval disabled for {}s — lexical-only until then",
            reason, COOLDOWN_MS / 1000);
    }
}
