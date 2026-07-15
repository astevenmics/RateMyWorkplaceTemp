package com.myworktea.repository;

import com.myworktea.domain.ApprovalStatus;
import com.myworktea.domain.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByCompanyId(Long companyId);
    long countByCompanyId(Long companyId);
    /**
     * One row per location (not per company) so a company with N locations surfaces as N
     * distinct browse-page cards, each with that specific location's own address and rating.
     */
    @Query("""
            select distinct l from Location l
            join l.company c
            left join c.categories cat
            where c.status = :status
              and (:q is null or
                   lower(c.name) like lower(concat('%', :q, '%')) or
                   lower(c.description) like lower(concat('%', :q, '%')) or
                   lower(cat.name) like lower(concat('%', :q, '%')) or
                   lower(l.city) like lower(concat('%', :q, '%')) or
                   lower(l.state) like lower(concat('%', :q, '%')) or
                   l.zipCode like concat('%', :q, '%'))
              and (:categoryId is null or cat.id = :categoryId)
            """)
    Page<Location> searchCards(@Param("q") String q,
                               @Param("categoryId") Long categoryId,
                               @Param("status") ApprovalStatus status,
                               Pageable pageable);
}