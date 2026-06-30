package com.ratemyworkplace.domain;

/** Lifecycle state shared by workplaces, proofs and feedback awaiting moderation. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
