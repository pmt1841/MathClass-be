package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Gender;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.mapper.UserMapper;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UpdateProfileRequest updateRequest;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setFullName("Old Name");
        user.setRole(Role.STUDENT);

        updateRequest = new UpdateProfileRequest();
        updateRequest.setFullName("New Name");
        updateRequest.setPhoneNumber("0123456789");
        updateRequest.setGender(Gender.MALE);
        updateRequest.setDateOfBirth(LocalDate.of(2000, 1, 1));
        
        userResponse = new UserResponse();
        userResponse.setId(1L);
        userResponse.setFullName("New Name");
    }

    @Test
    @DisplayName("Should update profile successfully")
    void updateProfile_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toUserResponse(user)).thenReturn(userResponse);

        UserResponse response = userService.updateProfile(1L, updateRequest);

        assertNotNull(response);
        assertEquals("New Name", response.getFullName());
        verify(userRepository, times(1)).save(user);
        assertEquals("New Name", user.getFullName());
        assertEquals("0123456789", user.getPhoneNumber());
        assertEquals(Gender.MALE, user.getGender());
    }

    @Test
    @DisplayName("Should throw BadRequestException if user not found on update")
    void updateProfile_UserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> userService.updateProfile(1L, updateRequest));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should upload avatar successfully")
    void uploadAvatar_Success() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        String expectedUrl = "https://example.com/avatar.png";

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(supabaseStorageService.uploadImage(file, "avatar")).thenReturn(expectedUrl);

        String resultUrl = userService.uploadAvatar(1L, file);

        assertEquals(expectedUrl, resultUrl);
        assertEquals(expectedUrl, user.getAvatarUrl());
        verify(userRepository, times(1)).save(user);
    }
}
