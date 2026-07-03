package com.ratemyworkplace.service;

import com.ratemyworkplace.domain.AuditAction;
import com.ratemyworkplace.domain.AuditCategory;
import com.ratemyworkplace.domain.AuditLog;
import com.ratemyworkplace.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records and queries the moderation/admin audit trail. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUserService currentUserService;

    public AuditService(AuditLogRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void record(AuditCategory category, AuditAction action, String summary, String detail, Long targetId) {
        String actor = currentUserService.current().map(u -> u.getUsername()).orElse("system");
        repository.save(new AuditLog(category, action, summary, detail, actor, targetId));
    }

    public Page<AuditLog> list(AuditCategory category, Pageable pageable) {
        return category == null
                ? repository.findAll(pageable)
                : repository.findByCategory(category, pageable);
    }
}
