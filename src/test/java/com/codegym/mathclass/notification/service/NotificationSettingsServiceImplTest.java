package com.codegym.mathclass.notification.service;

import com.codegym.mathclass.notification.dto.NotificationSettingsDto;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.notification.service.impl.NotificationSettingsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceImplTest {

    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;

    @InjectMocks
    private NotificationSettingsServiceImpl notificationSettingsService;

    private NotificationSettings settings;

    @BeforeEach
    void setUp() {
        settings = NotificationSettings.builder()
                .userId(1L)
                .masterEmail(true)
                .teacherJoinRequest(true)
                .build();
    }

    @Test
    @DisplayName("Should get existing notification settings")
    void getNotificationSettings_SettingsExist_ReturnsDto() {
        // Given
        when(notificationSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        // When
        NotificationSettingsDto result = notificationSettingsService.getNotificationSettings(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isMasterEmail()).isTrue();
        assertThat(result.isTeacherJoinRequest()).isTrue();
        verify(notificationSettingsRepository, never()).save(any(NotificationSettings.class));
    }

    @Test
    @DisplayName("Should create and return new notification settings if not exist")
    void getNotificationSettings_SettingsNotExist_CreatesAndReturnsDto() {
        // Given
        when(notificationSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(notificationSettingsRepository.save(any(NotificationSettings.class))).thenReturn(settings);

        // When
        NotificationSettingsDto result = notificationSettingsService.getNotificationSettings(1L);

        // Then
        assertThat(result).isNotNull();
        verify(notificationSettingsRepository, times(1)).save(any(NotificationSettings.class));
    }

    @Test
    @DisplayName("Should update notification settings")
    void updateNotificationSettings_ValidData_ReturnsUpdatedDto() {
        // Given
        NotificationSettingsDto requestDto = NotificationSettingsDto.builder()
                .masterEmail(false)
                .teacherJoinRequest(false)
                .build();

        when(notificationSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(notificationSettingsRepository.save(any(NotificationSettings.class))).thenAnswer(i -> i.getArgument(0));

        // When
        NotificationSettingsDto result = notificationSettingsService.updateNotificationSettings(1L, requestDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isMasterEmail()).isFalse();
        assertThat(result.isTeacherJoinRequest()).isFalse();
        verify(notificationSettingsRepository, times(1)).save(any(NotificationSettings.class));
    }
}
