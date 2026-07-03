package com.lacity.aipppc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacity.aipppc.model.AuditLog;
import com.lacity.aipppc.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the append-only audit trail required for auditability of "all
 * transactions" (SOW 2.2.14; Appendix 3 §2.1.9) and AI governance/oversight
 * (SOW 4.2). Failures to log never propagate — auditing must not break the
 * action being audited. The client IP of the current HTTP request is captured
 * automatically; mutations can attach before/after entity snapshots for
 * field-level change forensics.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int IP_MAX_LENGTH = 64;

    private final AuditLogRepository repository;
    private final ObjectMapper mapper;

    public AuditService(AuditLogRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public void record(String actorType, String actorId, String actorLabel,
                       String action, String entityType, String entityId, String detail) {
        record(actorType, actorId, actorLabel, action, entityType, entityId, detail, null, null);
    }

    /**
     * Full form with change snapshots. {@code before}/{@code after} are serialized
     * to JSON (Strings are stored as-is, assumed to already be JSON); pass null for
     * whichever side does not exist (create/delete) or for non-mutating actions.
     */
    public void record(String actorType, String actorId, String actorLabel,
                       String action, String entityType, String entityId, String detail,
                       Object before, Object after) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorType(actorType);
            entry.setActorId(actorId);
            entry.setActorLabel(actorLabel);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetail(detail);
            entry.setIpAddress(currentRequestIp());
            entry.setBeforeJson(toJson(before));
            entry.setAfterJson(toJson(after));
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Audit write failed for action {}: {}", action, e.getMessage());
        }
    }

    /** Convenience for actions performed by an authenticated user. */
    public void recordUser(String email, String action, String entityType, String entityId, String detail) {
        record("USER", email, email, action, entityType, entityId, detail);
    }

    /** User action with before/after snapshots of the mutated entity. */
    public void recordUser(String email, String action, String entityType, String entityId, String detail,
                           Object before, Object after) {
        record("USER", email, email, action, entityType, entityId, detail, before, after);
    }

    public void recordApi(String clientId, String action, String entityType, String entityId, String detail) {
        record("API_CLIENT", clientId, clientId, action, entityType, entityId, detail);
    }

    public void recordSystem(String action, String entityType, String entityId, String detail) {
        record("SYSTEM", null, "system", action, entityType, entityId, detail);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Audit snapshot serialization failed: {}", e.getMessage());
            return null;
        }
    }

    /** Client IP of the current HTTP request; null when writing outside a request thread. */
    private String currentRequestIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded == null || forwarded.isBlank())
            ? request.getRemoteAddr()
            : forwarded.split(",")[0].trim();
        if (ip == null || ip.isBlank()) return null;
        return ip.length() <= IP_MAX_LENGTH ? ip : ip.substring(0, IP_MAX_LENGTH);
    }
}
