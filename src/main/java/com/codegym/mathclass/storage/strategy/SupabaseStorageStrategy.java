package com.codegym.mathclass.storage.strategy;

import com.codegym.mathclass.storage.dto.StoragePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * SupabaseStorageStrategy triển khai lưu trữ tệp tin qua REST API của Supabase Storage.
 */
@Component("supabaseStorageStrategy")
public class SupabaseStorageStrategy implements StorageStrategy {

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String upload(byte[] fileData, String fileName, String contentType, StoragePolicy policy) {
        String extension = extractExtension(contentType, fileName);
        String uniqueFileName = UUID.randomUUID().toString() + extension;
        String bucketName = policy.folderPath();
        String objectPath = "images/" + uniqueFileName;
        String apiUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + objectPath;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabaseKey);
        headers.set("apikey", supabaseKey);
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
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        // Logic xoá tệp từ Supabase nếu cần thiết trong tương lai
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
