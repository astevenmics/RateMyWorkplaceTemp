package com.myworktea.repository;

import com.myworktea.domain.SiteUpdate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SiteUpdateRepository extends JpaRepository<SiteUpdate, Long> {
    Page<SiteUpdate> findByPublishedTrueOrderByCreatedAtDesc(Pageable pageable);
    List<SiteUpdate> findTop5ByPublishedTrueOrderByCreatedAtDesc();

    @Modifying
    @Query("update SiteUpdate s set s.author = null where s.author.id = :userId")
    void detachAuthor(@Param("userId") Long userId);
}