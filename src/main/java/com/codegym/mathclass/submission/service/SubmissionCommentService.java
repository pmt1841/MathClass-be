package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.request.SubmissionCommentRequest;
import com.codegym.mathclass.submission.dto.response.SubmissionCommentResponse;

import java.util.List;

public interface SubmissionCommentService {
    List<SubmissionCommentResponse> getCommentsBySubmissionId(Long submissionId, Integer versionNumber, String currentUserEmail);
    
    SubmissionCommentResponse addComment(Long submissionId, Long teacherId, SubmissionCommentRequest request);
    
    void deleteComment(Long submissionId, Long commentId, Long teacherId);
}
