package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.AssignmentDrawing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentDrawingRepository extends JpaRepository<AssignmentDrawing, Long> {
}
