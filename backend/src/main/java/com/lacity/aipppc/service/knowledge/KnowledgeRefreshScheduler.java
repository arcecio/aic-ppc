package com.lacity.aipppc.service.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled knowledgebase refresh (SOW 2.2.13 — "automated monthly updates of
 * reference information"; SOW 2.1.6 versioning/ongoing maintenance). Default cron
 * is 04:00 on the 1st of each month; off unless {@code KB_SCHEDULER_ENABLED=true}
 * (compose enables it). Re-runs the corpus sync (upsert by external_id) and the
 * embedding backfill; a process-local guard bounces overlapping invocations, the
 * same pattern as Blue's AdaSyncScheduler. In production the sync source expands
 * to per-source fetchers (amlegal LAMC, Title 24, LADBS Clearance Handbook) —
 * see docs/02-architecture.md.
 */
@Component
@ConditionalOnProperty(name = "app.kb.scheduler.enabled", havingValue = "true")
public class KnowledgeRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRefreshScheduler.class);

    private final KnowledgeSyncService syncService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public KnowledgeRefreshScheduler(KnowledgeSyncService syncService) {
        this.syncService = syncService;
        log.info("Knowledgebase refresh scheduler enabled");
    }

    @Scheduled(cron = "${app.kb.scheduler.cron:0 0 4 1 * *}")
    public void refresh() {
        if (!running.compareAndSet(false, true)) {
            log.info("Knowledgebase refresh already in progress; skipping this trigger");
            return;
        }
        try {
            var result = syncService.syncFromClasspath("SYSTEM");
            log.info("Scheduled knowledgebase refresh done: {} inserted, {} updated, {} embedded",
                result.inserted(), result.updated(), result.embedded());
        } catch (RuntimeException e) {
            log.error("Scheduled knowledgebase refresh failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }
}
