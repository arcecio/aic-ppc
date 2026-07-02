package com.lacity.aipppc.dto.admin;

import com.lacity.aipppc.model.RegulatoryCode;

import java.time.Instant;
import java.util.UUID;

/** Full knowledgebase entry for the admin console. */
public record RegulatoryCodeDto(
    UUID id,
    String externalId,
    String jurisdiction,
    String codeType,
    String section,
    String title,
    String summary,
    String url,
    String tags,
    String version,
    Instant updatedAt
) {
    public static RegulatoryCodeDto from(RegulatoryCode c) {
        return new RegulatoryCodeDto(c.getId(), c.getExternalId(), c.getJurisdiction().name(),
            c.getCodeType(), c.getSection(), c.getTitle(), c.getSummary(), c.getUrl(),
            c.getTags(), c.getVersion(), c.getUpdatedAt());
    }
}
