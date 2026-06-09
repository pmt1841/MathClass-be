package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, Long>, JpaSpecificationExecutor<Assignment> {

    List<Assignment> findByTeacherId(long teacherId);

    Page<Assignment> findByClassroom_ClassCodeAndStatus(String classCode, AssignmentStatus status, Pageable pageable);

    List<Assignment> findByParentId(Long parentId);
}
