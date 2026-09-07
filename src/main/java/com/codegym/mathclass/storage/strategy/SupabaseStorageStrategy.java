package com.codegym.mathclass.storage.strategy;

import com.codegym.mathclass.storage.dto.StoragePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * SupabaseStorageStrategy triển khai lưu trữ tệp tin qua REST API của Supabase Storage.
 */
@Component("supabaseStorageStrategy")
@Slf4j
public class SupabaseStorageStrategy implements StorageStrategy {

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    private final RestTemplate restTemplate;

    public SupabaseStorageStrategy(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.key:}") String supabaseKey) {
        this.supabaseUrl = (supabaseUrl != null && supabaseUrl.endsWith("/"))
                ? supabaseUrl.substring(0, supabaseUrl.length() - 1)
                : supabaseUrl;
        this.supabaseKey = supabaseKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String upload(byte[] fileData, String fileName, String contentType, StoragePolicy policy) {
        String extension = extractExtension(contentType, fileName);
        String uniqueFileName = UUID.randomUUID().toString() + extension;
        String bucketName = policy.folderPath();
        String objectPath = "images/" + uniqueFileName;
        String apiUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + objectPath;

        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.valueOf(contentType != null ? contentType : "application/octet-stream"));

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileData, headers);
        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Lỗi khi upload ảnh lên Supabase: " + response.getBody());
        }

        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + objectPath;
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank() || !fileUrl.contains("/storage/v1/object/")) {
            return;
        }

        String bucketName = extractBucketName(fileUrl);
        String objectPath = extractObjectPath(fileUrl);

        if (bucketName == null || objectPath == null || objectPath.isBlank()) {
            log.warn("[Supabase] Không thể trích xuất bucket/path từ URL: {}", fileUrl);
            return;
        }

        String apiUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + objectPath;
        log.info("[Supabase] Deleting object: bucket={}, path={}", bucketName, objectPath);

        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.DELETE, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[Supabase] Đã xóa tệp thành công: {}", fileUrl);
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("[Supabase] Tệp không tồn tại trên Supabase (có thể đã bị xóa trước đó): {}", fileUrl);
        } catch (Exception e) {
            log.error("[Supabase] Lỗi khi xóa tệp từ Supabase URL: {}, error: {}", fileUrl, e.getMessage());
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabaseKey);
        headers.set("apikey", supabaseKey);
        return headers;
    }

    private String extractBucketName(String publicUrl) {
        String marker = publicUrl.contains("/storage/v1/object/public/")
                ? "/storage/v1/object/public/"
                : "/storage/v1/object/";
        int markerIndex = publicUrl.indexOf(marker);
        if (markerIndex == -1) return null;

        String afterMarker = publicUrl.substring(markerIndex + marker.length());
        int slashIndex = afterMarker.indexOf('/');
        if (slashIndex == -1) return afterMarker;
        return afterMarker.substring(0, slashIndex);
    }

    private String extractObjectPath(String publicUrl) {
        String marker = publicUrl.contains("/storage/v1/object/public/")
                ? "/storage/v1/object/public/"
                : "/storage/v1/object/";
        int markerIndex = publicUrl.indexOf(marker);
        if (markerIndex == -1) return null;

        String afterMarker = publicUrl.substring(markerIndex + marker.length());
        int slashIndex = afterMarker.indexOf('/');
        if (slashIndex == -1) return "";
        return afterMarker.substring(slashIndex + 1);
    }

    private String extractExtension(String contentType, String fileName) {
        if (contentType != null) {
            switch (contentType.toLowerCase()) {
                case "image/png":
                    return ".png";
                case "image/jpeg":
                case "image/jpg":
                    return ".jpg";
                case "image/webp":
                    return ".webp";
                case "text/plain":
                    return ".txt";
                default:
                    break;
            }
        }
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return ".bin";
    }
}
