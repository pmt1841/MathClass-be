package com.codegym.mathclass.storage.service;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.storage.dto.StoragePolicy;
import com.codegym.mathclass.storage.strategy.StorageStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * StorageService là Lớp Facade / Dynamic Resolver điều phối việc lưu trữ tệp qua các StorageStrategy.
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    @Value("${storage.provider:SUPABASE}")
    private String activeProvider;

    private final Map<String, StorageStrategy> storageStrategies;

    /**
     * Xác định Storage Strategy active dựa theo cấu hình.
     */
    private StorageStrategy getActiveStrategy() {
        String beanName = activeProvider.trim().toLowerCase() + "StorageStrategy";
        StorageStrategy strategy = storageStrategies.get(beanName);
        if (strategy == null) {
            throw new IllegalStateException("Không tìm thấy Storage Strategy tương ứng với cấu hình: " + activeProvider);
        }
        return strategy;
    }

    /**
     * Tải tệp MultipartFile lên theo chính sách chỉ định.
     */
    public String upload(MultipartFile file, StoragePolicy policy) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tải lên không được để trống");
        }
        byte[] bytes = file.getBytes();
        validateFile(bytes.length, file.getContentType(), policy);
        return getActiveStrategy().upload(bytes, file.getOriginalFilename(), file.getContentType(), policy);
    }

    /**
     * Tải dữ liệu byte thô lên theo chính sách chỉ định.
     */
    public String upload(byte[] fileData, String fileName, String contentType, StoragePolicy policy) {
        if (fileData == null || fileData.length == 0) {
            throw new BadRequestException("Dữ liệu tệp không được để trống");
        }
        validateFile(fileData.length, contentType, policy);
        return getActiveStrategy().upload(fileData, fileName, contentType, policy);
    }

    /**
     * Xóa tệp theo URL.
     */
    public void delete(String fileUrl) {
        getActiveStrategy().delete(fileUrl);
    }

    private void validateFile(long fileSize, String contentType, StoragePolicy policy) {
        if (fileSize > policy.maxSizeBytes()) {
            long maxMb = policy.maxSizeBytes() / (1024 * 1024);
            throw new BadRequestException("Kích thước tệp không được vượt quá " + maxMb + "MB");
        }

        if (contentType != null && !policy.allowedMimeTypes().contains(contentType.toLowerCase())) {
            throw new BadRequestException("Chỉ chấp nhận các định dạng tệp: " + String.join(", ", policy.allowedMimeTypes()));
        }
    }
}
