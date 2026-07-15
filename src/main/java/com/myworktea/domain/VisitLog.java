package com.myworktea.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Per-day rollup of traffic counters powering the admin statistics graphs.
 * One row per calendar day; counters are incremented atomically.
 */
@Entity
@Table(name = "visit_stats", indexes = @Index(name = "idx_visit_day", columnList = "visit_day", unique = true))
public class VisitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "visit_day", nullable = false, unique = true)
    private LocalDate day;

    @Column(nullable = false)
    private long pageViews = 0L;

    @Column(nullable = false)
    private long logins = 0L;

    @Column(nullable = false)
    private long signups = 0L;

    public VisitLog() { }

    public VisitLog(LocalDate day) { this.day = day; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public long getPageViews() { return pageViews; }
    public void setPageViews(long pageViews) { this.pageViews = pageViews; }
    public long getLogins() { return logins; }
    public void setLogins(long logins) { this.logins = logins; }
    public long getSignups() { return signups; }
    public void setSignups(long signups) { this.signups = signups; }
}