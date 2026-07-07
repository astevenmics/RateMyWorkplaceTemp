package com.ratemyworkplace.domain;

/** What kind of moderation/admin action an {@link AuditLog} entry records. */
public enum AuditCategory {
    WORKPLACE,
    FEEDBACK,
    PROOF,
    USER
}