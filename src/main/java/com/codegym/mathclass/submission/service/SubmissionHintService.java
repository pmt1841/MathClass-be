package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.request.StudentHintRequest;
import com.codegym.mathclass.submission.dto.response.HintHistoryResponse;
import com.codegym.mathclass.submission.dto.response.StudentHintResponse;

public interface SubmissionHintService {

    StudentHintResponse requestHint(Long assignmentId, StudentHintRequest request, String studentEmail);

    HintHistoryResponse getHintHistory(Long submissionId, String currentUserEmail);
}
