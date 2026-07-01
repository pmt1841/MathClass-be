package com.codegym.mathclass.submission.repository;

import com.codegym.mathclass.submission.entity.SubmissionComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionCommentRepository extends JpaRepository<SubmissionComment, Long> {
    List<SubmissionComment> findBySubmissionIdOrderByCreatedAtAsc(Long submissionId);
}
