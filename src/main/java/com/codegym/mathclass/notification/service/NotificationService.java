package com.codegym.mathclass.notification.service;

import com.codegym.mathclass.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationService {
    SseEmitter createEmitter(Long userId);
    void saveAndSendNotification(Long userId, String message, String link);
    Page<NotificationResponse> getNotifications(Long userId, Pageable pageable);
    void markAllAsRead(Long userId);
    void markAsRead(Long notificationId, Long userId);
    long getUnreadCount(Long userId);
    void sendAiJobEvent(Long userId, String eventName, Object data);
}
