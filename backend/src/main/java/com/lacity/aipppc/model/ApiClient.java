package com.lacity.aipppc.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered machine integration (e.g. ePlanLA, LACPS) that calls the
 * {@code /api/v1} surface with an {@code X-API-Key}. Only the SHA-256 hash of the
 * key is stored. {@code webhookUrl} receives async screening-complete callbacks
 * (Appendix 3 §2.1.4).
 */
@Entity
@Table(name = "api_clients")
public class ApiClient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    /** Non-secret prefix shown in the UI/audit log to identify the key. */
    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ApiClient() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ApiClient c = new ApiClient();
        public Builder id(UUID id) { c.id = id; return this; }
        public Builder name(String name) { c.name = name; return this; }
        public Builder keyHash(String keyHash) { c.keyHash = keyHash; return this; }
        public Builder keyPrefix(String keyPrefix) { c.keyPrefix = keyPrefix; return this; }
        public Builder webhookUrl(String webhookUrl) { c.webhookUrl = webhookUrl; return this; }
        public Builder active(boolean active) { c.active = active; return this; }
        public ApiClient build() { return c; }
    }
}
