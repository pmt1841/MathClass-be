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
}
