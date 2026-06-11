package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.GradeRequestDto;
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
    public SubmissionResponseDto createSubmission(long studentId, SubmissionRequestDto requestDto) {
        if (requestDto.getAssignmentId() == null) {
            throw new BadRequestException("Thiếu assignmentId");
        }

        Assignment assignment = assignmentRepository.findById(requestDto.getAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));
        
        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh"));

        Submission submission = submissionRepository.findFirstByAssignmentIdAndStudentId(assignment.getId(), studentId)
                .orElse(new Submission());

        submission.setAssignment(assignment);
        submission.setStudent(student);

        String content = requestDto.getContent() == null ? "" : requestDto.getContent();

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
    @Transactional
    public SubmissionResponseDto updateSubmission(long submissionId, long studentId, SubmissionRequestDto requestDto) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        if (submission.getStudent().getId() != studentId) {
            throw new AccessDeniedException("Bạn không có quyền sửa bài nộp này");
        }

        Assignment assignment = submission.getAssignment();
        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập");
        }

        if (submission.getScore() != null) {
            throw new BadRequestException("Giáo viên đã chấm điểm, không thể sửa bài");
        }

        String content = requestDto.getContent() == null ? "" : requestDto.getContent();

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
    @Transactional
    public SubmissionResponseDto unsubmitSubmission(long submissionId, long studentId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        if (submission.getStudent().getId() != studentId) {
            throw new AccessDeniedException("Bạn không có quyền hủy bài nộp này");
        }

        Assignment assignment = submission.getAssignment();
        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập, không thể hủy nộp");
        }

        if (submission.getScore() != null) {
            throw new BadRequestException("Giáo viên đã chấm điểm, không thể hủy nộp");
        }

        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BadRequestException("Bài làm chưa được nộp");
        }

        submission.setStatus(SubmissionStatus.DRAFT);
        // Có thể reset submittedAt nếu muốn, nhưng giữ lại cũng không sao để biết lần nộp gần nhất
        
        Submission savedSubmission = submissionRepository.save(submission);
        return mapToDto(savedSubmission);
    }

    @Override
    @Transactional
    public SubmissionResponseDto gradeSubmission(long submissionId, long teacherId, GradeRequestDto requestDto) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        Assignment assignment = submission.getAssignment();
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền chấm bài nộp này");
        }

        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new BadRequestException("Học sinh chưa nộp bài");
        }

        submission.setScore(requestDto.getScore());
        
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
                
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xem danh sách bài nộp này");
        }

        // Theo yêu cầu: giáo viên chỉ thấy bài ĐÃ NỘP (SUBMITTED)
        List<Submission> submissions = submissionRepository.findAllByAssignmentIdAndStatusOrderByUpdatedAtDesc(assignmentId, SubmissionStatus.SUBMITTED);
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
                .score(submission.getScore())
                .submittedAt(submission.getSubmittedAt())
                .updatedAt(submission.getUpdatedAt())
                .build();
    }
}
