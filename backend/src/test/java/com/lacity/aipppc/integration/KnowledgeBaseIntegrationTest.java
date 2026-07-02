package com.lacity.aipppc.integration;

import com.lacity.aipppc.model.RegulatoryCode;
import com.lacity.aipppc.repository.RegulatoryCodeRepository;
import com.lacity.aipppc.service.embedding.EmbeddingService;
import com.lacity.aipppc.service.knowledge.KnowledgeSyncService;
import com.lacity.aipppc.service.screening.RegulatoryKnowledgeService;
import com.lacity.aipppc.support.TestPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Knowledgebase pipeline against a real pgvector Postgres: V4 migration applies,
 * corpus sync upserts by external_id (SOW 2.1.6 amendments update in place),
 * admin import creates + updates, and hybrid retrieval degrades gracefully to
 * lexical-only when the embedding sidecar is absent (as it is in CI).
 */
@SpringBootTest(properties = {
    "app.ai.provider=none",
    "app.bootstrap.admin-email=",
    // Point the embedding sidecar at a closed port: availability probing must
    // fail fast and the retriever must degrade to lexical-only.
    "app.embedding.url=http://localhost:59999"
})
@Testcontainers
class KnowledgeBaseIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(TestPostgres.IMAGE);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.storage.base-path", () -> System.getProperty("java.io.tmpdir") + "/aip-kb-test-storage");
    }

    @Autowired RegulatoryCodeRepository repository;
    @Autowired KnowledgeSyncService syncService;
    @Autowired RegulatoryKnowledgeService knowledgeService;
    @Autowired EmbeddingService embeddingService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void pgvectorExtensionAndEmbeddingColumnExist() {
        Integer ext = jdbc.queryForObject(
            "select count(*) from pg_extension where extname = 'vector'", Integer.class);
        assertThat(ext).isEqualTo(1);
        Integer col = jdbc.queryForObject(
            "select count(*) from information_schema.columns "
                + "where table_name = 'regulatory_codes' and column_name = 'embedding'", Integer.class);
        assertThat(col).isEqualTo(1);
    }

    @Test
    void classpathSyncUpsertsInPlaceInsteadOfSkipping() {
        // Seeded on boot; drift a row as if an amendment note were made locally.
        RegulatoryCode row = repository.findAll().get(0);
        String externalId = row.getExternalId();
        String originalTitle = row.getTitle();
        row.setTitle("DRIFTED TITLE");
        repository.save(row);

        KnowledgeSyncService.SyncResult result = syncService.syncFromClasspath("test");

        assertThat(result.updated()).isGreaterThanOrEqualTo(1);
        assertThat(repository.findByExternalId(externalId).orElseThrow().getTitle())
            .isEqualTo(originalTitle); // corpus is the source of truth — updated in place
        assertThat(result.inserted()).isZero(); // no duplicates on re-sync
    }

    @Test
    void adminImportCreatesThenUpdatesByExternalId() {
        var entry = new KnowledgeSyncService.CodeEntry(
            "TEST-AB2011", "STATE_CA", "State Housing Law", "AB 2011",
            "Affordable Housing on Commercial Zoned Land",
            "Allows streamlined ministerial approval of affordable housing on commercially zoned sites.",
            "https://leginfo.legislature.ca.gov/", "housing affordable commercial streamlined", "2024");

        var first = syncService.importCorpus(List.of(entry), "test");
        assertThat(first.inserted()).isEqualTo(1);

        var amended = new KnowledgeSyncService.CodeEntry(
            "TEST-AB2011", "STATE_CA", "State Housing Law", "AB 2011",
            "Affordable Housing on Commercial Zoned Land (as amended)",
            "Allows streamlined ministerial approval of affordable housing on commercially zoned sites.",
            "https://leginfo.legislature.ca.gov/", "housing affordable commercial streamlined", "2026");
        var second = syncService.importCorpus(List.of(amended), "test");

        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isEqualTo(1);
        RegulatoryCode updated = repository.findByExternalId("TEST-AB2011").orElseThrow();
        assertThat(updated.getTitle()).contains("as amended");
        assertThat(updated.getVersion()).isEqualTo("2026");
    }

    @Test
    void retrievalDegradesToLexicalOnlyWithoutEmbeddingSidecar() {
        assertThat(embeddingService.embedQuery("hillside grading")).isEmpty();

        String context = knowledgeService.buildContext(Map.of(
            "permitCategory", "MULTI_FAMILY",
            "zone", "RE15-1-H",
            "overlays", List.of("Hillside", "Very High Fire Hazard Severity Zone"),
            "hazards", List.of("Landslide"),
            "text", "new building with grading on a hillside lot near a fire zone"));

        // Lexical arm alone must still produce a non-empty, cited context block.
        assertThat(context).isNotBlank();
        assertThat(context).containsAnyOf("LAMC", "Title 24", "CBC", "CALGreen");
    }

    @Test
    void embeddedCountReflectsSidecarAbsence() {
        // No sidecar in tests → nothing embedded, and that's a supported state.
        assertThat(repository.countEmbedded()).isZero();
        assertThat(repository.findWithoutEmbedding()).isNotEmpty();
    }
}
