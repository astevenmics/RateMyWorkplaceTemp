package com.myworktea.repository;

import com.myworktea.domain.Rant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RantRepository extends JpaRepository<Rant, Long> {

    /** A random sample for the homepage teaser. RAND() is supported by both MariaDB (prod) and H2 (dev/test). */
    @Query(value = "SELECT * FROM rants ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Rant> findRandom(@Param("limit") int limit);

    /** The poster's most recent rant within the cooldown window, if any (for enforcing it). */
    Optional<Rant> findFirstByPosterIdAndCreatedAtAfterOrderByCreatedAtDesc(String posterId, Instant cutoff);

    /**
     * Same lookup as {@link #findById}, but takes a row-level write lock for the rest of the
     * transaction. Used by {@code RantService.vote()} so that concurrent votes on the same rant
     * (e.g. spam-clicking, or two open tabs) are serialized by the database instead of racing
     * each other's read-then-write against the same {@code rant_votes} row.
     * <p>Plain native SQL rather than JPA's {@code @Lock(PESSIMISTIC_WRITE)}: with the correct
     * MariaDB driver in place, Hibernate should render that as bare {@code FOR UPDATE} via
     * MariaDBDialect — but this used to run against MariaDB through the MySQL JDBC driver,
     * whose dialect auto-detection resolved to MySQLDialect and emitted {@code FOR UPDATE OF
     * <alias>} instead, which MariaDB's parser rejects outright. Writing the lock out as native
     * SQL (supported identically by MariaDB, MySQL and H2) means correctness here never again
     * depends on Hibernate's dialect detection picking the right dialect.
     */
    @Query(value = "SELECT * FROM rants WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Rant> findByIdForUpdate(@Param("id") Long id);
}
