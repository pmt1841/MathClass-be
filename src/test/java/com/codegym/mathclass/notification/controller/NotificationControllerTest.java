package com.codegym.mathclass.notification.controller;

import com.codegym.mathclass.notification.dto.NotificationResponse;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    // ==========================================
    // Tests for stream (SSE)
    // ==========================================

    @Test
    @DisplayName("Should return SSE stream")
    void stream_ValidUser_ReturnsSseEmitter() throws Exception {
        // Given
        SseEmitter emitter = new SseEmitter();
        when(notificationService.createEmitter(1L)).thenReturn(emitter);

        // When & Then
        mockMvc.perform(get("/api/notifications/stream"))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).createEmitter(1L);
    }

    // ==========================================
    // Tests for getNotifications
    // ==========================================

    @Test
    @DisplayName("Should return notifications page")
    void getNotifications_ValidRequest_ReturnsPage() throws Exception {
        // Given
        NotificationResponse response = new NotificationResponse();
        response.setId(10L);
        response.setMessage("New assignment");
        response.setLink("/assignments/1");
        response.setRead(false);
        response.setCreatedAt(LocalDateTime.now());

        Page<NotificationResponse> page = new PageImpl<>(Collections.singletonList(response), PageRequest.of(0, 10), 1);

        when(notificationService.getNotifications(eq(1L), any(PageRequest.class))).thenReturn(page);

        // When & Then
        mockMvc.perform(get("/api/notifications")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10L))
                .andExpect(jsonPath("$.content[0].message").value("New assignment"));

        verify(notificationService, times(1)).getNotifications(eq(1L), any(PageRequest.class));
    }

    // ==========================================
    // Tests for getUnreadCount
    // ==========================================

    @Test
    @DisplayName("Should return unread count")
    void getUnreadCount_ValidUser_ReturnsCount() throws Exception {
        // Given
        when(notificationService.getUnreadCount(1L)).thenReturn(5L);

        // When & Then
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));

        verify(notificationService, times(1)).getUnreadCount(1L);
    }

    // ==========================================
    // Tests for markAllAsRead
    // ==========================================

    @Test
    @DisplayName("Should mark all as read")
    void markAllAsRead_ValidUser_ReturnsOk() throws Exception {
        // Given
        doNothing().when(notificationService).markAllAsRead(1L);

        // When & Then
        mockMvc.perform(put("/api/notifications/read-all"))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).markAllAsRead(1L);
    }

    // ==========================================
    // Tests for markAsRead
    // ==========================================

    @Test
    @DisplayName("Should mark as read by id")
    void markAsRead_ValidId_ReturnsOk() throws Exception {
        // Given
        doNothing().when(notificationService).markAsRead(10L, 1L);

        // When & Then
        mockMvc.perform(put("/api/notifications/10/read"))
                .andExpect(status().isOk());

        verify(notificationService, times(1)).markAsRead(10L, 1L);
    }
}
