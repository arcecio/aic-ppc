package com.lacity.aipppc.service.screening;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substitutes {@code {{field}}} placeholders in rule messages/reasons with values
 * from the evaluation context, so a rule like "front yard is {@code {{frontYardSetbackFt}}} ft"
 * renders concrete numbers in the finding shown to the applicant.
 */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private TemplateRenderer() {}

    public static String render(String template, Map<String, Object> ctx) {
        if (template == null || template.indexOf("{{") < 0) return template;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object value = ctx.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(display(value)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String display(Object value) {
        if (value == null) return "(unspecified)";
        if (value instanceof List<?> list) return String.join(", ", list.stream().map(String::valueOf).toList());
        if (value instanceof Double d && d == Math.floor(d) && !d.isInfinite()) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(value);
    }
}
