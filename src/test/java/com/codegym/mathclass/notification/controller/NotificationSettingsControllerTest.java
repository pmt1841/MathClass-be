package com.codegym.mathclass.notification.controller;

import com.codegym.mathclass.notification.dto.NotificationSettingsDto;
import com.codegym.mathclass.notification.service.NotificationSettingsService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationSettingsService notificationSettingsService;

    @InjectMocks
    private NotificationSettingsController notificationSettingsController;

    private ObjectMapper objectMapper;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserDetails = new CustomUserDetails(
                1L, "User", "user@gmail.com", "password", true, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc = MockMvcBuilders.standaloneSetup(notificationSettingsController)
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
    @DisplayName("GET /api/settings/notifications Integration Tests")
    class GetNotificationSettingsEndpointTests {

        @Test
        @DisplayName("Should return notification settings for user and 200 OK")
        void getNotificationSettings_ValidUser_ReturnsOk() throws Exception {
            NotificationSettingsDto dto = new NotificationSettingsDto();
            dto.setMasterEmail(true);

            when(notificationSettingsService.getNotificationSettings(1L)).thenReturn(dto);

            mockMvc.perform(get("/api/settings/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.masterEmail").value(true));

            verify(notificationSettingsService, times(1)).getNotificationSettings(1L);
        }
    }

    @Nested
    @DisplayName("PUT /api/settings/notifications Integration Tests")
    class UpdateNotificationSettingsEndpointTests {

        @Test
        @DisplayName("Should update notification settings for user and return 200 OK")
        void updateNotificationSettings_ValidRequest_ReturnsOk() throws Exception {
            NotificationSettingsDto request = new NotificationSettingsDto();
            request.setMasterEmail(false);

            NotificationSettingsDto response = new NotificationSettingsDto();
            response.setMasterEmail(false);

            when(notificationSettingsService.updateNotificationSettings(eq(1L), any(NotificationSettingsDto.class)))
                    .thenReturn(response);

            mockMvc.perform(put("/api/settings/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.masterEmail").value(false));

            verify(notificationSettingsService, times(1)).updateNotificationSettings(eq(1L), any(NotificationSettingsDto.class));
        }
    }
}
