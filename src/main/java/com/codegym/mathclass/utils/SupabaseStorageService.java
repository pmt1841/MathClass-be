package com.codegym.mathclass.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final java.util.List<String> ALLOWED_TYPES = java.util.Arrays.asList(
            "image/png", "image/jpeg", "image/jpg", "image/webp"
    );


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

        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String uniqueFileName = UUID.randomUUID().toString() + extension;
        String objectPath = "images/" + uniqueFileName;
        String apiUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + objectPath;

        System.out.println("[Supabase] Uploading to URL: " + apiUrl);

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabaseKey);
        headers.set("apikey", supabaseKey);
        headers.setContentType(MediaType.valueOf(contentType));

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileData, headers);

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Lỗi khi upload ảnh lên Supabase: " + response.getBody());
        }

        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + objectPath;
    }
}
