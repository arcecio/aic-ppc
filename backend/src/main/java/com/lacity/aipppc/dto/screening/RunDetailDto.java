package com.lacity.aipppc.dto.screening;

import java.util.List;

/** A run plus its findings and clearances — the full results payload (SOW 2.2.8). */
public record RunDetailDto(
    RunDto run,
    List<FindingDto> findings,
    List<ClearanceDto> clearances
) {}
