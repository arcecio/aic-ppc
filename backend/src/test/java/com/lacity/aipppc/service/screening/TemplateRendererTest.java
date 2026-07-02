package com.lacity.aipppc.service.screening;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRendererTest {

    @Test
    void substitutesScalarAndListPlaceholders() {
        Map<String, Object> ctx = Map.of("zone", "R1-1", "frontYardSetbackFt", 10,
            "overlays", List.of("Hillside", "VHFHSZ"));
        assertThat(TemplateRenderer.render("Zone {{zone}} setback {{frontYardSetbackFt}} ft", ctx))
            .isEqualTo("Zone R1-1 setback 10 ft");
        assertThat(TemplateRenderer.render("Overlays: {{overlays}}", ctx))
            .isEqualTo("Overlays: Hillside, VHFHSZ");
    }

    @Test
    void missingPlaceholderRendersUnspecified() {
        assertThat(TemplateRenderer.render("Value {{nope}}", Map.of())).isEqualTo("Value (unspecified)");
    }

    @Test
    void doublesWithoutFractionRenderAsIntegers() {
        assertThat(TemplateRenderer.render("{{n}}", Map.of("n", 12.0))).isEqualTo("12");
    }

    @Test
    void plainTextIsUntouched() {
        assertThat(TemplateRenderer.render("no placeholders here", Map.of())).isEqualTo("no placeholders here");
        assertThat(TemplateRenderer.render(null, Map.of())).isNull();
    }
}
