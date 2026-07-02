package com.lacity.aipppc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacity.aipppc.model.*;
import com.lacity.aipppc.model.enums.*;
import com.lacity.aipppc.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Locale;

/**
 * Loads the JSON seed corpus (permit types, parcels, regulatory knowledgebase,
 * and the two rule packs) into the database on boot. Idempotent by natural key —
 * an entry that already exists is left untouched, so City staff edits to rules or
 * permit types are never clobbered on restart. This is how the RFP's
 * "knowledgebase" and "configurable rule-based engine" are provisioned (SOW 2.1,
 * 2.2.3, 2.2.5).
 */
@Component
@Order(20)
public class ReferenceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSeeder.class);

    private final ObjectMapper mapper;
    private final PermitTypeRepository permitTypes;
    private final ParcelRepository parcels;
    private final RegulatoryCodeRepository codes;
    private final ScreeningRuleRepository screeningRules;
    private final ClearanceRuleRepository clearanceRules;

    public ReferenceDataSeeder(ObjectMapper mapper,
                               PermitTypeRepository permitTypes,
                               ParcelRepository parcels,
                               RegulatoryCodeRepository codes,
                               ScreeningRuleRepository screeningRules,
                               ClearanceRuleRepository clearanceRules) {
        this.mapper = mapper;
        this.permitTypes = permitTypes;
        this.parcels = parcels;
        this.codes = codes;
        this.screeningRules = screeningRules;
        this.clearanceRules = clearanceRules;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedPermitTypes();
        seedParcels();
        seedRegulatoryCodes();
        seedScreeningRules();
        seedClearanceRules();
    }

    private JsonNode load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readTree(in);
        } catch (Exception e) {
            log.warn("Seed file {} not loaded: {}", path, e.getMessage());
            return null;
        }
    }

    private void seedPermitTypes() {
        JsonNode arr = load("seed/permit-types.json");
        if (arr == null || !arr.isArray()) return;
        int added = 0;
        for (JsonNode n : arr) {
            String code = n.path("code").asText();
            if (code.isBlank() || permitTypes.existsByCode(code)) continue;
            PermitType pt = PermitType.builder()
                .code(code)
                .name(n.path("name").asText(code))
                .category(parseEnum(PermitCategory.class, n.path("category").asText(), PermitCategory.OTHER))
                .description(text(n, "description"))
                .formSchemaJson(n.has("formSchema") ? n.get("formSchema").toString() : "[]")
                .requiredDocsJson(n.has("requiredDocs") ? n.get("requiredDocs").toString() : "[]")
                .active(true)
                .build();
            permitTypes.save(pt);
            added++;
        }
        if (added > 0) log.info("Seeded {} permit types", added);
    }

    private void seedParcels() {
        JsonNode arr = load("seed/parcels.json");
        if (arr == null || !arr.isArray()) return;
        int added = 0;
        for (JsonNode n : arr) {
            String apn = n.path("apn").asText();
            if (apn.isBlank() || parcels.findByApn(apn).isPresent()) continue;
            String address = n.path("address").asText();
            Parcel p = Parcel.builder()
                .apn(apn)
                .address(address)
                .addressNormalized(normalize(address))
                .zone(text(n, "zone"))
                .generalPlanLandUse(text(n, "generalPlanLandUse"))
                .overlaysJson(n.has("overlays") ? n.get("overlays").toString() : "[]")
                .hazardZonesJson(n.has("hazardZones") ? n.get("hazardZones").toString() : "[]")
                .councilDistrict(n.has("councilDistrict") && n.get("councilDistrict").isInt()
                    ? n.get("councilDistrict").asInt() : null)
                .communityPlanArea(text(n, "communityPlanArea"))
                .latitude(n.has("latitude") ? n.get("latitude").asDouble() : null)
                .longitude(n.has("longitude") ? n.get("longitude").asDouble() : null)
                .build();
            parcels.save(p);
            added++;
        }
        if (added > 0) log.info("Seeded {} parcels", added);
    }

    private void seedRegulatoryCodes() {
        JsonNode arr = load("seed/regulatory-codes.json");
        if (arr == null || !arr.isArray()) return;
        int added = 0;
        for (JsonNode n : arr) {
            String externalId = n.path("externalId").asText();
            if (externalId.isBlank() || codes.findByExternalId(externalId).isPresent()) continue;
            RegulatoryCode c = RegulatoryCode.builder()
                .externalId(externalId)
                .jurisdiction(parseEnum(Jurisdiction.class, n.path("jurisdiction").asText(), Jurisdiction.CITY_LA))
                .codeType(n.path("codeType").asText("LAMC"))
                .section(n.path("section").asText(""))
                .title(n.path("title").asText(externalId))
                .summary(text(n, "summary"))
                .url(text(n, "url"))
                .tags(text(n, "tags"))
                .version(text(n, "version"))
                .build();
            codes.save(c);
            added++;
        }
        if (added > 0) log.info("Seeded {} regulatory codes", added);
    }

    private void seedScreeningRules() {
        JsonNode arr = load("seed/screening-rules.json");
        if (arr == null || !arr.isArray()) return;
        int added = 0;
        for (JsonNode n : arr) {
            String code = n.path("code").asText();
            if (code.isBlank() || screeningRules.existsByCode(code)) continue;
            ScreeningRule r = ScreeningRule.builder()
                .code(code)
                .name(n.path("name").asText(code))
                .category(parseEnum(FindingCategory.class, n.path("category").asText(), FindingCategory.GENERAL))
                .severity(parseEnum(Severity.class, n.path("severity").asText(), Severity.WARNING))
                .conditionJson(conditionOf(n))
                .message(n.path("message").asText(""))
                .recommendation(text(n, "recommendation"))
                .codeReference(text(n, "codeReference"))
                .codeUrl(text(n, "codeUrl"))
                .confidence(n.has("confidence") ? n.get("confidence").asInt(90) : 90)
                .appliesToPermitTypes(text(n, "appliesToPermitTypes"))
                .priority(n.has("priority") ? n.get("priority").asInt(100) : 100)
                .active(true)
                .build();
            screeningRules.save(r);
            added++;
        }
        if (added > 0) log.info("Seeded {} screening rules", added);
    }

    private void seedClearanceRules() {
        JsonNode arr = load("seed/clearance-rules.json");
        if (arr == null || !arr.isArray()) return;
        int added = 0;
        for (JsonNode n : arr) {
            String code = n.path("code").asText();
            if (code.isBlank() || clearanceRules.existsByCode(code)) continue;
            ClearanceRule r = ClearanceRule.builder()
                .code(code)
                .department(parseEnum(Department.class, n.path("department").asText(), Department.LADBS))
                .clearanceName(n.path("clearanceName").asText(code))
                .conditionJson(conditionOf(n))
                .reason(n.path("reason").asText(""))
                .submittalRequirementsJson(n.has("submittalRequirements")
                    ? n.get("submittalRequirements").toString() : "[]")
                .infoUrl(text(n, "infoUrl"))
                .confidence(n.has("confidence") ? n.get("confidence").asInt(80) : 80)
                .appliesToPermitTypes(text(n, "appliesToPermitTypes"))
                .priority(n.has("priority") ? n.get("priority").asInt(100) : 100)
                .active(true)
                .build();
            clearanceRules.save(r);
            added++;
        }
        if (added > 0) log.info("Seeded {} clearance rules", added);
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private String conditionOf(JsonNode n) {
        if (n.has("condition")) return n.get("condition").toString();
        return "{\"any\":[]}"; // never fires
    }

    private String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private String normalize(String address) {
        if (address == null) return "";
        return address.toUpperCase(Locale.ROOT).replaceAll("[.,]", " ").replaceAll("\\s+", " ").trim();
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
