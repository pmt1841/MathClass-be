package com.codegym.mathclass.notification.service.impl;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationJobService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final EmailService emailService;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    // Chạy mỗi giờ
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void sendDeadlineReminders() {
        LocalDateTime now = LocalDateTime.now();
        // Lấy khoảng thời gian từ 23h đến 24h tới
        LocalDateTime startWindow = now.plusHours(23);
        LocalDateTime endWindow = now.plusHours(24);

        log.info("Bắt đầu quét Cronjob nhắc nhở bài tập. Khoảng thời gian: {} đến {}", startWindow, endWindow);

        List<Assignment> assignments = assignmentRepository.findByDeadlineBetweenAndIsReminderSentFalseAndStatus(
                startWindow, endWindow, AssignmentStatus.PUBLISHED);

        if (assignments.isEmpty()) {
            return;
        }

        for (Assignment assignment : assignments) {
            log.info("Tìm thấy bài tập sắp đến hạn: {} (ID: {})", assignment.getTitle(), assignment.getId());
            int sentCount = 0;

            for (User student : assignment.getClassroom().getStudents()) {
                // Kiểm tra xem học sinh đã nộp bài chưa (Status khác DRAFT)
                boolean hasSubmitted = submissionRepository.existsByAssignmentIdAndStudentIdAndStatusNot(
                        assignment.getId(), student.getId(), SubmissionStatus.DRAFT);

                if (!hasSubmitted) {
                    // Kiểm tra cấu hình thông báo
                    NotificationSettings settings = notificationSettingsRepository.findByUserId(student.getId())
                            .orElse(NotificationSettings.builder().build()); // Default là true hết

                    if (settings.isMasterEmail() && settings.isStudentDeadlineReminder()) {
                        sendReminderEmail(student, assignment);
                        sentCount++;
                    }
                }
            }

            // Đánh dấu đã gửi nhắc nhở cho bài tập này
            assignment.setReminderSent(true);
            assignmentRepository.save(assignment);

            log.info("Đã hoàn tất gửi {} email nhắc nhở cho bài tập ID: {}", sentCount, assignment.getId());
        }
    }

    private void sendReminderEmail(User student, Assignment assignment) {
        Context context = new Context();
        context.setVariable("studentName", student.getFullName());
        context.setVariable("assignmentTitle", assignment.getTitle());
        context.setVariable("className", assignment.getClassroom().getClassName());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
        String deadlineStr = assignment.getDeadline() != null ? assignment.getDeadline().format(formatter)
                : "Không xác định";
        context.setVariable("deadline", deadlineStr);

        // Sử dụng biến FRONTEND_URL lấy từ .env
        context.setVariable("assignmentUrl", frontendUrl + "/assignments/" + assignment.getId());

        emailService.sendHtmlMailAsync(
                student.getEmail(),
                "Nhắc nhở: Sắp đến hạn nộp bài tập " + assignment.getTitle(),
                "assignment-reminder",
                context);
    }
}
