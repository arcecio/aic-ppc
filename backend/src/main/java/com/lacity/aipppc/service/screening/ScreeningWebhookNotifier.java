package com.lacity.aipppc.service.screening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lacity.aipppc.model.ApiClient;
import com.lacity.aipppc.model.PreCheckRun;
import com.lacity.aipppc.model.enums.TriggeredBy;
import com.lacity.aipppc.repository.ApiClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fires event-driven webhook callbacks when a screening run finishes, so an
 * originating City portal (e.g. ePlanLA) is notified the moment analysis is
 * complete rather than polling (SOW 2.2.14; Appendix 3 §2.1.4 — "asynchronous
 * requests and ... webhooks or event-driven callbacks"). Best-effort and
 * non-blocking: only runs triggered via the integration API notify, and delivery
 * failures are logged, never propagated into the screening path.
 */
@Component
public class ScreeningWebhookNotifier {

    private static final Logger log = LoggerFactory.getLogger(ScreeningWebhookNotifier.class);

    private final ApiClientRepository apiClientRepository;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ScreeningWebhookNotifier(ApiClientRepository apiClientRepository, ObjectMapper mapper) {
        this.apiClientRepository = apiClientRepository;
        this.mapper = mapper;
    }

    public void notifyCompleted(PreCheckRun run) {
        if (run.getTriggeredBy() != TriggeredBy.API) return;
        ObjectNode payload = mapper.createObjectNode();
        payload.put("event", "screening.completed");
        payload.put("runId", run.getId().toString());
        payload.put("projectId", run.getProject().getId().toString());
        payload.put("universalProjectId", run.getProject().getUniversalProjectId());
        payload.put("status", run.getStatus().name());
        if (run.getReadinessStatus() != null) payload.put("readinessStatus", run.getReadinessStatus().name());
        if (run.getReadinessScore() != null) payload.put("readinessScore", run.getReadinessScore());
        payload.put("findingCount", run.getFindingCount());
        payload.put("clearanceCount", run.getClearanceCount());

        for (ApiClient client : apiClientRepository.findAll()) {
            if (!client.isActive() || client.getWebhookUrl() == null || client.getWebhookUrl().isBlank()) continue;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(client.getWebhookUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
                http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> { log.warn("Webhook to {} failed: {}",
                        client.getWebhookUrl(), ex.getMessage()); return null; });
            } catch (Exception e) {
                log.warn("Webhook build failed for {}: {}", client.getName(), e.getMessage());
            }
        }
    }
}
