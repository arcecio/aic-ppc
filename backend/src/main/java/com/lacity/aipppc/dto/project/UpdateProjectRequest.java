package com.lacity.aipppc.dto.project;

import java.util.Map;

/** Partial update of an intake record before screening. */
public record UpdateProjectRequest(
    String title,
    String projectScope,
    String intendedUse,
    String description,
    String address,
    String apn,
    Map<String, Object> formData
) {}
