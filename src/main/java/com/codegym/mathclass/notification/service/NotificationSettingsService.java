package com.codegym.mathclass.notification.service;

import com.codegym.mathclass.notification.dto.NotificationSettingsDto;

public interface NotificationSettingsService {
    NotificationSettingsDto getNotificationSettings(Long userId);
    NotificationSettingsDto updateNotificationSettings(Long userId, NotificationSettingsDto dto);
}
