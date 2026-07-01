package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.SubmissionCommentRequest;
import com.codegym.mathclass.submission.dto.SubmissionCommentResponse;

import java.util.List;

public interface SubmissionCommentService {
    List<SubmissionCommentResponse> getCommentsBySubmissionId(Long submissionId);
    
    SubmissionCommentResponse addComment(Long submissionId, Long teacherId, SubmissionCommentRequest request);
    
    void deleteComment(Long submissionId, Long commentId, Long teacherId);
}
