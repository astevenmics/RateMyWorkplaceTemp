package com.myworktea.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * An anonymous, non-company-specific work rant/vent. Unlike {@link Feedback}, these aren't tied
 * to a user or a company — no login is required to read or post one, and they go live
 * immediately (no approval queue); moderators can remove ones that violate the Terms &amp;
 * Conditions after the fact.
 */
@Entity
@Table(name = "rants", indexes = @Index(name = "idx_rant_created_at", columnList = "createdAt"))
public class Rant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Free-text, self-chosen handle — not tied to any account. Null/blank displays as "Anonymous". */
    @Column(length = 40)
    private String nickname;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Denormalized vote counts (kept in sync by {@code RantService.vote}) so sorting/browsing by
     *  them doesn't require an aggregate join over every vote row. */
    @Column(nullable = false)
    private int upvotes = 0;

    @Column(nullable = false)
    private int downvotes = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public int getUpvotes() { return upvotes; }
    public void setUpvotes(int upvotes) { this.upvotes = upvotes; }
    public int getDownvotes() { return downvotes; }
    public void setDownvotes(int downvotes) { this.downvotes = downvotes; }
}
