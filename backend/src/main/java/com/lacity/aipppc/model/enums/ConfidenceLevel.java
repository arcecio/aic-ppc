package com.lacity.aipppc.model.enums;

/** Coarse confidence bucket surfaced next to findings/clearances (SOW 2.2.4). */
public enum ConfidenceLevel {
    HIGH, MEDIUM, LOW;

    /** Buckets a 0-100 confidence score: &gt;=85 HIGH, &gt;=55 MEDIUM, else LOW. */
    public static ConfidenceLevel fromScore(int score) {
        if (score >= 85) return HIGH;
        if (score >= 55) return MEDIUM;
        return LOW;
    }
}
