package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.request.SubmissionDrawingRequest;
import com.codegym.mathclass.submission.dto.response.SubmissionDrawingResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionDrawing;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionDrawingRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.service.SubmissionDrawingService;
import com.codegym.mathclass.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubmissionDrawingServiceImpl implements SubmissionDrawingService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionDrawingRepository submissionDrawingRepository;
    private final SubmissionMapper submissionMapper;

    @Override
    @Transactional
    public SubmissionDrawingResponse saveOrUpdateDrawing(long submissionId, SubmissionDrawingRequest request,
            String currentUserUsername) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp với mã: " + submissionId));

        // Check if current user is the owner of the submission
        if (!submission.getStudent().getEmail().equals(currentUserUsername)) {
            throw new AccessDeniedException("Bạn không có quyền chỉnh sửa bài nộp này");
        }

        // Validate submission status
        if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
            throw new AccessDeniedException("Bài nộp đã được gửi. Vui lòng hủy nộp bài để chỉnh sửa hình vẽ.");
        }

        SubmissionDrawing drawing = submissionDrawingRepository.findBySubmissionId(submissionId)
                .orElse(SubmissionDrawing.builder()
                        .submission(submission)
                        .build());

        drawing.setShapeCode(request.getShapeCode());
        drawing.setJsxGraphData(request.getJsxGraphData());
        drawing.setMetadata(request.getMetadata());

        SubmissionDrawing savedDrawing = submissionDrawingRepository.save(drawing);

        return submissionMapper.toSubmissionDrawingResponse(savedDrawing);
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionDrawingResponse getDrawingBySubmissionId(long submissionId, String currentUserUsername) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp với mã: " + submissionId));

        boolean isStudentOwner = submission.getStudent().getEmail().equals(currentUserUsername);
        boolean isTeacherOwner = submission.getAssignment().getTeacher().getEmail().equals(currentUserUsername);

        if (!isStudentOwner && !isTeacherOwner) {
            throw new AccessDeniedException("Bạn không có quyền xem bản vẽ này");
        }

        SubmissionDrawing drawing = submissionDrawingRepository.findBySubmissionId(submission.getId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Không tìm thấy bản vẽ của bài nộp mã: " + submissionId));

        return submissionMapper.toSubmissionDrawingResponse(drawing);
    }
}
