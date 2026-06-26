package com.ratemywork.repository;

import com.ratemywork.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findByCompanyId(Long companyId);
    long countByCompanyId(Long companyId);
}
