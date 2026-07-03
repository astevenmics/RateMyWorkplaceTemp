package com.ratemyworkplace.repository;

import com.ratemyworkplace.domain.ApprovalStatus;
import com.ratemyworkplace.domain.EmploymentProof;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EmploymentProofRepository extends JpaRepository<EmploymentProof, Long> {

    @Modifying
    @Query("delete from EmploymentProof p where p.company.id = :companyId")
    void deleteByCompanyId(@Param("companyId") Long companyId);

    @Modifying
    @Query("delete from EmploymentProof p where p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update EmploymentProof p set p.reviewedBy = null where p.reviewedBy.id = :userId")
    void detachReviewer(@Param("userId") Long userId);

    List<EmploymentProof> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<EmploymentProof> findByStatus(ApprovalStatus status, Pageable pageable);

    long countByStatus(ApprovalStatus status);

    /** A company-wide proof (no specific location attached). */
    boolean existsByUserIdAndCompanyIdAndLocationIsNullAndStatus(Long userId, Long companyId, ApprovalStatus status);

    /** A proof scoped to one specific location. */
    boolean existsByUserIdAndLocationIdAndStatus(Long userId, Long locationId, ApprovalStatus status);

    // ----- one-proof-per-location enforcement (any of the given statuses) -----
    boolean existsByUserIdAndLocationIdAndStatusIn(Long userId, Long locationId, Collection<ApprovalStatus> statuses);

    boolean existsByUserIdAndCompanyIdAndLocationIsNullAndStatusIn(
            Long userId, Long companyId, Collection<ApprovalStatus> statuses);
}
