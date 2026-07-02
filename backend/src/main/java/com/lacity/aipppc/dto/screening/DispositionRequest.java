package com.lacity.aipppc.dto.screening;

import jakarta.validation.constraints.NotBlank;

/**
 * A staff human-in-the-loop decision on a finding or clearance
 * (Appendix 3 §5.1.5). {@code disposition} is one of ACCEPTED / MODIFIED /
 * REJECTED / PENDING.
 */
public record DispositionRequest(
    @NotBlank String disposition,
    String comment
) {}
