package com.lacity.aipppc.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record CreateApiClientRequest(@NotBlank String name, String webhookUrl) {}
