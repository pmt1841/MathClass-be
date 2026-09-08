package com.codegym.mathclass.notification.dto;

public record NotificationPayload(
        Long recipientId,
        String title,
        String message,
        String link,
        String eventName,
        Object data
) {}
