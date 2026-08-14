package com.codegym.mathclass.bugreport.repository;

import com.codegym.mathclass.bugreport.entity.BugReport;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BugReportRepository extends JpaRepository<BugReport, Long> {

    @EntityGraph(attributePaths = {"images"})
    Page<BugReport> findByStatus(BugReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"images"})
    Page<BugReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
