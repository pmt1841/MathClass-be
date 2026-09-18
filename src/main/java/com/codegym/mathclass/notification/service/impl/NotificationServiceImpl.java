package com.codegym.mathclass.notification.service.impl;

import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.notification.dto.NotificationPayload;
import com.codegym.mathclass.notification.dto.response.NotificationResponse;
import com.codegym.mathclass.notification.entity.Notification;
import com.codegym.mathclass.notification.repository.NotificationRepository;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.notification.strategy.CompositeNotificationStrategy;
import com.codegym.mathclass.notification.strategy.SseNotificationStrategy;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseNotificationStrategy sseNotificationStrategy;
    private final CompositeNotificationStrategy compositeNotificationStrategy;

    @Override
    public SseEmitter createEmitter(Long userId) {
        return sseNotificationStrategy.createEmitter(userId);
    }

    @Override
    @Transactional
    public void saveAndSendNotification(Long userId, String message, String link) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        Notification notification = Notification.builder()
                .user(user)
                .message(message)
                .link(link)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = mapToResponse(saved);

        NotificationPayload payload = new NotificationPayload(
                userId,
                "Thông báo từ MathClass",
                message,
                link,
                "NOTIFICATION",
                response
        );

        try {
            compositeNotificationStrategy.send(payload);
        } catch (Exception e) {
            log.error("[NotificationServiceImpl] Lỗi khi phân phối thông báo cho user {}: {}",
                    userId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        notificationRepository.markAsReadByIdAndUserId(notificationId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    public void sendAiJobEvent(Long userId, String eventName, Object data) {
        if (userId == null) {
            return;
        }
        NotificationPayload payload = new NotificationPayload(
                userId,
                null,
                null,
                null,
                eventName,
                data
        );
        sseNotificationStrategy.send(payload);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .link(notification.getLink())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
