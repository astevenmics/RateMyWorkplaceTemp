package com.myworktea.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * A registered account.
 * Email must be verified before a user may submit workplaces or feedback
 * Verification enforced in the service layer.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_email", columnList = "email", unique = true)
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 60)
    private String firstName;

    @Column(length = 60)
    private String lastName;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** Stored file name of the user's profile picture, or {@code null} if none has been set. */
    @Column(length = 255)
    private String avatarFileName;

    /** Content type of the stored profile picture (e.g. {@code image/png}). */
    @Column(length = 80)
    private String avatarContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_mod_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission", length = 40)
    @Enumerated(EnumType.STRING)
    private Set<ModeratorPermission> moderatorPermissions = EnumSet.noneOf(ModeratorPermission.class);

    /**
     * Default set to false.
     * Changed to true once user verifies their email
     */
    @Column(nullable = false)
    private boolean emailVerified = false;

    /** Full legal name (first + last), used by reviewers to match against employment proofs. */
    public String getFullName() {
        String f = firstName == null ? "" : firstName.trim();
        String l = lastName == null ? "" : lastName.trim();
        return (f + " " + l).trim();
    }

    @Column(nullable = false)
    private boolean enabled = true;

    /** Set when an admin/mod flags the account for an inappropriate username, etc. */
    @Column(length = 255)
    private String flaggedReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant lastLoginAt;

    /**
     * Set when the user requests their own account deletion; null otherwise.
     * The account is disabled immediately, then permanently purged
     * (feedback, proofs, etc. — but not workplaces they submitted)
     * once {@link #DELETION_GRACE_DAYS} have elapsed since this timestamp.
     */
    private Instant deletionRequestedAt;

    public static final int DELETION_GRACE_DAYS = 30;

    @Column(nullable = false)
    private boolean selfServiceDisabled = false;

    public boolean isFullyVerified() {
        return emailVerified;
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getAvatarFileName() { return avatarFileName; }
    public void setAvatarFileName(String avatarFileName) { this.avatarFileName = avatarFileName; }
    public String getAvatarContentType() { return avatarContentType; }
    public void setAvatarContentType(String avatarContentType) { this.avatarContentType = avatarContentType; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Set<ModeratorPermission> getModeratorPermissions() { return moderatorPermissions; }
    public void setModeratorPermissions(Set<ModeratorPermission> p) { this.moderatorPermissions = p; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getFlaggedReason() { return flaggedReason; }
    public void setFlaggedReason(String flaggedReason) { this.flaggedReason = flaggedReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getDeletionRequestedAt() { return deletionRequestedAt; }
    public void setDeletionRequestedAt(Instant deletionRequestedAt) { this.deletionRequestedAt = deletionRequestedAt; }
    public boolean isSelfServiceDisabled() { return selfServiceDisabled; }
    public void setSelfServiceDisabled(boolean selfServiceDisabled) { this.selfServiceDisabled = selfServiceDisabled; }
}