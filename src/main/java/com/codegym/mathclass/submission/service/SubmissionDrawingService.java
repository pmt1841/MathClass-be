package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.SubmissionDrawingRequest;
import com.codegym.mathclass.submission.dto.SubmissionDrawingResponse;

public interface SubmissionDrawingService {
    SubmissionDrawingResponse saveOrUpdateDrawing(long submissionId, SubmissionDrawingRequest request,
            String currentUserUsername);

    SubmissionDrawingResponse getDrawingBySubmissionId(long submissionId, String currentUserUsername);
}
