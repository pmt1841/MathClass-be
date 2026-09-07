package com.codegym.mathclass.classroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả đánh giá và gợi ý nhận xét từ AI")
public class AiStudentRemarkEvaluationResponse {

    @Schema(description = "Ngày bắt đầu quét dữ liệu", example = "2026-08-21")
    private LocalDate startDate;

    @Schema(description = "Ngày kết thúc quét dữ liệu", example = "2026-08-28")
    private LocalDate endDate;

    @Schema(description = "Tổng số bài tập đã giao trong lớp trong khoảng thời gian này", example = "10")
    private int totalAssignments;

    @Schema(description = "Tổng số bài tập học sinh đã nộp/hoàn thành trong khoảng thời gian này", example = "8")
    private int completedAssignments;

    @Schema(description = "Tổng số bài tập chưa nộp nhưng đã quá hạn", example = "1")
    private int overdueAssignments;

    @Schema(description = "Tổng số bài tập chưa nộp nhưng vẫn còn hạn nộp", example = "1")
    private int activeIncompleteAssignments;

    @Schema(description = "Điểm trung bình của các bài tập đã nộp (nếu có)", example = "8.5")
    private Double averageScore;

    @Schema(description = "Điểm mạnh và ưu điểm của học sinh do AI phân tích", example = "Nắm chắc kiến thức phương trình bậc hai, kỹ năng tính toán nhanh...")
    private String strengths;

    @Schema(description = "Điểm yếu và các lỗi sai cần cải thiện", example = "Thỉnh thoảng quên kiểm tra điều kiện của ẩn, làm bài còn vội vàng...")
    private String weaknesses;

    @Schema(description = "Đánh giá chung và phương pháp cải thiện", example = "Học sinh đã hoàn thành 8/10 bài tập trong khoảng thời gian từ 21/08/2026 đến 28/08/2026...")
    private String generalAssessment;

    @Schema(description = "Số lượng completion tokens từ AI", example = "450")
    private Integer completionTokens;
}
