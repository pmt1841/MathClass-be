package com.codegym.mathclass.submission.repository;

import com.codegym.mathclass.submission.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    Optional<Submission> findFirstByAssignmentIdAndStudentId(long assignmentId, long studentId);
    
    // Lấy danh sách bài nộp và sắp xếp theo thời gian nộp hoặc cập nhật mới nhất
    List<Submission> findAllByAssignmentIdOrderByUpdatedAtDesc(long assignmentId);

    List<Submission> findAllByAssignmentIdAndStatusOrderByUpdatedAtDesc(long assignmentId, com.codegym.mathclass.submission.entity.SubmissionStatus status);

    boolean existsByAssignmentId(long assignmentId);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM Submission s WHERE s.assignment.id = :assignmentId " +
            "AND s.status <> com.codegym.mathclass.submission.entity.SubmissionStatus.DRAFT " +
            "AND (:status IS NULL OR s.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(s.student.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    org.springframework.data.domain.Page<Submission> findSubmissionsByAssignment(
            @org.springframework.data.repository.query.Param("assignmentId") long assignmentId,
            @org.springframework.data.repository.query.Param("status") com.codegym.mathclass.submission.entity.SubmissionStatus status,
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);
}
