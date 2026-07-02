package com.lacity.aipppc.model.enums;

/** Which engine produced a finding. Rules are the primary mechanism; AI augments
 *  (SOW 2.2.3). COMPLETENESS is the document-checklist validator. */
public enum FindingSource {
    RULE, AI, COMPLETENESS
}
