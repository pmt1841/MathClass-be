package com.codegym.mathclass.submission.repository;

import com.codegym.mathclass.submission.entity.SubmissionHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionHintRepository extends JpaRepository<SubmissionHint, Long> {

    int countBySubmissionId(Long submissionId);

    List<SubmissionHint> findBySubmissionIdOrderByHintNumberAsc(Long submissionId);
}
