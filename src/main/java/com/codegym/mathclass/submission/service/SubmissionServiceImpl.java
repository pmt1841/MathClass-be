package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.GradeRequest;
import com.codegym.mathclass.submission.dto.SubmissionRequest;
import com.codegym.mathclass.submission.dto.SubmissionResponse;
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
    public SubmissionResponse createSubmission(long studentId, SubmissionRequest requestDto) {
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
    public SubmissionResponse updateSubmission(long submissionId, long studentId, SubmissionRequest requestDto) {
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
    public SubmissionResponse unsubmitSubmission(long submissionId, long studentId) {
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
    public SubmissionResponse gradeSubmission(long submissionId, long teacherId, GradeRequest requestDto) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        Assignment assignment = submission.getAssignment();
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền chấm bài nộp này");
        }

        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new BadRequestException("Học sinh chưa nộp bài");
        }

        submission.setScore(requestDto.getScore());
        submission.setTeacherFeedback(requestDto.getTeacherFeedback());
        submission.setStatus(SubmissionStatus.GRADED);
        
        Submission savedSubmission = submissionRepository.save(submission);
        return mapToDto(savedSubmission);
    }

    @Override
    public SubmissionResponse getMySubmission(long assignmentId, long studentId) {
        return submissionRepository.findFirstByAssignmentIdAndStudentId(assignmentId, studentId)
                .map(this::mapToDto)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SubmissionResponse> getSubmissionsByAssignment(
            long assignmentId, 
            long teacherId, 
            SubmissionStatus status, 
            String keyword, 
            org.springframework.data.domain.Pageable pageable) {
        
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));
                
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xem danh sách bài nộp này");
        }

        String searchKeyword = (keyword == null) ? "" : keyword;

        org.springframework.data.domain.Page<Submission> submissionPage = submissionRepository.findSubmissionsByAssignment(
                assignmentId, status, searchKeyword, pageable);
                
        return submissionPage.map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public SubmissionResponse getSubmissionDetail(long submissionId, long teacherId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        Assignment assignment = submission.getAssignment();
        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xem bài nộp này");
        }

        return mapToDto(submission);
    }

    private SubmissionResponse mapToDto(Submission submission) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .assignmentId(submission.getAssignment().getId())
                .studentId(submission.getStudent().getId())
                .studentName(submission.getStudent().getFullName())
                .content(submission.getContent())
                .teacherFeedback(submission.getTeacherFeedback())
                .status(submission.getStatus())
                .score(submission.getScore())
                .submittedAt(submission.getSubmittedAt())
                .updatedAt(submission.getUpdatedAt())
                .build();
    }
}
