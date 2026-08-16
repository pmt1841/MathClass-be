package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.GradeRequest;
import com.codegym.mathclass.submission.dto.SubmissionRequest;
import com.codegym.mathclass.submission.dto.SubmissionResponse;
import com.codegym.mathclass.submission.dto.SubmissionVersionResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.entity.SubmissionVersion;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.repository.SubmissionVersionRepository;
import com.codegym.mathclass.submission.service.SubmissionService;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private static final int MAX_SUBMISSION_VERSIONS = 3;

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository submissionVersionRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Value("${FRONTEND_URL}")
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
                .orElseGet(() -> Submission.builder()
                        .assignment(assignment)
                        .student(student)
                        .status(SubmissionStatus.DRAFT)
                        .content("")
                        .build());

        boolean isNewlySubmitted = (submission.getStatus() != SubmissionStatus.SUBMITTED
                && requestDto.getStatus() == SubmissionStatus.SUBMITTED);

        String content = Objects.requireNonNullElse(requestDto.getContent(), "");

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
            if (submissionVersionRepository.findMaxVersionNumberBySubmissionId(savedSubmission.getId()) == 0) {
                SubmissionVersion v1 = SubmissionVersion.builder()
                        .submission(savedSubmission)
                        .versionNumber(1)
                        .content(savedSubmission.getContent())
                        .submittedAt(savedSubmission.getSubmittedAt())
                        .build();
                submissionVersionRepository.save(v1);
            }
            sendSubmissionNotificationToTeacher(savedSubmission, assignment, 1);
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

        if (submission.getStatus() == SubmissionStatus.SUBMITTED && requestDto.getStatus() == SubmissionStatus.DRAFT) {
            requestDto.setStatus(SubmissionStatus.SUBMITTED);
        }

        boolean isNewlySubmitted = (submission.getStatus() != SubmissionStatus.SUBMITTED
                && requestDto.getStatus() == SubmissionStatus.SUBMITTED);

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
            if (submissionVersionRepository.findMaxVersionNumberBySubmissionId(savedSubmission.getId()) == 0) {
                SubmissionVersion v1 = SubmissionVersion.builder()
                        .submission(savedSubmission)
                        .versionNumber(1)
                        .content(savedSubmission.getContent())
                        .submittedAt(savedSubmission.getSubmittedAt())
                        .build();
                submissionVersionRepository.save(v1);
            }
            sendSubmissionNotificationToTeacher(savedSubmission, assignment, 1);
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

        Double maxScore = assignment.getMaxScore() != null ? assignment.getMaxScore() : 10.0;
        if (requestDto.getScore() != null && requestDto.getScore() > maxScore) {
            throw new BadRequestException("Điểm số không được vượt quá điểm tối đa (" + maxScore + ")");
        }

        if (requestDto.getTeacherFeedback() != null && !LaTeXSanitizer.isSafe(requestDto.getTeacherFeedback())) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(requestDto.getTeacherFeedback());
            throw new BadRequestException("Nội dung phản hồi chứa lệnh LaTeX không hợp lệ: " + dangerous);
        }

        submission.setScore(requestDto.getScore());
        submission.setTeacherFeedback(requestDto.getTeacherFeedback());
        submission.setStatus(SubmissionStatus.GRADED);

        Submission savedSubmission = submissionRepository.save(submission);

        submissionVersionRepository.findFirstBySubmissionIdOrderByVersionNumberDesc(savedSubmission.getId())
                .ifPresent(v -> {
                    v.setScore(savedSubmission.getScore());
                    v.setTeacherFeedback(savedSubmission.getTeacherFeedback());
                    submissionVersionRepository.save(v);
                });

        if (assignment.getAssignmentSheet() != null) {
            checkAndProcessSheetNotification(assignment, submission, true);
        } else {
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
        }

        return mapToDto(savedSubmission);
    }

    @Override
    @Transactional
    public SubmissionResponse resubmitSubmission(long submissionId, long studentId, SubmissionRequest requestDto) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        if (submission.getStudent().getId() != studentId) {
            throw new AccessDeniedException("Bạn không có quyền nộp lại bài này");
        }

        Assignment assignment = submission.getAssignment();
        if (!assignment.isAllowResubmit()) {
            throw new BadRequestException("Bài tập này không cho phép nộp lại");
        }

        if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
            throw new BadRequestException("Đã hết hạn nộp bài tập, không thể nộp lại");
        }

        String content = requestDto.getContent() == null ? "" : requestDto.getContent().trim();
        if (content.isEmpty()) {
            throw new BadRequestException("Nội dung bài làm không được để trống khi nộp bài");
        }

        if (!LaTeXSanitizer.isSafe(content)) {
            String dangerous = LaTeXSanitizer.findDangerousCommand(content);
            throw new BadRequestException("Nội dung bài làm chứa lệnh LaTeX không hợp lệ: " + dangerous);
        }

        int maxVer = submissionVersionRepository.findMaxVersionNumberBySubmissionId(submission.getId());
        if (maxVer >= MAX_SUBMISSION_VERSIONS) {
            throw new BadRequestException("Bạn đã sử dụng hết " + MAX_SUBMISSION_VERSIONS + " lần nộp bài cho bài tập này (tối đa " + MAX_SUBMISSION_VERSIONS + " lần nộp)");
        }

        if (maxVer == 0) {
            SubmissionVersion v1 = SubmissionVersion.builder()
                    .submission(submission)
                    .versionNumber(1)
                    .content(submission.getContent())
                    .score(submission.getScore())
                    .teacherFeedback(submission.getTeacherFeedback())
                    .submittedAt(submission.getSubmittedAt() != null ? submission.getSubmittedAt() : LocalDateTime.now())
                    .build();
            submissionVersionRepository.save(v1);
            maxVer = 1;
        } else {
            submissionVersionRepository.findFirstBySubmissionIdOrderByVersionNumberDesc(submission.getId())
                    .ifPresent(v -> {
                        if (submission.getScore() != null) v.setScore(submission.getScore());
                        if (submission.getTeacherFeedback() != null) v.setTeacherFeedback(submission.getTeacherFeedback());
                        submissionVersionRepository.save(v);
                    });
        }

        int nextVer = maxVer + 1;
        LocalDateTime now = LocalDateTime.now();

        SubmissionVersion newVersion = SubmissionVersion.builder()
                .submission(submission)
                .versionNumber(nextVer)
                .content(content)
                .score(null)
                .teacherFeedback(null)
                .submittedAt(now)
                .build();
        submissionVersionRepository.save(newVersion);

        submission.setContent(content);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setScore(null);
        submission.setTeacherFeedback(null);
        submission.setSubmittedAt(now);

        Submission savedSubmission = submissionRepository.save(submission);

        sendSubmissionNotificationToTeacher(savedSubmission, assignment, nextVer);

        return mapToDto(savedSubmission);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubmissionVersionResponse> getSubmissionVersions(long submissionId, long userId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        boolean isStudent = submission.getStudent().getId() == userId;
        boolean isTeacher = submission.getAssignment().getTeacher().getId() == userId;

        if (!isStudent && !isTeacher) {
            throw new AccessDeniedException("Bạn không có quyền xem lịch sử bài nộp này");
        }

        List<SubmissionVersion> versions = submissionVersionRepository.findBySubmissionIdOrderByVersionNumberAsc(submissionId);
        
        if (versions.isEmpty() && submission.getStatus() != SubmissionStatus.DRAFT) {
            SubmissionVersionResponse v1 = SubmissionVersionResponse.builder()
                    .id(0)
                    .submissionId(submission.getId())
                    .versionNumber(1)
                    .content(submission.getContent())
                    .score(submission.getScore())
                    .teacherFeedback(submission.getTeacherFeedback())
                    .submittedAt(submission.getSubmittedAt())
                    .createdAt(submission.getCreatedAt())
                    .build();
            return List.of(v1);
        }

        return versions.stream()
                .map(SubmissionVersionResponse::fromEntity)
                .toList();
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

        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? "%" + keyword.trim().toLowerCase() + "%" : null;

        Page<Submission> submissionPage = submissionRepository
                .findSubmissionsByAssignment(
                        assignmentId, status, searchKeyword, pageable);

        List<Long> submissionIds = submissionPage.getContent().stream().map(Submission::getId).toList();
        Map<Long, Integer> maxVersionMap = submissionIds.isEmpty() ? Collections.emptyMap() :
                submissionVersionRepository.findMaxVersionNumbersBySubmissionIds(submissionIds).stream()
                        .collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> ((Number) row[1]).intValue()
                        ));

        return submissionPage.map(sub -> mapToDto(sub, maxVersionMap.getOrDefault(sub.getId(), 0)));
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
        int totalVersions = submissionVersionRepository.findMaxVersionNumberBySubmissionId(submission.getId());
        return mapToDto(submission, totalVersions);
    }

    private SubmissionResponse mapToDto(Submission submission, int maxVer) {
        int totalVersions = maxVer;
        if (totalVersions == 0 && submission.getStatus() != SubmissionStatus.DRAFT) {
            totalVersions = 1;
        }

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
                .allowResubmit(submission.getAssignment() != null ? submission.getAssignment().isAllowResubmit() : false)
                .versionNumber(totalVersions > 0 ? totalVersions : 1)
                .totalVersions(totalVersions)
                .build();
    }

    private void sendSubmissionNotificationToTeacher(Submission submission, Assignment assignment, int versionNumber) {
        User teacher = assignment.getTeacher();
        User student = submission.getStudent();

        if (assignment.getAssignmentSheet() != null) {
            checkAndProcessSheetNotification(assignment, submission, false);
        } else {
            String subject = (versionNumber > 1)
                    ? "Học sinh " + student.getFullName() + " đã làm lại bài tập (Lần " + versionNumber + "): " + assignment.getTitle()
                    : "Học sinh " + student.getFullName() + " đã nộp bài tập: " + assignment.getTitle();
            String relativeLink = "/assignments/" + assignment.getId() + "/submissions/" + submission.getId();
            
            String link = frontendUrl + relativeLink;

            Context context = new Context();
            context.setVariable("teacherName", teacher.getFullName());
            context.setVariable("studentName", student.getFullName());
            context.setVariable("assignmentName", assignment.getTitle());
            context.setVariable("link", link);
            context.setVariable("versionNumber", versionNumber);

            emailService.sendHtmlMailAsync(teacher.getEmail(), subject, "submission-submitted", context);
            notificationService.saveAndSendNotification(teacher.getId(), subject, relativeLink);
        }
    }

    private void checkAndProcessSheetNotification(Assignment assignment, Submission currentSubmission, boolean isGrading) {
        AssignmentSheet sheet = assignment.getAssignmentSheet();
        List<Assignment> sheetAssignments = assignmentRepository.findByAssignmentSheetId(sheet.getId());
        if (sheetAssignments.isEmpty()) return;
        
        List<Long> assignmentIds = sheetAssignments.stream().map(Assignment::getId).toList();
        List<Submission> submissions = submissionRepository.findAllByAssignmentIdInAndStudentId(assignmentIds, currentSubmission.getStudent().getId());
        
        long processedCount = submissions.stream()
                .filter(s -> isGrading ? s.getStatus() == SubmissionStatus.GRADED : s.getStatus() != SubmissionStatus.DRAFT)
                .map(s -> s.getAssignment().getId())
                .distinct()
                .count();
                
        if (processedCount == sheetAssignments.size()) {
            sheetAssignments.sort(Comparator.comparing(Assignment::getId));
            Assignment firstAssignment = sheetAssignments.get(0);
            
            User student = currentSubmission.getStudent();
            Context context = new Context();
            context.setVariable("studentName", student.getFullName());
            context.setVariable("assignmentName", sheet.getTitle());
            
            String relativeLink;
            String subject;
            String templateName;
            String emailTo;
            Long notificationUserId;
            
            if (isGrading) {
                subject = "Giáo viên đã chấm điểm phiếu bài tập: " + sheet.getTitle();
                String classCodeParam = firstAssignment.getClassroom() != null ? "?classCode=" + firstAssignment.getClassroom().getClassCode() : "";
                relativeLink = "/assignments/" + firstAssignment.getId() + classCodeParam;
                String delimiter = relativeLink.contains("?") ? "&" : "?";
                relativeLink += delimiter + "sheetId=" + sheet.getId();
                
                templateName = "submission-graded";
                emailTo = student.getEmail();
                notificationUserId = student.getId();
            } else {
                User teacher = assignment.getTeacher();
                subject = "Học sinh " + student.getFullName() + " đã hoàn thành phiếu bài tập: " + sheet.getTitle();
                
                Submission firstSub = submissions.stream()
                        .filter(s -> s.getAssignment().getId() == firstAssignment.getId())
                        .findFirst()
                        .orElse(null);
                        
                relativeLink = "/assignments/" + firstAssignment.getId();
                if (firstSub != null) {
                    relativeLink += "/submissions/" + firstSub.getId() + "?sheetId=" + sheet.getId();
                } else {
                    relativeLink += "?sheetId=" + sheet.getId();
                }
                
                context.setVariable("teacherName", teacher.getFullName());
                templateName = "submission-submitted";
                emailTo = teacher.getEmail();
                notificationUserId = teacher.getId();
            }
            
            context.setVariable("link", frontendUrl + relativeLink);
            emailService.sendHtmlMailAsync(emailTo, subject, templateName, context);
            notificationService.saveAndSendNotification(notificationUserId, subject, relativeLink);
        }
    }
}
