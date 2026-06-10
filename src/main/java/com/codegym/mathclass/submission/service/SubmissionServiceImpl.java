package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.SubmissionRequestDto;
import com.codegym.mathclass.submission.dto.SubmissionResponseDto;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public SubmissionResponseDto saveSubmission(long assignmentId, long studentId, SubmissionRequestDto requestDto) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));
        
        // Kiểm tra xem đã hết hạn nộp chưa (nếu trạng thái bài tập không có deadline thì bỏ qua)
        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh"));

        Submission submission = submissionRepository.findFirstByAssignmentIdAndStudentId(assignmentId, studentId)
                .orElse(new Submission());

        submission.setAssignment(assignment);
        submission.setStudent(student);

        String content = requestDto.getContent();
        if (content == null) {
            content = "";
        }

        if (requestDto.getStatus() == SubmissionStatus.SUBMITTED) {
            if (content.trim().isEmpty()) {
                throw new BadRequestException("Nội dung bài làm không được để trống khi nộp bài");
            }
            if (submission.getSubmittedAt() == null) {
                submission.setSubmittedAt(LocalDateTime.now());
            }
        }

        submission.setContent(content);
        submission.setStatus(requestDto.getStatus());

        Submission savedSubmission = submissionRepository.save(submission);
        return mapToDto(savedSubmission);
    }

    @Override
    public SubmissionResponseDto getMySubmission(long assignmentId, long studentId) {
        return submissionRepository.findFirstByAssignmentIdAndStudentId(assignmentId, studentId)
                .map(this::mapToDto)
                .orElse(null);
    }

    @Override
    public List<SubmissionResponseDto> getSubmissionsByAssignment(long assignmentId, long teacherId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));
                
        // Xác minh xem giáo viên này có phải là người tạo bài tập không
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xem danh sách bài nộp này");
        }

        List<Submission> submissions = submissionRepository.findAllByAssignmentIdOrderByUpdatedAtDesc(assignmentId);
        return submissions.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private SubmissionResponseDto mapToDto(Submission submission) {
        return SubmissionResponseDto.builder()
                .id(submission.getId())
                .assignmentId(submission.getAssignment().getId())
                .studentId(submission.getStudent().getId())
                .studentName(submission.getStudent().getFullName())
                .content(submission.getContent())
                .status(submission.getStatus())
                .submittedAt(submission.getSubmittedAt())
                .updatedAt(submission.getUpdatedAt())
                .build();
    }
}
