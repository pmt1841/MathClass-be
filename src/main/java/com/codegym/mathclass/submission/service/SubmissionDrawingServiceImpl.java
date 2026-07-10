package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.SubmissionDrawingRequest;
import com.codegym.mathclass.submission.dto.SubmissionDrawingResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionDrawing;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionDrawingRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubmissionDrawingServiceImpl implements SubmissionDrawingService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionDrawingRepository submissionDrawingRepository;

    @Override
    @Transactional
    public SubmissionDrawingResponse saveOrUpdateDrawing(long submissionId, SubmissionDrawingRequest request,
            String currentUserUsername) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        // Check if current user is the owner of the submission
        if (!submission.getStudent().getEmail().equals(currentUserUsername)) {
            throw new AccessDeniedException("You are not allowed to modify this submission");
        }

        // Validate submission status
        if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
            throw new AccessDeniedException("Submission is already submitted. Please un-submit to edit your drawing.");
        }

        SubmissionDrawing drawing = submissionDrawingRepository.findBySubmissionId(submissionId)
                .orElse(SubmissionDrawing.builder()
                        .submission(submission)
                        .build());

        drawing.setShapeCode(request.getShapeCode());
        drawing.setJsxGraphData(request.getJsxGraphData());
        drawing.setMetadata(request.getMetadata());

        SubmissionDrawing savedDrawing = submissionDrawingRepository.save(drawing);

        return mapToResponse(savedDrawing);
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionDrawingResponse getDrawingBySubmissionId(long submissionId, String currentUserUsername) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + submissionId));

        boolean isStudentOwner = submission.getStudent().getEmail().equals(currentUserUsername);
        boolean isTeacherOwner = submission.getAssignment().getTeacher().getEmail().equals(currentUserUsername);

        if (!isStudentOwner && !isTeacherOwner) {
            throw new AccessDeniedException("You are not allowed to view this drawing");
        }

        SubmissionDrawing drawing = submissionDrawingRepository.findBySubmissionId(submission.getId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Drawing not found for submission id: " + submissionId));

        return mapToResponse(drawing);
    }

    private SubmissionDrawingResponse mapToResponse(SubmissionDrawing drawing) {
        return SubmissionDrawingResponse.builder()
                .id(drawing.getId())
                .submissionId(drawing.getSubmission().getId())
                .shapeCode(drawing.getShapeCode())
                .jsxGraphData(drawing.getJsxGraphData())
                .metadata(drawing.getMetadata())
                .createdAt(drawing.getCreatedAt())
                .updatedAt(drawing.getUpdatedAt())
                .build();
    }
}
