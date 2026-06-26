package com.ratemywork.repository;

import com.ratemywork.domain.VisitLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VisitLogRepository extends JpaRepository<VisitLog, Long> {
    Optional<VisitLog> findByDay(LocalDate day);
    List<VisitLog> findByDayBetweenOrderByDayAsc(LocalDate from, LocalDate to);
}
