package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.SubmissionCommentRequest;
import com.codegym.mathclass.submission.dto.SubmissionCommentResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionComment;
import com.codegym.mathclass.submission.repository.SubmissionCommentRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.service.SubmissionCommentService;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionCommentServiceImpl implements SubmissionCommentService {

    private final SubmissionCommentRepository submissionCommentRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionCommentResponse> getCommentsBySubmissionId(Long submissionId) {
        // Kiểm tra submission tồn tại
        if (!submissionRepository.existsById(submissionId)) {
            throw new ResourceNotFoundException("Không tìm thấy bài nộp với ID: " + submissionId);
        }

        List<SubmissionComment> comments = submissionCommentRepository.findBySubmissionIdOrderByCreatedAtAsc(submissionId);
        return comments.stream()
                .map(SubmissionCommentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SubmissionCommentResponse addComment(Long submissionId, Long teacherId, SubmissionCommentRequest request) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp với ID: " + submissionId));

        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giáo viên với ID: " + teacherId));

        SubmissionComment comment = SubmissionComment.builder()
                .submission(submission)
                .teacher(teacher)
                .quoteText(request.getQuoteText())
                .occurrenceIndex(request.getOccurrenceIndex())
                .imageCode(request.getImageCode())
                .content(request.getContent())
                .build();

        comment = submissionCommentRepository.save(comment);

        return SubmissionCommentResponse.fromEntity(comment);
    }

    @Override
    @Transactional
    public void deleteComment(Long submissionId, Long commentId, Long teacherId) {
        SubmissionComment comment = submissionCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhận xét với ID: " + commentId));

        if (comment.getSubmission().getId() != submissionId) {
            throw new BadRequestException("Nhận xét không thuộc về bài nộp này");
        }

        // Chỉ giáo viên tạo nhận xét mới có quyền xóa
        if (comment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xóa nhận xét này");
        }

        submissionCommentRepository.delete(comment);
    }
}
