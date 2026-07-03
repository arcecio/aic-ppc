package com.lacity.aipppc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacity.aipppc.model.AuditLog;
import com.lacity.aipppc.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** IP capture and before/after snapshot behavior of the audit writer. */
class AuditServiceTest {

    private AuditLogRepository repository;
    private AuditService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        service = new AuditService(repository, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void capturesRemoteAddrOfCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.recordUser("a@b.c", "USER_LOGIN", "User", "id-1", null);

        assertThat(captureSaved().getIpAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void prefersFirstForwardedForHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.9, 10.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.recordUser("a@b.c", "USER_LOGIN", "User", "id-1", null);

        assertThat(captureSaved().getIpAddress()).isEqualTo("198.51.100.9");
    }

    @Test
    void ipIsNullOutsideRequestThread() {
        service.recordSystem("KNOWLEDGEBASE_SYNC", "RegulatoryCode", "classpath", "n=1");

        assertThat(captureSaved().getIpAddress()).isNull();
    }

    @Test
    void serializesSnapshotsToJson() {
        service.recordUser("admin@b.c", "USER_ROLE_CHANGED", "User", "id-1", "role=ADMIN",
            Map.of("role", "STAFF"), Map.of("role", "ADMIN"));

        AuditLog saved = captureSaved();
        assertThat(saved.getBeforeJson()).isEqualTo("{\"role\":\"STAFF\"}");
        assertThat(saved.getAfterJson()).isEqualTo("{\"role\":\"ADMIN\"}");
    }

    @Test
    void snapshotsAreNullWhenOmitted() {
        service.recordUser("a@b.c", "USER_LOGIN", "User", "id-1", null);

        AuditLog saved = captureSaved();
        assertThat(saved.getBeforeJson()).isNull();
        assertThat(saved.getAfterJson()).isNull();
    }

    @Test
    void repositoryFailureNeverPropagates() {
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        service.recordUser("a@b.c", "USER_LOGIN", "User", "id-1", null);
        // no exception — auditing must not break the audited action
    }
}
