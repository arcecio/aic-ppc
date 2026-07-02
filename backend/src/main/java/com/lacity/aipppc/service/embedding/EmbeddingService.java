package com.lacity.aipppc.service.embedding;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Thin, failure-tolerant facade over the embedding provider. Callers get an
 * empty result when embeddings are unavailable and are expected to degrade
 * (the knowledgebase retriever falls back to lexical-only), so the AI/vector
 * arm can never take a screening run down with it.
 */
@Service
public class EmbeddingService {

    private final EmbeddingProvider provider;

    public EmbeddingService(EmbeddingProvider provider) {
        this.provider = provider;
    }

    public boolean available() {
        return provider.available();
    }

    public String providerType() {
        return provider.type();
    }

    public Optional<float[]> embedQuery(String text) {
        if (!provider.available() || text == null || text.isBlank()) return Optional.empty();
        try {
            return Optional.ofNullable(provider.embedQuery(text));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Embeds passages; returns an empty list (not partial results) on any failure. */
    public List<float[]> embedPassages(List<String> texts) {
        if (!provider.available() || texts == null || texts.isEmpty()) return List.of();
        try {
            List<float[]> out = provider.embedPassages(texts);
            return out.size() == texts.size() ? out : List.of();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** pgvector literal form: {@code [0.1,0.2,...]} — used with CAST(:v AS vector). */
    public static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
