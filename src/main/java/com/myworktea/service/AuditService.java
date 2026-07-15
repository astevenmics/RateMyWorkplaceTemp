package com.myworktea.service;

import com.myworktea.domain.AuditAction;
import com.myworktea.domain.AuditCategory;
import com.myworktea.domain.AuditLog;
import com.myworktea.domain.User;
import com.myworktea.repository.AuditLogRepository;
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
        String actor = currentUserService.current().map(User::getUsername).orElse("system");
        record(category, action, summary, detail, targetId, actor);
    }

    /** Use when the caller already has the acting user in hand, to avoid a redundant lookup. */
    @Transactional
    public void record(AuditCategory category, AuditAction action, String summary, String detail,
                       Long targetId, String actor) {
        repository.save(new AuditLog(category, action, summary, detail, actor, targetId));
    }

    public Page<AuditLog> list(AuditCategory category, Pageable pageable) {
        return category == null
                ? repository.findAll(pageable)
                : repository.findByCategory(category, pageable);
    }
}