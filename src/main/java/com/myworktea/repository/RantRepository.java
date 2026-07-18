package com.myworktea.repository;

import com.myworktea.domain.Rant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RantRepository extends JpaRepository<Rant, Long> {

    /** A random sample for the homepage teaser. RAND() is supported by both MySQL (prod) and H2 (dev/test). */
    @Query(value = "SELECT * FROM rants ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Rant> findRandom(@Param("limit") int limit);

    /** The poster's most recent rant within the cooldown window, if any (for enforcing it). */
    Optional<Rant> findFirstByPosterIdAndCreatedAtAfterOrderByCreatedAtDesc(String posterId, Instant cutoff);

    /**
     * Same lookup as {@link #findById}, but takes a row-level write lock for the rest of the
     * transaction. Used by {@code RantService.vote()} so that concurrent votes on the same rant
     * (e.g. spam-clicking, or two open tabs) are serialized by the database instead of racing
     * each other's read-then-write against the same {@code rant_votes} row.
     * <p>Plain native SQL rather than JPA's {@code @Lock(PESSIMISTIC_WRITE)}: Hibernate's MySQL
     * dialect renders that as {@code FOR UPDATE OF <alias>}, but production runs on MariaDB via
     * the MySQL JDBC driver, which Hibernate's dialect auto-detection can't tell apart from real
     * MySQL from the driver metadata alone — and MariaDB's parser rejects the {@code OF} clause.
     * Bare {@code FOR UPDATE} is supported identically by MySQL, MariaDB and H2, so writing it out
     * ourselves sidesteps the dialect-detection gap entirely.
     */
    @Query(value = "SELECT * FROM rants WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Rant> findByIdForUpdate(@Param("id") Long id);
}
