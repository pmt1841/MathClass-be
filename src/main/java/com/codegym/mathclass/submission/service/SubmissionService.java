package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.SubmissionRequest;
import com.codegym.mathclass.submission.dto.SubmissionResponse;

import java.util.List;

public interface SubmissionService {
    SubmissionResponse createSubmission(long studentId, SubmissionRequest requestDto);
    SubmissionResponse updateSubmission(long submissionId, long studentId, SubmissionRequest requestDto);
    SubmissionResponse unsubmitSubmission(long submissionId, long studentId);
    SubmissionResponse gradeSubmission(long submissionId, long teacherId, com.codegym.mathclass.submission.dto.GradeRequest requestDto);
    SubmissionResponse getMySubmission(long assignmentId, long studentId);
    org.springframework.data.domain.Page<SubmissionResponse> getSubmissionsByAssignment(long assignmentId, long teacherId, com.codegym.mathclass.submission.entity.SubmissionStatus status, String keyword, org.springframework.data.domain.Pageable pageable);
}
