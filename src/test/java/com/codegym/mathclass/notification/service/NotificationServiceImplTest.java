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
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("createEmitter Tests")
    class CreateEmitterTests {

        @Test
        @DisplayName("Should create and return SSE emitter")
        void createEmitter_ValidUserId_ReturnsEmitter() {
            SseEmitter emitter = notificationService.createEmitter(1L);

            assertThat(emitter).isNotNull();
        }
    }

    @Nested
    @DisplayName("saveAndSendNotification Tests")
    class SaveAndSendNotificationTests {

        @Test
        @DisplayName("Should save notification and push via SSE emitter when user exists")
        void saveAndSendNotification_ValidData_Success() {
            SseEmitter emitter = notificationService.createEmitter(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

            notificationService.saveAndSendNotification(1L, "Test message", "/test");

            verify(notificationRepository, times(1)).save(any(Notification.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when user does not exist")
        void saveAndSendNotification_UserNotFound_ThrowsException() {
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.saveAndSendNotification(1L, "Msg", "/"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("User not found");

            verify(notificationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getNotifications Tests")
    class GetNotificationsTests {

        @Test
        @DisplayName("Should return notifications page for user")
        void getNotifications_ValidUser_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Notification> page = new PageImpl<>(Collections.singletonList(notification));
            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page);

            Page<NotificationResponse> result = notificationService.getNotifications(1L, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("markAsRead & markAllAsRead Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark all notifications as read for user")
        void markAllAsRead_ValidUser_Success() {
            notificationService.markAllAsRead(1L);

            verify(notificationRepository, times(1)).markAllAsReadByUserId(1L);
        }

        @Test
        @DisplayName("Should mark single notification as read by id and userId")
        void markAsRead_ValidData_Success() {
            notificationService.markAsRead(100L, 1L);

            verify(notificationRepository, times(1)).markAsReadByIdAndUserId(100L, 1L);
        }
    }

    @Nested
    @DisplayName("getUnreadCount Tests")
    class GetUnreadCountTests {

        @Test
        @DisplayName("Should return count of unread notifications")
        void getUnreadCount_ValidUser_ReturnsCount() {
            when(notificationRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(5L);

            long count = notificationService.getUnreadCount(1L);

            assertThat(count).isEqualTo(5L);
            verify(notificationRepository, times(1)).countByUserIdAndIsReadFalse(1L);
        }
    }
}
