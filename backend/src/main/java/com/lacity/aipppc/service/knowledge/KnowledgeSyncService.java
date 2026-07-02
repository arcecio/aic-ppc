package com.lacity.aipppc.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacity.aipppc.model.RegulatoryCode;
import com.lacity.aipppc.model.enums.Jurisdiction;
import com.lacity.aipppc.repository.RegulatoryCodeRepository;
import com.lacity.aipppc.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Ingestion path for the regulatory knowledgebase (SOW 2.1.1 / 2.1.6). Every
 * entry is <b>upserted by {@code external_id}</b> — new sections insert, existing
 * sections update in place (title/summary/url/tags/version) — so re-running a
 * sync applies amendments without duplicating rows, exactly like Blue's ADA
 * importer. Sources:
 * <ul>
 *   <li>the bundled classpath corpus ({@code seed/regulatory-codes.json}), the
 *       City-approved snapshot shipped with each release;</li>
 *   <li>the admin bulk-import endpoint, which is how updated corpus drops
 *       (e.g. parsed amlegal LAMC / Title 24 / Clearance Handbook extracts)
 *       are applied between releases without a code change.</li>
 * </ul>
 * All writes are audited (SOW 2.2.13 — learning/updates must be transparent and
 * auditable; changes remain subject to City review).
 */
@Service
public class KnowledgeSyncService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncService.class);
    private static final String CLASSPATH_CORPUS = "seed/regulatory-codes.json";

    private final RegulatoryCodeRepository repository;
    private final KnowledgeIndexService indexService;
    private final ObjectMapper mapper;
    private final AuditService auditService;

    public KnowledgeSyncService(RegulatoryCodeRepository repository,
                                KnowledgeIndexService indexService,
                                ObjectMapper mapper,
                                AuditService auditService) {
        this.repository = repository;
        this.indexService = indexService;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    /** One corpus entry, matching the seed-file schema. */
    public record CodeEntry(String externalId, String jurisdiction, String codeType, String section,
                            String title, String summary, String url, String tags, String version) {}

    public record SyncResult(int inserted, int updated, int unchanged, int embedded) {
        public int total() { return inserted + updated + unchanged; }
    }

    /** Re-syncs from the bundled classpath corpus, then backfills embeddings. */
    @Transactional
    public SyncResult syncFromClasspath(String actorLabel) {
        JsonNode arr = readClasspath();
        if (arr == null || !arr.isArray()) {
            log.warn("Classpath corpus {} missing or malformed; sync skipped", CLASSPATH_CORPUS);
            return new SyncResult(0, 0, 0, 0);
        }
        return applyCorpus(toEntries(arr), actorLabel, "classpath:" + CLASSPATH_CORPUS);
    }

    /** Bulk upsert from an externally supplied corpus (admin import endpoint). */
    @Transactional
    public SyncResult importCorpus(List<CodeEntry> entries, String actorLabel) {
        return applyCorpus(entries, actorLabel, "admin-import");
    }

    private SyncResult applyCorpus(List<CodeEntry> entries, String actorLabel, String source) {
        int inserted = 0, updated = 0, unchanged = 0;
        for (CodeEntry e : entries) {
            if (e.externalId() == null || e.externalId().isBlank()) continue;
            RegulatoryCode existing = repository.findByExternalId(e.externalId().trim()).orElse(null);
            if (existing == null) {
                repository.save(toEntity(e));
                inserted++;
            } else if (apply(existing, e)) {
                repository.save(existing);
                updated++;
            } else {
                unchanged++;
            }
        }
        int embedded = indexService.embedMissing();
        SyncResult result = new SyncResult(inserted, updated, unchanged, embedded);
        auditService.record("SYSTEM".equals(actorLabel) ? "SYSTEM" : "USER", actorLabel, actorLabel,
            "KNOWLEDGEBASE_SYNC", "RegulatoryCode", source,
            "inserted=" + inserted + " updated=" + updated + " unchanged=" + unchanged
                + " embedded=" + embedded);
        if (inserted + updated > 0) {
            log.info("Knowledgebase sync ({}): {} inserted, {} updated, {} unchanged, {} embedded",
                source, inserted, updated, unchanged, embedded);
        }
        return result;
    }

    /** Copies corpus fields onto the entity; true when anything actually changed. */
    private boolean apply(RegulatoryCode target, CodeEntry e) {
        boolean changed = false;
        changed |= set(target.getJurisdiction().name(), parseJurisdiction(e.jurisdiction()).name(),
            v -> target.setJurisdiction(Jurisdiction.valueOf(v)));
        changed |= set(target.getCodeType(), nz(e.codeType(), "LAMC"), target::setCodeType);
        changed |= set(target.getSection(), nz(e.section(), ""), target::setSection);
        changed |= set(target.getTitle(), nz(e.title(), e.externalId()), target::setTitle);
        changed |= set(target.getSummary(), e.summary(), target::setSummary);
        changed |= set(target.getUrl(), e.url(), target::setUrl);
        changed |= set(target.getTags(), e.tags(), target::setTags);
        changed |= set(target.getVersion(), e.version(), target::setVersion);
        return changed;
    }

    private boolean set(String current, String next, java.util.function.Consumer<String> setter) {
        if (Objects.equals(current, next)) return false;
        setter.accept(next);
        return true;
    }

    private RegulatoryCode toEntity(CodeEntry e) {
        return RegulatoryCode.builder()
            .externalId(e.externalId().trim())
            .jurisdiction(parseJurisdiction(e.jurisdiction()))
            .codeType(nz(e.codeType(), "LAMC"))
            .section(nz(e.section(), ""))
            .title(nz(e.title(), e.externalId()))
            .summary(e.summary())
            .url(e.url())
            .tags(e.tags())
            .version(e.version())
            .build();
    }

    private List<CodeEntry> toEntries(JsonNode arr) {
        java.util.ArrayList<CodeEntry> out = new java.util.ArrayList<>();
        for (JsonNode n : arr) {
            out.add(new CodeEntry(
                text(n, "externalId"), text(n, "jurisdiction"), text(n, "codeType"),
                text(n, "section"), text(n, "title"), text(n, "summary"),
                text(n, "url"), text(n, "tags"), text(n, "version")));
        }
        return out;
    }

    private JsonNode readClasspath() {
        try (InputStream in = new ClassPathResource(CLASSPATH_CORPUS).getInputStream()) {
            return mapper.readTree(in);
        } catch (Exception e) {
            return null;
        }
    }

    private Jurisdiction parseJurisdiction(String raw) {
        if (raw == null || raw.isBlank()) return Jurisdiction.CITY_LA;
        try {
            return Jurisdiction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Jurisdiction.CITY_LA;
        }
    }

    private String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private String nz(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
