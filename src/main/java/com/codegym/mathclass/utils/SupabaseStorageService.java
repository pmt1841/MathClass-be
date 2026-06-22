package com.codegym.mathclass.utils;

import com.codegym.mathclass.assignment.dto.AssignmentImageDto;
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

    private final String BUCKET_NAME = "assignment_image";

    public AssignmentImageDto uploadImage(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String uniqueFileName = UUID.randomUUID().toString() + extension;
        String objectPath = "images/" + uniqueFileName;
        String apiUrl = supabaseUrl + "/storage/v1/object/" + BUCKET_NAME + "/" + objectPath;

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabaseKey);
        headers.set("apikey", supabaseKey);
        headers.setContentType(
                MediaType.valueOf(file.getContentType() != null ? file.getContentType() : "application/octet-stream"));

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Lỗi khi upload ảnh lên Supabase: " + response.getBody());
        }

        String publicUrl = supabaseUrl + "/storage/v1/object/public/" + BUCKET_NAME + "/" + objectPath;

        String imageCode = "[IMAGE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "]";

        return new AssignmentImageDto(imageCode, publicUrl);
    }
}
