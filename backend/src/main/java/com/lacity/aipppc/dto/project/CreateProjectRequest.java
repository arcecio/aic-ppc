package com.lacity.aipppc.dto.project;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Intake payload (SOW 2.2.1). {@code formData} holds the dynamic-form answers for
 * the selected permit type. Address/APN are resolved against the parcel/GIS
 * stand-in during creation.
 */
public record CreateProjectRequest(
    @NotBlank String title,
    @NotBlank String permitTypeCode,
    String projectScope,
    String intendedUse,
    String description,
    String address,
    String apn,
    Map<String, Object> formData
) {}
