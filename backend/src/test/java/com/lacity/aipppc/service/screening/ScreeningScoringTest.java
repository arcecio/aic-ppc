package com.lacity.aipppc.service.screening;

import com.lacity.aipppc.model.enums.ReadinessStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for readiness scoring & status derivation (SOW 2.2.2). */
class ScreeningScoringTest {

    private CompletenessService.Result completeness(int required, int present) {
        return new CompletenessService.Result(List.of(), required, present, List.of());
    }

    @Test
    void cleanSubmissionScoresHighAndIsReady() {
        int score = ScreeningService.computeScore(0, 0, 0, completeness(3, 3));
        assertThat(score).isEqualTo(100);
        assertThat(ScreeningService.computeStatus(false, 0, 0)).isEqualTo(ReadinessStatus.READY_FOR_SUBMISSION);
    }

    @Test
    void warningsReduceScoreAndRequireAttention() {
        int score = ScreeningService.computeScore(0, 3, 2, completeness(3, 3));
        assertThat(score).isEqualTo(100 - 15 - 2);
        assertThat(ScreeningService.computeStatus(false, 0, 3)).isEqualTo(ReadinessStatus.REQUIRES_ATTENTION);
    }

    @Test
    void missingRequiredDocsPenalizeAndMarkIncomplete() {
        int score = ScreeningService.computeScore(2, 0, 0, completeness(4, 2));
        // 100 - 2*12 (blocking) - 2*6 (2 missing required) = 64
        assertThat(score).isEqualTo(64);
        assertThat(ScreeningService.computeStatus(true, 2, 0)).isEqualTo(ReadinessStatus.INCOMPLETE);
    }

    @Test
    void scoreIsClampedToZero() {
        assertThat(ScreeningService.computeScore(20, 0, 0, completeness(0, 0))).isEqualTo(0);
    }

    @Test
    void completenessBlockingWinsOverPlainBlocking() {
        assertThat(ScreeningService.computeStatus(true, 5, 5)).isEqualTo(ReadinessStatus.INCOMPLETE);
    }
}
