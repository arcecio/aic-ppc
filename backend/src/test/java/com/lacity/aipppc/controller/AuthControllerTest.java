package com.lacity.aipppc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacity.aipppc.config.SecurityConfig;
import com.lacity.aipppc.dto.auth.AuthResponse;
import com.lacity.aipppc.dto.auth.UserDto;
import com.lacity.aipppc.repository.ApiClientRepository;
import com.lacity.aipppc.security.ApiKeyAuthFilter;
import com.lacity.aipppc.security.JwtAuthFilter;
import com.lacity.aipppc.security.JwtUtil;
import com.lacity.aipppc.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-slice test running against the real (CSRF-disabled, stateless)
 * SecurityConfig + JWT/API-key filters, mirroring the Blue pattern.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, ApiKeyAuthFilter.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean UserService userService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserDetailsService userDetailsService;
    @MockBean ApiClientRepository apiClientRepository;

    private AuthResponse sampleAuth() {
        UserDto dto = new UserDto(UUID.randomUUID(), "arch@example.com", "Ada", "APPLICANT",
            null, true, Instant.now());
        return new AuthResponse("jwt-token", dto);
    }

    @Test
    void registerReturnsToken() throws Exception {
        when(userService.register(any())).thenReturn(sampleAuth());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of(
                    "email", "arch@example.com", "password", "password123", "name", "Ada"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt-token"))
            .andExpect(jsonPath("$.user.email").value("arch@example.com"));
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(userService.login(any())).thenReturn(sampleAuth());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of(
                    "email", "arch@example.com", "password", "password123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of(
                    "email", "not-an-email", "password", "password123", "name", "Ada"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().is4xxClientError());
    }
}
