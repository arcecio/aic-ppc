package com.lacity.aipppc.service.screening;

import com.lacity.aipppc.model.RegulatoryCode;
import com.lacity.aipppc.repository.RegulatoryCodeRepository;
import com.lacity.aipppc.service.embedding.EmbeddingService;
import com.lacity.aipppc.service.rag.RrfFusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Hybrid retrieval over the regulatory knowledgebase (SOW 2.1.1), Blue-style:
 * <ul>
 *   <li><b>Lexical arm</b> — keyword search over title/summary/tags/section,
 *       with query terms derived from the project context (zone, overlays,
 *       hazards, salient words from the submission text).</li>
 *   <li><b>Vector arm</b> — the same query embedded via the TEI sidecar
 *       (e5-large-v2) against the pgvector HNSW index (cosine).</li>
 * </ul>
 * The two rankings are fused with Reciprocal Rank Fusion (k=60) and the top
 * sections become the compact context block handed to the AI provider. The
 * vector arm degrades silently to lexical-only when the embedding sidecar is
 * absent — retrieval never blocks a screening run. Also resolves code references
 * back to canonical URLs so findings carry links (SOW 2.2.4).
 */
@Service
public class RegulatoryKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryKnowledgeService.class);
    private static final int MAX_SNIPPETS = 8;
    private static final int PER_RETRIEVER_LIMIT = 10;

    private final RegulatoryCodeRepository repository;
    private final EmbeddingService embeddingService;

    public RegulatoryKnowledgeService(RegulatoryCodeRepository repository,
                                      EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    /** Builds the ranked, de-duplicated context block for the AI provider. */
    public String buildContext(Map<String, Object> ctx) {
        List<String> terms = keywords(ctx);
        Map<String, RegulatoryCode> byExternalId = new LinkedHashMap<>();

        // ── lexical arm ─────────────────────────────────────────────────────────
        List<String> lexicalRanked = new ArrayList<>();
        for (String term : terms) {
            for (RegulatoryCode c : repository.search(term)) {
                if (byExternalId.putIfAbsent(c.getExternalId(), c) == null) {
                    lexicalRanked.add(c.getExternalId());
                }
                if (lexicalRanked.size() >= PER_RETRIEVER_LIMIT) break;
            }
            if (lexicalRanked.size() >= PER_RETRIEVER_LIMIT) break;
        }

        // ── vector arm (degrades to empty when the sidecar is unavailable) ──────
        List<String> vectorRanked = new ArrayList<>();
        Optional<float[]> queryVector = embeddingService.embedQuery(queryText(ctx, terms));
        if (queryVector.isPresent()) {
            String literal = EmbeddingService.toVectorLiteral(queryVector.get());
            for (RegulatoryCode c : repository.searchByEmbedding(literal, PER_RETRIEVER_LIMIT)) {
                byExternalId.putIfAbsent(c.getExternalId(), c);
                vectorRanked.add(c.getExternalId());
            }
        }

        // ── RRF fusion ──────────────────────────────────────────────────────────
        List<String> fused = RrfFusion.fuse(List.of(lexicalRanked, vectorRanked));
        log.debug("KB retrieval: {} lexical, {} vector, {} fused (vector arm {})",
            lexicalRanked.size(), vectorRanked.size(), fused.size(),
            queryVector.isPresent() ? "on" : "off");

        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String externalId : fused) {
            if (n++ >= MAX_SNIPPETS) break;
            RegulatoryCode c = byExternalId.get(externalId);
            if (c == null) continue;
            sb.append("[").append(c.getCodeType()).append(" ").append(c.getSection()).append("] ")
              .append(c.getTitle()).append(" — ")
              .append(c.getSummary() == null ? "" : c.getSummary()).append("\n");
        }
        return sb.toString();
    }

    /** Resolves a free-form code reference (e.g. "LAMC 12.21-C") to its stored URL, if any. */
    public String urlForReference(String reference) {
        if (reference == null || reference.isBlank()) return null;
        String needle = reference.replaceAll("(?i)LAMC|CBC|Title\\s*24|CFC", "").trim();
        for (RegulatoryCode c : repository.search(needle.isBlank() ? reference : needle)) {
            if (c.getUrl() != null) return c.getUrl();
        }
        return null;
    }

    /** Natural-language query for the vector arm: terms + a slice of the submission text. */
    private String queryText(Map<String, Object> ctx, List<String> terms) {
        StringBuilder sb = new StringBuilder(String.join(" ", terms));
        Object text = ctx.get("text");
        if (text instanceof String s && !s.isBlank()) {
            sb.append(' ').append(s, 0, Math.min(s.length(), 500));
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> keywords(Map<String, Object> ctx) {
        List<String> terms = new ArrayList<>();
        addIf(terms, ctx.get("permitCategory"));
        addIf(terms, ctx.get("zone"));
        Object overlays = ctx.get("overlays");
        if (overlays instanceof List<?> l) l.forEach(o -> addIf(terms, o));
        Object hazards = ctx.get("hazards");
        if (hazards instanceof List<?> l) l.forEach(o -> addIf(terms, o));
        // A few salient words from the combined text.
        Object text = ctx.get("text");
        if (text instanceof String s) {
            for (String kw : List.of("setback", "parking", "accessib", "occupancy", "grading",
                "fire", "sign", "height", "egress", "energy", "green")) {
                if (s.contains(kw)) terms.add(kw);
            }
        }
        return terms.stream().filter(t -> t != null && !t.isBlank()).distinct().toList();
    }

    private void addIf(List<String> terms, Object v) {
        if (v != null) {
            String s = v.toString().toLowerCase(Locale.ROOT);
            // Use the leading token of multi-word overlays for a broader match.
            terms.add(s.split("[\\s:(]")[0]);
        }
    }
}
