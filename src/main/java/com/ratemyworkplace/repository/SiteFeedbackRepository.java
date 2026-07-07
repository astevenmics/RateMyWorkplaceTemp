package com.ratemyworkplace.repository;

import com.ratemyworkplace.domain.SiteFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteFeedbackRepository extends JpaRepository<SiteFeedback, Long> {
    Page<SiteFeedback> findByResolved(boolean resolved, Pageable pageable);
    long countByResolved(boolean resolved);

    @Modifying
    @Query("update SiteFeedback s set s.author = null where s.author.id = :userId")
    void detachAuthor(@Param("userId") Long userId);
}