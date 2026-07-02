package com.lacity.aipppc.model.enums;

/** Security-scan state of an uploaded file (SOW 2.2.1 — validated for format,
 *  size, and virus/security compliance prior to AI integration). */
public enum ScanStatus {
    PENDING, PASSED, FAILED, QUARANTINED
}
