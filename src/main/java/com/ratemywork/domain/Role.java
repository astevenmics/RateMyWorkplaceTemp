package com.ratemywork.domain;

/** Coarse-grained account role. Fine-grained moderator rights live in {@link ModeratorPermission}. */
public enum Role {
    USER,
    MODERATOR,
    ADMIN
}
