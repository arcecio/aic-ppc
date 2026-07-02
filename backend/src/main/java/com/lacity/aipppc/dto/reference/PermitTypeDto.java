package com.lacity.aipppc.dto.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.lacity.aipppc.model.PermitType;

import java.util.UUID;
import java.util.function.Function;

/**
 * Permit type with its dynamic form schema + required-document checklist, so the
 * applicant UI can render the correct intake form (SOW 2.2.1). {@code formSchema}
 * and {@code requiredDocs} are passed through as raw JSON nodes.
 */
public record PermitTypeDto(
    UUID id,
    String code,
    String name,
    String category,
    String description,
    JsonNode formSchema,
    JsonNode requiredDocs,
    boolean active
) {
    public static PermitTypeDto from(PermitType p, Function<String, JsonNode> parse) {
        return new PermitTypeDto(p.getId(), p.getCode(), p.getName(), p.getCategory().name(),
            p.getDescription(), parse.apply(p.getFormSchemaJson()), parse.apply(p.getRequiredDocsJson()),
            p.isActive());
    }
}
