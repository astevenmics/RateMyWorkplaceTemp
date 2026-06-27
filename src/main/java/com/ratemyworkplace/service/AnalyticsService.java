package com.ratemyworkplace.service;

import com.ratemyworkplace.domain.VisitLog;
import com.ratemyworkplace.repository.VisitLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Maintains the per-day traffic counters used by the admin statistics dashboard. */
@Service
public class AnalyticsService {

    private final VisitLogRepository repository;

    public AnalyticsService(VisitLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPageView() {
        VisitLog log = today();
        log.setPageViews(log.getPageViews() + 1);
    }

    @Transactional
    public void recordLogin() {
        VisitLog log = today();
        log.setLogins(log.getLogins() + 1);
    }

    @Transactional
    public void recordSignup() {
        VisitLog log = today();
        log.setSignups(log.getSignups() + 1);
    }

    private VisitLog today() {
        LocalDate day = LocalDate.now();
        return repository.findByDay(day).orElseGet(() -> {
            try {
                return repository.saveAndFlush(new VisitLog(day));
            } catch (DataIntegrityViolationException e) {
                // Another request created today's row concurrently.
                return repository.findByDay(day).orElseThrow();
            }
        });
    }
}
