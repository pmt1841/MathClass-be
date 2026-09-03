package com.codegym.mathclass.utils;

import com.codegym.mathclass.storage.dto.SupabaseFileObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupabaseStorageServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private SupabaseStorageService supabaseStorageService;

    private final String testUrl = "https://mock-supabase.co";
    private final String testKey = "mock-api-key";

    @BeforeEach
    void setUp() {
        supabaseStorageService = new SupabaseStorageService(testUrl, testKey);
        ReflectionTestUtils.setField(supabaseStorageService, "restTemplate", restTemplate);
    }

    @Nested
    @DisplayName("URL Extraction Tests")
    class UrlExtractionTests {

        @Test
        @DisplayName("Should correctly extract bucket and object path from standard public URL")
        void extractFromPublicUrl() {
            String url = "https://mock-supabase.co/storage/v1/object/public/assignment_image/images/photo.png";

            assertThat(supabaseStorageService.isSupabaseStorageUrl(url)).isTrue();
            assertThat(supabaseStorageService.extractBucketName(url)).isEqualTo("assignment_image");
            assertThat(supabaseStorageService.extractObjectPath(url)).isEqualTo("images/photo.png");
            assertThat(supabaseStorageService.extractObjectPath(url, "assignment_image")).isEqualTo("images/photo.png");
            assertThat(supabaseStorageService.extractObjectPath(url, "avatar")).isNull();
        }

        @Test
        @DisplayName("Should return null/false for non-supabase URLs")
        void nonSupabaseUrl() {
            String url = "https://lh3.googleusercontent.com/a/ACg8ocJ-avatar";

            assertThat(supabaseStorageService.isSupabaseStorageUrl(url)).isFalse();
            assertThat(supabaseStorageService.extractBucketName(url)).isNull();
            assertThat(supabaseStorageService.extractObjectPath(url)).isNull();
            assertThat(supabaseStorageService.extractObjectPath(url, "avatar")).isNull();
        }

        @Test
        @DisplayName("Should return null/false for null or blank URLs")
        void nullOrBlankUrl() {
            assertThat(supabaseStorageService.isSupabaseStorageUrl(null)).isFalse();
            assertThat(supabaseStorageService.isSupabaseStorageUrl("")).isFalse();
            assertThat(supabaseStorageService.extractBucketName(null)).isNull();
            assertThat(supabaseStorageService.extractObjectPath(null)).isNull();
        }
    }

    @Nested
    @DisplayName("deleteImage Tests")
    class DeleteImageTests {

        @Test
        @DisplayName("Should return true when DELETE API returns 200 OK")
        void deleteImage_Success() {
            when(restTemplate.exchange(
                    eq("https://mock-supabase.co/storage/v1/object/avatar/images/123.png"),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok("Deleted"));

            boolean result = supabaseStorageService.deleteImage("avatar", "images/123.png");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return true when DELETE API returns 404 (already deleted)")
        void deleteImage_NotFound_ReturnsTrue() {
            when(restTemplate.exchange(
                    eq("https://mock-supabase.co/storage/v1/object/avatar/images/123.png"),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenThrow(HttpClientErrorException.NotFound.create(HttpStatus.NOT_FOUND, "Not found", HttpHeaders.EMPTY, null, null));

            boolean result = supabaseStorageService.deleteImage("avatar", "images/123.png");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should delete image by valid public URL")
        void deleteImageByUrl_ValidUrl() {
            String url = "https://mock-supabase.co/storage/v1/object/public/avatar/images/my-avatar.jpg";

            when(restTemplate.exchange(
                    eq("https://mock-supabase.co/storage/v1/object/avatar/images/my-avatar.jpg"),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok("Deleted"));

            boolean result = supabaseStorageService.deleteImageByUrl(url);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("deleteImageByUrl should return false for external URL without calling REST")
        void deleteImageByUrl_ExternalUrl_ReturnsFalse() {
            boolean result = supabaseStorageService.deleteImageByUrl("https://google.com/avatar.jpg");
            assertThat(result).isFalse();
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("deleteImages (Batch) Tests")
    class DeleteImagesBatchTests {

        @Test
        @DisplayName("Should send prefixes in payload and return total deleted count")
        void deleteImages_Batch_Success() {
            List<String> paths = List.of("images/a.png", "images/b.jpg");

            when(restTemplate.exchange(
                    eq("https://mock-supabase.co/storage/v1/object/assignment_image"),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok("[{\"name\": \"images/a.png\"}]"));

            int deleted = supabaseStorageService.deleteImages("assignment_image", paths);
            assertThat(deleted).isEqualTo(2);

            ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(anyString(), eq(HttpMethod.DELETE), captor.capture(), eq(String.class));

            Map<String, Object> body = captor.getValue().getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("prefixes")).isEqualTo(paths);
        }

        @Test
        @DisplayName("Should return 0 when path list is empty or null")
        void deleteImages_EmptyList_ReturnsZero() {
            int deletedNull = supabaseStorageService.deleteImages("avatar", null);
            int deletedEmpty = supabaseStorageService.deleteImages("avatar", List.of());

            assertThat(deletedNull).isEqualTo(0);
            assertThat(deletedEmpty).isEqualTo(0);
            verifyNoInteractions(restTemplate);
        }
    }

    @Nested
    @DisplayName("listObjects Tests")
    class ListObjectsTests {

        @Test
        @DisplayName("Should return array of SupabaseFileObject when list API succeeds")
        void listObjects_Success() {
            SupabaseFileObject obj1 = SupabaseFileObject.builder()
                    .name("images/img1.png")
                    .createdAt("2026-08-25T10:00:00Z")
                    .build();

            SupabaseFileObject[] responseArray = new SupabaseFileObject[]{obj1};

            when(restTemplate.exchange(
                    eq("https://mock-supabase.co/storage/v1/object/list/assignment_image"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(SupabaseFileObject[].class)
            )).thenReturn(ResponseEntity.ok(responseArray));

            List<SupabaseFileObject> result = supabaseStorageService.listObjects("assignment_image", "images", 100, 0);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("images/img1.png");
            assertThat(result.get(0).getCreatedInstant()).isNotNull();
        }
    }
}
