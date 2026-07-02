package com.lacity.aipppc.service.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {

    @Test
    void itemRankedByBothArmsBeatsSingleArmWinners() {
        // "b" is #2 lexically and #1 by vector — should out-rank each arm's other picks.
        List<String> lexical = List.of("a", "b", "c");
        List<String> vector = List.of("b", "d");
        List<String> fused = RrfFusion.fuse(List.of(lexical, vector));
        assertThat(fused.get(0)).isEqualTo("b");
        assertThat(fused).containsExactlyInAnyOrder("a", "b", "c", "d");
    }

    @Test
    void singleListPreservesOrder() {
        assertThat(RrfFusion.fuse(List.of(List.of("x", "y", "z"))))
            .containsExactly("x", "y", "z");
    }

    @Test
    void emptyAndNullListsAreTolerated() {
        assertThat(RrfFusion.fuse(List.of())).isEmpty();
        List<List<String>> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        withNull.add(List.of("a"));
        assertThat(RrfFusion.fuse(withNull)).containsExactly("a");
    }

    @Test
    void tiesBreakByFirstAppearance() {
        // Same rank in disjoint lists → equal score; first-seen wins deterministically.
        List<String> fused = RrfFusion.fuse(List.of(List.of("a"), List.of("b")));
        assertThat(fused).containsExactly("a", "b");
    }

    @Test
    void duplicateWithinOneListAccumulates() {
        // Defensive: a key listed twice in one arm just sums — no crash, still ranked first.
        List<String> fused = RrfFusion.fuse(List.of(List.of("a", "a", "b")));
        assertThat(fused.get(0)).isEqualTo("a");
    }
}
