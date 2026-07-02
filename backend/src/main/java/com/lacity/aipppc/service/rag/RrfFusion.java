package com.lacity.aipppc.service.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (Cormack et al.) over ranked key lists — the same
 * fusion Blue uses to combine its lexical and vector retrievers.
 * score(key) = Σ over lists of 1 / (k + rank), rank starting at 1. Ties broken
 * by first appearance, so results are deterministic.
 */
public final class RrfFusion {

    public static final int DEFAULT_K = 60;

    private RrfFusion() {}

    public static List<String> fuse(List<List<String>> rankings, int k) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (List<String> ranking : rankings) {
            if (ranking == null) continue;
            for (int i = 0; i < ranking.size(); i++) {
                String key = ranking.get(i);
                if (key == null) continue;
                scores.merge(key, 1.0 / (k + i + 1), Double::sum);
            }
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(scores.entrySet());
        // Stable sort → equal scores keep insertion (first-appearance) order.
        entries.sort(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed());
        return entries.stream().map(Map.Entry::getKey).toList();
    }

    public static List<String> fuse(List<List<String>> rankings) {
        return fuse(rankings, DEFAULT_K);
    }
}
