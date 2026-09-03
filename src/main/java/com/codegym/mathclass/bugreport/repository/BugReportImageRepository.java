package com.codegym.mathclass.bugreport.repository;

import com.codegym.mathclass.bugreport.entity.BugReportImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BugReportImageRepository extends JpaRepository<BugReportImage, Long> {

    List<BugReportImage> findByBugReportId(Long bugReportId);

    @Query("SELECT DISTINCT bri.imageUrl FROM BugReportImage bri WHERE bri.imageUrl IS NOT NULL")
    List<String> findAllDistinctImageUrls();
}
