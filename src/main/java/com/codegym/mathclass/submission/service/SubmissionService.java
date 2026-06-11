package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.SubmissionRequestDto;
import com.codegym.mathclass.submission.dto.SubmissionResponseDto;

import java.util.List;

public interface SubmissionService {
    SubmissionResponseDto createSubmission(long studentId, SubmissionRequestDto requestDto);
    SubmissionResponseDto updateSubmission(long submissionId, long studentId, SubmissionRequestDto requestDto);
    SubmissionResponseDto unsubmitSubmission(long submissionId, long studentId);
    SubmissionResponseDto gradeSubmission(long submissionId, long teacherId, com.codegym.mathclass.submission.dto.GradeRequestDto requestDto);
    SubmissionResponseDto getMySubmission(long assignmentId, long studentId);
    List<SubmissionResponseDto> getSubmissionsByAssignment(long assignmentId, long teacherId);
}
