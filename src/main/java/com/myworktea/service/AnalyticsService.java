package com.myworktea.service;

import com.myworktea.domain.VisitLog;
import com.myworktea.repository.VisitLogRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maintains the per-day traffic counters used by the admin statistics dashboard.
 *
 * <p>Increments are accumulated in memory (atomic, lock-free)
 * so page-view / login / signup recording adds no database work to the request path and
 * avoids contending on the single hot daily row.
 * A scheduled task periodically drains the buffered deltas into the
 * {@code visit_stats} table; the final window is flushed on shutdown.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final VisitLogRepository repository;

    /** Buffered, not-yet-persisted deltas per calendar day. */
    private final ConcurrentMap<LocalDate, DayDelta> pending = new ConcurrentHashMap<>();

    public AnalyticsService(VisitLogRepository repository) {
        this.repository = repository;
    }

    public void recordPageView() {
        today().pageViews.incrementAndGet();
    }

    public void recordLogin() {
        today().logins.incrementAndGet();
    }

    public void recordSignup() {
        today().signups.incrementAndGet();
    }
    private DayDelta today() {
        return pending.computeIfAbsent(LocalDate.now(), d -> new DayDelta());
    }

    /** Drains buffered counters into the database. */
    @Scheduled(fixedDelayString = "${app.analytics.flush-interval-ms:30000}")
    @Transactional
    public void flush() {
        for (LocalDate day : pending.keySet()) {
            DayDelta delta = pending.get(day);
            if (delta == null) {
                continue;
            }
            // Atomically claim the accumulated counts;
            // concurrent increments keep adding to the same object and will be picked up on the next flush.
            long pv = delta.pageViews.getAndSet(0);
            long lg = delta.logins.getAndSet(0);
            long su = delta.signups.getAndSet(0);
            if (pv == 0 && lg == 0 && su == 0) {
                continue;
            }
            try {
                VisitLog row = findOrCreate(day);
                row.setPageViews(row.getPageViews() + pv);
                row.setLogins(row.getLogins() + lg);
                row.setSignups(row.getSignups() + su);
                repository.save(row);
            } catch (Exception e) {
                // Don't lose the deltas if the write failed — fold them back in.
                delta.pageViews.addAndGet(pv);
                delta.logins.addAndGet(lg);
                delta.signups.addAndGet(su);
                log.warn("Failed to flush analytics for {}: {}", day, e.getMessage());
            }
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        try {
            flush();
        } catch (Exception e) {
            log.warn("Final analytics flush failed: {}", e.getMessage());
        }
    }

    private VisitLog findOrCreate(LocalDate day) {
        return repository.findByDay(day).orElseGet(() -> {
            try {
                return repository.saveAndFlush(new VisitLog(day));
            } catch (DataIntegrityViolationException e) {
                return repository.findByDay(day).orElseThrow();
            }
        });
    }

    private static final class DayDelta {
        final AtomicLong pageViews = new AtomicLong();
        final AtomicLong logins = new AtomicLong();
        final AtomicLong signups = new AtomicLong();
    }
}