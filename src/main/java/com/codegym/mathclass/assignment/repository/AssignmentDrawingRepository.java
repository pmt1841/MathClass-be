package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.AssignmentDrawing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentDrawingRepository extends JpaRepository<AssignmentDrawing, Long> {
}
