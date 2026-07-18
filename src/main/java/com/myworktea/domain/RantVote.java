package com.myworktea.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Records one voter's up/down vote on one {@link Rant}, enforcing "one vote per voter" via the
 * unique constraint below. {@code voterId} is either {@code "user:<id>"} for a logged-in voter,
 * or {@code "anon:<client-generated id>"} for an anonymous one (a UUID the frontend persists in
 * localStorage) — the latter is a best-effort dedup, since nothing stops someone from clearing
 * storage or using another browser, but it's the only signal available without requiring login.
 */
@Entity
@Table(name = "rant_votes",
        indexes = @Index(name = "idx_rant_vote_rant", columnList = "rant_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_rant_vote_voter", columnNames = {"rant_id", "voter_id"}))
public class RantVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rant_id", nullable = false)
    private Rant rant;

    @Column(name = "voter_id", nullable = false, length = 120)
    private String voterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteType voteType;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Rant getRant() { return rant; }
    public void setRant(Rant rant) { this.rant = rant; }
    public String getVoterId() { return voterId; }
    public void setVoterId(String voterId) { this.voterId = voterId; }
    public VoteType getVoteType() { return voteType; }
    public void setVoteType(VoteType voteType) { this.voteType = voteType; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
