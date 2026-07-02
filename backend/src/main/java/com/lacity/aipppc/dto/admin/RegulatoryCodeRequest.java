package com.lacity.aipppc.dto.admin;

import com.lacity.aipppc.service.knowledge.KnowledgeSyncService;
import jakarta.validation.constraints.NotBlank;

/** Create/update/import payload for a knowledgebase entry (matches the corpus schema). */
public record RegulatoryCodeRequest(
    @NotBlank String externalId,
    String jurisdiction,
    String codeType,
    String section,
    @NotBlank String title,
    String summary,
    String url,
    String tags,
    String version
) {
    public KnowledgeSyncService.CodeEntry toEntry() {
        return new KnowledgeSyncService.CodeEntry(externalId, jurisdiction, codeType, section,
            title, summary, url, tags, version);
    }
}
