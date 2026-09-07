package com.codegym.mathclass.storage.dto;

import java.util.List;

/**
 * StoragePolicy chứa các quy tắc cấu hình lưu trữ tệp tin (dung lượng tối đa, loại MIME, đường dẫn).
 */
public record StoragePolicy(
        long maxSizeBytes,
        List<String> allowedMimeTypes,
        String folderPath
) {
    public static final StoragePolicy AVATAR = new StoragePolicy(
            2 * 1024 * 1024L, // 2MB
            List.of("image/png", "image/jpeg", "image/jpg", "image/webp"),
            "avatar"
    );

    public static final StoragePolicy ASSIGNMENT_IMAGE = new StoragePolicy(
            10 * 1024 * 1024L, // 10MB
            List.of("image/png", "image/jpeg", "image/jpg", "image/webp"),
            "assignment_image"
    );

    public static final StoragePolicy BUG_REPORT = new StoragePolicy(
            10 * 1024 * 1024L, // 10MB
            List.of("image/png", "image/jpeg", "image/jpg", "image/webp", "text/plain"),
            "bug_report"
    );
}
