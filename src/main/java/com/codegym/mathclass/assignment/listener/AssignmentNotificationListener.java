package com.codegym.mathclass.assignment.listener;

import com.codegym.mathclass.assignment.event.AssignmentPublishedEvent;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.context.Context;

/**
 * Listener lắng nghe sự kiện xuất bản bài tập để gửi email thông báo học sinh.
 * Thực thi bất đồng bộ sau khi transaction đã commit thành công.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignmentNotificationListener {

    private final EmailService emailService;

    @Value("${FRONTEND_URL:http://localhost:3000}")
    private String frontendUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAssignmentPublished(AssignmentPublishedEvent event) {
        if (event == null || event.targets() == null || event.targets().isEmpty()) {
            return;
        }

        log.info("Nhận sự kiện AssignmentPublishedEvent cho bài tập gốc ID: {}, số lượng lớp đích: {}",
                event.originalAssignmentId(), event.targets().size());

        for (AssignmentPublishedEvent.PublishedTarget target : event.targets()) {
            if (target == null || target.recipients() == null || target.recipients().isEmpty()) {
                continue;
            }

            for (AssignmentPublishedEvent.StudentRecipient recipient : target.recipients()) {
                if (recipient == null || recipient.email() == null || recipient.email().isBlank()) {
                    continue;
                }

                try {
                    Context context = new Context();
                    context.setVariable("studentName", recipient.fullName());
                    context.setVariable("assignmentName", target.assignmentTitle());
                    context.setVariable("link", frontendUrl + "/assignments/" + target.assignmentId());

                    emailService.sendHtmlMailAsync(
                            recipient.email(),
                            "Bài tập mới: " + target.assignmentTitle(),
                            "assignment-notification",
                            context
                    );
                } catch (Exception e) {
                    log.error("Lỗi gửi email thông báo bài tập mới tới {}: {}", recipient.email(), e.getMessage(), e);
                }
            }
        }
    }
}
