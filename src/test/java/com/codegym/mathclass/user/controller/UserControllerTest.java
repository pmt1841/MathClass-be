package com.codegym.mathclass.user.controller;

import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Gender;
import com.codegym.mathclass.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private ObjectMapper objectMapper;
    private UserResponse mockUserResponse;
    private UpdateProfileRequest mockUpdateRequest;
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUserResponse = new UserResponse();
        mockUserResponse.setId(1L);
        mockUserResponse.setFullName("Test User");
        mockUserResponse.setPhoneNumber("0123456789");
        mockUserResponse.setGender(Gender.MALE);
        mockUserResponse.setDateOfBirth(LocalDate.of(2000, 1, 1));

        mockUpdateRequest = new UpdateProfileRequest();
        mockUpdateRequest.setFullName("Test User");
        mockUpdateRequest.setPhoneNumber("0123456789");
        mockUpdateRequest.setGender(Gender.MALE);
        mockUpdateRequest.setDateOfBirth(LocalDate.of(2000, 1, 1));

        mockUserDetails = new CustomUserDetails(
                1L, "Test User", "test@test.com", "password", true, null, Collections.emptyList()
        );

        // Configure standalone setup with CustomUserDetails resolver
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
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
    // Tests for getCurrentUserProfile
    // ==========================================

    @Test
    @DisplayName("Should return current user profile")
    void getCurrentUserProfile_ValidUserDetails_ReturnsOkAndUserProfile() throws Exception {
        // Given
        when(userService.getUserProfile(1L)).thenReturn(mockUserResponse);

        // When & Then
        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.phoneNumber").value("0123456789"));
                
        verify(userService, times(1)).getUserProfile(1L);
    }

    // ==========================================
    // Tests for updateProfile
    // ==========================================

    @Test
    @DisplayName("Should update user profile")
    void updateProfile_ValidRequest_ReturnsOkAndUpdatedProfile() throws Exception {
        // Given
        when(userService.updateProfile(eq(1L), any(UpdateProfileRequest.class))).thenReturn(mockUserResponse);

        String requestJson = "{" +
                "\"fullName\":\"Test User\"," +
                "\"phoneNumber\":\"0123456789\"," +
                "\"gender\":\"MALE\"," +
                "\"dateOfBirth\":\"01-01-2000\"" +
                "}";

        // When & Then
        mockMvc.perform(put("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.gender").value("MALE"));
                
        verify(userService, times(1)).updateProfile(eq(1L), any(UpdateProfileRequest.class));
    }

    // ==========================================
    // Tests for uploadAvatar
    // ==========================================

    @Test
    @DisplayName("Should upload avatar and return new url")
    void uploadAvatar_ValidFile_ReturnsOkAndAvatarUrl() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "content".getBytes());
        String expectedUrl = "https://example.com/avatar.png";
        
        when(userService.uploadAvatar(eq(1L), any())).thenReturn(expectedUrl);

        // When & Then
        mockMvc.perform(multipart("/api/users/avatar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(expectedUrl));
                
        verify(userService, times(1)).uploadAvatar(eq(1L), any());
    }

}
