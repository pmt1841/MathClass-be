package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.AssignmentImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssignmentImageRepository extends JpaRepository<AssignmentImage, Long> {
    List<AssignmentImage> findByAssignmentId(Long assignmentId);

    @Query("SELECT COUNT(ai) > 0 FROM AssignmentImage ai WHERE ai.imageUrl = :imageUrl AND ai.assignment.id <> :assignmentId")
    boolean existsByImageUrlAndAssignmentIdNot(@Param("imageUrl") String imageUrl, @Param("assignmentId") Long assignmentId);

    @Query("SELECT DISTINCT ai.imageUrl FROM AssignmentImage ai WHERE ai.imageUrl IS NOT NULL")
    List<String> findAllDistinctImageUrls();
}
