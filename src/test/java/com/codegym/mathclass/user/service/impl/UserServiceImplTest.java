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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private User mockUser;
    private UserResponse mockUserResponse;
    private UpdateProfileRequest mockUpdateRequest;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setFullName("Old Name");
        mockUser.setRole(Role.STUDENT);

        mockUserResponse = new UserResponse();
        mockUserResponse.setId(1L);
        mockUserResponse.setFullName("New Name");

        mockUpdateRequest = new UpdateProfileRequest();
        mockUpdateRequest.setFullName("New Name");
        mockUpdateRequest.setPhoneNumber("0123456789");
        mockUpdateRequest.setGender(Gender.MALE);
        mockUpdateRequest.setDateOfBirth(LocalDate.of(2000, 1, 1));
        mockUpdateRequest.setAvatarUrl("https://example.com/avatar.png");
    }

    // ==========================================
    // Tests for getUserProfile
    // ==========================================

    @Test
    @DisplayName("Should return user profile when user exists")
    void getUserProfile_UserExists_ReturnsUserResponse() {
        // Given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userMapper.toUserResponse(mockUser)).thenReturn(mockUserResponse);

        // When
        UserResponse result = userService.getUserProfile(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        verify(userRepository, times(1)).findById(userId);
        verify(userMapper, times(1)).toUserResponse(mockUser);
    }

    @Test
    @DisplayName("Should throw BadRequestException when user does not exist")
    void getUserProfile_UserDoesNotExist_ThrowsBadRequestException() {
        // Given
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUserProfile(userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không tìm thấy người dùng với ID: " + userId);
        
        verify(userRepository, times(1)).findById(userId);
        verify(userMapper, never()).toUserResponse(any());
    }

    // ==========================================
    // Tests for updateProfile
    // ==========================================

    @Test
    @DisplayName("Should update profile successfully when user exists")
    void updateProfile_UserExists_UpdatesAndReturnsUserResponse() {
        // Given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(userMapper.toUserResponse(mockUser)).thenReturn(mockUserResponse);

        // When
        UserResponse result = userService.updateProfile(userId, mockUpdateRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo("New Name");
        
        verify(userRepository, times(1)).save(mockUser);
        assertThat(mockUser.getFullName()).isEqualTo("New Name");
        assertThat(mockUser.getPhoneNumber()).isEqualTo("0123456789");
        assertThat(mockUser.getGender()).isEqualTo(Gender.MALE);
        assertThat(mockUser.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    @DisplayName("Should update profile successfully when avatarUrl is null")
    void updateProfile_AvatarUrlIsNull_UpdatesOtherFieldsOnly() {
        // Given
        Long userId = 1L;
        mockUpdateRequest.setAvatarUrl(null);
        mockUser.setAvatarUrl("https://old-avatar.com/img.png");
        
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(userMapper.toUserResponse(mockUser)).thenReturn(mockUserResponse);

        // When
        userService.updateProfile(userId, mockUpdateRequest);

        // Then
        verify(userRepository, times(1)).save(mockUser);
        assertThat(mockUser.getAvatarUrl()).isEqualTo("https://old-avatar.com/img.png"); // Should not change
    }

    @Test
    @DisplayName("Should throw BadRequestException when user not found on update")
    void updateProfile_UserDoesNotExist_ThrowsBadRequestException() {
        // Given
        Long userId = 99L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.updateProfile(userId, mockUpdateRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không tìm thấy người dùng với ID: " + userId);
                
        verify(userRepository, never()).save(any());
    }

    // ==========================================
    // Tests for uploadAvatar
    // ==========================================

    @Test
    @DisplayName("Should upload avatar successfully when user exists and storage works")
    void uploadAvatar_UserExistsAndStorageSuccess_ReturnsAvatarUrl() throws IOException {
        // Given
        Long userId = 1L;
        MultipartFile mockFile = mock(MultipartFile.class);
        String expectedUrl = "https://example.com/new-avatar.png";

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(supabaseStorageService.uploadImage(mockFile, "avatar")).thenReturn(expectedUrl);

        // When
        String resultUrl = userService.uploadAvatar(userId, mockFile);

        // Then
        assertThat(resultUrl).isEqualTo(expectedUrl);
        assertThat(mockUser.getAvatarUrl()).isEqualTo(expectedUrl);
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    @DisplayName("Should throw BadRequestException when user not found on upload avatar")
    void uploadAvatar_UserDoesNotExist_ThrowsBadRequestException() {
        // Given
        Long userId = 99L;
        MultipartFile mockFile = mock(MultipartFile.class);
        
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.uploadAvatar(userId, mockFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không tìm thấy người dùng với ID: " + userId);
                
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw RuntimeException when storage throws IOException")
    void uploadAvatar_StorageThrowsIOException_ThrowsRuntimeException() throws IOException {
        // Given
        Long userId = 1L;
        MultipartFile mockFile = mock(MultipartFile.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(supabaseStorageService.uploadImage(mockFile, "avatar")).thenThrow(new IOException("Upload failed"));

        // When & Then
        assertThatThrownBy(() -> userService.uploadAvatar(userId, mockFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lỗi khi upload ảnh đại diện: Upload failed");
                
        verify(userRepository, never()).save(any());
    }
}
