package com.ratemyworkplace.domain;

/**
 * Granular moderator capabilities an admin can grant to a non-admin account.
 * Each maps to an authority of the form {@code MOD_<name>} in Spring Security.
 */
public enum ModeratorPermission {
    /** Review / hide feedback that violates the Terms &amp; Conditions. */
    MODERATE_FEEDBACK,
    /** Approve or reject uploaded employment proofs. */
    APPROVE_PROOFS,
    /** Approve or reject user-suggested workplaces. */
    APPROVE_WORKPLACES,
    /** Review users with suspicious profiles / usernames. */
    MANAGE_USERS;

    public String authority() {
        return "MOD_" + name();
    }
}
