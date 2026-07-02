package com.lacity.aipppc.service.screening;

import com.lacity.aipppc.model.RegulatoryCode;
import com.lacity.aipppc.repository.RegulatoryCodeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight retrieval over the regulatory knowledgebase (SOW 2.1.1). Given the
 * project context it selects the most relevant code sections and formats them as
 * a compact context block passed to the AI provider, and it can resolve a code
 * reference back to its canonical URL to enrich findings with links (SOW 2.2.4 —
 * "include relevant code references (including links whenever possible)").
 */
@Service
public class RegulatoryKnowledgeService {

    private static final int MAX_SNIPPETS = 8;

    private final RegulatoryCodeRepository repository;

    public RegulatoryKnowledgeService(RegulatoryCodeRepository repository) {
        this.repository = repository;
    }

    /** Builds a de-duplicated, ranked context block from keyword terms in the project. */
    public String buildContext(Map<String, Object> ctx) {
        List<String> terms = keywords(ctx);
        Map<String, RegulatoryCode> hits = new LinkedHashMap<>();
        for (String term : terms) {
            for (RegulatoryCode c : repository.search(term)) {
                hits.putIfAbsent(c.getExternalId(), c);
                if (hits.size() >= MAX_SNIPPETS * 2) break;
            }
            if (hits.size() >= MAX_SNIPPETS * 2) break;
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (RegulatoryCode c : hits.values()) {
            if (n++ >= MAX_SNIPPETS) break;
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
