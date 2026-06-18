package com.codegym.mathclass.submission.repository;

import com.codegym.mathclass.submission.entity.SubmissionDrawing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubmissionDrawingRepository extends JpaRepository<SubmissionDrawing, Long> {
    Optional<SubmissionDrawing> findBySubmissionId(long submissionId);
}
