package com.codegym.mathclass.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Trạng thái cấu hình và lịch sử dọn dẹp bộ nhớ Cloud")
public class StorageCleanupStatusResponse {

    @Schema(description = "Tiến trình dọn dẹp định kỳ có đang bật hay không", example = "true")
    private boolean enabled;

    @Schema(description = "Biểu thức Cron định kỳ", example = "0 0 3 * * SUN")
    private String cronExpression;

    @Schema(description = "Thời gian đệm an toàn tính theo giờ", example = "24")
    private int gracePeriodHours;

    @Schema(description = "Thời điểm chạy dọn dẹp gần nhất")
    private LocalDateTime lastRunAt;

    @Schema(description = "Kết quả lần dọn dẹp gần nhất")
    private StorageCleanupResponse lastRunResult;
}
