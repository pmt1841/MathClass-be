package com.codegym.mathclass.utils;

import com.codegym.mathclass.storage.dto.SupabaseFileObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final RestTemplate restTemplate;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
            "image/png", "image/jpeg", "image/jpg", "image/webp"
    );

    public SupabaseStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.key}") String supabaseKey) {
        this.supabaseUrl = supabaseUrl;
        this.supabaseKey = supabaseKey;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String uploadImage(MultipartFile file, String bucketName) throws IOException {
        return uploadImage(file.getBytes(), file.getOriginalFilename(), file.getContentType(), bucketName);
    }

    public String uploadImage(byte[] fileData, String originalFilename, String contentType, String bucketName) throws IOException {
        if (fileData.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Kích thước file không được vượt quá 5MB");
        }

        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Chỉ chấp nhận file định dạng: png, jpeg, jpg, webp");
        }

        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Định dạng tệp không được hỗ trợ");
        };

        String uniqueFileName = UUID.randomUUID().toString() + extension;
        String objectPath = "images/" + uniqueFileName;
        String apiUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + objectPath;

        log.info("[Supabase] Uploading image to URL: {}", apiUrl);

        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.valueOf(contentType));

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileData, headers);

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Lỗi khi upload ảnh lên Supabase: " + response.getBody());
        }

        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + objectPath;
    }

    public boolean deleteImage(String bucketName, String objectPath) {
        if (bucketName == null || objectPath == null || objectPath.isBlank()) {
            return false;
        }

        String cleanPath = objectPath.startsWith("/") ? objectPath.substring(1) : objectPath;
        String apiUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + cleanPath;

        log.info("[Supabase] Deleting single object: bucket={}, path={}", bucketName, cleanPath);

        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.DELETE, requestEntity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("[Supabase] Object not found during delete (already deleted?): bucket={}, path={}", bucketName, cleanPath);
            return true;
        } catch (Exception e) {
            log.error("[Supabase] Failed to delete object: bucket={}, path={}, error={}", bucketName, cleanPath, e.getMessage());
            return false;
        }
    }

    public boolean deleteImageByUrl(String publicUrl) {
        if (!isSupabaseStorageUrl(publicUrl)) {
            return false;
        }
        String bucketName = extractBucketName(publicUrl);
        String objectPath = extractObjectPath(publicUrl);

        if (bucketName != null && objectPath != null && !objectPath.isBlank()) {
            return deleteImage(bucketName, objectPath);
        }
        return false;
    }

    public int deleteImages(String bucketName, List<String> objectPaths) {
        if (bucketName == null || objectPaths == null || objectPaths.isEmpty()) {
            return 0;
        }

        List<String> cleanPaths = objectPaths.stream()
                .filter(Objects::nonNull)
                .map(p -> p.startsWith("/") ? p.substring(1) : p)
                .filter(p -> !p.isBlank())
                .distinct()
                .toList();

        if (cleanPaths.isEmpty()) {
            return 0;
        }

        String apiUrl = supabaseUrl + "/storage/v1/object/" + bucketName;
        log.info("[Supabase] Batch deleting {} objects in bucket={}", cleanPaths.size(), bucketName);

        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        int totalDeleted = 0;
        int batchSize = 100;

        for (int i = 0; i < cleanPaths.size(); i += batchSize) {
            List<String> subBatch = cleanPaths.subList(i, Math.min(i + batchSize, cleanPaths.size()));
            Map<String, Object> payload = Map.of("prefixes", subBatch);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.DELETE, requestEntity, String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    totalDeleted += subBatch.size();
                } else {
                    log.warn("[Supabase] Batch delete response status: {}", response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("[Supabase] Batch delete error for bucket {}: {}", bucketName, e.getMessage());
            }
        }

        return totalDeleted;
    }

    public int deleteImagesByUrls(List<String> publicUrls) {
        if (publicUrls == null || publicUrls.isEmpty()) {
            return 0;
        }

        Map<String, List<String>> bucketPathsMap = new HashMap<>();
        for (String url : publicUrls) {
            if (isSupabaseStorageUrl(url)) {
                String bucket = extractBucketName(url);
                String path = extractObjectPath(url);
                if (bucket != null && path != null && !path.isBlank()) {
                    bucketPathsMap.computeIfAbsent(bucket, k -> new ArrayList<>()).add(path);
                }
            }
        }

        int totalDeleted = 0;
        for (Map.Entry<String, List<String>> entry : bucketPathsMap.entrySet()) {
            totalDeleted += deleteImages(entry.getKey(), entry.getValue());
        }

        return totalDeleted;
    }

    public List<SupabaseFileObject> listObjects(String bucketName, String prefix, int limit, int offset) {
        if (bucketName == null) {
            return Collections.emptyList();
        }

        String apiUrl = supabaseUrl + "/storage/v1/object/list/" + bucketName;

        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("prefix", prefix != null ? prefix : "");
        payload.put("limit", limit > 0 ? limit : 100);
        payload.put("offset", Math.max(offset, 0));
        payload.put("sortBy", Map.of("column", "created_at", "order", "asc"));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<SupabaseFileObject[]> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, requestEntity, SupabaseFileObject[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }
        } catch (Exception e) {
            log.error("[Supabase] Failed to list objects in bucket {}: {}", bucketName, e.getMessage());
        }

        return Collections.emptyList();
    }

    public boolean isSupabaseStorageUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return false;
        }
        return publicUrl.contains("/storage/v1/object/");
    }

    public String extractBucketName(String publicUrl) {
        if (!isSupabaseStorageUrl(publicUrl)) {
            return null;
        }
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

    public String extractObjectPath(String publicUrl) {
        if (!isSupabaseStorageUrl(publicUrl)) {
            return null;
        }
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

    public String extractObjectPath(String publicUrl, String bucketName) {
        String detectedBucket = extractBucketName(publicUrl);
        if (detectedBucket == null || !detectedBucket.equalsIgnoreCase(bucketName)) {
            return null;
        }
        return extractObjectPath(publicUrl);
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabaseKey);
        headers.set("apikey", supabaseKey);
        return headers;
    }
}
