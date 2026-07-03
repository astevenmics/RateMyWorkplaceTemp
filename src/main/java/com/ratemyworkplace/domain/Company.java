package com.ratemyworkplace.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A company/workplace brand. A company has many {@link Location}s (Starbucks at
 * two addresses, etc.) and may belong to several {@link Category}s.
 */
@Entity
@Table(name = "companies", indexes = @Index(name = "idx_company_name", columnList = "name"))
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(length = 200)
    private String website;

    @Column(length = 255)
    private String logoUrl;

    // SUBSELECT batches the collection load for every Company in the current
    // page/session into a single extra query, instead of one per-row SELECT
    // (the default for an EAGER @ManyToMany) when listing/searching companies.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "company_categories",
            joinColumns = @JoinColumn(name = "company_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @Fetch(FetchMode.SUBSELECT)
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    private List<Location> locations = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    // JOIN is safe (unlike on a collection) since a to-one association can't multiply
    // result rows; this avoids a per-row SELECT when DtoMapper.companyDetail() reads
    // submittedBy (e.g. once per row on the admin pending-workplaces listing).
    @ManyToOne(fetch = FetchType.LAZY)
    @Fetch(FetchMode.JOIN)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    /** Denormalised aggregates kept in sync by the feedback service for fast listing/sorting. */
    @Column(nullable = false)
    private double averageRating = 0d;

    @Column(nullable = false)
    private long ratingCount = 0L;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public Set<Category> getCategories() { return categories; }
    public void setCategories(Set<Category> categories) { this.categories = categories; }
    public List<Location> getLocations() { return locations; }
    public void setLocations(List<Location> locations) { this.locations = locations; }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public User getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(User submittedBy) { this.submittedBy = submittedBy; }
    public double getAverageRating() { return averageRating; }
    public void setAverageRating(double averageRating) { this.averageRating = averageRating; }
    public long getRatingCount() { return ratingCount; }
    public void setRatingCount(long ratingCount) { this.ratingCount = ratingCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
