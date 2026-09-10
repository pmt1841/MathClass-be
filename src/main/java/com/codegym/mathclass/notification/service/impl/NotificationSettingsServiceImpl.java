package com.codegym.mathclass.notification.service.impl;

import com.codegym.mathclass.notification.dto.response.NotificationSettingsResponse;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.notification.service.NotificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationSettingsServiceImpl implements NotificationSettingsService {

    private final NotificationSettingsRepository notificationSettingsRepository;

    @Override
    public NotificationSettingsResponse getNotificationSettings(Long userId) {
        NotificationSettings settings = notificationSettingsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    NotificationSettings newSettings = NotificationSettings.builder()
                            .userId(userId)
                            .build();
                    return notificationSettingsRepository.save(newSettings);
                });
        return mapToDto(settings);
    }

    @Override
    public NotificationSettingsResponse updateNotificationSettings(Long userId, NotificationSettingsResponse dto) {
        NotificationSettings settings = notificationSettingsRepository.findByUserId(userId)
                .orElseGet(() -> NotificationSettings.builder().userId(userId).build());

        settings.setMasterEmail(dto.isMasterEmail());
        settings.setTeacherJoinRequest(dto.isTeacherJoinRequest());
        settings.setTeacherNewSubmission(dto.isTeacherNewSubmission());
        settings.setStudentNewAssignment(dto.isStudentNewAssignment());
        settings.setStudentGraded(dto.isStudentGraded());
        settings.setStudentDeadlineReminder(dto.isStudentDeadlineReminder());

        NotificationSettings saved = notificationSettingsRepository.save(settings);
        return mapToDto(saved);
    }

    private NotificationSettingsResponse mapToDto(NotificationSettings settings) {
        return NotificationSettingsResponse.builder()
                .masterEmail(settings.isMasterEmail())
                .teacherJoinRequest(settings.isTeacherJoinRequest())
                .teacherNewSubmission(settings.isTeacherNewSubmission())
                .studentNewAssignment(settings.isStudentNewAssignment())
                .studentGraded(settings.isStudentGraded())
                .studentDeadlineReminder(settings.isStudentDeadlineReminder())
                .build();
    }
}
