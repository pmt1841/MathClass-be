package com.codegym.mathclass.classroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request yêu cầu AI đánh giá tiến độ học sinh theo mốc thời gian")
public class AiStudentRemarkEvaluateRequest {

    @Min(value = 1, message = "Số ngày quét tối thiểu là 1")
    @Max(value = 365, message = "Số ngày quét tối đa là 365 ngày")
    @Schema(description = "Số ngày quét gần nhất (ví dụ: 3, 7, 30). Mặc định là 7 nếu không chọn ngày cụ thể", example = "7")
    private Integer days;

    @Schema(description = "Ngày bắt đầu quét (tùy chọn thay cho days)", example = "2026-08-01")
    private LocalDate startDate;

    @Schema(description = "Ngày kết thúc quét (tùy chọn thay cho days)", example = "2026-08-28")
    private LocalDate endDate;
}
