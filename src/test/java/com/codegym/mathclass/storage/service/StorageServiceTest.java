package com.codegym.mathclass.storage.service;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.storage.dto.StoragePolicy;
import com.codegym.mathclass.storage.strategy.StorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private StorageStrategy supabaseStrategy;

    @Mock
    private StorageStrategy localStrategy;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        Map<String, StorageStrategy> strategyMap = Map.of(
                "supabaseStorageStrategy", supabaseStrategy,
                "localStorageStrategy", localStrategy
        );
        storageService = new StorageService(strategyMap);
        ReflectionTestUtils.setField(storageService, "activeProvider", "SUPABASE");
    }

    @Test
    @DisplayName("Upload file vượt quá dung lượng tối đa của Policy sẽ ném BadRequestException")
    void upload_ExceedsMaxFileSize_ThrowsBadRequestException() {
        byte[] largeData = new byte[3 * 1024 * 1024]; // 3MB > 2MB AVATAR Policy
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", largeData);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> storageService.upload(file, StoragePolicy.AVATAR));

        assertTrue(ex.getMessage().contains("Kích thước tệp không được vượt quá"));
        verifyNoInteractions(supabaseStrategy);
    }

    @Test
    @DisplayName("Upload file với Content-Type bị null hoặc trống sẽ ném BadRequestException")
    void upload_NullOrBlankContentType_ThrowsBadRequestException() {
        MockMultipartFile fileNullType = new MockMultipartFile("file", "avatar.png", null, new byte[100]);
        MockMultipartFile fileBlankType = new MockMultipartFile("file", "avatar.png", "   ", new byte[100]);

        assertThrows(BadRequestException.class, () -> storageService.upload(fileNullType, StoragePolicy.AVATAR));
        assertThrows(BadRequestException.class, () -> storageService.upload(fileBlankType, StoragePolicy.AVATAR));
        verifyNoInteractions(supabaseStrategy);
    }

    @Test
    @DisplayName("Upload file với Content-Type không hợp lệ sẽ ném BadRequestException")
    void upload_UnallowedContentType_ThrowsBadRequestException() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.exe", "application/x-msdownload", new byte[100]);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> storageService.upload(file, StoragePolicy.AVATAR));

        assertTrue(ex.getMessage().contains("Chỉ chấp nhận các định dạng tệp"));
        verifyNoInteractions(supabaseStrategy);
    }

    @Test
    @DisplayName("Upload file hợp lệ sẽ gọi Strategy tương ứng và trả về URL")
    void upload_ValidFile_DelegatesToActiveStrategy() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[100]);
        when(supabaseStrategy.upload(any(), eq("avatar.png"), eq("image/png"), eq(StoragePolicy.AVATAR)))
                .thenReturn("https://supabase.co/avatar.png");

        String resultUrl = storageService.upload(file, StoragePolicy.AVATAR);

        assertEquals("https://supabase.co/avatar.png", resultUrl);
        verify(supabaseStrategy, times(1)).upload(any(), eq("avatar.png"), eq("image/png"), eq(StoragePolicy.AVATAR));
    }

    @Test
    @DisplayName("Chuyển đổi provider sang LOCAL sẽ ủy quyền cho LocalStorageStrategy")
    void upload_LocalProvider_DelegatesToLocalStrategy() throws IOException {
        ReflectionTestUtils.setField(storageService, "activeProvider", "LOCAL");

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[100]);
        when(localStrategy.upload(any(), eq("avatar.png"), eq("image/png"), eq(StoragePolicy.AVATAR)))
                .thenReturn("/uploads/avatar/avatar.png");

        String resultUrl = storageService.upload(file, StoragePolicy.AVATAR);

        assertEquals("/uploads/avatar/avatar.png", resultUrl);
        verify(localStrategy, times(1)).upload(any(), eq("avatar.png"), eq("image/png"), eq(StoragePolicy.AVATAR));
    }

    @Test
    @DisplayName("Xóa tệp sẽ ủy quyền cho Active Strategy")
    void delete_DelegatesToActiveStrategy() {
        String fileUrl = "https://supabase.co/avatar.png";

        storageService.delete(fileUrl);

        verify(supabaseStrategy, times(1)).delete(fileUrl);
    }

    @Test
    @DisplayName("Cấu hình Provider không hợp lệ sẽ ném IllegalStateException")
    void getActiveStrategy_UnknownProvider_ThrowsIllegalStateException() {
        ReflectionTestUtils.setField(storageService, "activeProvider", "UNKNOWN");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[100]);

        assertThrows(IllegalStateException.class, () -> storageService.upload(file, StoragePolicy.AVATAR));
    }
}
