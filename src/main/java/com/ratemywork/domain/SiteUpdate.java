package com.ratemywork.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** An admin-authored "What's new" announcement shown in the footer/news section. */
@Entity
@Table(name = "site_updates")
public class SiteUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 8000)
    private String body;

    @Column(length = 40)
    private String tag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false)
    private boolean published = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
