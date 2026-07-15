package com.myworktea.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * An immutable record of a moderation/admin action (approved &amp; rejected workplaces and
 * reviews, deleted users, …). Details are stored as a text snapshot so the record survives
 * even after the underlying entity is removed.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_category", columnList = "category"),
        @Index(name = "idx_audit_created", columnList = "createdAt")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    /** One-line human-readable summary, e.g. "Workplace 'Starbucks' approved". */
    @Column(nullable = false, length = 255)
    private String summary;

    /** Longer snapshot detail (submitter, contents, reason, …). */
    @Column(length = 4000)
    private String detail;

    /** Username of the admin/moderator who performed the action. */
    @Column(length = 40)
    private String actor;

    /** Optional reference to the original entity id (it may since have been deleted). */
    private Long targetId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AuditLog() { }

    public AuditLog(AuditCategory category, AuditAction action, String summary,
                    String detail, String actor, Long targetId) {
        this.category = category;
        this.action = action;
        this.summary = summary;
        this.detail = detail;
        this.actor = actor;
        this.targetId = targetId;
    }

    public Long getId() { return id; }
    public AuditCategory getCategory() { return category; }
    public void setCategory(AuditCategory category) { this.category = category; }
    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}