package com.codegym.mathclass.notification.service;

import com.codegym.mathclass.notification.dto.NotificationSettingsDto;
import com.codegym.mathclass.notification.entity.NotificationSettings;
import com.codegym.mathclass.notification.repository.NotificationSettingsRepository;
import com.codegym.mathclass.notification.service.impl.NotificationSettingsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("getNotificationSettings Tests")
    class GetNotificationSettingsTests {

        @Test
        @DisplayName("Should return existing notification settings for user")
        void getNotificationSettings_SettingsExist_ReturnsDto() {
            when(notificationSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

            NotificationSettingsDto result = notificationSettingsService.getNotificationSettings(1L);

            assertThat(result).isNotNull();
            assertThat(result.isMasterEmail()).isTrue();
            assertThat(result.isTeacherJoinRequest()).isTrue();
            verify(notificationSettingsRepository, never()).save(any(NotificationSettings.class));
        }

        @Test
        @DisplayName("Should create and return default settings when user has no settings")
        void getNotificationSettings_SettingsNotExist_CreatesAndReturnsDto() {
            when(notificationSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());
            when(notificationSettingsRepository.save(any(NotificationSettings.class))).thenReturn(settings);

            NotificationSettingsDto result = notificationSettingsService.getNotificationSettings(1L);

            assertThat(result).isNotNull();
            verify(notificationSettingsRepository, times(1)).save(any(NotificationSettings.class));
        }
    }

    @Nested
    @DisplayName("updateNotificationSettings Tests")
    class UpdateNotificationSettingsTests {

        @Test
        @DisplayName("Should update existing notification settings successfully")
        void updateNotificationSettings_ValidData_ReturnsUpdatedDto() {
            NotificationSettingsDto requestDto = NotificationSettingsDto.builder()
                    .masterEmail(false)
                    .teacherJoinRequest(false)
                    .build();

            when(notificationSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
            when(notificationSettingsRepository.save(any(NotificationSettings.class))).thenAnswer(i -> i.getArgument(0));

            NotificationSettingsDto result = notificationSettingsService.updateNotificationSettings(1L, requestDto);

            assertThat(result).isNotNull();
            assertThat(result.isMasterEmail()).isFalse();
            assertThat(result.isTeacherJoinRequest()).isFalse();
            verify(notificationSettingsRepository, times(1)).save(any(NotificationSettings.class));
        }
    }
}
