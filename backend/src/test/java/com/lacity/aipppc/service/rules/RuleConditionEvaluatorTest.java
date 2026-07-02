package com.lacity.aipppc.service.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the configurable rule condition grammar (SOW 2.2.3). */
class RuleConditionEvaluatorTest {

    private final RuleConditionEvaluator evaluator = new RuleConditionEvaluator(new ObjectMapper());

    private Map<String, Object> ctx() {
        return new java.util.HashMap<>(Map.of(
            "permitType", "MULTIFAMILY_NEW",
            "permitCategory", "MULTI_FAMILY",
            "zone", "RE15-1-H",
            "overlays", List.of("Hillside", "Very High Fire Hazard Severity Zone"),
            "hazards", List.of("Fault", "Landslide"),
            "stories", 3,
            "units", 8,
            "text", "new building with grading and a driveway in the public right-of-way"
        ));
    }

    @Test
    void eqAndNeqOnStringsAreCaseInsensitive() {
        assertThat(evaluator.matches("{\"field\":\"permitType\",\"op\":\"eq\",\"value\":\"multifamily_new\"}", ctx())).isTrue();
        assertThat(evaluator.matches("{\"field\":\"permitType\",\"op\":\"neq\",\"value\":\"SFD_NEW\"}", ctx())).isTrue();
    }

    @Test
    void numericComparisons() {
        assertThat(evaluator.matches("{\"field\":\"stories\",\"op\":\"gte\",\"value\":3}", ctx())).isTrue();
        assertThat(evaluator.matches("{\"field\":\"stories\",\"op\":\"gt\",\"value\":3}", ctx())).isFalse();
        assertThat(evaluator.matches("{\"field\":\"units\",\"op\":\"gte\",\"value\":5}", ctx())).isTrue();
        assertThat(evaluator.matches("{\"field\":\"units\",\"op\":\"lt\",\"value\":5}", ctx())).isFalse();
    }

    @Test
    void ltOnMissingNumericFieldDoesNotFire() {
        // A rule like "setback < 15" must NOT fire when the applicant left setback blank.
        assertThat(evaluator.matches("{\"field\":\"frontYardSetbackFt\",\"op\":\"lt\",\"value\":15}", ctx())).isFalse();
    }

    @Test
    void containsOnStringAndList() {
        assertThat(evaluator.matches("{\"field\":\"text\",\"op\":\"contains\",\"value\":\"grading\"}", ctx())).isTrue();
        assertThat(evaluator.matches("{\"field\":\"overlays\",\"op\":\"contains\",\"value\":\"Hillside\"}", ctx())).isTrue();
        assertThat(evaluator.matches("{\"field\":\"overlays\",\"op\":\"contains\",\"value\":\"Coastal Zone\"}", ctx())).isFalse();
    }

    @Test
    void containsAnyIntersectsListsAndSubstrings() {
        assertThat(evaluator.matches(
            "{\"field\":\"overlays\",\"op\":\"containsAny\",\"value\":[\"Coastal Zone\",\"Hillside\"]}", ctx())).isTrue();
        assertThat(evaluator.matches(
            "{\"field\":\"text\",\"op\":\"containsAny\",\"value\":[\"driveway\",\"curb\"]}", ctx())).isTrue();
        assertThat(evaluator.matches(
            "{\"field\":\"overlays\",\"op\":\"containsAny\",\"value\":[\"HPOZ\",\"Q Condition\"]}", ctx())).isFalse();
    }

    @Test
    void inOperator() {
        assertThat(evaluator.matches(
            "{\"field\":\"permitCategory\",\"op\":\"in\",\"value\":[\"COMMERCIAL\",\"MULTI_FAMILY\"]}", ctx())).isTrue();
    }

    @Test
    void existsAndMissing() {
        assertThat(evaluator.matches("{\"field\":\"zone\",\"op\":\"exists\"}", ctx())).isTrue();
        assertThat(evaluator.matches("{\"field\":\"occupancyGroup\",\"op\":\"missing\"}", ctx())).isTrue();
    }

    @Test
    void allAnyNotComposition() {
        String rule = "{\"all\":["
            + "{\"field\":\"overlays\",\"op\":\"containsAny\",\"value\":[\"Hillside\"]},"
            + "{\"any\":[{\"field\":\"stories\",\"op\":\"gte\",\"value\":3},{\"field\":\"units\",\"op\":\"gte\",\"value\":100}]},"
            + "{\"not\":{\"field\":\"zone\",\"op\":\"eq\",\"value\":\"R1-1\"}}"
            + "]}";
        assertThat(evaluator.matches(rule, ctx())).isTrue();
    }

    @Test
    void malformedConditionFailsSafeToFalse() {
        assertThat(evaluator.matches("not json", ctx())).isFalse();
        assertThat(evaluator.matches("", ctx())).isFalse();
        assertThat(evaluator.matches(null, ctx())).isFalse();
    }
}
