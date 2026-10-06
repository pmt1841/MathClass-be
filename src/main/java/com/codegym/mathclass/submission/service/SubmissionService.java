package com.codegym.mathclass.submission.service;

import java.util.List;
import com.codegym.mathclass.submission.dto.request.SubmissionRequest;
import com.codegym.mathclass.submission.dto.response.SubmissionResponse;
import com.codegym.mathclass.submission.dto.response.SubmissionVersionResponse;
import com.codegym.mathclass.submission.dto.request.GradeRequest;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubmissionService {
    SubmissionResponse createSubmission(long studentId, SubmissionRequest request);

    SubmissionResponse updateSubmission(long submissionId, long studentId, SubmissionRequest request);

    SubmissionResponse unsubmitSubmission(long submissionId, long studentId);

    SubmissionResponse gradeSubmission(long submissionId, long teacherId,
            GradeRequest request);

    SubmissionResponse getMySubmission(long assignmentId, long studentId);

    Page<SubmissionResponse> getSubmissionsByAssignment(long assignmentId,
            long teacherId, SubmissionStatus status, String keyword,
            Pageable pageable);

    SubmissionResponse getSubmissionDetail(long submissionId, long teacherId);

    SubmissionResponse resubmitSubmission(long submissionId, long studentId, SubmissionRequest request);

    List<SubmissionVersionResponse> getSubmissionVersions(long submissionId, long userId);
}
