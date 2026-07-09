package com.codegym.mathclass.notification.service;

import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.notification.dto.NotificationResponse;
import com.codegym.mathclass.notification.entity.Notification;
import com.codegym.mathclass.notification.repository.NotificationRepository;
import com.codegym.mathclass.notification.service.impl.NotificationServiceImpl;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User user;
    private Notification notification;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        notification = Notification.builder()
                .user(user)
                .message("Test message")
                .link("/test")
                .isRead(false)
                .build();
        notification.setId(100L);
    }

    @Test
    @DisplayName("Should create SSE emitter")
    void createEmitter_ValidUserId_ReturnsEmitter() {
        // When
        SseEmitter emitter = notificationService.createEmitter(1L);

        // Then
        assertThat(emitter).isNotNull();
        // Internally it sends an INIT event but that's handled asynchronously or synchronously.
    }

    @Test
    @DisplayName("Should save and send notification successfully")
    void saveAndSendNotification_ValidData_Success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // When
        notificationService.saveAndSendNotification(1L, "Test message", "/test");

        // Then
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should throw Exception when saving notification for non-existent user")
    void saveAndSendNotification_UserNotFound_ThrowsException() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> notificationService.saveAndSendNotification(1L, "Msg", "/"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("Should return notifications page")
    void getNotifications_ValidUser_ReturnsPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(Collections.singletonList(notification));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page);

        // When
        Page<NotificationResponse> result = notificationService.getNotifications(1L, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should mark all as read")
    void markAllAsRead_ValidUser_Success() {
        // When
        notificationService.markAllAsRead(1L);

        // Then
        verify(notificationRepository, times(1)).markAllAsReadByUserId(1L);
    }

    @Test
    @DisplayName("Should mark as read by id")
    void markAsRead_ValidData_Success() {
        // When
        notificationService.markAsRead(100L, 1L);

        // Then
        verify(notificationRepository, times(1)).markAsReadByIdAndUserId(100L, 1L);
    }

    @Test
    @DisplayName("Should return unread count")
    void getUnreadCount_ValidUser_ReturnsCount() {
        // Given
        when(notificationRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(5L);

        // When
        long count = notificationService.getUnreadCount(1L);

        // Then
        assertThat(count).isEqualTo(5L);
    }
}
