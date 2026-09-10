package com.codegym.mathclass.notification.service;

import com.codegym.mathclass.notification.dto.response.NotificationSettingsResponse;

public interface NotificationSettingsService {
    NotificationSettingsResponse getNotificationSettings(Long userId);
    NotificationSettingsResponse updateNotificationSettings(Long userId, NotificationSettingsResponse dto);
}
