package com.ratemywork.repository;

import com.ratemywork.domain.ApprovalStatus;
import com.ratemywork.domain.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    @Modifying
    @Query("update Company c set c.submittedBy = null where c.submittedBy.id = :userId")
    void detachSubmitter(@Param("userId") Long userId);

    Page<Company> findByStatus(ApprovalStatus status, Pageable pageable);

    long countByStatus(ApprovalStatus status);

    @Query("""
            select distinct c from Company c
            left join c.categories cat
            left join c.locations loc
            where c.status = :status
              and (:q is null or
                   lower(c.name) like lower(concat('%', :q, '%')) or
                   lower(c.description) like lower(concat('%', :q, '%')) or
                   lower(cat.name) like lower(concat('%', :q, '%')) or
                   lower(loc.city) like lower(concat('%', :q, '%')) or
                   lower(loc.state) like lower(concat('%', :q, '%')) or
                   loc.zipCode like concat('%', :q, '%'))
              and (:categoryId is null or cat.id = :categoryId)
            """)
    Page<Company> search(@Param("q") String q,
                         @Param("categoryId") Long categoryId,
                         @Param("status") ApprovalStatus status,
                         Pageable pageable);

    @Query("""
            select c from Company c
            where c.status = :status and c.ratingCount > 0
            order by c.averageRating desc, c.ratingCount desc
            """)
    List<Company> findTopRated(@Param("status") ApprovalStatus status, Pageable pageable);
}
