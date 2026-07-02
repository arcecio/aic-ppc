package com.lacity.aipppc.service.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates a rule's boolean condition tree against a flat project context. This
 * is the engine behind the RFP's requirement that pre-screening and clearance
 * logic be a <b>configurable rule-based engine</b> that City staff can change
 * without vendor code changes (SOW 2.2.3, 2.2.5; Appendix 3 §5.1.6).
 *
 * <p>Node grammar (see docs/04-rule-engine.md):
 * <pre>
 *   {"all":[node,...]}            AND
 *   {"any":[node,...]}            OR
 *   {"not":node}
 *   {"field":F,"op":OP,"value":V} leaf
 * </pre>
 * Operators: eq, neq, in, contains, containsAny, gt, gte, lt, lte, exists,
 * missing, regex. A malformed node evaluates to {@code false} (fail-safe) and is
 * logged, so a bad staff edit can never crash a screening run.
 */
@Component
public class RuleConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleConditionEvaluator.class);

    private final ObjectMapper objectMapper;

    public RuleConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Parses and evaluates the JSON condition string. Returns false on any parse error. */
    public boolean matches(String conditionJson, Map<String, Object> context) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(conditionJson);
            return evaluate(node, context);
        } catch (Exception e) {
            log.warn("Rule condition failed to parse/evaluate: {}", e.getMessage());
            return false;
        }
    }

    boolean evaluate(JsonNode node, Map<String, Object> ctx) {
        if (node == null || node.isNull()) return false;

        if (node.has("all")) {
            for (JsonNode child : node.get("all")) {
                if (!evaluate(child, ctx)) return false;
            }
            return true;
        }
        if (node.has("any")) {
            for (JsonNode child : node.get("any")) {
                if (evaluate(child, ctx)) return true;
            }
            return false;
        }
        if (node.has("not")) {
            return !evaluate(node.get("not"), ctx);
        }
        if (node.has("field")) {
            return evaluateLeaf(node, ctx);
        }
        log.warn("Unrecognized rule node: {}", node);
        return false;
    }

    private boolean evaluateLeaf(JsonNode node, Map<String, Object> ctx) {
        String field = node.get("field").asText();
        String op = node.hasNonNull("op") ? node.get("op").asText().toLowerCase(Locale.ROOT) : "eq";
        Object actual = ctx.get(field);
        JsonNode valueNode = node.get("value");

        return switch (op) {
            case "exists" -> present(actual);
            case "missing" -> !present(actual);
            case "eq" -> equalsScalar(actual, valueNode);
            case "neq" -> !equalsScalar(actual, valueNode);
            case "in" -> inList(actual, valueNode);
            case "contains" -> contains(actual, valueNode);
            case "containsany" -> containsAny(actual, valueNode);
            case "gt" -> compareNum(actual, valueNode) > 0;
            case "gte" -> compareNum(actual, valueNode) >= 0;
            case "lt" -> {
                Integer c = safeCompare(actual, valueNode);
                yield c != null && c < 0;
            }
            case "lte" -> {
                Integer c = safeCompare(actual, valueNode);
                yield c != null && c <= 0;
            }
            case "regex" -> actual != null && valueNode != null
                && Pattern.compile(valueNode.asText(), Pattern.CASE_INSENSITIVE)
                    .matcher(actual.toString()).find();
            default -> {
                log.warn("Unknown operator '{}'", op);
                yield false;
            }
        };
    }

    private boolean present(Object v) {
        if (v == null) return false;
        if (v instanceof String s) return !s.isBlank();
        if (v instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    private boolean equalsScalar(Object actual, JsonNode value) {
        if (actual == null || value == null) return false;
        if (actual instanceof Number n && value.isNumber()) {
            return Double.compare(n.doubleValue(), value.asDouble()) == 0;
        }
        if (actual instanceof Boolean b) {
            return b == value.asBoolean();
        }
        return actual.toString().equalsIgnoreCase(value.asText());
    }

    private boolean inList(Object actual, JsonNode value) {
        if (actual == null || value == null || !value.isArray()) return false;
        for (JsonNode item : value) {
            if (equalsScalarText(actual, item)) return true;
        }
        return false;
    }

    private boolean equalsScalarText(Object actual, JsonNode item) {
        if (actual instanceof Number n && item.isNumber()) {
            return Double.compare(n.doubleValue(), item.asDouble()) == 0;
        }
        return actual.toString().equalsIgnoreCase(item.asText());
    }

    /** contains: string field → substring; list field → membership. value is scalar. */
    @SuppressWarnings("unchecked")
    private boolean contains(Object actual, JsonNode value) {
        if (actual == null || value == null) return false;
        String needle = value.asText().toLowerCase(Locale.ROOT);
        if (actual instanceof List<?> list) {
            return ((List<Object>) list).stream()
                .anyMatch(o -> o != null && o.toString().equalsIgnoreCase(value.asText()));
        }
        return actual.toString().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** containsAny: value is an array. String field → any substring; list field → intersection. */
    @SuppressWarnings("unchecked")
    private boolean containsAny(Object actual, JsonNode value) {
        if (actual == null || value == null || !value.isArray()) return false;
        if (actual instanceof List<?> list) {
            for (JsonNode item : value) {
                boolean hit = ((List<Object>) list).stream()
                    .anyMatch(o -> o != null && o.toString().equalsIgnoreCase(item.asText()));
                if (hit) return true;
            }
            return false;
        }
        String hay = actual.toString().toLowerCase(Locale.ROOT);
        for (JsonNode item : value) {
            if (hay.contains(item.asText().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private int compareNum(Object actual, JsonNode value) {
        Integer c = safeCompare(actual, value);
        return c == null ? -1 : c; // gt/gte with a missing field are false
    }

    /** Numeric comparison; null when either side isn't numeric (so lt/lte don't fire on missing). */
    private Integer safeCompare(Object actual, JsonNode value) {
        Double a = toDouble(actual);
        Double b = value != null && value.isNumber() ? value.asDouble()
            : (value != null ? tryParse(value.asText()) : null);
        if (a == null || b == null) return null;
        return Double.compare(a, b);
    }

    private Double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) return tryParse(s);
        return null;
    }

    private Double tryParse(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return null; }
    }
}
