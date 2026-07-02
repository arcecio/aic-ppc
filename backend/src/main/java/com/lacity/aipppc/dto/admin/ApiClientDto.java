package com.lacity.aipppc.dto.admin;

import com.lacity.aipppc.model.ApiClient;

import java.time.Instant;
import java.util.UUID;

/** Integration client metadata (never includes the secret key). */
public record ApiClientDto(
    UUID id, String name, String keyPrefix, String webhookUrl,
    boolean active, Instant lastUsedAt, Instant createdAt
) {
    public static ApiClientDto from(ApiClient c) {
        return new ApiClientDto(c.getId(), c.getName(), c.getKeyPrefix(), c.getWebhookUrl(),
            c.isActive(), c.getLastUsedAt(), c.getCreatedAt());
    }
}
