package com.myworktea.repository;

import com.myworktea.domain.Rant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RantRepository extends JpaRepository<Rant, Long> {

    Page<Rant> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** A random sample for the homepage teaser. RAND() is supported by both MySQL (prod) and H2 (dev/test). */
    @Query(value = "SELECT * FROM rants ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    List<Rant> findRandom(@Param("limit") int limit);
}
