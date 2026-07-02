package com.lacity.aipppc.security;

import com.lacity.aipppc.model.ApiClient;
import com.lacity.aipppc.repository.ApiClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates integration clients (ePlanLA and other City systems) by the
 * {@code X-API-Key} header (Appendix 3 §3.1.1 — "authentication ... for all
 * transactions"). Keys are stored only as SHA-256 hashes; the raw key is shown
 * once at creation. A successful match sets a principal with ROLE_API_CLIENT and
 * stashes the {@link ApiClient} id as the request attribute {@code apiClientId}
 * so downstream controllers can attribute audit-log entries.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    public static final String HEADER = "X-API-Key";
    public static final String CLIENT_ID_ATTR = "apiClientId";

    private final ApiClientRepository apiClientRepository;

    public ApiKeyAuthFilter(ApiClientRepository apiClientRepository) {
        this.apiClientRepository = apiClientRepository;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain
    ) throws ServletException, IOException {
        final String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()
            || SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String hash = ApiKeyHasher.sha256(rawKey);
            Optional<ApiClient> match = apiClientRepository.findByKeyHashAndActiveTrue(hash);
            if (match.isPresent()) {
                ApiClient client = match.get();
                var authToken = new UsernamePasswordAuthenticationToken(
                    client.getName(), null, List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                request.setAttribute(CLIENT_ID_ATTR, client.getId());
                client.setLastUsedAt(Instant.now());
                apiClientRepository.save(client);
            } else {
                log.debug("Unknown API key presented (prefix {})", ApiKeyHasher.prefix(rawKey));
            }
        } catch (RuntimeException e) {
            log.debug("API key auth failed: {}", e.getMessage());
        }
        chain.doFilter(request, response);
    }
}
