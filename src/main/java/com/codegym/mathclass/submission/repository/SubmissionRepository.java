package com.codegym.mathclass.submission.repository;

import com.codegym.mathclass.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.codegym.mathclass.submission.entity.SubmissionStatus;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findFirstByAssignmentIdAndStudentId(long assignmentId, long studentId);

    /**
     * Batch query: lấy submission của một học sinh cho nhiều assignments cùng lúc.
     * Dùng để tránh N+1 khi hiển thị danh sách phiếu bài tập cho STUDENT.
     */
    @Query("""
            SELECT s FROM Submission s
            WHERE s.assignment.id IN :assignmentIds
            AND s.student.id = :studentId
            """)
    List<Submission> findAllByAssignmentIdInAndStudentId(
            @Param("assignmentIds") List<Long> assignmentIds,
            @Param("studentId") long studentId
    );
    
    List<Submission> findAllByAssignmentIdIn(List<Long> assignmentIds);
    
    // Lấy danh sách bài nộp và sắp xếp theo thời gian nộp hoặc cập nhật mới nhất
    List<Submission> findAllByAssignmentIdOrderByUpdatedAtDesc(long assignmentId);

    List<Submission> findAllByAssignmentIdAndStatusOrderByUpdatedAtDesc(long assignmentId, SubmissionStatus status);

    boolean existsByAssignmentId(long assignmentId);

    boolean existsByAssignmentIdAndStudentIdAndStatusNot(long assignmentId, long studentId, SubmissionStatus status);

    @Query("SELECT s FROM Submission s WHERE s.assignment.id = :assignmentId " +
            "AND s.status <> 'DRAFT' " +
            "AND (:status IS NULL OR s.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(s.student.fullName) LIKE :keyword)")
    Page<Submission> findSubmissionsByAssignment(
            @Param("assignmentId") long assignmentId,
            @Param("status") SubmissionStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT s.student.id AS studentId,
                   s.student.fullName AS studentName,
                   s.student.email AS studentEmail,
                   COUNT(s.id) AS completedCount,
                   MAX(s.submittedAt) AS latestSubmittedAt,
                   SUM(s.score) AS totalScore
            FROM Submission s
            WHERE s.assignment.id IN :assignmentIds
              AND s.status <> 'DRAFT'
            GROUP BY s.student.id, s.student.fullName, s.student.email
            HAVING COUNT(s.id) = :totalExercises
            """)
    Page<CompletedStudentProjection> findCompletedStudentsForSheet(
            @Param("assignmentIds") List<Long> assignmentIds,
            @Param("totalExercises") long totalExercises,
            Pageable pageable
    );

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.assignment.classroom.teacher.id = :teacherId AND s.status = :status")
    int countByTeacherAndStatus(@Param("teacherId") long teacherId, @Param("status") SubmissionStatus status);

    @Query("SELECT s FROM Submission s WHERE s.assignment.classroom.teacher.id = :teacherId AND s.status = 'SUBMITTED' ORDER BY s.submittedAt DESC")
    Page<Submission> findPendingSubmissionsByTeacher(@Param("teacherId") long teacherId, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.student.id = :studentId AND s.status = :status")
    int countByStudentAndStatus(@Param("studentId") long studentId, @Param("status") SubmissionStatus status);

    @Query("SELECT s FROM Submission s WHERE s.student.id = :studentId AND s.status = 'GRADED' ORDER BY s.updatedAt DESC")
    Page<Submission> findGradedSubmissionsByStudent(@Param("studentId") long studentId, Pageable pageable);

    @Query("SELECT s.student, AVG(s.score) FROM Submission s WHERE s.assignment.classroom.teacher.id = :teacherId AND s.status = 'GRADED' GROUP BY s.student HAVING AVG(s.score) < 5.0")
    List<Object[]> findStudentsWithLowAverageScore(@Param("teacherId") long teacherId);
}
