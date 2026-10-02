package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.storage.dto.StoragePolicy;
import com.codegym.mathclass.storage.service.StorageService;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAvatarServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private UserAvatarServiceImpl avatarService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setFullName("User Test");
        mockUser.setProvider(Provider.LOCAL);
        mockUser.setRole(Role.STUDENT);
        mockUser.setAvatarUrl("https://example.com/old-avatar.png");
    }

    @Test
    @DisplayName("Should upload avatar successfully and delete old avatar")
    void uploadAvatar_success() throws Exception {
        Long userId = 1L;
        MultipartFile mockFile = mock(MultipartFile.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storageService.upload(mockFile, StoragePolicy.AVATAR)).thenReturn("https://example.com/new-avatar.png");

        String result = avatarService.uploadAvatar(userId, mockFile);

        assertThat(result).isEqualTo("https://example.com/new-avatar.png");
        assertThat(mockUser.getAvatarUrl()).isEqualTo("https://example.com/new-avatar.png");
        verify(userRepository, times(1)).save(mockUser);
        verify(storageService, times(1)).delete("https://example.com/old-avatar.png");
    }

    @Test
    @DisplayName("Should handle delete old avatar failure gracefully")
    void uploadAvatar_deleteOldAvatarFails_stillSucceeds() throws Exception {
        Long userId = 1L;
        MultipartFile mockFile = mock(MultipartFile.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storageService.upload(mockFile, StoragePolicy.AVATAR)).thenReturn("https://example.com/new-avatar.png");
        doThrow(new RuntimeException("Storage delete error")).when(storageService).delete(anyString());

        String result = avatarService.uploadAvatar(userId, mockFile);

        assertThat(result).isEqualTo("https://example.com/new-avatar.png");
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    @DisplayName("Should throw BadRequestException when user is from GOOGLE provider")
    void uploadAvatar_googleUser_throwsBadRequest() {
        Long userId = 1L;
        mockUser.setProvider(Provider.GOOGLE);
        MultipartFile mockFile = mock(MultipartFile.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> avatarService.uploadAvatar(userId, mockFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể thay đổi ảnh đại diện cho tài khoản liên kết Google");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("Should throw BadRequestException when user not found")
    void uploadAvatar_userNotFound_throwsBadRequest() {
        Long userId = 99L;
        MultipartFile mockFile = mock(MultipartFile.class);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> avatarService.uploadAvatar(userId, mockFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không tìm thấy người dùng với ID: 99");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("Should throw BadRequestException when upload throws IOException")
    void uploadAvatar_ioException_throwsBadRequest() throws Exception {
        Long userId = 1L;
        MultipartFile mockFile = mock(MultipartFile.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(storageService.upload(mockFile, StoragePolicy.AVATAR)).thenThrow(new IOException("Connection reset"));

        assertThatThrownBy(() -> avatarService.uploadAvatar(userId, mockFile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Lỗi khi upload ảnh đại diện: Connection reset");

        verify(userRepository, never()).save(any());
    }
}
