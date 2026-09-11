package com.codegym.mathclass.storage.strategy;

import com.codegym.mathclass.storage.dto.StoragePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupabaseStorageStrategyTest {

    @Mock
    private RestTemplate restTemplate;

    private SupabaseStorageStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SupabaseStorageStrategy("https://supabase.co", "test-api-key");
        ReflectionTestUtils.setField(strategy, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("upload tệp thành công lên Supabase Storage và trả về public URL")
    void testUploadSuccess() {
        byte[] data = "fake-image-content".getBytes();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"Key\":\"test\"}"));

        String publicUrl = strategy.upload(data, "photo.png", "image/png", StoragePolicy.AVATAR);

        assertNotNull(publicUrl);
        assertTrue(publicUrl.contains("https://supabase.co/storage/v1/object/public/avatar/images/"));
        assertTrue(publicUrl.endsWith(".png"));
    }

    @Test
    @DisplayName("upload thất bại khi Supabase trả về mã lỗi HTTP")
    void testUploadFailureThrowsException() {
        byte[] data = "data".getBytes();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.badRequest().body("Bucket not found"));

        assertThrows(RuntimeException.class, () ->
                strategy.upload(data, "doc.pdf", "application/pdf", StoragePolicy.ASSIGNMENT_IMAGE)
        );
    }

    @Test
    @DisplayName("delete gọi DELETE request tới endpoint Supabase object")
    void testDeleteSuccess() {
        String fileUrl = "https://supabase.co/storage/v1/object/public/avatars/images/avatar-123.jpg";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Deleted"));

        strategy.delete(fileUrl);

        verify(restTemplate).exchange(
                eq("https://supabase.co/storage/v1/object/avatars/images/avatar-123.jpg"),
                eq(HttpMethod.DELETE),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    @DisplayName("delete bỏ qua nếu url rỗng hoặc không đúng định dạng Supabase")
    void testDeleteInvalidUrl() {
        strategy.delete(null);
        strategy.delete("");
        strategy.delete("https://example.com/other-file.jpg");

        verifyNoInteractions(restTemplate);
    }
}
