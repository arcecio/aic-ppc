package com.lacity.aipppc.service;

import com.lacity.aipppc.model.AuditLog;
import com.lacity.aipppc.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Writes the append-only audit trail required for auditability of "all
 * transactions" (SOW 2.2.14; Appendix 3 §2.1.9) and AI governance/oversight
 * (SOW 4.2). Failures to log never propagate — auditing must not break the
 * action being audited.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String actorType, String actorId, String actorLabel,
                       String action, String entityType, String entityId, String detail) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorType(actorType);
            entry.setActorId(actorId);
            entry.setActorLabel(actorLabel);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setDetail(detail);
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Audit write failed for action {}: {}", action, e.getMessage());
        }
    }

    /** Convenience for actions performed by an authenticated user. */
    public void recordUser(String email, String action, String entityType, String entityId, String detail) {
        record("USER", email, email, action, entityType, entityId, detail);
    }

    public void recordApi(String clientId, String action, String entityType, String entityId, String detail) {
        record("API_CLIENT", clientId, clientId, action, entityType, entityId, detail);
    }

    public void recordSystem(String action, String entityType, String entityId, String detail) {
        record("SYSTEM", null, "system", action, entityType, entityId, detail);
    }
}
