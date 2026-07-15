package com.myworktea.domain;

/** Lifecycle state shared by workplaces, proofs and feedback awaiting moderation. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}