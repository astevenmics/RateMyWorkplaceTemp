package com.ratemyworkplace.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A physical workplace location of a {@link Company}.
 * Feedback and employment proofs are scoped to a specific location.
 */
@Entity
@Table(name = "locations", indexes = {
        @Index(name = "idx_location_zip", columnList = "zipCode"),
        @Index(name = "idx_location_city", columnList = "city")
})
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(length = 140)
    private String label;

    @Column(length = 200)
    private String addressLine;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 20)
    private String zipCode;

    @Column(length = 80)
    private String country;

    /**
     * Functional departments/positions present at this specific location. Free text so a
     * custom department/position (not in the predefined quick-pick list) can be recorded too —
     * see {@link Department#normalize}.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "location_departments", joinColumns = @JoinColumn(name = "location_id"))
    @Column(name = "department", length = 60)
    @Fetch(FetchMode.SUBSELECT)
    private Set<String> departments = new LinkedHashSet<>();

    @Column(nullable = false)
    private double averageRating = 0d;

    @Column(nullable = false)
    private long ratingCount = 0L;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public Set<String> getDepartments() { return departments; }
    public void setDepartments(Set<String> departments) { this.departments = departments; }
    public double getAverageRating() { return averageRating; }
    public void setAverageRating(double averageRating) { this.averageRating = averageRating; }
    public long getRatingCount() { return ratingCount; }
    public void setRatingCount(long ratingCount) { this.ratingCount = ratingCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}