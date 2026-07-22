package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentSheetRepository extends JpaRepository<AssignmentSheet, Long>, JpaSpecificationExecutor<AssignmentSheet> {
    java.util.Optional<AssignmentSheet> findFirstByTeacherIdAndTitleAndClassroomIsNull(long teacherId, String title);
    java.util.List<AssignmentSheet> findByTeacherIdAndTitle(long teacherId, String title);
}
