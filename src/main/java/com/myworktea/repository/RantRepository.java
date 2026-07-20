package com.myworktea.repository;

import com.myworktea.domain.Rant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RantRepository extends JpaRepository<Rant, Long> {

    /** A random sample for the homepage teaser. RANDOM() is supported by both PostgreSQL (prod) and H2 (dev/test). */
    @Query(value = "SELECT * FROM rants ORDER BY RANDOM() LIMIT :limit", nativeQuery = true)
    List<Rant> findRandom(@Param("limit") int limit);

    /** The poster's most recent rant within the cooldown window, if any (for enforcing it). */
    Optional<Rant> findFirstByPosterIdAndCreatedAtAfterOrderByCreatedAtDesc(String posterId, Instant cutoff);

    /**
     * Same lookup as {@link #findById}, but takes a row-level write lock for the rest of the
     * transaction. Used by {@code RantService.vote()} so that concurrent votes on the same rant
     * (e.g. spam-clicking, or two open tabs) are serialized by the database instead of racing
     * each other's read-then-write against the same {@code rant_votes} row.
     * <p>Plain native SQL rather than JPA's {@code @Lock(PESSIMISTIC_WRITE)}: PostgreSQL doesn't
     * have the row-lock dialect footgun a previous MariaDB setup hit here (where a misdetected
     * dialect emitted {@code FOR UPDATE OF <alias>}, which that database's parser rejected), but
     * writing the lock as portable native SQL rather than relying on Hibernate's dialect-specific
     * lock-clause generation means correctness here never depends on dialect detection at all.
     */
    @Query(value = "SELECT * FROM rants WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<Rant> findByIdForUpdate(@Param("id") Long id);
}
