package com.codegym.mathclass.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả chi tiết tiến trình dọn dẹp bộ nhớ Cloud")
public class StorageCleanupResponse {

    @Schema(description = "Danh sách các bucket đã quét", example = "[\"avatar\", \"assignment_image\"]")
    private List<String> scannedBuckets;

    @Schema(description = "Tổng số file đã quét qua các bucket", example = "1540")
    private int totalFilesScanned;

    @Schema(description = "Số file mồ côi (không có trong CSDL và đủ tuổi) phát hiện", example = "42")
    private int orphanFilesDetected;

    @Schema(description = "Số file mồ côi đã xóa thành công trên Supabase", example = "42")
    private int filesDeletedSuccessfully;

    @Schema(description = "Số file xóa thất bại", example = "0")
    private int failedDeletions;

    @Schema(description = "Thời gian thực thi (mili-giây)", example = "1850")
    private long executionTimeMs;

    @Schema(description = "Chế độ chạy thử nghiệm", example = "false")
    private boolean dryRun;

    @Schema(description = "Thời điểm hoàn tất")
    private LocalDateTime completedAt;
}
