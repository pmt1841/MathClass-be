package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.SubmissionRequestDto;
import com.codegym.mathclass.submission.dto.SubmissionResponseDto;

import java.util.List;

public interface SubmissionService {
    SubmissionResponseDto saveSubmission(long assignmentId, long studentId, SubmissionRequestDto requestDto);
    SubmissionResponseDto getMySubmission(long assignmentId, long studentId);
    List<SubmissionResponseDto> getSubmissionsByAssignment(long assignmentId, long teacherId);
}
