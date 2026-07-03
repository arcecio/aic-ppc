package com.lacity.aipppc.controller;

import com.lacity.aipppc.dto.admin.RegulatoryCodeDto;
import com.lacity.aipppc.dto.admin.RegulatoryCodeRequest;
import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.RegulatoryCode;
import com.lacity.aipppc.repository.RegulatoryCodeRepository;
import com.lacity.aipppc.service.AuditService;
import com.lacity.aipppc.service.UserService;
import com.lacity.aipppc.service.embedding.EmbeddingService;
import com.lacity.aipppc.service.knowledge.KnowledgeIndexService;
import com.lacity.aipppc.service.knowledge.KnowledgeSyncService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin console for the regulatory knowledgebase (SOW 2.1.1 / 2.1.6 / 2.2.13):
 * CRUD over individual code sections, bulk corpus import (upsert by
 * external_id — the between-release update path for amendments), a manual sync
 * trigger, and an index/status view. ADMIN role required; every write is audited
 * so knowledgebase changes remain transparent and reviewable by the City.
 */
@RestController
@RequestMapping("/api/admin/regulatory-codes")
public class AdminKnowledgeController {

    private final RegulatoryCodeRepository repository;
    private final KnowledgeSyncService syncService;
    private final KnowledgeIndexService indexService;
    private final EmbeddingService embeddingService;
    private final AuditService auditService;
    private final UserService userService;

    @Value("${app.kb.scheduler.enabled:false}")
    private boolean schedulerEnabled;
    @Value("${app.kb.scheduler.cron:0 0 4 1 * *}")
    private String schedulerCron;

    public AdminKnowledgeController(RegulatoryCodeRepository repository,
                                    KnowledgeSyncService syncService,
                                    KnowledgeIndexService indexService,
                                    EmbeddingService embeddingService,
                                    AuditService auditService,
                                    UserService userService) {
        this.repository = repository;
        this.syncService = syncService;
        this.indexService = indexService;
        this.embeddingService = embeddingService;
        this.auditService = auditService;
        this.userService = userService;
    }

    private String actor(UserDetails ud) {
        return userService.requireUser(ud.getUsername()).getEmail();
    }

    @GetMapping
    public List<RegulatoryCodeDto> list(@RequestParam(value = "q", required = false) String q) {
        List<RegulatoryCode> rows = (q == null || q.isBlank())
            ? repository.findAll() : repository.search(q.trim());
        return rows.stream().map(RegulatoryCodeDto::from).toList();
    }

    @PostMapping
    public RegulatoryCodeDto create(@AuthenticationPrincipal UserDetails ud,
                                    @Valid @RequestBody RegulatoryCodeRequest req) {
        if (repository.findByExternalId(req.externalId().trim()).isPresent()) {
            throw ApiException.conflict("A code entry with externalId " + req.externalId()
                + " already exists — use PUT or the import endpoint to update it");
        }
        // importCorpus writes the REGCODE_CREATED audit entry (with an after-snapshot).
        syncService.importCorpus(List.of(req.toEntry()), actor(ud));
        RegulatoryCode saved = repository.findByExternalId(req.externalId().trim())
            .orElseThrow(() -> ApiException.badRequest("Create failed"));
        return RegulatoryCodeDto.from(saved);
    }

    @PutMapping("/{id}")
    public RegulatoryCodeDto update(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id,
                                    @Valid @RequestBody RegulatoryCodeRequest req) {
        RegulatoryCode existing = repository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Code entry not found"));
        if (!existing.getExternalId().equals(req.externalId().trim())) {
            throw ApiException.badRequest("externalId is immutable (" + existing.getExternalId() + ")");
        }
        // importCorpus writes the REGCODE_UPDATED audit entry (with before/after snapshots).
        syncService.importCorpus(List.of(req.toEntry()), actor(ud));
        return RegulatoryCodeDto.from(repository.findById(id).orElseThrow());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails ud, @PathVariable UUID id) {
        RegulatoryCode existing = repository.findById(id)
            .orElseThrow(() -> ApiException.notFound("Code entry not found"));
        RegulatoryCodeDto before = RegulatoryCodeDto.from(existing);
        repository.delete(existing);
        auditService.recordUser(actor(ud), "REGCODE_DELETED", "RegulatoryCode", id.toString(),
            existing.getExternalId(), before, null);
        return ResponseEntity.noContent().build();
    }

    /** Bulk corpus import — upsert by externalId. The between-release amendment path. */
    @PostMapping("/import")
    public Map<String, Object> importCorpus(@AuthenticationPrincipal UserDetails ud,
                                            @RequestBody List<@Valid RegulatoryCodeRequest> entries) {
        var result = syncService.importCorpus(entries.stream().map(RegulatoryCodeRequest::toEntry).toList(),
            actor(ud));
        return syncResultBody(result);
    }

    /** Manual re-sync from the bundled corpus + embedding backfill. */
    @PostMapping("/sync")
    public Map<String, Object> sync(@AuthenticationPrincipal UserDetails ud) {
        var result = syncService.syncFromClasspath(actor(ud));
        return syncResultBody(result);
    }

    /** Index/observability: totals, embedded count, embedding + scheduler config. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalEntries", repository.count());
        body.put("embeddedEntries", indexService.embeddedCount());
        body.put("embeddingProvider", embeddingService.providerType());
        body.put("embeddingAvailable", embeddingService.available());
        body.put("schedulerEnabled", schedulerEnabled);
        body.put("schedulerCron", schedulerCron);
        return body;
    }

    private Map<String, Object> syncResultBody(KnowledgeSyncService.SyncResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inserted", result.inserted());
        body.put("updated", result.updated());
        body.put("unchanged", result.unchanged());
        body.put("embedded", result.embedded());
        body.put("total", result.total());
        return body;
    }
}
