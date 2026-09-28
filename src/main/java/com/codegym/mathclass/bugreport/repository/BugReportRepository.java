package com.codegym.mathclass.bugreport.repository;

import com.codegym.mathclass.bugreport.entity.BugReport;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BugReportRepository extends JpaRepository<BugReport, Long>, JpaSpecificationExecutor<BugReport> {

    Page<BugReport> findByStatus(BugReportStatus status, Pageable pageable);

    Page<BugReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(BugReportStatus status);

    long countByStatusAndCreatedAtLessThan(BugReportStatus status, LocalDateTime endDate);

    List<BugReport> findTop5ByOrderByCreatedAtDesc();

    List<BugReport> findByOrderCodeIn(List<String> orderCodes);

    Optional<BugReport> findFirstByOrderCodeOrderByCreatedAtDesc(String orderCode);
}
