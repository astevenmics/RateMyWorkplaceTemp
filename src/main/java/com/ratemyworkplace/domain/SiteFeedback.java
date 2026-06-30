package com.ratemyworkplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Feedback about the website/project itself, submitted from the footer for admins to review. */
@Entity
@Table(name = "site_feedback", indexes = @Index(name = "idx_site_feedback_resolved", columnList = "resolved"))
public class SiteFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for anonymous visitors. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(length = 160)
    private String contactEmail;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, length = 4000)
    private String message;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
