package com.codegym.mathclass.storage.strategy;

import com.codegym.mathclass.storage.dto.StoragePolicy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * LocalStorageStrategy triển khai lưu trữ tệp trực tiếp trên ổ đĩa cục bộ của Server (phục vụ Dev/Offline/Test).
 */
@Component("localStorageStrategy")
public class LocalStorageStrategy implements StorageStrategy {

    private static final String BASE_UPLOAD_DIR = "./uploads";

    @Override
    public String upload(byte[] fileData, String fileName, String contentType, StoragePolicy policy) {
        String extension = extractExtension(contentType, fileName);
        String uniqueFileName = UUID.randomUUID().toString() + extension;
        String subFolder = policy.folderPath();

        Path targetDir = Paths.get(BASE_UPLOAD_DIR, subFolder);
        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            Path targetFilePath = targetDir.resolve(uniqueFileName);
            Files.write(targetFilePath, fileData);

            return "/uploads/" + subFolder + "/" + uniqueFileName;
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu tệp vào ổ đĩa cục bộ: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
            return;
        }
        String relativePath = fileUrl.substring("/uploads/".length());
        Path filePath = Paths.get(BASE_UPLOAD_DIR, relativePath);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
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
