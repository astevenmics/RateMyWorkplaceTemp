package com.ratemyworkplace.repository;

import com.ratemyworkplace.domain.AuditCategory;
import com.ratemyworkplace.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByCategory(AuditCategory category, Pageable pageable);
}
