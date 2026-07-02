package com.lacity.aipppc.model.enums;

/** Human-in-the-loop review state (Appendix 3 §5.1.5). Staff may accept, modify,
 *  or reject any AI/rule-generated finding or clearance before final disposition. */
public enum StaffDisposition {
    PENDING, ACCEPTED, MODIFIED, REJECTED
}
