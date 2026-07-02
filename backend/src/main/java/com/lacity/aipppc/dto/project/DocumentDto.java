package com.lacity.aipppc.dto.project;

import com.lacity.aipppc.model.Document;

import java.time.Instant;
import java.util.UUID;

public record DocumentDto(
    UUID id,
    String originalName,
    String fileType,
    long sizeBytes,
    String docCategory,
    String scanStatus,
    String scanDetail,
    int version,
    int extractedTextChars,
    Instant uploadedAt
) {
    public static DocumentDto from(Document d) {
        return new DocumentDto(d.getId(), d.getOriginalName(), d.getFileType(), d.getSizeBytes(),
            d.getDocCategory(), d.getScanStatus().name(), d.getScanDetail(), d.getVersion(),
            d.getExtractedTextChars(), d.getUploadedAt());
    }
}
