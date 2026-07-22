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
import com.codegym.mathclass.utils.EmailService;
import com.codegym.mathclass.utils.LaTeXSanitizer;
import com.codegym.mathclass.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

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

        boolean isNewlySubmitted = (submission.getStatus() != SubmissionStatus.SUBMITTED && requestDto.getStatus() == SubmissionStatus.SUBMITTED);

        String content = requestDto.getContent() == null ? "" : requestDto.getContent();

        if (content != null && !LaTeXSanitizer.isSafe(content)) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(content);
            throw new BadRequestException("Nội dung bài làm chứa lệnh LaTeX không hợp lệ: " + dangerous);
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

        if (isNewlySubmitted) {
            sendSubmissionNotificationToTeacher(savedSubmission, assignment);
        }

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

        boolean isNewlySubmitted = (submission.getStatus() != SubmissionStatus.SUBMITTED && requestDto.getStatus() == SubmissionStatus.SUBMITTED);

        Assignment assignment = submission.getAssignment();
        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập");
        }

        if (submission.getScore() != null) {
            throw new BadRequestException("Giáo viên đã chấm điểm, không thể sửa bài");
        }

        String content = requestDto.getContent() == null ? "" : requestDto.getContent();

        if (content != null && !LaTeXSanitizer.isSafe(content)) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(content);
            throw new BadRequestException("Nội dung bài làm chứa lệnh LaTeX không hợp lệ: " + dangerous);
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

        if (isNewlySubmitted) {
            sendSubmissionNotificationToTeacher(savedSubmission, assignment);
        }

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
        // Có thể reset submittedAt nếu muốn, nhưng giữ lại cũng không sao để biết lần
        // nộp gần nhất

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

        if (requestDto.getTeacherFeedback() != null && !LaTeXSanitizer.isSafe(requestDto.getTeacherFeedback())) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(requestDto.getTeacherFeedback());
            throw new BadRequestException("Nội dung phản hồi chứa lệnh LaTeX không hợp lệ: " + dangerous);
        }

        submission.setScore(requestDto.getScore());
        submission.setTeacherFeedback(requestDto.getTeacherFeedback());
        submission.setStatus(SubmissionStatus.GRADED);

        Submission savedSubmission = submissionRepository.save(submission);

        // Send email notification to student
        String subject = "Giáo viên đã chấm điểm bài tập: " + assignment.getTitle();
        String classCodeParam = assignment.getClassroom() != null ? "?classCode=" + assignment.getClassroom().getClassCode() : "";
        String relativeLink = "/assignments/" + assignment.getId() + classCodeParam;
        String link = frontendUrl + relativeLink;
        Context context = new Context();
        context.setVariable("studentName", submission.getStudent().getFullName());
        context.setVariable("assignmentName", assignment.getTitle());
        context.setVariable("link", link);
        emailService.sendHtmlMailAsync(submission.getStudent().getEmail(), subject, "submission-graded", context);

        notificationService.saveAndSendNotification(submission.getStudent().getId(), subject, relativeLink);

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
    public Page<SubmissionResponse> getSubmissionsByAssignment(
            long assignmentId,
            long teacherId,
            SubmissionStatus status,
            String keyword,
            Pageable pageable) {

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập"));

        if (assignment.getTeacher().getId() != teacherId) {
            throw new AccessDeniedException("Bạn không có quyền xem danh sách bài nộp này");
        }

        String searchKeyword = (keyword == null) ? "" : keyword;

        Page<Submission> submissionPage = submissionRepository
                .findSubmissionsByAssignment(
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

    private void sendSubmissionNotificationToTeacher(Submission submission, Assignment assignment) {
        User teacher = assignment.getTeacher();
        User student = submission.getStudent();

        String subject = "Học sinh " + student.getFullName() + " đã nộp bài tập: " + assignment.getTitle();
        String link = frontendUrl + "/assignments/" + assignment.getId() + "/submissions/" + submission.getId();

        Context context = new Context();
        context.setVariable("teacherName", teacher.getFullName());
        context.setVariable("studentName", student.getFullName());
        context.setVariable("assignmentName", assignment.getTitle());
        context.setVariable("link", link);

        emailService.sendHtmlMailAsync(teacher.getEmail(), subject, "submission-submitted", context);
        notificationService.saveAndSendNotification(teacher.getId(), subject, link);
    }
}
