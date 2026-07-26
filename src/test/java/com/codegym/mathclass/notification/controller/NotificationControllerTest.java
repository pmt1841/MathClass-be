package com.codegym.mathclass.notification.controller;

import com.codegym.mathclass.notification.dto.NotificationResponse;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        mockUserDetails = new CustomUserDetails(
                1L, "User", "user@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().isAssignableFrom(CustomUserDetails.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return mockUserDetails;
                    }
                })
                .build();
    }

    @Nested
    @DisplayName("GET /api/notifications/stream Integration Tests")
    class StreamEndpointTests {

        @Test
        @DisplayName("Should return SSE stream and 200 OK")
        void stream_ValidUser_ReturnsSseEmitter() throws Exception {
            SseEmitter emitter = new SseEmitter();
            when(notificationService.createEmitter(1L)).thenReturn(emitter);

            mockMvc.perform(get("/api/notifications/stream"))
                    .andExpect(status().isOk());

            verify(notificationService, times(1)).createEmitter(1L);
        }
    }

    @Nested
    @DisplayName("GET /api/notifications Integration Tests")
    class GetNotificationsEndpointTests {

        @Test
        @DisplayName("Should return notifications page and 200 OK")
        void getNotifications_ValidRequest_ReturnsPage() throws Exception {
            NotificationResponse response = new NotificationResponse();
            response.setId(10L);
            response.setMessage("New assignment");
            response.setLink("/assignments/1");
            response.setRead(false);
            response.setCreatedAt(LocalDateTime.now());

            Page<NotificationResponse> page = new PageImpl<>(Collections.singletonList(response), PageRequest.of(0, 10), 1);

            when(notificationService.getNotifications(eq(1L), any(PageRequest.class))).thenReturn(page);

            mockMvc.perform(get("/api/notifications")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(10L))
                    .andExpect(jsonPath("$.content[0].message").value("New assignment"));

            verify(notificationService, times(1)).getNotifications(eq(1L), any(PageRequest.class));
        }
    }

    @Nested
    @DisplayName("GET /api/notifications/unread-count Integration Tests")
    class GetUnreadCountEndpointTests {

        @Test
        @DisplayName("Should return unread notifications count and 200 OK")
        void getUnreadCount_ValidUser_ReturnsCount() throws Exception {
            when(notificationService.getUnreadCount(1L)).thenReturn(5L);

            mockMvc.perform(get("/api/notifications/unread-count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));

            verify(notificationService, times(1)).getUnreadCount(1L);
        }
    }

    @Nested
    @DisplayName("PUT /api/notifications/read-all Integration Tests")
    class MarkAllAsReadEndpointTests {

        @Test
        @DisplayName("Should mark all notifications as read and return 200 OK")
        void markAllAsRead_ValidUser_ReturnsOk() throws Exception {
            doNothing().when(notificationService).markAllAsRead(1L);

            mockMvc.perform(put("/api/notifications/read-all"))
                    .andExpect(status().isOk());

            verify(notificationService, times(1)).markAllAsRead(1L);
        }
    }

    @Nested
    @DisplayName("PUT /api/notifications/{id}/read Integration Tests")
    class MarkAsReadEndpointTests {

        @Test
        @DisplayName("Should mark single notification as read by id and return 200 OK")
        void markAsRead_ValidId_ReturnsOk() throws Exception {
            doNothing().when(notificationService).markAsRead(10L, 1L);

            mockMvc.perform(put("/api/notifications/10/read"))
                    .andExpect(status().isOk());

            verify(notificationService, times(1)).markAsRead(10L, 1L);
        }
    }
}
