package com.codegym.mathclass.submission.helper;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.utils.EmailService;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;

import java.util.Comparator;
import java.util.List;

/**
 * Helper chuyên trách xử lý gửi email HTML và thông báo in-app cho bài tập và phiếu bài tập.
 * Tách biệt theo nguyên tắc Đơn trách nhiệm (SRP), giúp SubmissionServiceImpl không phụ thuộc vào hạ tầng email.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionNotificationHelper {

    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    /**
     * Gửi thông báo khi học sinh nộp hoặc nộp lại bài tập.
     */
    public void sendSubmissionNotification(Submission submission, Assignment assignment, int versionNumber) {
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

    /**
     * Gửi thông báo khi giáo viên hoàn tất chấm điểm bài tập.
     */
    public void sendGradingNotification(Submission submission, Assignment assignment) {
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
    }

    /**
     * Kiểm tra và gửi thông báo tổng hợp khi học sinh hoàn thành hoặc giáo viên chấm xong toàn bộ phiếu bài tập.
     */
    public void checkAndProcessSheetNotification(Assignment assignment, Submission currentSubmission, boolean isGrading) {
        AssignmentSheet sheet = assignment.getAssignmentSheet();
        if (sheet == null) {
            return;
        }

        List<Assignment> sheetAssignments = assignmentRepository.findByAssignmentSheetId(sheet.getId());
        if (sheetAssignments.isEmpty()) {
            return;
        }

        List<Long> assignmentIds = sheetAssignments.stream().map(Assignment::getId).toList();
        List<Submission> submissions = submissionRepository.findAllByAssignmentIdInAndStudentId(assignmentIds, currentSubmission.getStudent().getId());

        long processedCount = submissions.stream()
                .filter(s -> isGrading ? s.getStatus() == SubmissionStatus.GRADED : s.getStatus() != SubmissionStatus.DRAFT)
                .map(s -> s.getAssignment().getId())
                .distinct()
                .count();

        if (processedCount == sheetAssignments.size()) {
            Assignment firstAssignment = sheetAssignments.stream()
                    .min(Comparator.comparing(Assignment::getId))
                    .orElse(sheetAssignments.get(0));

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
