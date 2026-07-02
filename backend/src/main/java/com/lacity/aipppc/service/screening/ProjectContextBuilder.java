package com.lacity.aipppc.service.screening;

import com.fasterxml.jackson.databind.JsonNode;
import com.lacity.aipppc.model.Document;
import com.lacity.aipppc.model.Parcel;
import com.lacity.aipppc.model.PermitType;
import com.lacity.aipppc.model.Project;
import com.lacity.aipppc.model.enums.ScanStatus;
import com.lacity.aipppc.service.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flattens a project, its resolved parcel, dynamic-form answers, and uploaded
 * documents into the single string-keyed context the {@code RuleConditionEvaluator}
 * reads. Every field named in docs/04-rule-engine.md is populated here, including
 * the derived {@code presentDocs} / {@code missingDocs} lists that drive both
 * completeness rules and clearance/screening conditions.
 */
@Component
public class ProjectContextBuilder {

    private final JsonUtil json;

    public ProjectContextBuilder(JsonUtil json) {
        this.json = json;
    }

    public Map<String, Object> build(Project project, PermitType permitType,
                                     List<Document> documents, String combinedDocText) {
        Map<String, Object> ctx = new LinkedHashMap<>();

        ctx.put("permitType", project.getPermitTypeCode());
        ctx.put("permitCategory", permitType != null ? permitType.getCategory().name() : "OTHER");
        ctx.put("scope", lower(project.getProjectScope()));
        ctx.put("intendedUse", lower(project.getIntendedUse()));
        ctx.put("description", lower(project.getDescription()));

        // Parcel / GIS context.
        Parcel parcel = project.getParcel();
        ctx.put("hasParcel", parcel != null);
        if (parcel != null) {
            ctx.put("zone", parcel.getZone());
            ctx.put("overlays", json.toStringList(parcel.getOverlaysJson()));
            ctx.put("hazards", json.toStringList(parcel.getHazardZonesJson()));
            ctx.put("councilDistrict", parcel.getCouncilDistrict());
            ctx.put("communityPlanArea", parcel.getCommunityPlanArea());
        } else {
            ctx.put("zone", null);
            ctx.put("overlays", new ArrayList<String>());
            ctx.put("hazards", new ArrayList<String>());
        }

        // Dynamic form answers (each field id becomes a context key).
        Map<String, Object> form = json.toMap(project.getFormDataJson());
        for (Map.Entry<String, Object> e : form.entrySet()) {
            ctx.putIfAbsent(e.getKey(), e.getValue());
        }

        // Present / missing document keys.
        List<String> present = new ArrayList<>();
        for (Document d : documents) {
            if (d.getScanStatus() == ScanStatus.PASSED && d.getDocCategory() != null
                && !present.contains(d.getDocCategory())) {
                present.add(d.getDocCategory());
            }
        }
        ctx.put("presentDocs", present);
        ctx.put("missingDocs", missingRequired(permitType, present));

        // Combined text for keyword matching (scope + description + all doc text).
        String text = String.join(" ",
            safe(project.getTitle()), safe(project.getProjectScope()),
            safe(project.getIntendedUse()), safe(project.getDescription()),
            safe(combinedDocText)).toLowerCase(Locale.ROOT);
        ctx.put("text", text);

        return ctx;
    }

    /** Required-but-not-present document keys from the permit type's checklist. */
    public List<String> missingRequired(PermitType permitType, List<String> present) {
        List<String> missing = new ArrayList<>();
        if (permitType == null) return missing;
        JsonNode docs = json.readTree(permitType.getRequiredDocsJson());
        if (docs == null || !docs.isArray()) return missing;
        for (JsonNode d : docs) {
            boolean required = d.path("required").asBoolean(false);
            String key = d.path("docKey").asText("");
            if (required && !key.isBlank() && !present.contains(key)) {
                missing.add(key);
            }
        }
        return missing;
    }

    private String lower(String s) { return s == null ? null : s.toLowerCase(Locale.ROOT); }
    private String safe(String s) { return s == null ? "" : s; }
}
