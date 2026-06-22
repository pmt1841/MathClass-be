package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.SubmissionRequest;
import com.codegym.mathclass.submission.dto.SubmissionResponse;
import com.codegym.mathclass.submission.dto.GradeRequest;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubmissionService {
    SubmissionResponse createSubmission(long studentId, SubmissionRequest requestDto);

    SubmissionResponse updateSubmission(long submissionId, long studentId, SubmissionRequest requestDto);

    SubmissionResponse unsubmitSubmission(long submissionId, long studentId);

    SubmissionResponse gradeSubmission(long submissionId, long teacherId,
            GradeRequest requestDto);

    SubmissionResponse getMySubmission(long assignmentId, long studentId);

    Page<SubmissionResponse> getSubmissionsByAssignment(long assignmentId,
            long teacherId, SubmissionStatus status, String keyword,
            Pageable pageable);

    SubmissionResponse getSubmissionDetail(long submissionId, long teacherId);
}
