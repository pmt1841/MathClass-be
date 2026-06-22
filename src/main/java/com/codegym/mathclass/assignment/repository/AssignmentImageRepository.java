package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.AssignmentImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentImageRepository extends JpaRepository<AssignmentImage, Long> {
    List<AssignmentImage> findByAssignmentId(Long assignmentId);
}
