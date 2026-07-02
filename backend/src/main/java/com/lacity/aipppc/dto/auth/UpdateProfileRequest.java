package com.lacity.aipppc.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
    @NotBlank String name,
    String organization
) {}
