package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.AssignmentSheetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentSheetItemRepository extends JpaRepository<AssignmentSheetItem, Long> {
    List<AssignmentSheetItem> findBySheetId(Long sheetId);
    List<AssignmentSheetItem> findByAssignmentIdIn(List<Long> assignmentIds);
}
