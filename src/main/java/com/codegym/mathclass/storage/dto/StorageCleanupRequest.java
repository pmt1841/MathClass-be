package com.codegym.mathclass.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu kích hoạt dọn dẹp bộ nhớ Cloud")
public class StorageCleanupRequest {

    @Schema(description = "Thời gian đệm an toàn tính theo giờ (mặc định 24 giờ)", example = "24")
    @Min(value = 0, message = "Thời gian đệm không được nhỏ hơn 0")
    @Builder.Default
    private Integer gracePeriodHours = 24;

    @Schema(description = "Chế độ chạy thử nghiệm - không xóa thật file (mặc định false)", example = "false")
    @Builder.Default
    private Boolean dryRun = false;
}
