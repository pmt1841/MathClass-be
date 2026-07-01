package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long>, JpaSpecificationExecutor<Assignment> {

    List<Assignment> findByTeacherId(long teacherId);

    int countByTeacherIdAndStatus(long teacherId, AssignmentStatus status);

    Page<Assignment> findByClassroom_ClassCodeAndStatus(String classCode, AssignmentStatus status, Pageable pageable);

    List<Assignment> findByParentId(Long parentId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(a) FROM Assignment a " +
           "JOIN a.classroom c " +
           "JOIN c.students s " +
           "WHERE s.id = :studentId " +
           "AND a.status = 'PUBLISHED' " +
           "AND NOT EXISTS (SELECT sub FROM com.codegym.mathclass.submission.entity.Submission sub WHERE sub.assignment = a AND sub.student.id = :studentId AND sub.status <> 'DRAFT')")
    int countPendingAssignmentsForStudent(@org.springframework.data.repository.query.Param("studentId") long studentId);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM Assignment a " +
           "JOIN a.classroom c " +
           "JOIN c.students s " +
           "WHERE s.id = :studentId " +
           "AND a.status = 'PUBLISHED' " +
           "AND NOT EXISTS (SELECT sub FROM com.codegym.mathclass.submission.entity.Submission sub WHERE sub.assignment = a AND sub.student.id = :studentId AND sub.status <> 'DRAFT') " +
           "ORDER BY a.deadline ASC")
    Page<Assignment> findPendingAssignmentsForStudent(@org.springframework.data.repository.query.Param("studentId") long studentId, Pageable pageable);
}
