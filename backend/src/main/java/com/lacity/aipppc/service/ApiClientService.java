package com.lacity.aipppc.service;

import com.lacity.aipppc.dto.admin.ApiClientCreatedDto;
import com.lacity.aipppc.dto.admin.ApiClientDto;
import com.lacity.aipppc.dto.admin.CreateApiClientRequest;
import com.lacity.aipppc.exception.ApiException;
import com.lacity.aipppc.model.ApiClient;
import com.lacity.aipppc.model.User;
import com.lacity.aipppc.repository.ApiClientRepository;
import com.lacity.aipppc.security.ApiKeyHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages integration API clients (SOW 2.2.15; Appendix 3 §2.1). Creating a client
 * mints a random key, stores only its SHA-256 hash, and returns the raw key
 * exactly once. Revoking sets {@code active=false} so the key stops authenticating.
 */
@Service
public class ApiClientService {

    private final ApiClientRepository repository;
    private final AuditService auditService;

    public ApiClientService(ApiClientRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public ApiClientCreatedDto create(User admin, CreateApiClientRequest req) {
        String rawKey = ApiKeyHasher.generateRawKey();
        ApiClient client = ApiClient.builder()
            .name(req.name())
            .keyHash(ApiKeyHasher.sha256(rawKey))
            .keyPrefix(ApiKeyHasher.prefix(rawKey))
            .webhookUrl(req.webhookUrl())
            .active(true)
            .build();
        repository.save(client);
        auditService.recordUser(admin.getEmail(), "API_CLIENT_CREATED", "ApiClient",
            client.getId().toString(), req.name());
        return new ApiClientCreatedDto(ApiClientDto.from(client), rawKey);
    }

    public List<ApiClientDto> list() {
        return repository.findAll().stream().map(ApiClientDto::from).toList();
    }

    @Transactional
    public void revoke(User admin, UUID id) {
        ApiClient client = repository.findById(id)
            .orElseThrow(() -> ApiException.notFound("API client not found"));
        client.setActive(false);
        repository.save(client);
        auditService.recordUser(admin.getEmail(), "API_CLIENT_REVOKED", "ApiClient", id.toString(), client.getName());
    }
}
