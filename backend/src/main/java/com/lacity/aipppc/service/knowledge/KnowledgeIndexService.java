package com.lacity.aipppc.service.knowledge;

import com.lacity.aipppc.model.RegulatoryCode;
import com.lacity.aipppc.repository.RegulatoryCodeRepository;
import com.lacity.aipppc.service.embedding.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Maintains the vector index over the regulatory knowledgebase: finds sections
 * with no embedding and embeds them in batches via the TEI sidecar. A no-op when
 * the sidecar is unavailable — rows simply stay lexical-only until the next sync
 * (SOW 2.1.6: updates integrate without disrupting the application).
 */
@Service
public class KnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);
    private static final int BATCH_SIZE = 16;

    private final RegulatoryCodeRepository repository;
    private final EmbeddingService embeddingService;

    public KnowledgeIndexService(RegulatoryCodeRepository repository, EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    /** Embeds all rows with a NULL embedding. Returns the number embedded. */
    @Transactional
    public int embedMissing() {
        if (!embeddingService.available()) return 0;
        List<RegulatoryCode> missing = repository.findWithoutEmbedding();
        if (missing.isEmpty()) return 0;

        int embedded = 0;
        for (int from = 0; from < missing.size(); from += BATCH_SIZE) {
            List<RegulatoryCode> batch = missing.subList(from, Math.min(from + BATCH_SIZE, missing.size()));
            List<float[]> vectors = embeddingService.embedPassages(batch.stream().map(this::passageText).toList());
            if (vectors.size() != batch.size()) {
                // Sidecar went away mid-run; stop quietly, the rest stays lexical-only.
                break;
            }
            for (int i = 0; i < batch.size(); i++) {
                repository.updateEmbedding(batch.get(i).getId(), EmbeddingService.toVectorLiteral(vectors.get(i)));
                embedded++;
            }
        }
        if (embedded > 0) {
            log.info("Embedded {} regulatory code section(s) ({} total indexed)", embedded, repository.countEmbedded());
        }
        return embedded;
    }

    public long embeddedCount() {
        return repository.countEmbedded();
    }

    /** Passage text fed to e5: code family + section + title + summary + tags. */
    private String passageText(RegulatoryCode c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getCodeType()).append(' ').append(c.getSection()).append(". ").append(c.getTitle());
        if (c.getSummary() != null) sb.append(". ").append(c.getSummary());
        if (c.getTags() != null) sb.append(" Keywords: ").append(c.getTags());
        return sb.toString();
    }
}
