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
    
    // Lấy danh sách bài nộp và sắp xếp theo thời gian nộp hoặc cập nhật mới nhất
    List<Submission> findAllByAssignmentIdOrderByUpdatedAtDesc(long assignmentId);

    List<Submission> findAllByAssignmentIdAndStatusOrderByUpdatedAtDesc(long assignmentId, SubmissionStatus status);

    boolean existsByAssignmentId(long assignmentId);

    @Query("SELECT s FROM Submission s WHERE s.assignment.id = :assignmentId " +
            "AND s.status <> com.codegym.mathclass.submission.entity.SubmissionStatus.DRAFT " +
            "AND (:status IS NULL OR s.status = :status) " +
            "AND LOWER(s.student.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Submission> findSubmissionsByAssignment(
            @Param("assignmentId") long assignmentId,
            @Param("status") SubmissionStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
