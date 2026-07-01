package com.ratemyworkplace.repository;

import com.ratemyworkplace.domain.ApprovalStatus;
import com.ratemyworkplace.domain.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @Modifying
    @Query("delete from Feedback f where f.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") Long companyId);

    @Modifying
    @Query("delete from Feedback f where f.author.id = :authorId")
    void deleteByAuthorId(@Param("authorId") Long authorId);

    Page<Feedback> findByLocationIdAndStatus(Long locationId, ApprovalStatus status, Pageable pageable);

    Page<Feedback> findByCompanyIdAndStatus(Long companyId, ApprovalStatus status, Pageable pageable);

    Page<Feedback> findByStatus(ApprovalStatus status, Pageable pageable);

    long countByStatus(ApprovalStatus status);

    /** Average rating + review count in one aggregate query (used to keep denormalised rollups in sync). */
    interface RatingStats {
        double getAverage();
        long getCount();
    }

    @Query("select coalesce(avg(f.rating),0) as average, count(f) as count " +
            "from Feedback f where f.location.id = :locationId and f.status = 'APPROVED'")
    RatingStats locationStats(@Param("locationId") Long locationId);

    @Query("select coalesce(avg(f.rating),0) as average, count(f) as count " +
            "from Feedback f where f.company.id = :companyId and f.status = 'APPROVED'")
    RatingStats companyStats(@Param("companyId") Long companyId);
}
