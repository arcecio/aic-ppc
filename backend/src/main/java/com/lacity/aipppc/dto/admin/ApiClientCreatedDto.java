package com.lacity.aipppc.dto.admin;

/** Returned once at creation — carries the raw API key, shown only this one time. */
public record ApiClientCreatedDto(ApiClientDto client, String apiKey) {}
