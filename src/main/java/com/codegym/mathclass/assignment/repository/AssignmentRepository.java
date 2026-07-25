package com.codegym.mathclass.assignment.repository;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface AssignmentRepository extends JpaRepository<Assignment, Long>, JpaSpecificationExecutor<Assignment> {

    List<Assignment> findByTeacherId(long teacherId);

    /**
     * Lấy assignments theo danh sách IDs, chỉ trả về những bài thuộc về teacherId.
     * Dùng để validate ownership trong publishAssignmentSheet.
     */
    List<Assignment> findAllByIdInAndTeacherId(List<Long> ids, long teacherId);


    List<Assignment> findByDeadlineBetweenAndIsReminderSentFalseAndStatus(LocalDateTime start, LocalDateTime end, AssignmentStatus status);

    int countByTeacherIdAndStatus(long teacherId, AssignmentStatus status);

    Page<Assignment> findByClassroom_ClassCodeAndStatus(String classCode, AssignmentStatus status, Pageable pageable);

    List<Assignment> findByParentId(Long parentId);

    @Query("SELECT COUNT(a) FROM Assignment a " +
           "JOIN a.classroom c " +
           "JOIN c.students s " +
           "WHERE s.id = :studentId " +
           "AND a.status = 'PUBLISHED' " +
           "AND NOT EXISTS (SELECT sub FROM Submission sub WHERE sub.assignment = a AND sub.student.id = :studentId AND sub.status <> 'DRAFT')")
    int countPendingAssignmentsForStudent(@Param("studentId") long studentId);

    @Query("SELECT a FROM Assignment a " +
           "JOIN a.classroom c " +
           "JOIN c.students s " +
           "WHERE s.id = :studentId " +
           "AND a.status = 'PUBLISHED' " +
           "AND NOT EXISTS (SELECT sub FROM Submission sub WHERE sub.assignment = a AND sub.student.id = :studentId AND sub.status <> 'DRAFT') " +
           "ORDER BY a.deadline ASC")
    Page<Assignment> findPendingAssignmentsForStudent(@Param("studentId") long studentId, Pageable pageable);

    @Query("SELECT a FROM Assignment a WHERE a.teacher.id = :teacherId AND a.deadline < :now AND a.status = 'PUBLISHED'")
    List<Assignment> findAssignmentsPastDeadline(@Param("teacherId") long teacherId, @Param("now") LocalDateTime now);
}
